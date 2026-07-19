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
import com.example.chauoi.dichvu.DichVu
import com.example.chauoi.dichvu.YouMedDichVu
import com.example.chauoi.dichvu.VNeIDDichVu

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

    // Khởi tạo danh sách dịch vụ (Khai báo 1 lần dùng chung cho mọi chức năng)
    private val dsDichVu: List<DichVu> = listOf(
        YouMedDichVu(),
        VNeIDDichVu()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        btnMicro = findViewById(R.id.btnMicro)
        btnOpenYouMed = findViewById(R.id.btnOpenYouMed)
        btnOpenVNeID = findViewById(R.id.btnOpenVNeID)

        // Khởi tạo Text-to-Speech phát tiếng phản hồi
        ttsManager = TextToSpeechManager(this)

        // Kiểm tra và xin quyền thu âm ghi âm
        checkRecordAudioPermission()

        // Khởi tạo bộ lắng nghe giọng nói
        initSpeechRecognizer()

        // Thiết lập sự kiện click
        btnMicro.setOnClickListener {
            tvStatus.text = "Đang lắng nghe..."
            btnMicro.setCardBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
            speechManager.startListening()
        }

        // Tìm dịch vụ tương ứng trong danh sách và gọi hàm moUngDung()
        btnOpenYouMed.setOnClickListener {
            dsDichVu.find { it is YouMedDichVu }?.moUngDung(this)
        }

        btnOpenVNeID.setOnClickListener {
            dsDichVu.find { it is VNeIDDichVu }?.moUngDung(this)
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

    private fun initSpeechRecognizer() {
        speechManager = SpeechRecognitionManager(
            context = this,
            onResult = { sentence ->
                btnMicro.setCardBackgroundColor(android.graphics.Color.parseColor("#FF7043"))
                tvStatus.text = "Bạn vừa nói: \"$sentence\""
                val cleanSentence = sentence.lowercase()

                // Duyệt qua danh sách dịch vụ xem có từ khóa nào khớp với câu nói không
                val dichVuPhuHop = dsDichVu.find { dichVu ->
                    dichVu.tuKhoaGiongNoi.any { tuKhoa -> cleanSentence.contains(tuKhoa) }
                }

                if (dichVuPhuHop != null) {
                    // Nếu tìm thấy, đọc câu phản hồi được cấu hình sẵn trong Class dịch vụ đó
                    ttsManager.speak(dichVuPhuHop.cauPhanHoiKhiMo)

                    // Chờ 3.5 giây cho ứng dụng nói xong rồi mới mở app
                    btnMicro.postDelayed({
                        dichVuPhuHop.moUngDung(this@MainActivity)
                    }, 3500)
                } else {
                    // Nếu nói bậy hoặc không nằm trong từ khóa nào
                    ttsManager.speak("Cháu chưa nghe rõ, ông bà vui lòng nói: đặt lịch khám, hoặc: làm lại căn cước.")
                }
            },
            onErrorMsg = { error ->
                btnMicro.setCardBackgroundColor(android.graphics.Color.parseColor("#FF7043"))
                tvStatus.text = "Lỗi: $error"
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
        ttsManager.shutdown()
    }
}