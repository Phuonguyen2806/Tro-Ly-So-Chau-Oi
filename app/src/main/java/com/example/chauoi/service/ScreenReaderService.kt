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
import com.example.chauoi.utils.StepGuidance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "ChauOiService"
        private const val YOUMED_PACKAGE = "com.youmed.info"
        private const val DELAY_MS = 800L
    }

    private lateinit var ttsManager: TextToSpeechManager
    private var speechManager: SpeechRecognitionManager? = null

    private var buocTruocDo: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var docManHinhRunnable: Runnable? = null
    private var lastPaymentInfoHash: Int = 0

    // Cấu hình AI và Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val geminiHelper = GeminiHelper()

    // WindowManager để vẽ nút Micro nổi đè màn hình
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var currentTextContent: String = ""

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

        ttsManager = TextToSpeechManager(this)

        // Khởi tạo Speech Recognizer lắng nghe khi người dùng bấm nút nổi
        initSpeechRecognizer()

        // Khởi tạo và vẽ nút Micro nổi lên màn hình
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

                    // Reset màu sắc Micro khi nhận diện xong
                    resetMicButtonUi()

                    // Gửi câu hỏi lên Gemini AI
                    ttsManager.speak("Ông bà đợi cháu một lát nhé.")
                    serviceScope.launch {
                        val answer = geminiHelper.askAssistant(currentTextContent, sentence)
                        ttsManager.speak(answer)
                        Log.d(TAG, "🤖 Gemini trả lời: $answer")
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
        
        // Inflate view nổi
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_mic, null)

        // Cài đặt vị trí layout đè lên các ứng dụng khác
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // Dùng TYPE này để không cần xin thêm quyền vẽ phức tạp ở Realme
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 350
        }

        val cardMic = floatingView?.findViewById<CardView>(R.id.cardMic)
        val imgMic = floatingView?.findViewById<ImageView>(R.id.imgMic)

        // Sự kiện chạm để kéo thả hoặc bấm nút Micro
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
                    
                    // Nếu kéo đi xa quá 50px thì không coi là click nữa (tránh màn hình quá nhạy)
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
                        // Click vào nút micro
                        ttsManager.stop() // Dừng TTS ngay lập tức để không thu âm tạp âm
                        
                        // Đổi màu Micro sang màu Xanh báo hiệu đang nghe
                        cardMic?.setCardBackgroundColor(0xFF4CAF50.toInt())
                        
                        Log.d(TAG, "🎙️ [Người dùng chạm Mic] Bắt đầu ghi âm giọng nói...")
                        speechManager?.startListening()
                    }
                    true
                }
                else -> false
            }
        }

        // Vẽ view lên màn hình
        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể add Floating View", e)
        }
    }

    private fun resetMicButtonUi() {
        val cardMic = floatingView?.findViewById<CardView>(R.id.cardMic)
        cardMic?.setCardBackgroundColor(0xFFFF7043.toInt()) // Đổi về màu cam ban đầu
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != YOUMED_PACKAGE) return

        val isRelevant = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                         event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!isRelevant) return

        docManHinhRunnable?.let { handler.removeCallbacks(it) }
        docManHinhRunnable = Runnable { docManHinh() }
        handler.postDelayed(docManHinhRunnable!!, DELAY_MS)
    }

    private fun docManHinh() {
        val rootNode = rootInActiveWindow ?: return
        val allText = collectAllText(rootNode)
        rootNode.recycle()
        if (allText.isBlank()) return

        // Lưu trữ text hiện tại để sử dụng khi người dùng yêu cầu hướng dẫn lại
        currentTextContent = allText

        Log.d(TAG, "📄 $allText")
        val buocHienTai = nhanDienBuoc(allText)
        Log.d(TAG, "📍 $buocHienTai")

        // Nếu chuyển sang bước khác thì reset hash thanh toán
        if (buocHienTai != "Bước 9: Xác nhận & Thanh toán") {
            lastPaymentInfoHash = 0
        }

        if (buocHienTai != buocTruocDo && buocHienTai != "Không nhận diện") {
            buocTruocDo = buocHienTai
            
            val huongDan = if (buocHienTai == "Bước 9: Xác nhận & Thanh toán") {
                val extracted = trichXuatThongTinThanhToan(allText)
                lastPaymentInfoHash = extracted.hashCode()
                extracted
            } else {
                StepGuidance.getGuidance(buocHienTai)
            }
            
            if (huongDan.isNotEmpty()) {
                ttsManager.speak(huongDan)
                Log.d(TAG, "🔊 $huongDan")
            }
        } else if (buocHienTai == "Bước 9: Xác nhận & Thanh toán") {
            val extracted = trichXuatThongTinThanhToan(allText)
            val currentHash = extracted.hashCode()
            
            if (currentHash != lastPaymentInfoHash) {
                lastPaymentInfoHash = currentHash
                buocTruocDo = buocHienTai
                ttsManager.speak(extracted)
                Log.d(TAG, "🔊 [Thông tin thay đổi] $extracted")
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
        serviceScope.cancel() // Hủy scope khi tắt service
        
        // Xóa nút Micro nổi khi tắt Service
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Không thể gỡ bỏ floating view", e)
            }
        }
        floatingView = null
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

    private fun nhanDienBuoc(text: String): String {
        return when {
            text.contains("mật khẩu không chính xác", ignoreCase = true) ->
                "Lỗi: Đăng nhập sai"

            text.contains("Thông tin đăng nhập", ignoreCase = true) &&
            text.contains("Nhập mật khẩu", ignoreCase = true) ->
                "Bước 1: Đăng nhập"

            text.contains("Xác nhận thông tin", ignoreCase = true) &&
            text.contains("Thanh toán", ignoreCase = true) ->
                "Bước 9: Xác nhận & Thanh toán"

            text.contains("Đây có phải hồ sơ của bạn không", ignoreCase = true) ->
                "Xác nhận hồ sơ"

            text.contains("Kết quả tìm kiếm", ignoreCase = true) &&
            text.contains("Bệnh viện Lê Văn Thịnh", ignoreCase = true) ->
                "Kết quả tìm hồ sơ"

            text.contains("Tra cứu hồ sơ bệnh nhân", ignoreCase = true) &&
            text.contains("Mã Bệnh Nhân", ignoreCase = true) ->
                "Tra cứu hồ sơ"

            text.contains("Chọn Giờ khám", ignoreCase = true) &&
            text.contains("6 Giờ khám", ignoreCase = true) ->
                "Bước 8b: Chọn giờ khám"

            text.contains("Chọn Ngày khám", ignoreCase = true) &&
            text.contains("5 Ngày khám", ignoreCase = true) ->
                "Bước 8: Chọn ngày khám"

            text.contains("Chọn Bệnh nhân", ignoreCase = true) &&
            text.contains("4 Bệnh nhân", ignoreCase = true) ->
                "Bước 7: Chọn bệnh nhân"

            text.contains("Chọn Phòng khám", ignoreCase = true) &&
            text.contains("3 Phòng khám", ignoreCase = true) ->
                "Bước 6: Chọn phòng khám"

            text.contains("Chọn Chuyên khoa", ignoreCase = true) &&
            text.contains("2 Chuyên khoa", ignoreCase = true) ->
                "Bước 5: Chọn chuyên khoa"

            text.contains("Chọn Đối tượng khám", ignoreCase = true) &&
            text.contains("Khám Dịch Vụ", ignoreCase = true) ->
                "Bước 4: Chọn đối tượng"

            text.contains("Nơi khám: Bệnh viện", ignoreCase = true) &&
            text.contains("Đặt lịch ngay", ignoreCase = true) ->
                "Bước 3: Chọn bệnh viện"

            text.contains("Tiêm chủng", ignoreCase = true) &&
            text.contains("Trang chủ", ignoreCase = true) ->
                "Trang chủ"

            text.contains("Đặt lịch thành công", ignoreCase = true) ||
            text.contains("Đặt khám thành công", ignoreCase = true) ->
                "Hoàn thành"

            else -> "Không nhận diện"
        }
    }

    private fun trichXuatThongTinThanhToan(text: String): String {
        return "Bạn hãy đọc kỹ thông tin và xác nhận thanh toán."
    }
}
