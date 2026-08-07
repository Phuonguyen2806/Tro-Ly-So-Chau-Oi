package com.example.chauoi

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.chauoi.dichVu.CauHinhDichVu
import com.example.chauoi.dichVu.DichVuLoader
import com.example.chauoi.dichVu.moUngDung
import com.example.chauoi.tts.SpeechRecognitionManager
import com.example.chauoi.tts.TextToSpeechManager
import com.example.chauoi.tts.VoiceError
import com.example.chauoi.ai.GeminiHelper
import com.example.chauoi.service.ScreenReaderService
import com.example.chauoi.dichVu.PhienLamViec
import kotlinx.coroutines.launch
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val TAG = "ChauOiMainActivity"
    }
    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var ttsManager: TextToSpeechManager
    private lateinit var voiceErrorChecker: VoiceError
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var tvStatus: TextView
    private lateinit var btnMicro: CardView
    private lateinit var frameMicWrapper: FrameLayout
    private lateinit var cardYouMed: CardView
    private lateinit var cardVNeID: CardView
    private lateinit var cardVssID: CardView
    private lateinit var btnTroGiup: CardView

    // Danh sách dịch vụ nạp từ assets/services/*.json
    private lateinit var dsDichVu: List<CauHinhDichVu>
    private var lastScannedScreenText: String = ""

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
        frameMicWrapper = findViewById(R.id.frameMicWrapper)

        cardYouMed = findViewById(R.id.cardYouMed)
        cardVNeID = findViewById(R.id.cardVNeID)
        cardVssID = findViewById(R.id.cardVssID)
        btnTroGiup = findViewById(R.id.btnTroGiup)

        ttsManager = TextToSpeechManager(this)
        voiceErrorChecker = VoiceError()
        geminiHelper = GeminiHelper()

        checkRecordAudioPermission()
        initSpeechRecognizer()

        btnMicro.setOnClickListener {
            setListeningStateUI(true)
            ttsManager.stop()
            speechManager.startListening()
        }

        // Gán sự kiện chạm trực tiếp các thẻ dịch vụ
        cardYouMed.setOnClickListener {
            dsDichVu.find { it.tenPackage == "com.youmed.info" }?.moUngDung(this)
        }

        cardVNeID.setOnClickListener {
            dsDichVu.find { it.tenPackage == "com.vnid" }?.moUngDung(this)
        }

        cardVssID.setOnClickListener {
            dsDichVu.find { it.tenPackage == "com.bhxhapp" }?.moUngDung(this)
        }
        btnTroGiup.setOnClickListener {
            hienThiDialogHuongDanSuDung()
        }
    }

    override fun onResume() {
        super.onResume()
        // Mỗi lần quay lại app (kể cả sau khi ông bà vừa bật Cài Đặt xong) đều kiểm tra lại
        if (!isAccessibilityServiceEnabled()) {
            hienThiDialogBatAccessibility()
        }
    }

    /**
     * Kiểm tra xem ScreenReaderService đã được bật trong Cài Đặt > Trợ Năng chưa.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, ScreenReaderService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val enabledComponentName = ComponentName.unflattenFromString(colonSplitter.next())
            if (enabledComponentName != null && enabledComponentName == expectedComponentName) {
                return true
            }
        }
        return false
    }
    /**
     * Hiển thị dialog hướng dẫn 3 bước bật quyền Trợ Năng cho ông bà,
     * kèm đọc to hướng dẫn bằng giọng nói.
     */
    private fun hienThiDialogBatAccessibility() {
        val view = layoutInflater.inflate(R.layout.dialog_huong_dan_accessibility, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<CardView>(R.id.btnMoCaiDat).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        view.findViewById<TextView>(R.id.btnDeSau).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        ttsManager.speak(
            "Ông bà ơi, để cháu hướng dẫn được ông bà thao tác trên màn hình, " +
                    "mình cần bật quyền Trợ Năng trước ạ. Ông bà bấm nút Mở Cài Đặt Ngay, " +
                    "sau đó tìm chữ Cháu Ơi rồi bật lên giúp cháu nhé."
        )
    }

    /**
     * Hiển thị dialog giải thích cách dùng nút Loa (mic) và nút Con Mắt.
     */
    private fun hienThiDialogHuongDanSuDung() {
        val view = layoutInflater.inflate(R.layout.dialog_huong_dan_su_dung, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        view.findViewById<CardView>(R.id.btnDongHuongDan).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        ttsManager.speak(
            "Nút loa màu cam ở trên dùng để ông bà bấm rồi nói mong muốn. " +
                    "Nút con mắt màu xám ở dưới dùng để cháu xem màn hình và hướng dẫn bước tiếp theo ạ."
        )
    }

    private fun setListeningStateUI(isListening: Boolean) {
        if (isListening) {
            // ĐANG NGHE = MÀU XANH LÁ
            btnMicro.setCardBackgroundColor(ContextCompat.getColor(this, R.color.accent_mic_active))
            tvStatus.text = "🔴 Đang lắng nghe... Xin ông bà hãy nói"
            tvStatus.setBackgroundResource(R.drawable.bg_status_pill_listening)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_listening_text))
        } else {
            // SẴN SÀNG = MÀU CAM NỔI BẬT
            btnMicro.setCardBackgroundColor(ContextCompat.getColor(this, R.color.accent_mic_idle))
            tvStatus.text = "🎙️ Sẵn sàng lắng nghe ông bà"
            tvStatus.setBackgroundResource(R.drawable.bg_status_pill_ready)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ready_text))
        }
    }

    private fun setErrorStateUI(errorMessage: String) {
        btnMicro.setCardBackgroundColor(ContextCompat.getColor(this, R.color.accent_mic_error))
        tvStatus.text = errorMessage
        tvStatus.setBackgroundResource(R.drawable.bg_status_pill_error)
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error_text))
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
                setListeningStateUI(false)
                val cleanSentence = sentence.lowercase()
                tvStatus.text = "💬 Ông bà vừa nói: \"$sentence\""

                // 1. KIỂM TRA CƠ CHẾ PHÀN NÀN / SỬA SAI
                if (voiceErrorChecker.isUserComplaining(sentence)) {
                    // Xóa cache màn hình hiện tại để ép AI phân tích mới
                    val currentScreenKey = if (lastScannedScreenText.isNotBlank()) {
                        lastScannedScreenText.take(200)
                    } else {
                        "man_hinh_mac_dinh"
                    }

                    // Xóa khỏi cache toàn cục bên ScreenReaderService nếu có khớp
                    ScreenReaderService.screenResponseCache.remove(currentScreenKey.hashCode())

                    PhienLamViec.cauHoiGhiAmTamThoi = "Ông bà vừa báo bước trước bị sai ($sentence). Hãy hướng dẫn lại thật chi tiết bằng cách khác."

                    ttsManager.speak("Cháu đang xem lại màn hình để hướng dẫn chính xác hơn, ông bà đợi chút nhé.")

                    lifecycleScope.launch {
                        try {
                            val prompt = "Ông bà đang phàn nàn vì làm sai: $sentence. Hãy đọc lại nội dung và đưa ra hướng dẫn thay thế dễ hiểu hơn."
                            val freshInstruction = geminiHelper.hoiTuDo(prompt)
                            ttsManager.speak(freshInstruction)
                            tvStatus.text = freshInstruction
                        } catch (e: Exception) {
                            ttsManager.speak("Cháu xin lỗi, mạng bị lỗi, ông bà đợi cháu chút nhé.")
                        }
                    }
                    return@SpeechRecognitionManager
                }
                // 2. XỬ LÝ TÌM DỊCH VỤ DỰA TRÊN CẤU HÌNH JSON NẠP VÀO
                val dichVuPhuHop = dsDichVu.map { dichVu ->
                    val score = dichVu.tuKhoaGiongNoi.count { cleanSentence.contains(it) }
                    dichVu to score
                }.filter { it.second > 0 }
                    .maxByOrNull { it.second }?.first

                if (dichVuPhuHop != null) {
                    setListeningStateUI(false)
                    tvStatus.text = "💬 Ông bà vừa nói: \"$sentence\""
                    ttsManager.speak(dichVuPhuHop.cauPhanHoiKhiMo)
                    btnMicro.postDelayed({
                        dichVuPhuHop.moUngDung(this@MainActivity)
                    }, 3500)
                } else {
                    // 3. NẾU NÓI CÁC TÍNH NĂNG KHÔNG HỖ TRỢ (vd: khai sinh, khai tử,...) hoặc nói chung chung
                    val cauThongBaoChuaHoTro = "Chức năng này cháu chưa hỗ trợ, ông bà vui lòng chọn trực tiếp thẻ dịch vụ bên dưới nhé."
                    setErrorStateUI("⚠️ Chức năng này cháu chưa hỗ trợ: \"$sentence\"\nÔng bà hãy thử nói lại hoặc bấm thẻ dịch vụ bên dưới nhé.")
                    ttsManager.speak(cauThongBaoChuaHoTro)
                }
            },
            onErrorMsg = { error ->
                setErrorStateUI("⚠️ Lỗi ghi âm: $error\nÔng bà vui lòng bấm lại nút micro.")
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
        ttsManager.shutdown()
    }
}