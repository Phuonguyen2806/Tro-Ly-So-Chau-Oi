# Dự Án: Cháu Ơi - Trợ Lý Thông Minh Hỗ Trợ Đặt Lịch Khám Bệnh

**Cháu Ơi** là ứng dụng Android được thiết kế chuyên biệt để hỗ trợ người cao tuổi trong việc đặt lịch khám bệnh trực tuyến. Dự án tập trung vào việc đơn giản hóa các luồng thao tác phức tạp trên ứng dụng y tế thực tế thông qua trí tuệ nhân tạo (AI) nhận diện giao diện và điều khiển bằng giọng nói.

## Các Tính Năng Chính
*   **Điều khiển bằng giọng nói (AI Voice Command):** Tích hợp công nghệ nhận diện giọng nói tiếng Việt, cho phép người dùng ra lệnh bằng các câu lệnh tự nhiên (ví dụ: "Cháu ơi, đặt lịch khám").
*   **Trợ lý ảo thông minh (Accessibility AI):** Theo dõi màn hình ứng dụng y tế thời gian thực, tự động nhận diện bước thao tác hiện tại.
*   **Hướng dẫn giọng nói (Text-to-Speech):** Tự động đọc hướng dẫn chi tiết cho từng bước thao tác, giúp người cao tuổi không cần đọc chữ.
*   **Nút Micro Nổi (Floating Mic Overlay):** Công cụ hỗ trợ kéo thả, cho phép người dùng kích hoạt nghe lệnh giọng nói bất kỳ lúc nào để yêu cầu hướng dẫn lại hoặc chuyển tiếp bước.
*   **Tối ưu hóa trải nghiệm người cao tuổi:** Giao diện tối giản, nút bấm lớn, màu sắc ấm áp, phản hồi bằng giọng nói thân thiện.

## Luồng Hoạt Động
1.  **Kích hoạt:** Người dùng mở app và ra lệnh bằng giọng nói.
2.  **Mở app y tế:** Hệ thống tự động khởi động ứng dụng đặt lịch khám thực tế.
3.  **Hỗ trợ thông minh:** Hệ thống nhận diện màn hình hiện tại của ứng dụng y tế, đọc hướng dẫn bằng giọng nói.
4.  **Phản hồi:** Khi người dùng nói "Xong rồi" hoặc "Hướng dẫn", hệ thống sẽ hỗ trợ đọc lại hoặc hướng dẫn bước kế tiếp.
5.  **Xác nhận:** Tại bước cuối cùng, hệ thống nhắc người dùng kiểm tra thông tin kỹ lưỡng trước khi xác nhận thanh toán bằng câu: "Bạn hãy đọc kỹ thông tin và xác nhận thanh toán."

## Công Nghệ Sử Dụng
*   **Android Accessibility Service:** Để can thiệp và theo dõi giao diện ứng dụng khác.
*   **Android SpeechRecognizer:** Nhận diện giọng nói tiếng Việt.
*   **Android Text-to-Speech (TTS):** Chuyển văn bản thành giọng nói tiếng Việt.
*   **WindowManager API:** Vẽ nút điều khiển nổi (Floating Overlay) trên màn hình.
*   **Kotlin & Android SDK:** Ngôn ngữ lập trình và các thư viện hỗ trợ xử lý giao diện.

## Trạng Thái Dự Án
Dự án đã hoàn thiện cơ bản về mặt chức năng nhận diện màn hình và điều khiển bằng giọng nói. Các bước tiếp theo sẽ bao gồm kiểm thử thực tế trên thiết bị của người cao tuổi và tinh chỉnh hiệu năng.
