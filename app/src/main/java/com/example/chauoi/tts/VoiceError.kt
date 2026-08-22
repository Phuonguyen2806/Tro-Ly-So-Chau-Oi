package com.example.chauoi.tts
import java.util.Locale

class VoiceError {

    // Danh sách từ khóa dành riêng cho PHÀN NÀN / BÁO BƯỚC BỊ SAI (Cần xóa Cache quét lại)
    private val complaintKeywords = listOf(
        "chưa được", "sai rồi", "không đúng", "khác rồi",
        "cháu ơi chưa được", "nhầm rồi", "vẫn thế", "không phải",
        "đâu ra", "lại sai", "trật lất", "đâu có được", "không được",
        "sai bét", "chỉ bậy", "bị lỗi", "làm không được", "sao không thấy"
    )
    // Danh sách từ khóa dành riêng cho YÊU CẦU ĐỌC LẠI / NGHE LẠI (Tận dụng Cache phát ngay)
    private val repeatKeywords = listOf(
        "đọc lại", "nói lại", "phát lại", "nghe lại", "nhắc lại",
        "chưa nghe rõ", "không nghe rõ", "nói lại lần nữa", "nói lại xem",
        "đọc lại hướng dẫn", "đọc lại nội dung", "chưa kịp nghe",
        "chưa hiểu", "chưa rõ", "hướng dẫn lại", "nói lại coi", "đọc lại coi",
        "nói chậm lại", "đọc chậm thôi", "nói to lên"
    )
    /**
     * Kiểm tra xem câu nói của người dùng có phải là câu phàn nàn/báo sai không
     */
    fun isUserComplaining(userSpeechText: String?): Boolean {
        if (userSpeechText.isNullOrBlank()) return false
        val lowerText = userSpeechText.lowercase(Locale.ROOT)
        return complaintKeywords.any { keyword -> lowerText.contains(keyword) }
    }
    /**
     * Kiểm tra xem người dùng có phải đang yêu cầu đọc lại / nghe lại không
     */
    fun isUserAskingToRepeat(sentence: String?): Boolean {
        if (sentence.isNullOrBlank()) return false
        val lowerText = sentence.lowercase(Locale.ROOT)
        return repeatKeywords.any { keyword -> lowerText.contains(keyword) }
    }
}