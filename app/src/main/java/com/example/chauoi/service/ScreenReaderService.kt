package com.example.chauoi.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import androidx.cardview.widget.CardView
import com.example.chauoi.R
import com.example.chauoi.ai.GeminiHelper
import com.example.chauoi.tts.SpeechRecognitionManager
import com.example.chauoi.tts.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.content.Intent
import android.util.LruCache
import com.example.chauoi.dichVu.CauHinhDichVu
import com.example.chauoi.dichVu.DichVuLoader
import com.example.chauoi.dichVu.PhienLamViec

class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "ChauOiService"
        private const val DELAY_MS = 800L
    }

    // Danh sách dịch vụ được nạp từ assets/services/*.json khi service khởi động.
    // Muốn thêm dịch vụ mới: chỉ cần thêm 1 file JSON vào assets/services/, KHÔNG cần sửa code ở đây.
    private lateinit var dsDichVu: List<CauHinhDichVu>

    private lateinit var ttsManager: TextToSpeechManager
    private var speechManager: SpeechRecognitionManager? = null

    private var buocTruocDo: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var docManHinhRunnable: Runnable? = null
    private var lastDynamicHash: Int = 0
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val geminiHelper = GeminiHelper()
    // Cờ chống gọi AI chồng lên nhau khi nhiều màn hình lạ xuất hiện liên tiếp
    private var dangHoiAI = false

    // Bộ nhớ đệm LRU Cache lưu phản hồi của AI theo mã Hash của màn hình (lưu tối đa 50 màn hình)
    private val screenResponseCache = LruCache<Int, String>(50)

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var currentTextContent: String = ""
    private var currentPackageName: String = ""
    private var manHinhDangHoiAI: String? = null


    // Chỗ DUY NHẤT còn giữ code cứng: các bước cần đọc nội dung ĐỘNG từ màn hình
    // (ví dụ số tiền thanh toán thật) thay vì câu tĩnh lấy từ JSON.
    // key = "tenPackage:idBuoc" -> hàm nhận allText, trả về câu cần đọc.
    private val xuLyDacBietMap: Map<String, (String) -> String> = mapOf(
        "com.youmed.info:buoc9_xac_nhan_thanh_toan" to { _ ->
            "Bạn hãy đọc kỹ thông tin và xác nhận thanh toán."
        }
        // Thêm dịch vụ/bước mới cần xử lý động: thêm 1 dòng ở đây,
        // với key = "<tenPackage>:<id trong JSON>"
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

        // Nạp cấu hình dịch vụ từ JSON thay vì danh sách class hard-code
        dsDichVu = DichVuLoader.taiTatCa(this)
        Log.d(TAG, "📦 Đã nạp ${dsDichVu.size} dịch vụ: ${dsDichVu.map { it.tenGoi }}")

        ttsManager = TextToSpeechManager(this)
        initSpeechRecognizer()
        initFloatingMicrophone()

        Log.d(TAG, "✅ Cháu Ơi Service đã khởi động!")
    }

    private fun initSpeechRecognizer() {
        try {
            speechManager = SpeechRecognitionManager(
                context = this,
                onResult = { sentence ->
                    val clean = sentence.lowercase()
                    Log.d(TAG, "🎙️ Service nghe thấy lệnh: \"$clean\"")
                    resetMicButtonUi()

                    // Bước 1: Kiểm tra có phải lệnh mở dịch vụ không
                    val dichVuPhuHop = dsDichVu.find { dv ->
                        dv.tuKhoaGiongNoi.any { clean.contains(it) }
                    }

                    if (dichVuPhuHop != null) {
                        // Kiểm tra: đang ở trong app này rồi không?
                        if (currentPackageName == dichVuPhuHop.tenPackage) {
                            // Đang trong app rồi → là câu hỏi trong app, KHÔNG mở lại!
                            Log.d(TAG, "💬 Đang trong ${dichVuPhuHop.tenGoi}, xử lý như câu hỏi")
                            ttsManager.speak("Ông bà đợi cháu một lát nhé.")
                            serviceScope.launch {
                                val answer = geminiHelper.askAssistant(
                                    screenText = currentTextContent,
                                    userQuestion = sentence,
                                    tenDichVu = dichVuPhuHop.tenGoi,
                                    mucDich = PhienLamViec.mucDichHienTai
                                )
                                ttsManager.speak(answer)
                                Log.d(TAG, "🤖 Gemini trả lời: $answer")
                            }
                        } else {
                            // Chưa mở app này → nhận diện mục đích và mở app
                            val mucDichPhuHop = dichVuPhuHop.mucDich.find { md ->
                                md.tuKhoaGiongNoi.any { clean.contains(it) }
                            }
                            val tenMucDich = mucDichPhuHop?.id ?: "chung"
                            PhienLamViec.mucDichHienTai = tenMucDich
                            Log.d(TAG, "🎯 Đã gán mục đích: $tenMucDich (${mucDichPhuHop?.tenGoi})")

                            ttsManager.speak(dichVuPhuHop.cauPhanHoiKhiMo)
                            val intent = packageManager
                                .getLaunchIntentForPackage(dichVuPhuHop.tenPackage)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                                Log.d(TAG, "🚀 Đã mở ứng dụng ${dichVuPhuHop.tenGoi}")
                            } else {
                                Log.w(TAG, "⚠️ Không tìm thấy app: ${dichVuPhuHop.tenPackage}")
                            }
                        }
                    } else {
                        // Không phải lệnh mở app → là câu hỏi → gửi Gemini trả lời
                        ttsManager.speak("Ông bà đợi cháu một lát nhé.")
                        serviceScope.launch {
                            val answer = geminiHelper.askAssistant(
                                screenText = currentTextContent,
                                userQuestion = sentence
                            )
                            ttsManager.speak(answer)
                            Log.d(TAG, "🤖 Gemini trả lời: $answer")
                        }
                    }
                },
                onErrorMsg = { error ->
                    Log.w(TAG, "SpeechRecognizer warning: $error")
                    resetMicButtonUi()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Không thể khởi tạo SpeechRecognizer trong Service", e)
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

        val cardMic = floatingView?.findViewById<CardView>(R.id.cardMic)
        val imgMic = floatingView?.findViewById<ImageView>(R.id.imgMic)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        floatingView?.setOnTouchListener { _, event ->
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

                    if (abs(diffX) > 50 || abs(diffY) > 50) {
                        isClick = false
                    }

                    layoutParams.x = initialX + diffX.toInt()
                    layoutParams.y = initialY + diffY.toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        ttsManager.stop()
                        cardMic?.setCardBackgroundColor(0xFF4CAF50.toInt())
                        Log.d(TAG, "🎙️ [Người dùng chạm Mic] Bắt đầu ghi âm giọng nói...")
                        speechManager?.startListening()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể add Floating View", e)
        }
    }

    private fun resetMicButtonUi() {
        val cardMic = floatingView?.findViewById<CardView>(R.id.cardMic)
        cardMic?.setCardBackgroundColor(0xFFFF7043.toInt())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val duocTheoDoi = dsDichVu.any { it.tenPackage == packageName }
        if (!duocTheoDoi) return

        val isRelevant = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!isRelevant) return

        docManHinhRunnable?.let { handler.removeCallbacks(it) }
        docManHinhRunnable = Runnable { docManHinh(packageName) }
        handler.postDelayed(docManHinhRunnable!!, DELAY_MS)
    }

    private fun docManHinh(packageName: String) {
        val dichVu = dsDichVu.find { it.tenPackage == packageName } ?: return
        val rootNode = rootInActiveWindow ?: return

        // Kiểm tra lại: nội dung đọc được có THẬT SỰ thuộc đúng app đang theo dõi không.
        // Tránh trường hợp giữa lúc chờ (DELAY_MS), màn hình bị 1 popup hệ thống
        // (gợi ý mật khẩu Google, thông báo...) hoặc launcher đè lên, khiến app
        // tưởng nhầm đang đọc VNeID/YouMed trong khi thực chất đang đọc app khác.
        val rootPackageName = rootNode.packageName?.toString()
        if (rootPackageName != dichVu.tenPackage) {
            rootNode.recycle()
            Log.d(TAG, "⏭ Bỏ qua vì cửa sổ hiện tại thuộc package khác: $rootPackageName")
            return
        }

        val allText = collectAllText(rootNode)
        rootNode.recycle()
        if (allText.isBlank()) return

        currentTextContent = allText
        currentPackageName = packageName

        val buocHienTai = DichVuLoader.timBuocPhuHop(dichVu.buoc, allText)
        Log.d(TAG, "📍 ${buocHienTai?.id ?: "Không nhận diện"}")

        if (buocHienTai == null) {
            // Không có bước nào trong JSON khớp -> nhờ AI sinh hướng dẫn tạm thời,
            // dựa trên cấu trúc Semantic UI Tree và bộ nhớ đệm Cache.
            val semanticTree = collectSemanticUITree(rootNode)
            xuLyManHinhChuaBietBangAI(dichVu.tenGoi, semanticTree.ifEmpty { allText })
            return
        }

        val khoaXuLyDacBiet = "$packageName:${buocHienTai.id}"
        val dong = if (buocHienTai.xuLyDacBiet) xuLyDacBietMap[khoaXuLyDacBiet]?.invoke(allText) else null

        if (buocHienTai.id != buocTruocDo) {
            buocTruocDo = buocHienTai.id
            val huongDan = dong ?: buocHienTai.layHuongDan(com.example.chauoi.dichVu.PhienLamViec.mucDichHienTai)
            lastDynamicHash = dong?.hashCode() ?: 0

            if (huongDan.isNotEmpty()) {
                ttsManager.speak(huongDan)
                Log.d(TAG, "🔊 $huongDan")
            }
        } else if (dong != null) {
            val currentHash = dong.hashCode()
            if (currentHash != lastDynamicHash) {
                lastDynamicHash = currentHash
                ttsManager.speak(dong)
                Log.d(TAG, "🔊 [Thông tin thay đổi] $dong")
            }
        }
    }

    override fun onInterrupt() {
        ttsManager.stop()
        speechManager?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        docManHinhRunnable?.let { handler.removeCallbacks(it) }
        ttsManager.shutdown()
        speechManager?.destroy()
        speechManager = null
        serviceScope.cancel()

        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Không thể gỡ bỏ floating view", e)
            }
        }
        floatingView = null
    }

    /**
     * Làm sạch text đọc từ màn hình trước khi gửi AI:
     * - Xoá khoảng trắng thừa (nhiều dấu cách liên tiếp → 1 dấu cách)
     * - Bỏ các từ trùng lặp liên tiếp (VD: "Chọn Chọn Chọn" → "Chọn")
     * - Giới hạn độ dài để tiết kiệm token
     */
    private fun lamSachText(text: String, maxLength: Int = 600): String {
        return text
            .replace(Regex("\\s+"), " ")       // Nhiều khoảng trắng → 1 khoảng trắng
            .replace(Regex("(\\S+)(\\s\\1)+"), "$1") // Bỏ từ trùng liên tiếp
            .trim()
            .take(maxLength)
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

    /**
     * Trích xuất cấu trúc giao diện theo ngữ nghĩa (Semantic UI Tree):
     * Phân loại Nút bấm (Clickable), Ô nhập (Editable) và Văn bản hiển thị.
     */
    private fun collectSemanticUITree(node: AccessibilityNodeInfo): String {
        val buttons = mutableListOf<String>()
        val inputs = mutableListOf<String>()
        val texts = mutableListOf<String>()

        fun traverse(n: AccessibilityNodeInfo) {
            val nodeText = (n.text ?: n.contentDescription ?: "").toString().trim()
            val className = (n.className ?: "").toString()

            if (nodeText.isNotEmpty() && nodeText.length < 50) {
                if (n.isClickable || className.contains("Button", ignoreCase = true)) {
                    if (!buttons.contains(nodeText)) buttons.add(nodeText)
                } else if (n.isEditable || className.contains("EditText", ignoreCase = true)) {
                    if (!inputs.contains(nodeText)) inputs.add(nodeText)
                } else {
                    if (!texts.contains(nodeText)) texts.add(nodeText)
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
        if (buttons.isNotEmpty()) {
            sb.append("[Nút bấm]: ").append(buttons.take(6).joinToString(", ")).append("\n")
        }
        if (inputs.isNotEmpty()) {
            sb.append("[Ô nhập]: ").append(inputs.take(4).joinToString(", ")).append("\n")
        }
        if (texts.isNotEmpty()) {
            sb.append("[Thông tin]: ").append(texts.take(8).joinToString(", "))
        }

        return sb.toString().trim()
    }

    private fun xuLyManHinhChuaBietBangAI(tenDichVu: String, semanticTree: String) {
        val screenHash = (tenDichVu + ":" + semanticTree).hashCode()

        // 1. Kiểm tra Cache phản hồi trước khi gọi AI
        val cachedResponse = screenResponseCache.get(screenHash)
        if (cachedResponse != null) {
            Log.d(TAG, "⚡ [CACHE HIT] Đọc từ Cache cho màn hình $tenDichVu (Hash: $screenHash)")
            ttsManager.speak(cachedResponse)
            manHinhDangHoiAI = semanticTree
            return
        }

        // 2. Tránh gọi AI trùng lặp
        if (semanticTree == manHinhDangHoiAI) return
        if (dangHoiAI) {
            Log.d(TAG, "⏳ Đang chờ AI phản hồi, bỏ qua màn hình mới")
            return
        }

        manHinhDangHoiAI = semanticTree
        dangHoiAI = true

        val mucDich = PhienLamViec.mucDichHienTai ?: "không rõ"

        serviceScope.launch {
            try {
                // Prompt tối ưu dạng Semantic UI Tree
                val prompt = """
                    Ứng dụng: $tenDichVu | Mục tiêu: $mucDich
                    Cấu trúc màn hình:
                    $semanticTree
                """.trimIndent()

                val huongDan = geminiHelper.hoiTuDo(prompt)

                // Lưu phản hồi vào Cache nếu phản hồi hợp lệ
                if (huongDan.isNotEmpty() && !huongDan.contains("quá tải")) {
                    screenResponseCache.put(screenHash, huongDan)
                }

                ttsManager.speak(huongDan)

                Log.w(TAG, "🤖 [AI HƯỚNG DẪN MÀN HÌNH MỚI]\n" +
                        "Dịch vụ: $tenDichVu | Mục đích: $mucDich\n" +
                        "Semantic Tree: $semanticTree\n" +
                        "AI trả lời: $huongDan")
            } finally {
                dangHoiAI = false
            }
        }
    }
}