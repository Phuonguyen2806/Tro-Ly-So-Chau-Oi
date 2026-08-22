package com.example.chauoi.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.cardview.widget.CardView
import com.example.chauoi.R
import com.example.chauoi.ai.GeminiHelper
import com.example.chauoi.ai.AIIntentMatcher
import com.example.chauoi.dichVu.CauHinhDichVu
import com.example.chauoi.dichVu.DichVuLoader
import com.example.chauoi.dichVu.PhienLamViec
import com.example.chauoi.tts.SpeechRecognitionManager
import com.example.chauoi.tts.TextToSpeechManager
import com.example.chauoi.tts.VoiceError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "ChauOiService"

        // Màu cho nút "Theo dõi" (nút trên): xám = tắt, xanh lá = đang quét.
        private val MAU_THEO_DOI_TAT = 0xFF9E9E9E.toInt()
        private val MAU_THEO_DOI_BAT = 0xFF4CAF50.toInt()

        // Màu cho nút "Hỏi" (nút dưới): Cam = rảnh, Xanh lá = đang ghi âm (đồng bộ ứng dụng).
        private val MAU_HOI_RANH = 0xFFFF7043.toInt()
        private val MAU_HOI_DANG_GHI_AM = 0xFF4CAF50.toInt()

        var cauTraLoiMoiNhat: String? = null // Lưu lại chính xác câu AI vừa đọc
        // Chia sẻ Cache ra ngoài để khi người dùng phàn nàn có thể xóa sạch cache màn hình cũ
        val screenResponseCache = LruCache<Int, String>(50)
    }

    private lateinit var voiceErrorChecker: VoiceError
    private lateinit var dsDichVu: List<CauHinhDichVu>
    private lateinit var ttsManager: TextToSpeechManager
    private var speechManager: SpeechRecognitionManager? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val geminiHelper = GeminiHelper()
    private lateinit var aiIntentMatcher: AIIntentMatcher
    private var dangHoiAI = false
    private var dangQuetManHinh = false // Cờ tránh bấm nút con mắt 2 lần liên tục

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var floatingLayoutParams: WindowManager.LayoutParams? = null

    private var currentTextContent: String = ""
    private var currentPackageName: String = ""
    private var lastEventTime = 0L
    private val micHandler = Handler(Looper.getMainLooper())


    override fun onServiceConnected() {
        super.onServiceConnected()

        // THÊM DÒNG NÀY ĐỂ CLEAR TRẠNG THÁI MỖI KHI CHẠY LẠI APP/SERVICE
        getSharedPreferences("chau_oi_prefs", MODE_PRIVATE).edit().clear().apply()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info

        dsDichVu = DichVuLoader.taiTatCa(this)
        ttsManager = TextToSpeechManager(this)
        aiIntentMatcher = AIIntentMatcher(this)
        voiceErrorChecker = VoiceError()
        initSpeechRecognizer()
        initFloatingMicrophone()
    }

    private fun initSpeechRecognizer() {
        try {
            speechManager = SpeechRecognitionManager(
                context = this,
                onReady = {
                    val cardHoi = floatingView?.findViewById<CardView>(R.id.cardHoi)
                    cardHoi?.setCardBackgroundColor(MAU_HOI_DANG_GHI_AM)
                },
                onSpeechEnded = {
                    resetNutHoiUi()
                },
                onResult = { sentence ->
                    val clean = sentence.lowercase()
                    Log.d(TAG, "🎙️ Service nghe thấy lệnh: \"$clean\"")
                    resetNutHoiUi()

                    // Lọc tiếng vọng
                    if (clean.contains("con mắt ở dưới") || clean.contains("đang xem màn hình") || clean.contains("đợi một lát")) {
                        return@SpeechRecognitionManager
                    }

                    // KIỂM TRA PHÀN NÀN
                    if (voiceErrorChecker.isUserComplaining(sentence)) {
                        if (currentTextContent.isNotBlank()) {
                            val activePackage = rootInActiveWindow?.packageName?.toString() ?: "chung"
                            val hashKey = activePackage + ":" + currentTextContent.replace(Regex("\\d+"), "#") + ":COMPLAINT"
                            screenResponseCache.remove(hashKey.hashCode())
                        }
                        val cauTruoc = cauTraLoiMoiNhat ?: "Chưa có hướng dẫn trước"
                        PhienLamViec.cauHoiGhiAmTamThoi = """
                            [CẢNH BÁO SỬA SAI]: 
                            - Hướng dẫn vừa đưa ra trước đó: "$cauTruoc"
                            - Phản hồi từ người dùng: "$sentence" (Báo bước trước bị sai hoặc không làm được).
                            - YÊU CẦU: Nhìn lại toàn bộ giao diện màn hình hiện tại. Bỏ qua hướng dẫn cũ. Hãy tìm nút bấm hoặc ô nhập liệu khác phù hợp hơn, hướng dẫn lại ngắn gọn, thật dễ hiểu cho người lớn tuổi.
                        """.trimIndent()
                        kichHoatQuetManHinh()
                        return@SpeechRecognitionManager
                    }
                    // KIỂM TRA YÊU CẦU ĐỌC LẠI -> TỰ ĐỘNG QUÉT VÀ ĐỌC LẠI TỪ CACHE (Không bắt bấm nút con mắt)
                    if (voiceErrorChecker.isUserAskingToRepeat(sentence)) {
                        val cauCu = cauTraLoiMoiNhat
                        if (!cauCu.isNullOrBlank()) {
                            Log.d(TAG, "🔊 Phát lại ngay câu trả lời gần nhất cho người dùng")
                            ttsManager.speak(cauCu)
                        } else {
                            // Trường hợp chưa có câu trả lời nào trước đó thì mới quét
                            kichHoatQuetManHinh()
                        }
                        return@SpeechRecognitionManager
                    }
                    // CÁC CÂU HỎI BÌNH THƯỜNG -> LƯU CÂU HỎI VÀ HƯỚNG DẪN BẤM NÚT CON MẮT ĐỂ QUÉT
                    val activePackage = rootInActiveWindow?.packageName?.toString()
                    if (activePackage != null) {
                        currentPackageName = activePackage
                    }
                    // 🟢 ĐÃ TÁCH BIỆT HOÀN TOÀN KHỎI ĐIỀU HƯỚNG APP 🟢
                    // Dù người dùng nói gì ở nút trôi nổi, ta cũng chỉ coi là hỏi về màn hình hiện tại. Không bao giờ nhảy app.
                    PhienLamViec.cauHoiGhiAmTamThoi = sentence
                    ttsManager.speak("Ông bà hãy chạm vào nút hình con mắt ở dưới, để cháu xem màn hình và trả lời nhé.")
                },
                onErrorMsg = { error ->
                    Log.w(TAG, "SpeechRecognizer warning: $error")
                    resetNutHoiUi()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Không thể khởi tạo SpeechRecognizer", e)
        }
    }


    @SuppressLint("ClickableViewAccessibility")

    /**
     * Tự động hiện bong bóng chữ cạnh 2 nút thật (không cần ông bà bấm icon hướng dẫn nào cả),
     * kèm đọc giọng nói. Mỗi "dấu mốc" (lần đầu chung, hoặc lần đầu vào từng app) chỉ hiện 1 lần.
     * Tự ẩn sau 6 giây.
     */
    private fun hienThiGoiYNutBongBongNeuCan(danhDauMoc: String, cauNoi: String) {
        val prefs = getSharedPreferences("chau_oi_prefs", MODE_PRIVATE)
        val key = "da_goi_y_nut_$danhDauMoc"
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()

        val tvHintHoi = floatingView?.findViewById<TextView>(R.id.tvHintHoi)
        val tvHintTheoDoi = floatingView?.findViewById<TextView>(R.id.tvHintTheoDoi)
        tvHintHoi?.visibility = View.VISIBLE
        tvHintTheoDoi?.visibility = View.VISIBLE
        ttsManager.speak(cauNoi)

        Handler(Looper.getMainLooper()).postDelayed({
            tvHintHoi?.visibility = View.GONE
            tvHintTheoDoi?.visibility = View.GONE
        }, 6000L)
    }


    private fun initFloatingMicrophone() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_mic, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 350
        }
        floatingLayoutParams = layoutParams

        val cardTheoDoi = floatingView?.findViewById<CardView>(R.id.cardTheoDoi)
        val cardHoi = floatingView?.findViewById<CardView>(R.id.cardHoi)
        cardTheoDoi?.setCardBackgroundColor(MAU_THEO_DOI_TAT)
        cardHoi?.setCardBackgroundColor(MAU_HOI_RANH)

        ganKeoThaVaCham(cardTheoDoi) { kichHoatQuetManHinh() }
        ganKeoThaVaCham(cardHoi) {
            // Nếu đang lắng nghe thì bấm lần nữa = TẮT mic, trả về màu cam
            if (speechManager?.isListening == true) {
                speechManager?.stopListening()
                ttsManager.stop()
                micHandler.removeCallbacksAndMessages(null)
                resetNutHoiUi()
                return@ganKeoThaVaCham
            }

            cardHoi?.isEnabled = false
            ttsManager.stop()

            micHandler.removeCallbacksAndMessages(null)

            micHandler.postDelayed({
                speechManager?.startListening()
                cardHoi?.isEnabled = true
            }, 500)

        }

        try {
            windowManager.addView(floatingView, layoutParams)
            // Tự động hiện bong bóng gợi ý ngay khi 2 nút vừa xuất hiện, không cần bấm icon hướng dẫn nào
            hienThiGoiYNutBongBongNeuCan(
                "lan_dau_chung",
                "Chào ông bà, đây là 2 nút trợ lý của Cháu Ơi. Nút cam bên trên để hỏi, nút xám bên dưới để cháu xem màn hình ạ."
            )
        } catch (e: Exception) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ganKeoThaVaCham(view: View?, onTap: () -> Unit) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        view?.setOnTouchListener { _, event ->
            val layoutParams = floatingLayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.rawX - initialTouchX
                    val diffY = event.rawY - initialTouchY
                    if (abs(diffX) > 50 || abs(diffY) > 50) isClick = false
                    layoutParams.x = initialX + diffX.toInt()
                    layoutParams.y = initialY + diffY.toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isClick) onTap()
                    true
                }

                else -> false
            }
        }
    }

    private fun resetNutHoiUi() {
        val cardHoi = floatingView?.findViewById<CardView>(R.id.cardHoi)
        cardHoi?.setCardBackgroundColor(MAU_HOI_RANH)
    }

    // LOGIC QUÉT MÀN HÌNH MỚI (1 LẦN VÀ TẮT)
    private fun kichHoatQuetManHinh() {
        if (dangQuetManHinh) return
        dangQuetManHinh = true
        rungPhanHoi()

        val cardTheoDoi = floatingView?.findViewById<CardView>(R.id.cardTheoDoi)
        cardTheoDoi?.setCardBackgroundColor(MAU_THEO_DOI_BAT)

        if (PhienLamViec.cauHoiGhiAmTamThoi != null) {
            ttsManager.speak("Cháu đang xem màn hình để trả lời, ông bà đợi một lát nhé.")
        } else {
            ttsManager.speak("Cháu đang xem màn hình, ông bà đợi một lát nhé.")
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            ttsManager.speak("Cháu không thấy màn hình nào cả.")
            tatNutTheoDoi()
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            thucHienQuetManHinhMotLan(rootNode)
        }
    }

    private fun tatNutTheoDoi() {
        Handler(Looper.getMainLooper()).post {
            val cardTheoDoi = floatingView?.findViewById<CardView>(R.id.cardTheoDoi)
            cardTheoDoi?.setCardBackgroundColor(MAU_THEO_DOI_TAT)
            dangQuetManHinh = false
        }
    }

    private fun thucHienQuetManHinhMotLan(rootNode: AccessibilityNodeInfo) {
        val packageName = rootNode.packageName?.toString() ?: ""
        currentPackageName = packageName

        val dichVu = dsDichVu.find { it.tenPackage == packageName }
        if (dichVu == null) {
            ttsManager.speak("Ứng dụng này chưa được cháu hỗ trợ ạ.")
            rootNode.recycle()
            tatNutTheoDoi()
            return
        }

        val semanticTreeGoc = collectSemanticUITree(rootNode)
        rootNode.recycle()
        if (semanticTreeGoc.isBlank()) {
            ttsManager.speak("Màn hình này trống, cháu không thấy thông tin gì ạ.")
            tatNutTheoDoi()
            return
        }

        val semanticTree = lamSachText(semanticTreeGoc, maxLength = 2000)
        currentTextContent = semanticTree

        // 🚀 CHUYỂN LUỒNG 100% SANG AI: Bỏ qua JSON, gửi thẳng cho Gemini AI quét tự do
        xuLyManHinhBangAI(dichVu.tenGoi, semanticTree)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // Chỉ xử lý khi có sự kiện CHUYỂN CỬA SỔ (mở app mới hoặc qua màn hình mới)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val dichVu = dsDichVu.find { it.tenPackage == packageName }
            if (dichVu != null) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastEventTime > 4000) {
                    if (packageName != currentPackageName) {
                        currentPackageName = packageName
                        ttsManager.speak(dichVu.cauChaoMung)
                        hienThiGoiYNutBongBongNeuCan(
                            "lan_dau_${packageName}",
                            "Ông bà chạm nút cam bên trên để hỏi, hoặc chạm nút xám bên dưới để cháu xem màn hình và hướng dẫn nhé."
                        )
                    } else {
                        ttsManager.speak(dichVu.cauNhanChuyenManHinh)
                    }
                    lastEventTime = currentTime
                }
            } else {
                // Ông bà đã rời app được hỗ trợ (về màn hình chính hoặc app khác)
                // -> Xóa mục đích cũ, tránh mục đích lần trước lẫn sang lần dùng sau
                if (currentPackageName != packageName) {
                    PhienLamViec.mucDichHienTai = null
                    PhienLamViec.cauHoiGhiAmTamThoi = null
                }
                currentPackageName = packageName
            }
        }
    }

    private fun xuLyManHinhBangAI(tenDichVu: String, noiDungManHinh: String) {
        val cauHoi = PhienLamViec.cauHoiGhiAmTamThoi
        // Nếu là phàn nàn, gắn thêm từ khóa "COMPLAINT" vào khóa băm để ép hệ thống tạo mới hoàn toàn
        val isComplaint = cauHoi != null && voiceErrorChecker.isUserComplaining(cauHoi)
        val hashSuffix =
            if (isComplaint) ":COMPLAINT:${System.currentTimeMillis()}" else ":${cauHoi ?: ""}"
        val hashKey = tenDichVu + ":" + noiDungManHinh.replace(Regex("\\d+"), "#") + hashSuffix
        val screenHash = hashKey.hashCode()
        // Nếu không phải phàn nàn thì mới check cache cũ
        if (!isComplaint) {
            val cachedResponse = screenResponseCache.get(screenHash)
            if (cachedResponse != null) {
                ttsManager.speak(cachedResponse)
                PhienLamViec.cauHoiGhiAmTamThoi = null
                tatNutTheoDoi()
                return
            }
        }
        if (dangHoiAI) {
            tatNutTheoDoi()
            return
        }

        dangHoiAI = true
        // Chuyển ID mục đích (vd: "xem_gplx") sang tên gọi con người đọc được (vd: "Xem giấy phép lái xe")
        // Trước đây gửi thẳng ID kỹ thuật khiến Gemini không hiểu, hay trả lời "chưa rõ màn hình"
        val mucDich = PhienLamViec.mucDichHienTai
            ?: "ông bà chưa nói rõ, hãy dựa vào toàn bộ nội dung màn hình để đoán việc cần làm tiếp theo"

        serviceScope.launch {
            try {
                // ĐƯA CÂU HỎI HOẶC LỜI PHÀN NÀN VÀO PROMPT ĐỂ AI BIẾT ĐƯỜNG SỬA ĐỔI
                val contextCauHoi =
                    if (cauHoi != null) "\nLƯU Ý QUAN TRỌNG TỪ ÔNG BÀ (CẦN SỬA ĐỔI NGAY): $cauHoi" else ""
                val prompt = """
                    Ứng dụng: $tenDichVu | Mục tiêu: $mucDich $contextCauHoi
                    Toàn bộ nội dung màn hình (gồm cả nút bấm, ô nhập, thông tin):
                    $noiDungManHinh
                """.trimIndent()

                val huongDan = geminiHelper.hoiTuDo(prompt)

                if (huongDan.isNotEmpty() && !huongDan.contains("quá tải") && !isComplaint) {
                    screenResponseCache.put(screenHash, huongDan)
                    // LƯU LẠI CÂU AI VỪA ĐỌC ĐỂ DÙNG KHI NGƯỜI DÙNG BẢO "ĐỌC LẠI"
                    cauTraLoiMoiNhat = huongDan
                }

                ttsManager.speak(huongDan)
                PhienLamViec.cauHoiGhiAmTamThoi = null // Giải phóng câu hỏi

            } finally {
                dangHoiAI = false
                withContext(Dispatchers.Main) {
                    tatNutTheoDoi() // BẮT BUỘC TẮT NÚT CON MẮT KHI XONG AI
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun rungPhanHoi() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(60)
        }
    }

    private fun lamSachText(text: String, maxLength: Int = 4000): String {
        return text.replace(Regex("\\s+"), " ")
            .replace(Regex("(\\S+)(\\s\\1)+"), "$1")
            .trim()
            .take(maxLength)
    }

    private fun collectSemanticUITree(node: AccessibilityNodeInfo): String {
        val nutBam = mutableListOf<String>()
        val oNhap = mutableListOf<String>()
        val thongTin = mutableListOf<String>()

        fun coTheDocDuoc(str: String): Boolean {
            // Bỏ qua chuỗi rỗng
            if (str.isBlank()) return false
            // Bỏ qua chuỗi quá ngắn (icon thường chỉ có 1-2 ký tự mô tả kỹ thuật)
            if (str.length < 2) return false
            // Bỏ qua nếu chuỗi chỉ gồm toàn ký tự đặc biệt/ASCII không có nghĩa
            // Giữ lại nếu có ít nhất 2 chữ cái có nghĩa (tiếng Việt hoặc chữ thường)
            val soKyTuCoNghia = str.count { it.isLetter() }
            if (soKyTuCoNghia < 2) return false
            return true
        }

        fun layTextNutBam(n: AccessibilityNodeInfo): String {
            // Ưu tiên n.text (văn bản hiển thị thật sự) trước
            // Chỉ dùng contentDescription nếu không có text và contentDescription đủ dài có nghĩa
            val fromText = n.text?.toString()?.trim() ?: ""
            val fromHint = n.hintText?.toString()?.trim() ?: ""
            val fromDesc = n.contentDescription?.toString()?.trim() ?: ""

            return when {
                fromText.isNotEmpty() -> fromText
                fromHint.isNotEmpty() -> fromHint
                // Chỉ dùng contentDescription nếu trông như chữ có nghĩa (không phải tên icon kỹ thuật)
                fromDesc.isNotEmpty() && coTheDocDuoc(fromDesc) && fromDesc.length >= 3 -> fromDesc
                else -> ""
            }
        }

        fun traverse(n: AccessibilityNodeInfo) {
            // ⭐ Bỏ qua các node không hiển thị trên màn hình (ví dụ: sidebar menu ẩn)
            if (!n.isVisibleToUser) return

            if (n.isEditable) {
                val text = (n.text ?: n.hintText ?: n.contentDescription ?: "").toString().trim()
                val tenONhap = text.ifEmpty { "Ô nhập liệu" }
                if (!oNhap.contains(tenONhap)) oNhap.add(tenONhap)
            } else {
                val text = layTextNutBam(n)
                if (text.isNotEmpty() && coTheDocDuoc(text)) {
                    if (n.isClickable) {
                        if (!nutBam.contains(text)) nutBam.add(text)
                    } else {
                        if (!thongTin.contains(text)) thongTin.add(text)
                    }
                }
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }
        traverse(node)

        val sb = StringBuilder()
        if (nutBam.isNotEmpty()) sb.append("[Nút bấm]: ").append(nutBam.joinToString(", "))
            .append("\n")
        if (oNhap.isNotEmpty()) sb.append("[Ô nhập]: ").append(oNhap.joinToString(", "))
            .append("\n")
        if (thongTin.isNotEmpty()) sb.append("[Thông tin]: ").append(thongTin.joinToString(", "))

        val result = sb.toString()
        // 🖨️ In ra Logcat để debug thực tế - xem màn hình nào bị lấy nhầm icon
        android.util.Log.d("ChauOiService", "📋 [SEMANTIC TREE]\n$result")
        return result
    }

    private fun collectAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(collectAllText(child))
            child.recycle()
        }
        return sb.toString()
    }

    override fun onInterrupt() {
        ttsManager.stop()
        speechManager?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
        speechManager?.destroy()
        speechManager = null
        serviceScope.cancel()

        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        floatingView = null
    }
}