package com.example.chauoi.dichVu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object DichVuLoader {
    private const val TAG = "DichVuLoader"

    /** Nạp danh sách CauHinhDichVu (Tự động nạp danh sách mặc định trong Kotlin code nếu không có JSON) */
    fun taiTatCa(context: Context): List<CauHinhDichVu> {
        val ketQua = mutableListOf<CauHinhDichVu>()
        try {
            val danhSachFile = context.assets.list("services") ?: emptyArray()
            for (tenFile in danhSachFile) {
                try {
                    val noiDung = context.assets.open("services/$tenFile")
                        .bufferedReader().use { it.readText() }
                    ketQua.add(parseCauHinh(JSONObject(noiDung)))
                    Log.d(TAG, "✅ Đã nạp cấu hình: $tenFile")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Lỗi đọc file $tenFile", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Không tìm thấy thư mục assets/services/", e)
        }

        if (ketQua.isEmpty()) {
            Log.d(TAG, "🚀 Nạp danh sách ứng dụng mặc định từ Kotlin code (Không dùng JSON)")
            ketQua.addAll(layDanhSachMacDinh())
        }

        return ketQua
    }
    private fun layDanhSachMacDinh(): List<CauHinhDichVu> {
        return listOf(
            CauHinhDichVu(
                tenGoi = "YouMed",
                tenPackage = "com.youmed.info",
                tuKhoaGiongNoi = listOf("đặt lịch khám", "đặt khám", "youmed", "bác sĩ", "đặt lịch bác sĩ"),
                cauPhanHoiKhiMo = "Cháu đang mở ứng dụng đặt lịch khám YouMed cho ông bà đây ạ!",
                cauChaoMung = "Ông bà đã vào YouMed. Lần đầu tiên, ông bà hãy chạm nút micro màu cam ở trên và nói cho cháu biết ông bà muốn làm gì nhé.",
                cauNhanChuyenManHinh = "Màn hình đã chuyển. Ông bà tiếp tục chạm vào nút con mắt ở dưới để cháu hướng dẫn bước tiếp theo nhé.",
                buoc = emptyList()
            ),
            CauHinhDichVu(
                tenGoi = "VNeID",
                tenPackage = "com.vnid",
                tuKhoaGiongNoi = listOf("vneid", "vi en id", "căn cước công dân", "thẻ căn cước", "cccd", "tạm vắng", "tạm trú", "thường trú", "khai báo", "dân cư",
                    "thông báo lưu trú", "giấy tờ", "định danh", "tài khoản định danh", "đăng ký xe", "khai tử","giấy phép lái xe"),
                cauPhanHoiKhiMo = "Cháu đang mở ứng dụng VNeID cho ông bà đây ạ!",
                cauChaoMung = "Ông bà đã vào VNeID. Lần đầu tiên, ông bà hãy chạm nút micro màu cam ở trên và nói cho cháu biết ông bà cần làm thủ tục gì nhé.",
                cauNhanChuyenManHinh = "Màn hình đã chuyển. Ông bà tiếp tục chạm vào nút con mắt ở dưới để cháu quét và hướng dẫn nhé.",
                buoc = emptyList()
            ),
            CauHinhDichVu(
                tenGoi = "VssID",
                tenPackage = "com.bhxhapp",
                tuKhoaGiongNoi = listOf("vssid", "vi ss id", "bảo hiểm xã hội", "bảo hiểm y tế", "sổ bảo hiểm", "thẻ bảo hiểm", "quá trình tham gia"),
                cauPhanHoiKhiMo = "Cháu đang mở ứng dụng VssID cho ông bà đây ạ!",
                cauChaoMung = "Ông bà đã vào ứng dụng Bảo hiểm. Lần đầu tiên, ông bà hãy chạm nút micro màu cam ở trên và nói mục đích cho cháu biết nhé.",
                cauNhanChuyenManHinh = "Màn hình đã chuyển. Ông bà tiếp tục chạm vào nút con mắt ở dưới để cháu hướng dẫn bước tiếp theo nhé.",
                buoc = emptyList()
            )
        )
    }
    private fun parseCauHinh(obj: JSONObject): CauHinhDichVu {
        return CauHinhDichVu(
            tenGoi = obj.getString("tenGoi"),
            tenPackage = obj.getString("tenPackage"),
            tuKhoaGiongNoi = obj.getJSONArray("tuKhoaGiongNoi").toStringList(),
            cauPhanHoiKhiMo = obj.getString("cauPhanHoiKhiMo"),
            // Đọc 2 trường mới, có giá trị mặc định phòng khi quên ghi trong JSON
            cauChaoMung = obj.optString("cauChaoMung", "Ông bà đã vào ứng dụng. Hãy bấm nút hình con mắt để cháu hướng dẫn nhé."),
            cauNhanChuyenManHinh = obj.optString("cauNhanChuyenManHinh", "Ông bà tiếp tục nhấn vào con mắt để cháu quét màn hình nhé."),
            mucDich = if (obj.has("mucDich"))
                obj.getJSONArray("mucDich").let { mang ->
                    (0 until mang.length()).map { i -> parseMucDich(mang.getJSONObject(i)) }
                } else emptyList(),
            buoc = obj.getJSONArray("buoc").let { mang ->
                (0 until mang.length()).map { i -> parseBuoc(mang.getJSONObject(i)) }
            }
        )
    }

    private fun parseMucDich(obj: JSONObject): MucDich {
        return MucDich(
            id = obj.getString("id"),
            tenGoi = obj.getString("tenGoi"),
            tuKhoaGiongNoi = obj.getJSONArray("tuKhoaGiongNoi").toStringList()
        )
    }

    private fun parseBuoc(obj: JSONObject): BuocDichVu {
        return BuocDichVu(
            id = obj.getString("id"),
            kieuKhop = obj.optString("kieuKhop", "ALL"),
            tuKhoa = obj.getJSONArray("tuKhoa").toStringList(),
            tuKhoaLoaiTru = if (obj.has("tuKhoaLoaiTru"))
                obj.getJSONArray("tuKhoaLoaiTru").toStringList() else emptyList(),
            huongDan = obj.getString("huongDan"),
            huongDanTheoMucDich = if (obj.has("huongDanTheoMucDich")) {
                val mapObj = obj.getJSONObject("huongDanTheoMucDich")
                mapObj.keys().asSequence().associateWith { key -> mapObj.getString(key) }
            } else emptyMap(),
            xuLyDacBiet = obj.optBoolean("xuLyDacBiet", false)
        )
    }
    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }

    /**
     * Thay cho nhanDienBuoc() cũ. Tính điểm khớp cho từng bước, trả về bước có điểm cao nhất
     * trong số các bước hợp lệ (khớp đủ nếu ALL, khớp >=1 nếu ANY, và không dính từ loại trừ).
     */
    fun timBuocPhuHop(dsBuoc: List<BuocDichVu>, allText: String): BuocDichVu? {
        return dsBuoc
            .filter { buoc -> buoc.tuKhoaLoaiTru.none { allText.contains(it, ignoreCase = true) } }
            .mapNotNull { buoc ->
                val soKhop = buoc.tuKhoa.count { allText.contains(it, ignoreCase = true) }
                val hopLe = if (buoc.kieuKhop == "ANY") soKhop >= 1 else soKhop == buoc.tuKhoa.size
                if (hopLe) buoc to soKhop else null
            }
            .maxByOrNull { it.second }
            ?.first
    }
}
 
