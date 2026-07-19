package com.example.chauoi.dichVu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object DichVuLoader {
    private const val TAG = "DichVuLoader"

    /** Đọc toàn bộ file .json trong assets/services/ và nạp thành danh sách CauHinhDichVu */
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
            Log.e(TAG, "❌ Không thể liệt kê thư mục services/", e)
        }
        return ketQua
    }

    private fun parseCauHinh(obj: JSONObject): CauHinhDichVu {
        return CauHinhDichVu(
            tenGoi = obj.getString("tenGoi"),
            tenPackage = obj.getString("tenPackage"),
            tuKhoaGiongNoi = obj.getJSONArray("tuKhoaGiongNoi").toStringList(),
            cauPhanHoiKhiMo = obj.getString("cauPhanHoiKhiMo"),
            buoc = obj.getJSONArray("buoc").let { mang ->
                (0 until mang.length()).map { i -> parseBuoc(mang.getJSONObject(i)) }
            }
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
 
