package com.example.chauoi.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TextToSpeechManager(private val context: Context) {

    companion object {
        private const val TAG = "ChauOiTTS"
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {

                val result = tts?.setLanguage(Locale("vi", "VN"))

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                    Log.w(TAG, "⚠️ Không có giọng tiếng Việt, dùng tiếng Anh tạm")
                } else {
                    Log.d(TAG, "✅ Đã đặt ngôn ngữ tiếng Việt")
                }

                // Tốc độ đọc chậm hơn bình thường – phù hợp người cao tuổi
                tts?.setSpeechRate(0.85f)
                tts?.setPitch(1.0f)

                isReady = true
                Log.d(TAG, "✅ TTS sẵn sàng!")

                // Nếu có câu chờ từ trước thì đọc luôn
                pendingText?.let {
                    speak(it)
                    pendingText = null
                }

            } else {
                Log.e(TAG, "❌ Khởi tạo TTS thất bại, status = $status")
            }
        }
    }

    fun speak(text: String) {
        if (!isReady) {
            Log.w(TAG, "TTS chưa sẵn sàng, lưu lại: $text")
            pendingText = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CHAUOI_${System.currentTimeMillis()}")
        Log.d(TAG, "🔊 Đang đọc: $text")
    }

    fun stop() {
        tts?.stop()
        Log.d(TAG, "⏹ Đã dừng TTS")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.d(TAG, "🔴 TTS đã tắt")
    }
}
