package com.example.chauoi.dichvu

class VNeIDDichVu : DichVu {

    override val tenGoi = "VNeID"
    override val tenPackage = "com.vnid"

    override val tuKhoaGiongNoi = listOf(
        "vneid", "căn cước", "cccd", "làm lại thẻ",
        "cấp lại thẻ", "làm mới thẻ", "cấp đổi thẻ", "đổi thẻ"
    )
    override val cauPhanHoiKhiMo = "Cháu đang mở ứng dụng VNeID để làm lại thẻ căn cước cho ông bà đây ạ!"

    override fun nhanDienBuoc(text: String): String {
        return when {
            text.contains("đã được đặt thành công", ignoreCase = true) ||
                    text.contains("Tạo yêu cầu thành công", ignoreCase = true) ||
                    text.contains("Nộp hồ sơ thành công", ignoreCase = true) ->
                "VNeID: Hoàn thành nộp hồ sơ"

            text.contains("Tôi xin cam đoan", ignoreCase = true) ->
                "VNeID: Xác nhận và nộp hồ sơ"

            text.contains("Cơ quan thực hiện", ignoreCase = true) ->
                "VNeID: Chọn nơi làm thủ tục"

            text.contains("Kiểm tra thông tin", ignoreCase = true) &&
                    !text.contains("Bạn đang muốn tìm kiếm", ignoreCase = true) ->
                "VNeID: Kiểm tra thông tin cá nhân"

            text.contains("Đề nghị cấp đổi Căn cước khai cho bản thân", ignoreCase = true) ->
                "VNeID: Chọn loại thủ tục"

            text.contains("Tạo mới yêu cầu", ignoreCase = true) ->
                "VNeID: Trang tạo yêu cầu"

            text.contains("Nhập passcode", ignoreCase = true) ->
                "VNeID: Nhập passcode"

            text.contains("Bạn đang muốn tìm kiếm", ignoreCase = true) ->
                "VNeID: Thủ tục hành chính"

            text.contains("Ví giấy tờ", ignoreCase = true) &&
                    text.contains("Quét mã", ignoreCase = true) ->
                "VNeID: Trang chủ"

            text.contains("Vui lòng nhập thông tin đăng nhập để tiếp tục", ignoreCase = true) ->
                "VNeID: Bắt đầu cấp lại CCCD"

            else -> "Không nhận diện"
        }
    }

    override fun layHuongDan(buoc: String): String {
        return when (buoc) {

            "VNeID: Bắt đầu cấp lại CCCD" ->
                "Chào ông bà, cháu đang giúp ông bà làm lại Căn cước công dân trên VNeID. " +
                        "Đầu tiên, ông bà hãy nhập số căn cước công dân và mật khẩu hoặc dùng vân tay để đăng nhập nhé."

            "VNeID: Trang chủ" ->
                "Ông bà đã đăng nhập thành công. Ở màn hình chính, ông bà hãy tìm và bấm vào ô " +
                        "'Thủ tục hành chính' có biểu tượng hình tờ giấy nhé."

            "VNeID: Thủ tục hành chính" ->
                "Ông bà đang ở danh sách thủ tục. Hãy tìm dòng 'Cấp đổi thẻ Căn cước' hoặc " +
                        "'Cấp lại thẻ Căn cước' rồi bấm vào đó ạ."

            "VNeID: Chọn loại thủ tục" ->
                "Bây giờ ông bà hãy chọn 'Bản thân' nếu làm cho chính mình, " +
                        "hoặc 'Khai hộ' nếu làm hộ cho người khác nhé."

            "VNeID: Trang tạo yêu cầu" ->
                "Ông bà đang ở trang Cấp đổi thẻ Căn cước. " +
                        "Hãy bấm vào nút 'Tạo mới yêu cầu' màu xanh ở giữa màn hình nhé."

            "VNeID: Nhập passcode" ->
                "Ông bà hãy nhập mã passcode sáu số quen thuộc của mình để tiếp tục nhé."

            "VNeID: Kiểm tra thông tin cá nhân" ->
                "Ông bà hãy nhìn vào màn hình kiểm tra lại các thông tin: Họ tên, giới tính, " +
                        "ngày tháng năm sinh, số định danh cá nhân và số điện thoại đã đúng chưa ạ. " +
                        "Phần email không bắt buộc, ông bà có thể bỏ qua. " +
                        "Nếu mọi thông tin đã đúng, hãy bấm vào nút 'Kiểm tra thông tin' màu xanh ở phía dưới nhé."

            "VNeID: Chọn nơi làm thủ tục" ->
                "Đến bước này, ông bà hãy chọn nơi mình muốn đến làm thủ tục, ví dụ chọn Công an phường " +
                        "hoặc Công an quận nơi ông bà đang sinh sống. Sau đó, ông bà chọn một ngày trong lịch " +
                        "mà ông bà thấy thuận tiện để đến chụp ảnh ạ."

            "VNeID: Xác nhận và nộp hồ sơ" ->
                "Chỉ còn bước cuối cùng thôi ạ! Ông bà hãy đọc qua một chút, sau đó tích vào ô " +
                        "'Tôi xin cam đoan' và bấm nút 'Nộp hồ sơ'. Cháu chúc ông bà làm hồ sơ thuận lợi nhé!"

            "VNeID: Hoàn thành nộp hồ sơ" ->
                "Tuyệt vời! Ông bà đã nộp hồ sơ thành công rồi. " +
                        "Ứng dụng sẽ sớm gửi thông báo hoặc tin nhắn hẹn ngày giờ, ông bà nhớ chú ý điện thoại nhé!"

            else -> ""
        }
    }
}