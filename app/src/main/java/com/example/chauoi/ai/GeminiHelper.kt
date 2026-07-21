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

    suspend fun askAssistant(screenText: String, userQuestion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Bạn là một người cháu ngoan, đóng vai trò trợ lý giọng nói trên điện thoại giúp ông bà cao tuổi thao tác trên điện thoại. Ông bà đang dùng một trong hai tính năng sau:
                    1. Đặt lịch khám bệnh trên app YouMed
                    2. Đăng ký cấp lại căn cước công dân (CCCD) qua app VNeID
                    
                    Nhiệm vụ của bạn là đưa ra một câu trả lời ngắn gọn (dưới 30 chữ), dễ nghe, thân thiện, và hướng dẫn đúng nút bấm cần thiết. KHÔNG ĐƯỢC trả lời dài dòng. KHÔNG dùng định dạng như dấu * hoặc gạch đầu dòng vì hệ thống sẽ đọc ra âm thanh.

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

    suspend fun hoiTuDo(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val promptAnToan = prompt + "\nLưu ý: TRẢ LỜI VĂN BẢN TRƠN, TUYỆT ĐỐI KHÔNG DÙNG ký tự Markdown như dấu sao (*) hay dấu thăng (#) vì hệ thống đọc giọng nói (TTS) sẽ bị lỗi."

                val response = generativeModel.generateContent(promptAnToan)
                val rawText = response.text ?: "Cháu chưa rõ bước này, ông bà thử hỏi cháu trực tiếp nhé."

                rawText.replace("*", "")
                    .replace("#", "")
                    .replace("_", "")
            } catch (e: Exception) {
                android.util.Log.e("ChauOiService", "Lỗi Gemini API: ", e)
                "Cháu chưa rõ bước này, ông bà thử hỏi cháu trực tiếp nhé."
            }
        }
    }
}
