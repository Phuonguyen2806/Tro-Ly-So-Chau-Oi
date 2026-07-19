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
import com.example.chauoi.dichVu.CauHinhDichVu
import com.example.chauoi.dichVu.DichVuLoader
import com.example.chauoi.dichVu.moUngDung

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

    // Danh sách dịch vụ nạp từ assets/services/*.json thay vì khai báo cứng danh sách class
    private lateinit var dsDichVu: List<CauHinhDichVu>

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

        ttsManager = TextToSpeechManager(this)
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

                val dichVuPhuHop = dsDichVu.find { dichVu ->
                    dichVu.tuKhoaGiongNoi.any { tuKhoa -> cleanSentence.contains(tuKhoa) }
                }

                if (dichVuPhuHop != null) {
                    ttsManager.speak(dichVuPhuHop.cauPhanHoiKhiMo)
                    btnMicro.postDelayed({
                        dichVuPhuHop.moUngDung(this@MainActivity)
                    }, 3500)
                } else {
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