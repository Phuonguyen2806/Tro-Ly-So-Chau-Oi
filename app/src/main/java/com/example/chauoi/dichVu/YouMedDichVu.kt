package com.example.chauoi.dichvu

class YouMedDichVu : DichVu {

    override val tenGoi = "YouMed"
    override val tenPackage = "com.youmed.info"

    override val tuKhoaGiongNoi = listOf("đặt lịch", "khám", "cháu ơi")
    override val cauPhanHoiKhiMo = "Cháu đang mở ứng dụng đặt lịch khám YouMed cho ông bà đây ạ!"

    override fun nhanDienBuoc(text: String): String {
        return when {
            text.contains("mật khẩu không chính xác", ignoreCase = true) ->
                "Lỗi: Đăng nhập sai"

            text.contains("Thông tin đăng nhập", ignoreCase = true) &&
                    text.contains("Nhập mật khẩu", ignoreCase = true) ->
                "Bước 1: Đăng nhập"

            text.contains("Xác nhận thông tin", ignoreCase = true) &&
                    text.contains("Thanh toán", ignoreCase = true) ->
                "Bước 9: Xác nhận & Thanh toán"

            text.contains("Đây có phải hồ sơ của bạn không", ignoreCase = true) ->
                "Xác nhận hồ sơ"

            text.contains("Kết quả tìm kiếm", ignoreCase = true) &&
                    text.contains("Bệnh viện Lê Văn Thịnh", ignoreCase = true) ->
                "Kết quả tìm hồ sơ"

            text.contains("Tra cứu hồ sơ bệnh nhân", ignoreCase = true) &&
                    text.contains("Mã Bệnh Nhân", ignoreCase = true) ->
                "Tra cứu hồ sơ"

            text.contains("Chọn Giờ khám", ignoreCase = true) &&
                    text.contains("6 Giờ khám", ignoreCase = true) ->
                "Bước 8b: Chọn giờ khám"

            text.contains("Chọn Ngày khám", ignoreCase = true) &&
                    text.contains("5 Ngày khám", ignoreCase = true) ->
                "Bước 8: Chọn ngày khám"

            text.contains("Chọn Bệnh nhân", ignoreCase = true) &&
                    text.contains("4 Bệnh nhân", ignoreCase = true) ->
                "Bước 7: Chọn bệnh nhân"

            text.contains("Chọn Phòng khám", ignoreCase = true) &&
                    text.contains("3 Phòng khám", ignoreCase = true) ->
                "Bước 6: Chọn phòng khám"

            text.contains("Chọn Chuyên khoa", ignoreCase = true) &&
                    text.contains("2 Chuyên khoa", ignoreCase = true) ->
                "Bước 5: Chọn chuyên khoa"

            text.contains("Chọn Đối tượng khám", ignoreCase = true) &&
                    text.contains("Khám Dịch Vụ", ignoreCase = true) ->
                "Bước 4: Chọn đối tượng"

            text.contains("Nơi khám: Bệnh viện", ignoreCase = true) &&
                    text.contains("Đặt lịch ngay", ignoreCase = true) ->
                "Bước 3: Chọn bệnh viện"

            text.contains("Tiêm chủng", ignoreCase = true) &&
                    text.contains("Trang chủ", ignoreCase = true) ->
                "Trang chủ"

            text.contains("Đặt lịch thành công", ignoreCase = true) ||
                    text.contains("Đặt khám thành công", ignoreCase = true) ->
                "Hoàn thành"

            else -> "Không nhận diện"
        }
    }

    override fun xuLyDacBiet(buoc: String, text: String): String? {
        if (buoc == "Bước 9: Xác nhận & Thanh toán") {
            return trichXuatThongTinThanhToan(text)
        }
        return null
    }

    private fun trichXuatThongTinThanhToan(text: String): String {
        return "Bạn hãy đọc kỹ thông tin và xác nhận thanh toán."
    }

