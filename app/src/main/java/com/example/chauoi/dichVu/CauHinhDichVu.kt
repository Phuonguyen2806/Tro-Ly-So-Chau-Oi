package com.example.chauoi.dichVu

/**
 * 1 bước/màn hình của 1 dịch vụ.
 * kieuKhop = "ALL"  -> phải khớp ĐỦ tất cả từ khóa trong tuKhoa mới nhận là đúng bước
 * kieuKhop = "ANY"  -> chỉ cần khớp 1 trong các từ khóa là đủ
 * tuKhoaLoaiTru     -> nếu văn bản chứa bất kỳ từ nào ở đây thì LOẠI bước này ra
 * xuLyDacBiet       -> true nếu bước này cần code xử lý động (VD: đọc số tiền thật),
 *                       khi đó huongDan chỉ dùng làm câu dự phòng nếu code xử lý lỗi
 */
/** 1 mục đích cụ thể trong 1 dịch vụ, VD: "Xem thẻ BHYT", "Cấp đổi CCCD" */
data class MucDich(
    val id: String,
    val tenGoi: String,
    val tuKhoaGiongNoi: List<String>
)

data class BuocDichVu(
    val id: String,
    val kieuKhop: String = "ALL",
    val tuKhoa: List<String>,
    val tuKhoaLoaiTru: List<String> = emptyList(),
    val huongDan: String,
    // Câu hướng dẫn RIÊNG cho từng mục đích, khóa = id của MucDich.
    // Nếu bước này không có trong map, hoặc chưa xác định được mục đích -> dùng huongDan mặc định.
    val huongDanTheoMucDich: Map<String, String> = emptyMap(),
    val xuLyDacBiet: Boolean = false
) {
    fun layHuongDan(mucDichHienTai: String?): String {
        if (mucDichHienTai != null) {
            huongDanTheoMucDich[mucDichHienTai]?.let { return it }
        }
        return huongDan
    }
}

/** Cấu hình đầy đủ cho 1 dịch vụ/ứng dụng, nạp từ 1 file JSON trong assets/services/ */
data class CauHinhDichVu(
    val tenGoi: String,
    val tenPackage: String,
    val tuKhoaGiongNoi: List<String>,
    val cauPhanHoiKhiMo: String,
    val mucDich: List<MucDich> = emptyList(),
    val buoc: List<BuocDichVu>
)
 
