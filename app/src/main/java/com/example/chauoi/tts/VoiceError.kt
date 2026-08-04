package com.example.chauoi.tts
import java.util.Locale

class VoiceError {

    private val complaintKeywords = listOf(
        "chưa được", "sai rồi", "nói lại", "không đúng",
        "khác rồi", "cháu ơi chưa được", "hướng dẫn lại", "nhầm rồi",
        "vẫn thế", "không phải", "đâu ra", "làm sao", "lại sai", "trật lất"
    )

    /**
     * Kiểm tra xem câu nói của người dùng có phải là câu phàn nàn/báo sai không
     */
    fun isUserComplaining(userSpeechText: String?): Boolean {
        if (userSpeechText.isNullOrBlank()) return false
        val lowerText = userSpeechText.lowercase(Locale.ROOT)
        return complaintKeywords.any { keyword -> lowerText.contains(keyword) }
    }
}