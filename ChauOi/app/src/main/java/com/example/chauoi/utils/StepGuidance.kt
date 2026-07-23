package com.example.chauoi.utils

object StepGuidance {

    fun getGuidance(buoc: String): String {
        return when (buoc) {

            // ── Lỗi ────────────────────────────────────────────────────────
            "Lỗi: Đăng nhập sai" ->
                "Tên đăng nhập hoặc mật khẩu không đúng. " +
                "Hãy kiểm tra lại và thử nhập lại."

            // ── Bước 1 ─────────────────────────────────────────────────────
            "Bước 1: Đăng nhập" ->
                "Bạn đang ở bước đăng nhập. " +
                "Hãy nhập số điện thoại vào ô Thông tin đăng nhập. " +
                "Sau đó nhập mật khẩu và bấm nút Đăng nhập."

            // ── Trang chủ ──────────────────────────────────────────────────
            "Trang chủ" ->
                "Bạn đã đăng nhập thành công! " +
                "Để đặt lịch khám bệnh viện, hãy bấm vào ô Đặt khám bệnh viện."

            // ── Bước 3 ─────────────────────────────────────────────────────
            "Bước 3: Chọn bệnh viện" ->
                "Bạn đang ở bước chọn bệnh viện. " +
                "Hãy vuốt lên xuống để xem danh sách. " +
                "Bấm Đặt lịch ngay tại bệnh viện bạn muốn đến."

            // ── Bước 4 ─────────────────────────────────────────────────────
            "Bước 4: Chọn đối tượng" ->
                "Bạn cần chọn đối tượng khám. " +
                "Nếu có thẻ Bảo Hiểm Y Tế, hãy chọn Khám Bảo Hiểm Y Tế. " +
                "Nếu không, hãy chọn Khám Dịch Vụ."

            // ── Bước 5 ─────────────────────────────────────────────────────
            "Bước 5: Chọn chuyên khoa" ->
                "Bạn đang ở bước chọn chuyên khoa. " +
                "Hãy chọn khoa phù hợp với bệnh của bạn, " +
                "ví dụ Nội khoa, Nhi khoa hoặc Ngoại khoa."

            // ── Bước 6 ─────────────────────────────────────────────────────
            "Bước 6: Chọn phòng khám" ->
                "Bạn đang ở bước chọn phòng khám. " +
                "Hãy vuốt lên xuống để xem các phòng và bấm Chọn."

            // ── Bước 7 ─────────────────────────────────────────────────────
            "Bước 7: Chọn bệnh nhân" ->
                "Bạn cần chọn hồ sơ bệnh nhân. " +
                "Nếu đã có hồ sơ, hãy chọn tên trong danh sách. " +
                "Nếu chưa có, hãy bấm Tạo mới để tạo hồ sơ."

            // ── Tra cứu hồ sơ ──────────────────────────────────────────────
            "Tra cứu hồ sơ" ->
                "Bạn đang ở màn hình tra cứu hồ sơ bệnh nhân. " +
                "Hãy nhập Mã bệnh nhân, Họ tên và Ngày sinh, " +
                "sau đó bấm nút Tra cứu."

            // ── Kết quả tìm hồ sơ ──────────────────────────────────────────
            "Kết quả tìm hồ sơ" ->
                "Đã tìm thấy hồ sơ bệnh nhân. " +
                "Hãy kiểm tra thông tin họ tên và ngày sinh. " +
                "Nếu đúng, hãy bấm Xác nhận hồ sơ."

            // ── Xác nhận hồ sơ ─────────────────────────────────────────────
            "Xác nhận hồ sơ" ->
                "Vui lòng xác nhận hồ sơ của bạn. " +
                "Nếu đúng, hãy bấm Xác nhận. " +
                "Nếu không phải, hãy bấm Không."

            // ── Bước 8 ─────────────────────────────────────────────────────
            "Bước 8: Chọn ngày khám" ->
                "Bạn đang ở bước chọn ngày khám. " +
                "Hãy bấm vào ngày bạn muốn đến khám trên lịch."

            // ── Bước 8b ────────────────────────────────────────────────────
            "Bước 8b: Chọn giờ khám" ->
                "Bạn đang ở bước chọn giờ khám. " +
                "Hãy chọn buổi sáng hoặc buổi chiều, " +
                "sau đó bấm Tiếp tục để xác nhận."

            // ── Bước 9 ─────────────────────────────────────────────────────
            "Bước 9: Xác nhận & Thanh toán" ->
                "Bạn đang ở bước xác nhận thông tin và thanh toán. " +
                "Hãy kiểm tra lại tên bệnh viện, chuyên khoa, ngày giờ khám. " +
                "Sau đó chọn phương thức thanh toán và bấm Thanh toán để hoàn tất."

            // ── Hoàn thành ─────────────────────────────────────────────────
            "Hoàn thành" ->
                "Chúc mừng! Bạn đã đặt lịch khám thành công. " +
                "Hãy nhớ đến đúng giờ nhé!"

            else -> ""
        }
    }
}
