package com.example.chauoi.dichVu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object DichVuLoader {
    private const val TAG = "DichVuLoader"

    /** Nạp danh sách CauHinhDichVu (Tự động nạp danh sách mặc định trong Kotlin code nếu không có JSON) */
    fun taiTatCa(context: Context): List<CauHinhDichVu> {
        Log.d(TAG, "🚀 Nạp danh sách ứng dụng mặc định từ Kotlin code")
        return layDanhSachMacDinh()
    }
    private fun layDanhSachMacDinh(): List<CauHinhDichVu> {
        return listOf(
            CauHinhDichVu(
                tenGoi = "VNeID",
                tenPackage = "com.vnid",
//                tuKhoaGiongNoi = listOf("vneid", "vi en id", "căn cước công dân", "thẻ căn cước", "cccd", "tạm vắng", "tạm trú", "thường trú", "khai báo", "dân cư",
//                    "thông báo lưu trú", "giấy tờ", "định danh", "tài khoản định danh", "đăng ký xe", "khai tử", "thủ tục hành chính", "giấy phép lái xe",
//                    "người phụ thuộc", "Định danh người dưới 14 tuổi", "thông tin cư trú", "lý lịch tư pháp","đăng ký khai sinh", "cấp hộ chiêu", "tích hợp giấy tờ", "thông tin thuế", "tình trạng hôn nhân" ),
                cauMauYDinh = listOf(
                    // Căn cước & Thông tin cá nhân
                    "quên thẻ căn cước",
                    "xem thẻ căn cước công dân",
                    "thông tin định danh cá nhân",
                    "kiểm tra căn cước",
                    "định danh điện tử",
                    "xem số định danh",
                    "thông tin cư trú của tao",
                    "tôi ở đâu trong hộ khẩu",
                    "kiểm tra nhân khẩu",

                    // Giấy tờ tích hợp (Bằng lái, Đăng ký xe,...)
                    "xem giấy phép lái xe",
                    "bằng lái xe của tao",
                    "giấy tờ xe máy ô tô",
                    "đăng ký xe của tao",
                    "bảo hiểm xe bắt buộc",
                    "tích hợp giấy tờ",
                    "kiểm tra bằng lái",

                    // Lưu trú & Hành chính
                    "khai báo tạm trú tạm vắng",
                    "khai báo đi vắng",
                    "thông báo lưu trú",
                    "thủ tục hành chính công",
                    "làm hộ chiếu",
                    "cấp hộ chiếu online",
                    "lý lịch tư pháp",
                    "thông báo tố giác tội phạm",
                    "thủ tục công an"
                ),
                cauPhanHoiKhiMo = "Cháu đang mở ứng dụng VNeID cho ông bà đây ạ!",
                cauChaoMung = "Ông bà đã vào VNeID. Lần đầu tiên, ông bà hãy chạm nút micro màu cam ở trên và nói cho cháu biết ông bà cần làm thủ tục gì nhé.",
                cauNhanChuyenManHinh = "Màn hình đã chuyển. Ông bà tiếp tục chạm vào nút con mắt ở dưới để cháu quét và hướng dẫn nhé.",
//                buoc = emptyList()
            ),
            CauHinhDichVu(
                tenGoi = "YouMed",
                tenPackage = "com.youmed.info",
//                tuKhoaGiongNoi = listOf("đặt lịch khám", "đặt khám", "youmed", "bác sĩ", "đặt lịch bác sĩ"),
                cauMauYDinh = listOf(
                    // Đặt lịch khám
                    "đặt lịch khám bệnh",
                    "đặt khám bác sĩ",
                    "muốn đặt lịch khám",
                    "đặt lịch khám tại phòng khám",
                    "đặt lịch khám tại bệnh viện",
                    "đặt lịch xét nghiệm tại nhà",
                    "đặt bác sĩ đến nhà khám",

                    // Tìm kiếm & Tra cứu
                    "tìm bác sĩ giỏi",
                    "bác sĩ chuyên khoa",
                    "xem kết quả khám bệnh",
                    "tra cứu lịch sử khám bệnh",
                    "toa thuốc của tao đâu",
                    "xem đơn thuốc cũ",

                    // Tư vấn & Hỗ trợ
                    "tư vấn sức khỏe trực tuyến",
                    "hỏi đáp với bác sĩ",
                    "gọi cho bác sĩ",
                    "tôi bị ốm phải làm sao"
                ),
                cauPhanHoiKhiMo = "Cháu đang mở ứng dụng đặt lịch khám YouMed cho ông bà đây ạ!",
                cauChaoMung = "Ông bà đã vào YouMed. Lần đầu tiên, ông bà hãy chạm nút micro màu xanh ở trên và nói cho cháu biết ông bà muốn làm gì nhé.",
                cauNhanChuyenManHinh = "Màn hình đã chuyển. Ông bà tiếp tục chạm vào nút con mắt ở dưới để cháu hướng dẫn bước tiếp theo nhé.",
//                buoc = emptyList()
            ),
            CauHinhDichVu(
                tenGoi = "VssID",
                tenPackage = "com.bhxhapp",
//                tuKhoaGiongNoi = listOf("vssid", "vi ss id", "bảo hiểm xã hội", "bảo hiểm y tế", "sổ bảo hiểm", "thẻ bảo hiểm", "quá trình tham gia", "sổ khám chữa bệnh", "thông tin hưởng", "ủy quyền lĩnh thay", "cấp lại sổ bảo hiểm xã hội", "đăng ký tài khoản cho con", "thay đổi hình thức lĩnh", "thay đổi hình thức lãnh", "chuyển địa bàn hưởng lương hưu", "tra cứu mã số", "tra cứu cơ quan bảo bảo hiểm xã hội"),
                cauMauYDinh = listOf(
                    // Lương hưu & Tiền trợ cấp
                    "xem tiền lương hưu tháng này",
                    "tiền hưu về chưa",
                    "lương hưu tháng này bao nhiêu",
                    "kiểm tra tiền bảo hiểm",
                    "tiền trợ cấp thất nghiệp",

                    // Sổ BHXH & Quá trình tham gia
                    "tra cứu sổ bảo hiểm xã hội",
                    "xem quá trình đóng bảo hiểm",
                    "kiểm tra thời gian đóng bảo hiểm",
                    "mã số bảo hiểm xã hội của tao",
                    "cấp lại sổ bảo hiểm xã hội",

                    // Thẻ BHYT & Khám chữa bệnh
                    "kiểm tra bảo hiểm y tế",
                    "xem thẻ bảo hiểm y tế",
                    "thẻ bhyt còn hạn không",
                    "lịch sử khám chữa bệnh bảo hiểm",
                    "tra cứu cơ quan bảo hiểm xã hội",

                    // Thay đổi thông tin hưởng
                    "thay đổi hình thức nhận tiền hưu",
                    "đổi cách nhận lương hưu",
                    "chuyển địa bàn hưởng lương hưu",
                    "ủy quyền lĩnh thay lương hưu"
                ),
                cauPhanHoiKhiMo = "Cháu đang mở ứng dụng VssID cho ông bà đây ạ!",
                cauChaoMung = "Ông bà đã vào ứng dụng Bảo hiểm. Lần đầu tiên, ông bà hãy chạm nút micro màu cam ở trên và nói mục đích cho cháu biết nhé.",
                cauNhanChuyenManHinh = "Màn hình đã chuyển. Ông bà tiếp tục chạm vào nút con mắt ở dưới để cháu hướng dẫn bước tiếp theo nhé.",
//                buoc = emptyList()
            )
        )
    }
}
 
