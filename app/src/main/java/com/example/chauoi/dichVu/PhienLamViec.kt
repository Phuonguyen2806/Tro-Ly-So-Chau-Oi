package com.example.chauoi.dichVu

/**
 * Lưu mục đích (Goal) người dùng đang thực hiện trong phiên hiện tại.
 * Không lưu SQLite - chỉ tồn tại trong bộ nhớ khi app đang chạy,
 * mất đi khi tắt app - đúng bản chất "session" tạm thời.
 */
object PhienLamViec {
    var mucDichHienTai: String? = null
}