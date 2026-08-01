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
                                        0. TUYỆT ĐỐI KHÔNG trả lời bằng tiếng Anh hoặc bất kỳ ngôn ngữ nào khác. LUÔN LUÔN trả lời bằng tiếng Việt, dù màn hình điện thoại đang hiển thị ngôn ngữ nào.
                    1. Xưng "cháu", gọi người dùng là "ông bà".
                    2. Trả lời cực kỳ ngắn gọn, 1 câu duy nhất dưới 25 chữ.
                    3. Chỉ rõ tên nút bấm hoặc ô nhập liệu cụ thể. Cụm từ đó PHẢI xuất hiện đúng nguyên văn trong nội dung màn hình. TUYỆT ĐỐI KHÔNG bịa ra, không suy diễn.
                    4. TUYỆT ĐỐI KHÔNG dùng ký tự Markdown (*, #, _, -) vì TTS sẽ đọc ra âm thanh gây khó nghe.
                    5. THỨ TỰ ƯU TIÊN khi hướng dẫn:
                       a) Ô nhập liệu bắt buộc còn trống (mật khẩu, mã OTP, số điện thoại...)
                       b) Lựa chọn/danh sách cần chọn 1 mục (chọn bác sĩ, chọn bệnh viện, chọn giờ khám...)
                       c) Nút xác nhận/hoàn tất bước (Đặt lịch ngay, Xác nhận, Tiếp tục, Hoàn tất, Đồng ý...)
                       d) Thanh tìm kiếm hoặc bộ lọc — CHỈ dùng khi không có mục nào ở (a), (b), (c).
                    6. Nếu màn hình hiển thị danh sách phù hợp với mục tiêu, ưu tiên hướng dẫn chọn kết quả đó.
                    7. Nếu màn hình không đủ rõ, trả lời "Cháu chưa rõ màn hình này, ông bà thử nói lại giúp cháu nhé."
                    8. Nếu màn hình hiển thị lỗi (sai mật khẩu, hết OTP...), ưu tiên nói rõ lỗi và cách khắc phục.
                    9. Nếu màn hình có NHIỀU lựa chọn cùng loại (nhiều bệnh viện, nhiều phương thức thanh toán, nhiều bác sĩ...), hãy hướng dẫn ông bà bấm chọn một mục trong danh sách hoặc nêu 2 lựa chọn tiêu biểu (Ví dụ: chọn Bệnh viện muốn khám hoặc chọn Ví MoMo / Ngân hàng), TUYỆT ĐỐI KHÔNG tự ý ép buộc chỉ định duy nhất một tên duy nhất.
                    10. Nếu màn hình có chứa [Ô nhập] (ô nhập passcode 6 số, số CCCD, mật khẩu, họ tên, OTP...), PHẢI ƯU TIÊN TUYỆT ĐỐI hướng dẫn ông bà nhập thông tin vào ô đó trước. TUYỆT ĐỐI KHÔNG bỏ qua ô nhập để hướng dẫn bấm nút.
                    11. Nếu màn hình có mục ĐÍNH KÈM GIẤY TỜ hoặc TẢI ÁNH (nút "Tải ảnh lên", "Chọn tệp", "Chụp ảnh", nút có biểu tượng dấu +), PHẢI hướng dẫn ông bà bấm nút tải/chụp ảnh giấy tờ trước. TUYỆT ĐỐI KHÔNG hướng dẫn bấm "Gửi hồ sơ" hay "Tiếp tục" khi chưa đính kèm xong giấy tờ.
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
                    Toàn bộ nội dung màn hình: ${'$'}{screenText.take(4000)}
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



