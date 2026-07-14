package com.example.chauoi.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SpeechRecognitionManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onErrorMsg: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "ChauOiSpeech"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN") // Nhận diện tiếng Việt chuẩn
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "vi-VN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false) // Chỉ lấy kết quả cuối cùng hoàn chỉnh
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "🎙️ Bắt đầu lắng nghe giọng nói...")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "🎙️ Người dùng đang nói...")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "🎙️ Đã nói xong, đang phân tích...")
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Lỗi âm thanh"
                SpeechRecognizer.ERROR_CLIENT -> "Lỗi kết nối client"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thiếu quyền ghi âm RECORD_AUDIO"
                SpeechRecognizer.ERROR_NETWORK -> "Lỗi kết nối mạng"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hết thời gian mạng"
                SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận diện được từ khóa"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Hệ thống nhận diện đang bận"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy tiếng nói"
                else -> "Lỗi không xác định ($error)"
            }
            Log.e(TAG, "❌ Lỗi nhận diện: $message")
            onErrorMsg(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val bestMatch = matches[0]
                Log.d(TAG, "🎙️ Kết quả tốt nhất: $bestMatch")
                onResult(bestMatch)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
            Log.d(TAG, "✅ Khởi tạo SpeechRecognizer thành công")
        } else {
            Log.e(TAG, "❌ Thiết bị không hỗ trợ SpeechRecognizer")
        }
    }

    /**
     * Bắt đầu lắng nghe giọng nói người dùng
     */
    fun startListening() {
        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi bắt đầu ghi âm", e)
        }
    }

    /**
     * Dừng lắng nghe
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /**
     * Giải phóng tài nguyên
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
