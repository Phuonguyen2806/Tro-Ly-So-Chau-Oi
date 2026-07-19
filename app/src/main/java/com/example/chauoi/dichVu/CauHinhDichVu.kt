package com.example.chauoi.dichVu

/**
 * 1 bước/màn hình của 1 dịch vụ.
 * kieuKhop = "ALL"  -> phải khớp ĐỦ tất cả từ khóa trong tuKhoa mới nhận là đúng bước
 * kieuKhop = "ANY"  -> chỉ cần khớp 1 trong các từ khóa là đủ
 * tuKhoaLoaiTru     -> nếu văn bản chứa bất kỳ từ nào ở đây thì LOẠI bước này ra
 * xuLyDacBiet       -> true nếu bước này cần code xử lý động (VD: đọc số tiền thật),
 *                       khi đó huongDan chỉ dùng làm câu dự phòng nếu code xử lý lỗi
 */
data class BuocDichVu(
    val id: String,
    val kieuKhop: String = "ALL",
    val tuKhoa: List<String>,
    val tuKhoaLoaiTru: List<String> = emptyList(),
    val huongDan: String,
    val xuLyDacBiet: Boolean = false
)

/** Cấu hình đầy đủ cho 1 dịch vụ/ứng dụng, nạp từ 1 file JSON trong assets/services/ */
data class CauHinhDichVu(
    val tenGoi: String,
    val tenPackage: String,
    val tuKhoaGiongNoi: List<String>,
    val cauPhanHoiKhiMo: String,
    val buoc: List<BuocDichVu>
)
 
