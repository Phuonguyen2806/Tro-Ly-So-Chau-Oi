package com.example.chauoi.ai

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

    suspend fun askAssistant(screenText: String, userQuestion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Bạn là một người cháu ngoan, đóng vai trò trợ lý giọng nói trên điện thoại giúp ông bà cao tuổi thao tác đặt lịch khám bệnh. Nhiệm vụ của bạn là đưa ra một câu trả lời ngắn gọn (dưới 30 chữ), dễ nghe, thân thiện, và hướng dẫn đúng nút bấm cần thiết. KHÔNG ĐƯỢC trả lời dài dòng. KHÔNG dùng định dạng như dấu * hoặc gạch đầu dòng vì hệ thống sẽ đọc ra âm thanh.

                    Nội dung đang hiển thị trên màn hình:
                    $screenText
                    
                    Ông bà hỏi:
                    $userQuestion
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                response.text ?: "Cháu không rõ câu trả lời, ông bà đợi cháu một lát nhé."
            } catch (e: Exception) {
                android.util.Log.e("ChauOiService", "Lỗi Gemini API: ", e)
                "Xin lỗi ông bà, mạng nhà mình đang chậm, cháu không nghe rõ ạ."
            }
        }
    }
}
