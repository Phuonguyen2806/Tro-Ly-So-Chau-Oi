package com.example.chauoi.dichVu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Mở ứng dụng đích của 1 dịch vụ, hoặc mở Play Store nếu chưa cài. */
fun CauHinhDichVu.moUngDung(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(tenPackage)
    if (launchIntent != null) {
        context.startActivity(launchIntent)
        Log.d("DichVu", "🚀 Đã mở ứng dụng $tenGoi")
    } else {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$tenPackage"))
            )
        } catch (e: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$tenPackage"))
            )
        }
    }
}
 
