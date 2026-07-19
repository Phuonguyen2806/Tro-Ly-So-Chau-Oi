package com.example.chauoi.dichvu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

interface DichVu {

    /** Tên hiển thị/log, ví dụ "YouMed" */
    val tenGoi: String

    /** Package name thật của app đích, ví dụ "com.youmed.info" */
    val tenPackage: String

    val tuKhoaGiongNoi: List<String>

    val cauPhanHoiKhiMo: String

    /**
     * Phân tích toàn bộ text đọc được trên màn hình, trả về tên bước
     * (dùng làm khóa để tra câu hướng dẫn). Trả về "Không nhận diện"
     * nếu không khớp bước nào đã biết.
     */
    fun nhanDienBuoc(text: String): String

    /**
     * Trả về câu hướng dẫn (TTS sẽ đọc) cho 1 bước.
     * Trả về chuỗi rỗng nếu bước đó không cần đọc gì.
     */
    fun layHuongDan(buoc: String): String

    /**
     * Dành cho các bước cần xử lý ĐỘNG thay vì câu hướng dẫn tĩnh
     * (ví dụ: bước thanh toán cần đọc số tiền thật trích từ màn hình).
     * Trả về null nếu bước này dùng câu tĩnh bình thường từ layHuongDan().
     */
    fun xuLyDacBiet(buoc: String, text: String): String? = null
    fun moUngDung(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(tenPackage)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            Log.d("DichVu", "🚀 Đã mở ứng dụng $tenGoi")
        } else {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$tenPackage")
                    )
                )
            } catch (e: Exception) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$tenPackage")))
            }
        }
    }
}