    override fun layHuongDan(buoc: String): String {
        return when (buoc) {

            "Lỗi: Đăng nhập sai" ->
                "Tên đăng nhập hoặc mật khẩu không đúng. " +
                        "Hãy kiểm tra lại và thử nhập lại."

            "Bước 1: Đăng nhập" ->
                "Bạn đang ở bước đăng nhập. " +
                        "Hãy nhập số điện thoại vào ô Thông tin đăng nhập. " +
                        "Sau đó nhập mật khẩu và bấm nút Đăng nhập."

            "Trang chủ" ->
                "Bạn đã đăng nhập thành công! " +
                        "Để đặt lịch khám bệnh viện, hãy bấm vào ô Đặt khám bệnh viện."

            "Bước 3: Chọn bệnh viện" ->
                "Bạn đang ở bước chọn bệnh viện. " +
                        "Hãy vuốt lên xuống để xem danh sách. " +
                        "Bấm Đặt lịch ngay tại bệnh viện bạn muốn đến."

            "Bước 4: Chọn đối tượng" ->
                "Bạn cần chọn đối tượng khám. " +
                        "Nếu có thẻ Bảo Hiểm Y Tế, hãy chọn Khám Bảo Hiểm Y Tế. " +
                        "Nếu không, hãy chọn Khám Dịch Vụ."

            "Bước 5: Chọn chuyên khoa" ->
                "Bạn đang ở bước chọn chuyên khoa. " +
                        "Hãy chọn khoa phù hợp với bệnh của bạn, " +
                        "ví dụ Nội khoa, Nhi khoa hoặc Ngoại khoa."

            "Bước 6: Chọn phòng khám" ->
                "Bạn đang ở bước chọn phòng khám. " +
                        "Hãy vuốt lên xuống để xem các phòng và bấm Chọn."

            "Bước 7: Chọn bệnh nhân" ->
                "Bạn cần chọn hồ sơ bệnh nhân. " +
                        "Nếu đã có hồ sơ, hãy chọn tên trong danh sách. " +
                        "Nếu chưa có, hãy bấm Tạo mới để tạo hồ sơ."

            "Tra cứu hồ sơ" ->
                "Bạn đang ở màn hình tra cứu hồ sơ bệnh nhân. " +
                        "Hãy nhập Mã bệnh nhân, Họ tên và Ngày sinh, " +
                        "sau đó bấm nút Tra cứu."

            "Kết quả tìm hồ sơ" ->
                "Đã tìm thấy hồ sơ bệnh nhân. " +
                        "Hãy kiểm tra thông tin họ tên và ngày sinh. " +
                        "Nếu đúng, hãy bấm Xác nhận hồ sơ."

            "Xác nhận hồ sơ" ->
                "Vui lòng xác nhận hồ sơ của bạn. " +
                        "Nếu đúng, hãy bấm Xác nhận. " +
                        "Nếu không phải, hãy bấm Không."

            "Bước 8: Chọn ngày khám" ->
                "Bạn đang ở bước chọn ngày khám. " +
                        "Hãy bấm vào ngày bạn muốn đến khám trên lịch."

            "Bước 8b: Chọn giờ khám" ->
                "Bạn đang ở bước chọn giờ khám. " +
                        "Hãy chọn buổi sáng hoặc buổi chiều, " +
                        "sau đó bấm Tiếp tục để xác nhận."

            "Bước 9: Xác nhận & Thanh toán" ->
                "Bạn đang ở bước xác nhận thông tin và thanh toán. " +
                        "Hãy kiểm tra lại tên bệnh viện, chuyên khoa, ngày giờ khám. " +
                        "Sau đó chọn phương thức thanh toán và bấm Thanh toán để hoàn tất."

            "Hoàn thành" ->
                "Chúc mừng! Bạn đã đặt lịch khám thành công. " +
                        "Hãy nhớ đến đúng giờ nhé!"

            else -> ""
        }
    }
}