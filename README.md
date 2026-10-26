# TermIDE Studio v2.0 - Android IDE & Terminal

Ứng dụng **Android IDE & Terminal** (Kotlin + Android Native XML Views) thiết kế chuẩn thích ứng màn hình điện thoại (Mobile Adaptive UI) và đóng gói thành file `.apk` sẵn sàng cài đặt trên thiết bị Android (API 24+ / Android 7.0 đến Android 15+).

## Tính năng mới trong v2.0.0

### 1. Chế độ Chia đôi màn hình (`⬒ Split`: Code Editor + Terminal)
- Bật/tắt 1 chạm ngay trên thanh tiêu đề để **vừa sửa code ở nửa trên, vừa xem kết quả chạy Terminal trực tiếp ở nửa dưới** mà không cần chuyển tab.

### 2. Terminal Emulator hỗ trợ màu ANSI & Tự động hoàn thành (`TAB`)
- **ANSI Color Rendering (`AnsiParser.kt`)**: Hiển thị đầy đủ bảng màu ANSI 16/256 màu và chữ đậm trong màn hình kết quả Terminal.
- **Tab Auto-Completion**: Nhấn phím `TAB` trên thanh phím tắt sẽ tự động hoàn thành tên file/thư mục trong thư mục hiện hành hoặc câu lệnh shell.
- **Sao chép nhanh (`📋 Copy`)**: Sao chép toàn bộ lịch sử xuất Terminal vào Clipboard chỉ với 1 chạm.

### 3. Trình soạn thảo Đa Tab (Multi-Tab Code Editor) + Undo/Redo
- Mở cùng lúc nhiều file trên thanh **Open File Tabs** (`hello.sh`, `sysinfo.sh`, `script.py`, `Main.kt`...) và chuyển đổi tức thì, kèm dấu báo file chưa lưu (`●`).
- Hỗ trợ **Hoàn tác (`↶` Undo)** và **Làm lại (`↷` Redo)** riêng biệt cho từng tab file.

### 4. Tìm kiếm toàn bộ Project (`🔎 Tìm trong Project`) & Đổi Theme IDE (`🎨`)
- Tìm kiếm từ khóa xuyên suốt tất cả các file trong `workspace/` và nhảy thẳng tới dòng khớp khi chạm vào kết quả.
- Chuyển đổi giữa 4 giao diện màu sắc (**GitHub Dark**, **Dracula Purple**, **Monokai Warm**, **Matrix OLED Black**) và tự động lưu cấu hình.

## Hướng dẫn Build APK

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

File APK sau khi build nằm tại:
- Bản Release (đã ký v2): `TermIDE-release.apk` (`app/build/outputs/apk/release/app-release.apk`)
- Bản Debug: `TermIDE-debug.apk` (`app/build/outputs/apk/debug/app-debug.apk`)
