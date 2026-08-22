package com.example.chauoi.ai

import android.content.Context
import java.util.Locale
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import com.example.chauoi.dichVu.CauHinhDichVu

class AIIntentMatcher(context: Context, modelName: String = "universal_sentence_encoder.tflite") {
    private var textEmbedder: TextEmbedder? = null

    init {
        try {
            val baseOptions = BaseOptions.builder().setModelAssetPath(modelName).build()
            val options = TextEmbedderOptions.builder().setBaseOptions(baseOptions).build()
            textEmbedder = TextEmbedder.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Tìm ứng dụng phù hợp nhất dựa trên câu nói của người dùng
     */
    fun timAppPhuHop(cauNoiCuaOngBa: String, danhSach: List<CauHinhDichVu>): CauHinhDichVu? {
        val embedder = textEmbedder ?: return null
        val lowerText = cauNoiCuaOngBa.lowercase(Locale.getDefault())

        // BƯỚC 1: LỌC CÂU HỎI NGOÀI LỀ (CHẶN NGAY TỪ ĐẦU)
        val tuKhoaNgoaiLe = listOf("thời tiết", "mưa", "nắng", "nấu ăn", "món ăn", "giải trí", "tin tức", "bóng đá")
        if (tuKhoaNgoaiLe.any { lowerText.contains(it) }) {
            // Câu hỏi không liên quan đến dịch vụ y tế/hành chính -> Trả về null để không mở lung tung
            return null
        }
        // BƯỚC 2: KHỚP TỪ KHÓA CỨNG ĐỘC QUYỀN (ƯU TIÊN SỐ 1 ĐỂ TRÁNH NHẬN NHẦM)
        // Nhóm VNeID: Gom toàn bộ các thủ tục hành chính, giấy tờ, dân cư, cư trú, thuế, hộ tịch...
        val vneidKeywords = listOf(
            // Giấy tờ cá nhân & Tích hợp
            "vneid", "vi en id", "căn cước", "cccd", "thẻ căn cước", "chứng minh", "định danh",
            "tài khoản định danh", "giấy tờ", "tích hợp giấy tờ", "thẻ cứng", "chứng minh thư",

            // Bằng lái & Xe cộ
            "bằng lái", "giấy phép lái xe", "đăng ký xe", "bảo hiểm xe", "xe máy", "ô tô",

            // Cư trú & Lưu trú
            "tạm trú", "tạm vắng", "lưu trú", "hộ khẩu", "nhân khẩu", "thông báo lưu trú",
            "khai báo", "khai báo đi vắng", "cư trú", "thông tin cư trú",

            // Hộ tịch (Khai sinh, khai tử, kết hôn...)
            "khai tử", "giấy khai tử", "khai sinh", "giấy khai sinh", "kết hôn",
            "tình trạng hôn nhân", "hộ tịch",

            // Thủ tục hành chính & Giấy tờ khác
            "hộ chiếu", "cấp hộ chiếu", "lý lịch tư pháp", "thủ tục hành chính",
            "tố giác tội phạm", "thông báo tố giác", "thủ tục công an", "người phụ thuộc",
            "định danh dưới 14 tuổi", "thông tin thuế", "nộp phạt",


            "hộ gia đình", "thành viên", "công an", "đi xa", "tạm vắng"
        )
        if (vneidKeywords.any { lowerText.contains(it) }) {
            return danhSach.find { it.tenGoi.equals("VNeID", ignoreCase = true) }
        }

        // Nhóm VssID: Lương hưu, bảo hiểm xã hội, bảo hiểm y tế, thẻ bhyt, sổ bảo hiểm
        val vssidKeywords = listOf(
            "lương hưu", "tiền hưu", "bảo hiểm xã hội", "bảo hiểm y tế", "bhxh", "bhyt",
            "sổ bảo hiểm", "thẻ bảo hiểm", "thẻ xanh", "quá trình đóng bảo hiểm", "trợ cấp",
            "lịch sử khám chữa bệnh bảo hiểm", "lịch sử bhyt", "khám bệnh bảo hiểm",
            "tiền tuổi già", "tiền hưu"
        )
        if (vssidKeywords.any { lowerText.contains(it) }) {
            return danhSach.find { it.tenGoi.equals("VssID", ignoreCase = true) }
        }

        // Nhóm YouMed: Khám bệnh, bác sĩ, đặt lịch, đau bụng, ốm đau, nhà thuốc, toa thuốc
        val youmedKeywords = listOf(
            "khám bệnh", "bác sĩ", "đặt lịch", "đặt khám", "đau bụng", "đau đầu", "ốm",
            "bệnh viện", "phòng khám", "toa thuốc", "đơn thuốc", "y tế", "sức khỏe",
            "đơn thuốc", "kê đơn", "thuốc", "thầy thuốc"
        )
        if (youmedKeywords.any { lowerText.contains(it) }) {
            return danhSach.find { it.tenGoi.equals("YouMed", ignoreCase = true) }
        }

        // BƯỚC 3: DÙNG AI EMBEDDING CHO CÁC CÂU CÒN LẠI
        val ketQuaUser = try {
            embedder.embed(cauNoiCuaOngBa).embeddingResult().embeddings().first()
        } catch (e: Exception) {
            return null
        }

        var appDungNhat: CauHinhDichVu? = null
        var doTuongDongCaoNhat: Float = -1.0f

        if (cauNoiCuaOngBa.length > 80 && (cauNoiCuaOngBa.contains("chạm nút") || cauNoiCuaOngBa.contains("màn hình"))) {
            android.util.Log.w("ChauOiAI", "Phát hiện tiếng vọng dài, bỏ qua AI.")
            return null
        }

        val nguongChapNhan = 0.87f

        // 2. So sánh vector câu nói của người dùng với các câu mẫu của từng app
        for (dichVu in danhSach) {
            for (cauMau in dichVu.cauMauYDinh) {
                try {
                val ketQuaMau = embedder.embed(cauMau).embeddingResult().embeddings().first()

                // Tính độ tương đồng Cosine giữa 2 vector (-1.0 đến 1.0)
                val doTuongDong = TextEmbedder.cosineSimilarity(ketQuaUser, ketQuaMau)

                if (doTuongDong > doTuongDongCaoNhat) {
                    doTuongDongCaoNhat = doTuongDong.toFloat()
                    appDungNhat = dichVu
                }
                } catch (e: Exception) {
                    // Bỏ qua lỗi nhỏ nếu embed câu mẫu lỗi
                }
            }
        }

        // Nếu độ tương đồng vượt ngưỡng thì trả về app đó
        return if (doTuongDongCaoNhat >= nguongChapNhan) appDungNhat else null
    }

    fun close() {
        textEmbedder?.close()
    }
}