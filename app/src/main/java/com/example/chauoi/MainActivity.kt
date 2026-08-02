package com.example.chauoi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.chauoi.tts.SpeechRecognitionManager
import com.example.chauoi.tts.TextToSpeechManager
import com.example.chauoi.tts.VoiceError
import com.example.chauoi.dichVu.CauHinhDichVu
import com.example.chauoi.dichVu.DichVuLoader
import com.example.chauoi.dichVu.moUngDung
import com.example.chauoi.ai.GeminiHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val TAG = "ChauOiMainActivity"
    }

    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var ttsManager: TextToSpeechManager

    private lateinit var tvStatus: TextView
    private lateinit var btnMicro: CardView
    private lateinit var btnOpenYouMed: Button
    private lateinit var btnOpenVNeID: Button
    private lateinit var btnOpenVssID: Button
    private lateinit var geminiHelper: GeminiHelper

    // Danh sách dịch vụ nạp từ assets/services/*.json thay vì khai báo cứng danh sách class
    private lateinit var dsDichVu: List<CauHinhDichVu>
    private val screenCache = mutableMapOf<String, String>()
    private var lastScannedScreenText: String = "" // Cập nhật chuỗi này mỗi khiAccessibility quét được màn hình thực tế

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dsDichVu = DichVuLoader.taiTatCa(this)

        tvStatus = findViewById(R.id.tvStatus)
        btnMicro = findViewById(R.id.btnMicro)
        btnOpenYouMed = findViewById(R.id.btnOpenYouMed)
        btnOpenVNeID = findViewById(R.id.btnOpenVNeID)
        btnOpenVssID = findViewById(R.id.btnOpenVssID)

        ttsManager = TextToSpeechManager(this)
        geminiHelper = GeminiHelper()

        checkRecordAudioPermission()
        initSpeechRecognizer()

        btnMicro.setOnClickListener {
            tvStatus.text = "Đang lắng nghe..."
            btnMicro.setCardBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
            speechManager.startListening()
        }

        // Tìm dịch vụ theo tenPackage (giữ nguyên hành vi 2 nút cũ);
        // nếu thêm dịch vụ mới có nút riêng, chỉ cần thêm 1 dòng find tương tự.
        btnOpenYouMed.setOnClickListener {
            dsDichVu.find { it.tenPackage == "com.youmed.info" }?.moUngDung(this)
        }

        btnOpenVNeID.setOnClickListener {
            dsDichVu.find { it.tenPackage == "com.vnid" }?.moUngDung(this)
        }
        btnOpenVssID.setOnClickListener {
            dsDichVu.find { it.tenPackage == "com.bhxhapp" }?.moUngDung(this)
        }
    }

    private fun checkRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Đã cấp quyền ghi âm!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "App cần quyền ghi âm để nhận giọng nói", Toast.LENGTH_LONG).show()
            }
        }
    }

