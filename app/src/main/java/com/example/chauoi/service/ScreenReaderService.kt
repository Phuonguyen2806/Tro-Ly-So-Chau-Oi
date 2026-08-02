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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.cardview.widget.CardView
import com.example.chauoi.R
import com.example.chauoi.ai.GeminiHelper
import com.example.chauoi.dichVu.CauHinhDichVu
import com.example.chauoi.dichVu.DichVuLoader
import com.example.chauoi.dichVu.PhienLamViec
import com.example.chauoi.tts.SpeechRecognitionManager
import com.example.chauoi.tts.TextToSpeechManager
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

        // Màu cho nút "Hỏi" (nút dưới): chàm = rảnh, đỏ = đang ghi âm.
        private val MAU_HOI_RANH = 0xFF3949AB.toInt()
        private val MAU_HOI_DANG_GHI_AM = 0xFFE53935.toInt()
    }

    private lateinit var dsDichVu: List<CauHinhDichVu>
    private lateinit var ttsManager: TextToSpeechManager
    private var speechManager: SpeechRecognitionManager? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val geminiHelper = GeminiHelper()

    private var dangHoiAI = false
    private var dangQuetManHinh = false // Cờ tránh bấm nút con mắt 2 lần liên tục

    private val screenResponseCache = LruCache<Int, String>(50)

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var floatingLayoutParams: WindowManager.LayoutParams? = null

    private var currentTextContent: String = ""
    private var currentPackageName: String = ""
    private var lastEventTime = 0L

    private val xuLyDacBietMap: Map<String, (String) -> String> = mapOf(
        "com.youmed.info:buoc9_xac_nhan_thanh_toan" to { _ ->
            "Bạn hãy đọc kỹ thông tin và xác nhận thanh toán."
        }
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
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
        initSpeechRecognizer()
        initFloatingMicrophone()
        huongDanLanDauNeuCan()
    }

    private fun huongDanLanDauNeuCan() {
        val prefs = getSharedPreferences("chau_oi_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("da_huong_dan_su_dung", false)) return
        Handler(Looper.getMainLooper()).postDelayed({
            ttsManager.speak(
                "Chào ông bà, đây là 2 nút trợ lý của Cháu Ơi ạ. " +
                        "Khi muốn hỏi, ông bà chạm nút micro bên trên. " +
                        "Sau đó, chạm nút con mắt bên dưới để cháu quét màn hình và hướng dẫn nhé."
            )
        }, 1500L)
        prefs.edit().putBoolean("da_huong_dan_su_dung", true).apply()
    }

    private fun initSpeechRecognizer() {
        try {
            speechManager = SpeechRecognitionManager(
                context = this,
                onResult = { sentence ->
                    val clean = sentence.lowercase()
                    Log.d(TAG, "🎙️ Service nghe thấy lệnh: \"$clean\"")
                    resetNutHoiUi()

                    // Chủ động lấy package hiện hành để kiểm tra đang ở đâu
                    val activePackage = rootInActiveWindow?.packageName?.toString()
                    if (activePackage != null) {
                        currentPackageName = activePackage
                    }

                    val dichVuPhuHop = dsDichVu.find { dv ->
                        dv.tuKhoaGiongNoi.any { clean.contains(it) }
                    }

                    if (dichVuPhuHop != null) {
                        if (currentPackageName == dichVuPhuHop.tenPackage) {
                            // Đang trong app rồi -> Lưu câu hỏi, bảo ng dùng bấm mắt để quét
                            PhienLamViec.cauHoiGhiAmTamThoi = sentence
                            ttsManager.speak("Ông bà hãy chạm vào nút hình con mắt ở dưới, để cháu quét màn hình này và trả lời nhé.")
                        } else {
                            // Mở app mới
                            val mucDichPhuHop = dichVuPhuHop.mucDich.find { md ->
                                md.tuKhoaGiongNoi.any { clean.contains(it) }
                            }
                            PhienLamViec.mucDichHienTai = mucDichPhuHop?.id ?: "chung"

                            ttsManager.speak(dichVuPhuHop.cauPhanHoiKhiMo)
                            val intent = packageManager.getLaunchIntentForPackage(dichVuPhuHop.tenPackage)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            }
                        }
                    } else {
                        // Câu hỏi tự do -> Lưu câu hỏi, bảo ng dùng bấm mắt để quét
                        PhienLamViec.cauHoiGhiAmTamThoi = sentence
                        ttsManager.speak("Ông bà hãy chạm vào nút hình con mắt ở trên, để cháu xem màn hình và hướng dẫn nhé.")
                    }
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
            ttsManager.stop()
            cardHoi?.setCardBackgroundColor(MAU_HOI_DANG_GHI_AM)
            speechManager?.startListening()
        }

        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) { }
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

    // =====================================================================
    // LOGIC QUÉT MÀN HÌNH MỚI (1 LẦN VÀ TẮT)
    // =====================================================================

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

        thucHienQuetManHinhMotLan()
    }

    private fun tatNutTheoDoi() {
        val cardTheoDoi = floatingView?.findViewById<CardView>(R.id.cardTheoDoi)
        cardTheoDoi?.setCardBackgroundColor(MAU_THEO_DOI_TAT)
        dangQuetManHinh = false
    }

    private fun thucHienQuetManHinhMotLan() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            ttsManager.speak("Cháu không thấy màn hình nào cả.")
            tatNutTheoDoi()
            return
        }

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
        xuLyManHinhChuaBietBangAI(dichVu.tenGoi, semanticTree)
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
                    } else {
                        ttsManager.speak(dichVu.cauNhanChuyenManHinh)
                    }
                    lastEventTime = currentTime
                }
            } else {
                currentPackageName = packageName
            }
        }
    }

    private fun xuLyManHinhChuaBietBangAI(tenDichVu: String, noiDungManHinh: String) {
        val cauHoi = PhienLamViec.cauHoiGhiAmTamThoi
        val hashKey = tenDichVu + ":" + noiDungManHinh.replace(Regex("\\d+"), "#") + ":" + (cauHoi ?: "")
        val screenHash = hashKey.hashCode()

        val cachedResponse = screenResponseCache.get(screenHash)
        if (cachedResponse != null) {
            ttsManager.speak(cachedResponse)
            PhienLamViec.cauHoiGhiAmTamThoi = null
            tatNutTheoDoi()
            return
        }

        if (dangHoiAI) {
            tatNutTheoDoi()
            return
        }

        dangHoiAI = true
        val mucDich = PhienLamViec.mucDichHienTai ?: "không rõ"

        serviceScope.launch {
            try {
                // Ghép nối câu hỏi nếu người dùng vừa mới bấm Mic hỏi trước đó
                val contextCauHoi = if (cauHoi != null) "\nÔng bà vừa hỏi: $cauHoi" else ""
                val prompt = """
                    Ứng dụng: $tenDichVu | Mục tiêu: $mucDich $contextCauHoi
                    Toàn bộ nội dung màn hình (gồm cả nút bấm, ô nhập, thông tin):
                    $noiDungManHinh
                """.trimIndent()

                val huongDan = geminiHelper.hoiTuDo(prompt)

                if (huongDan.isNotEmpty() && !huongDan.contains("quá tải")) {
                    screenResponseCache.put(screenHash, huongDan)
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

        fun traverse(n: AccessibilityNodeInfo) {
            val text = (n.text ?: n.hintText ?: n.contentDescription ?: "").toString().trim()
            if (n.isEditable) {
                val tenONhap = text.ifEmpty { "Ô nhập liệu" }
                if (!oNhap.contains(tenONhap)) oNhap.add(tenONhap)
            } else if (text.isNotEmpty()) {
                if (n.isClickable) {
                    if (!nutBam.contains(text)) nutBam.add(text)
                } else {
                    if (!thongTin.contains(text)) thongTin.add(text)
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
        if (nutBam.isNotEmpty()) sb.append("[Nút bấm]: ").append(nutBam.joinToString(", ")).append("\n")
        if (oNhap.isNotEmpty()) sb.append("[Ô nhập]: ").append(oNhap.joinToString(", ")).append("\n")
        if (thongTin.isNotEmpty()) sb.append("[Thông tin]: ").append(thongTin.joinToString(", "))
        return sb.toString()
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
            } catch (e: Exception) { }
        }
        floatingView = null
    }
}