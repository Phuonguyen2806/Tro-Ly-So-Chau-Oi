package com.example.chauoi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val YOUMED_PACKAGE = "com.youmed.info"
        private const val TAG = "ChauOiMainActivity"
    }

    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var ttsManager: TextToSpeechManager
    
    private lateinit var tvStatus: TextView
    private lateinit var btnMicro: CardView
    private lateinit var btnOpenYouMed: Button

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

        // Khởi tạo Text-to-Speech phát tiếng phản hồi
        ttsManager = TextToSpeechManager(this)

        // Kiểm tra và xin quyền thu âm ghi âm
        checkRecordAudioPermission()

        // Khởi tạo bộ lắng nghe giọng nói
        initSpeechRecognizer()

        // Thiết lập sự kiện click
        btnMicro.setOnClickListener {
            tvStatus.text = "Đang lắng nghe..."
            speechManager.startListening()
        }

        btnOpenYouMed.setOnClickListener {
            moUngDungYouMed()
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
                tvStatus.text = "Bạn vừa nói: \"$sentence\""
                // Xử lý AI nhận diện ý định (Intent Recognition)
                val cleanSentence = sentence.lowercase()
                if (cleanSentence.contains("đặt lịch") || cleanSentence.contains("khám") || cleanSentence.contains("cháu ơi")) {
                    ttsManager.speak("Cháu đang mở ứng dụng đặt lịch khám YouMed cho ông bà đây ạ!")
                    // Delay 3 giây để đọc xong câu rồi mới mở app YouMed
                    btnMicro.postDelayed({
                        moUngDungYouMed()
                    }, 3500)
                } else {
                    ttsManager.speak("Cháu chưa nghe rõ, ông bà vui lòng bấm lại micro và nói: đặt lịch khám.")
                }
            },
            onErrorMsg = { error ->
                tvStatus.text = "Lỗi: $error"
            }
        )
    }

    private fun moUngDungYouMed() {
        val launchIntent = packageManager.getLaunchIntentForPackage(YOUMED_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
            Log.d(TAG, "🚀 Đã mở ứng dụng YouMed")
        } else {
            // Nếu chưa cài đặt YouMed, mở Google Play để tải
            Toast.makeText(this, "Chưa cài YouMed. Đang mở CH Play...", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$YOUMED_PACKAGE")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$YOUMED_PACKAGE")))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
        ttsManager.shutdown()
    }
}