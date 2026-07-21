package com.example.chauoi.ai

import com.example.chauoi.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiHelper {

    // API Key được lưu trong local.properties (không push lên GitHub)
    // Hướng dẫn: Thêm GEMINI_API_KEY=your_key vào file local.properties
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val generativeModel = GenerativeModel(
        modelName = "gemini-flash-latest",
        apiKey = apiKey
    )

    /**
     * Trả lời câu hỏi của người dùng bấm mic trong lúc dùng app.
     *
     * Prompt được tối ưu:
     * - Biết đúng app đang dùng (tenDichVu) → không cần liệt kê tất cả app
     * - Biết mục tiêu (mucDich) → trả lời đúng ngữ cảnh hơn
     * - Giới hạn screenText 500 ký tự → tiết kiệm ~50% token so với trước
     */
    suspend fun askAssistant(
        screenText: String,
        userQuestion: String,
        tenDichVu: String = "ứng dụng",
        mucDich: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val contextMucDich = if (mucDich != null) "Mục tiêu: $mucDich." else ""
                val prompt = """
                    Vai trò: Cháu giúp ông bà thao tác $tenDichVu. $contextMucDich
                    Màn hình hiện tại: ${screenText.take(500)}
                    Ông bà hỏi: $userQuestion

                    Trả lời 1 câu dưới 25 chữ. Xưng "cháu", gọi "ông bà". Chỉ rõ tên nút bấm cụ thể. Không dùng dấu * # _ vì hệ thống đọc thành tiếng.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                response.text ?: "Cháu không rõ câu trả lời, ông bà đợi cháu một lát nhé."
            } catch (e: Exception) {
                android.util.Log.e("ChauOiService", "Lỗi Gemini API: ", e)
                "Xin lỗi ông bà, mạng nhà mình đang chậm, cháu không nghe rõ ạ."
            }
        }
    }

    /**
     * Sinh hướng dẫn cho màn hình chưa có trong JSON.
     * Prompt được truyền vào từ ScreenReaderService với đầy đủ ngữ cảnh.
     * Hàm này chỉ đảm bảo output không có ký tự Markdown gây lỗi TTS.
     */
    suspend fun hoiTuDo(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                val rawText = response.text ?: "Cháu chưa rõ bước này, ông bà thử hỏi cháu trực tiếp nhé."

                // Xoá ký tự Markdown vì TTS sẽ đọc thành tiếng, gây khó nghe
                rawText.replace("*", "")
                    .replace("#", "")
                    .replace("_", "")
                    .trim()
            } catch (e: Exception) {
                android.util.Log.e("ChauOiService", "Lỗi Gemini API: ", e)
                "Cháu chưa rõ bước này, ông bà thử hỏi cháu trực tiếp nhé."
            }
        }
    }
}

