package com.example.chauoi.ai

import com.example.chauoi.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiHelper {

    // Đọc danh sách API Keys từ local.properties (có thể phân cách bằng dấu phẩy: KEY1,KEY2,KEY3)
    private val apiKeys: List<String> = BuildConfig.GEMINI_API_KEY
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private var currentKeyIndex = 0

    private fun createGenerativeModel(key: String): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-flash-latest",
            apiKey = key,
            systemInstruction = content {
                text(
                    """
                    Bạn là một người cháu ngoan, đóng vai trợ lý giọng nói điện thoại giúp ông bà cao tuổi.
                    Nhiệm vụ: Hướng dẫn ông bà thao tác trên màn hình điện thoại.
                    Quy tắc bắt buộc:
                    1. Xưng "cháu", gọi người dùng là "ông bà".
                    2. Trả lời cực kỳ ngắn gọn, 1 câu duy nhất dưới 25 chữ.
                    3. Chỉ rõ tên nút bấm hoặc ô nhập liệu cụ thể trên màn hình.
                    4. TUYỆT ĐỐI KHÔNG dùng ký tự Markdown (*, #, _, -) vì hệ thống Speech (TTS) sẽ đọc ra âm thanh gây khó nghe.
                    """.trimIndent()
                )
            }
        )
    }

    /**
     * Hàm gọi API có tích hợp Cơ chế Xoay vòng Key (Key Rotation)
     * Nếu Key hiện tại dính QuotaExceededException hoặc lỗi 403, tự động chuyển sang Key tiếp theo!
     * Nếu tất cả Key đều bận ngắn hạn, tự động chờ 2.5s (thời gian hồi Quota) để thử lại lượt 2.
     */
    private suspend fun generateWithKeyRotation(prompt: String): String {
        if (apiKeys.isEmpty()) {
            throw Exception("Chưa cấu hình GEMINI_API_KEY trong local.properties")
        }

        val totalKeys = apiKeys.size
        val maxPasses = 2 // Thử tối đa 2 vòng xoay

        for (pass in 0 until maxPasses) {
            var attempts = 0
            while (attempts < totalKeys) {
                val keyIndex = (currentKeyIndex + attempts) % totalKeys
                val activeKey = apiKeys[keyIndex]

                try {
                    val model = createGenerativeModel(activeKey)
                    val response = model.generateContent(prompt)
                    val rawText = response.text

                    if (rawText != null) {
                        // Cập nhật Key đang hoạt động tốt
                        currentKeyIndex = keyIndex
                        return lamSachPhanHoi(rawText)
                    }
                } catch (e: Exception) {
                    val errStr = e.toString()
                    android.util.Log.w(
                        "ChauOiService",
                        "⚠️ Key [$keyIndex/${totalKeys - 1}] bị giới hạn. Đang xoay..."
                    )

                    // Nếu dính lỗi Quota hoặc Permission Denied ➔ Xoay sang Key tiếp theo
                    if (errStr.contains("QuotaExceededException", ignoreCase = true) ||
                        errStr.contains("403", ignoreCase = true) ||
                        errStr.contains("PERMISSION_DENIED", ignoreCase = true)
                    ) {
                        attempts++
                        continue
                    } else {
                        throw e
                    }
                }
                attempts++
            }

            // Nếu đi hết cả 3 key ở lượt 1 mà đều bận ngắn hạn ➔ Chờ 4s cho Quota tự hồi rồi thử lượt 2
            if (pass < maxPasses - 1) {
                android.util.Log.i("ChauOiService", "⏳ Tất cả Keys bận ngắn hạn, tự động chờ 4 giây cho Quota tự nhả...")
                kotlinx.coroutines.delay(4000)
            }
        }

        throw Exception("Tất cả ${apiKeys.size} API Keys đều đang bị giới hạn Hạn mức (QuotaExceeded).")
    }

    /**
     * Trả lời câu hỏi của người dùng bấm mic trong lúc dùng app.
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
                    Ứng dụng: $tenDichVu | $contextMucDich
                    Màn hình: ${screenText.take(600)}
                    Ông bà hỏi: $userQuestion
                """.trimIndent()

                generateWithKeyRotation(prompt)
            } catch (e: Exception) {
                android.util.Log.e("ChauOiService", "Lỗi tất cả Gemini Keys: ", e)
                if (e.toString().contains("QuotaExceeded", ignoreCase = true) ||
                    e.message?.contains("Tất cả", ignoreCase = true) == true
                ) {
                    "Hệ thống đang quá tải, ông bà vui lòng đợi khoảng 1 phút rồi thử lại nhé."
                } else {
                    "Xin lỗi ông bà, mạng nhà mình đang chậm, cháu không nghe rõ ạ."
                }
            }
        }
    }

    /**
     * Sinh hướng dẫn cho màn hình chưa có trong JSON.
     */
    suspend fun hoiTuDo(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                generateWithKeyRotation(prompt)
            } catch (e: Exception) {
                android.util.Log.e("ChauOiService", "Lỗi tất cả Gemini Keys: ", e)
                if (e.toString().contains("QuotaExceeded", ignoreCase = true) ||
                    e.message?.contains("Tất cả", ignoreCase = true) == true
                ) {
                    "Hệ thống đang quá tải, ông bà vui lòng đợi khoảng 1 phút rồi thử lại nhé."
                } else {
                    "Cháu chưa rõ bước này, ông bà thử hỏi cháu trực tiếp nhé."
                }
            }
        }
    }

    /**
     * Loại bỏ sạch các ký tự Markdown rác trước khi đưa cho TTS đọc.
     */
    private fun lamSachPhanHoi(rawText: String): String {
        return rawText
            .replace("*", "")
            .replace("#", "")
            .replace("_", "")
            .replace("`", "")
            .replace("- ", "")
            .trim()
    }
}