//    private fun initSpeechRecognizer() {
//        speechManager = SpeechRecognitionManager(
//            context = this,
//            onResult = { sentence ->
//                btnMicro.setCardBackgroundColor(android.graphics.Color.parseColor("#FF7043"))
//                tvStatus.text = "Bạn vừa nói: \"$sentence\""
//                val cleanSentence = sentence.lowercase()
//
//                val dichVuPhuHop = dsDichVu.map { dichVu ->
//                    // Đếm số từ khóa trong câu nói khớp với danh sách từ khóa của dịch vụ
//                    val score = dichVu.tuKhoaGiongNoi.count { cleanSentence.contains(it) }
//                    dichVu to score
//                }.filter { it.second > 0 } // Chỉ lấy những dịch vụ có điểm > 0
//                    .maxByOrNull { it.second }?.first // Chọn dịch vụ có nhiều từ khóa khớp nhất
//
//                if (dichVuPhuHop != null) {
//                    ttsManager.speak(dichVuPhuHop.cauPhanHoiKhiMo)
//                    btnMicro.postDelayed({
//                        dichVuPhuHop.moUngDung(this@MainActivity)
//                    }, 3500)
//                } else {
//                    ttsManager.speak("Cháu chưa nghe rõ, ông bà vui lòng nói: đặt lịch khám, hoặc: làm lại căn cước.")
//                }
//            },
//            onErrorMsg = { error ->
//                btnMicro.setCardBackgroundColor(android.graphics.Color.parseColor("#FF7043"))
//                tvStatus.text = "Lỗi: $error"
//            }
//        )
//    }
private fun initSpeechRecognizer() {
    val voiceErrorChecker = VoiceError()

    speechManager = SpeechRecognitionManager(
        context = this,
        onResult = { userSpeechText ->
            tvStatus.text = "Bạn vừa nói: \"$userSpeechText\""
            btnMicro.setCardBackgroundColor(android.graphics.Color.parseColor("#6200EE")) // Trả về màu cũ

            // 2. Trong hàm initSpeechRecognizer phần xử lý phàn nàn:
            if (voiceErrorChecker.isUserComplaining(userSpeechText)) {

                // 1. Kiểm tra xem đã có nội dung màn hình thực tế chưa
                val currentScreenKey = if (lastScannedScreenText.isNotBlank()) {
                    lastScannedScreenText.take(200)
                } else {
                    "man_hinh_mac_dinh" // Khóa dự phòng nếu người dùng phàn nàn ngay khi vừa mở app
                }

                if (currentScreenKey.isNotBlank()) {
                    if (screenCache.containsKey(currentScreenKey)) {
                        screenCache.remove(currentScreenKey)
                        android.util.Log.d(
                            "ChauOiMainActivity",
                            "Đã xóa cache lỗi của màn hình hiện tại."
                        )
                    }
                }

                tvStatus.text = "Đang xem lại màn hình, bác đợi chút..."
                ttsManager.speak("Cháu đang xem lại màn hình cho bác, đợi cháu một lát nhé.")

                lifecycleScope.launch {
                    try {
                        // Lấy lại nội dung màn hình thực tế mới nhất và gọi AI quét lại
                        val freshInstruction =
                            geminiHelper.hoiTuDo("Ông bà đang phàn nàn là chưa đúng. Hãy đọc lại màn hình hiện tại và hướng dẫn lại thật chính xác bước tiếp theo.")

                        ttsManager.speak(freshInstruction)
                        tvStatus.text = freshInstruction

                        if (currentScreenKey.isNotBlank()) {
                            screenCache[currentScreenKey] = freshInstruction
                        }
                    } catch (e: Exception) {
                        ttsManager.speak("Cháu xin lỗi, mạng bị lỗi, bác đợi cháu chút nhé.")
                    }
                }
            } else {
                // 3. Nếu KHÔNG phàn nàn -> Tiến hành dò xem họ muốn mở dịch vụ nào như bình thường
                val cleanSentence = userSpeechText.lowercase()

                val dichVuPhuHop = dsDichVu.map { dichVu ->
                    val score = dichVu.tuKhoaGiongNoi.count { cleanSentence.contains(it) }
                    dichVu to score
                }.filter { it.second > 0 }
                    .maxByOrNull { it.second }?.first

                if (dichVuPhuHop != null) {
                    ttsManager.speak(dichVuPhuHop.cauPhanHoiKhiMo)
                    btnMicro.postDelayed({
                        dichVuPhuHop.moUngDung(this@MainActivity)
                    }, 3500)
                } else {
                    ttsManager.speak("Cháu chưa nghe rõ, ông bà vui lòng nói: đặt lịch khám, hoặc: làm lại căn cước.")
                }
            }
        },
        onErrorMsg = { error ->
            tvStatus.text = "Lỗi: $error"
            btnMicro.setCardBackgroundColor(android.graphics.Color.parseColor("#FF7043"))
        }
    )
}/**
     * Dùng hàm này để cập nhật nội dung màn hình mới nhất
     * mỗi khi Accessibility Service hoặc bộ quét màn hình của nhóm bạn hoạt động.
     */
    fun updateCurrentScreenContent(scannedText: String) {
        if (!scannedText.isBlank()) {
            lastScannedScreenText = scannedText
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
        ttsManager.shutdown()
    }
}