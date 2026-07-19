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
import com.example.chauoi.dichvu.DichVu
import com.example.chauoi.dichvu.YouMedDichVu
import com.example.chauoi.dichvu.VNeIDDichVu

class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "ChauOiService"
        private const val DELAY_MS = 800L
    }

    // Danh sách dịch vụ được hỗ trợ. Muốn thêm dịch vụ mới:
// tạo 1 class implement DichVu trong package "dichvu", rồi thêm 1 dòng vào đây.
    private val dsDichVu: List<DichVu> = listOf(
        YouMedDichVu(),
        VNeIDDichVu()
    )

    private lateinit var ttsManager: TextToSpeechManager
    private var speechManager: SpeechRecognitionManager? = null

    private var buocTruocDo: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var docManHinhRunnable: Runnable? = null
    private var lastDynamicHash: Int = 0
    // Cấu hình AI và Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val geminiHelper = GeminiHelper()

    // WindowManager để vẽ nút Micro nổi đè màn hình
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var currentTextContent: String = ""
    private var currentPackageName: String = ""

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
        val allText = collectAllText(rootNode)
        rootNode.recycle()
        if (allText.isBlank()) return

        // Lưu trữ text hiện tại để sử dụng khi người dùng yêu cầu hướng dẫn lại
        currentTextContent = allText
        currentPackageName = packageName

        Log.d(TAG, "📄 [${dichVu.tenGoi}] $allText")
        val buocHienTai = dichVu.nhanDienBuoc(allText)
        Log.d(TAG, "📍 $buocHienTai")

        if (buocHienTai != buocTruocDo && buocHienTai != "Không nhận diện") {
            buocTruocDo = buocHienTai

            val dong = dichVu.xuLyDacBiet(buocHienTai, allText)
            val huongDan = if (dong != null) {
                lastDynamicHash = dong.hashCode()
                dong
            } else {
                lastDynamicHash = 0
                dichVu.layHuongDan(buocHienTai)
            }

            if (huongDan.isNotEmpty()) {
                ttsManager.speak(huongDan)
                Log.d(TAG, "🔊 $huongDan")
            }
        } else {
            val dong = dichVu.xuLyDacBiet(buocHienTai, allText)
            if (dong != null) {
                val currentHash = dong.hashCode()
                if (currentHash != lastDynamicHash) {
                    lastDynamicHash = currentHash
                    buocTruocDo = buocHienTai
                    ttsManager.speak(dong)
                    Log.d(TAG, "🔊 [Thông tin thay đổi] $dong")
                }
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
}
