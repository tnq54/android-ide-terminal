# TermIDE Studio v3.0 Full Edition - Android IDE & Terminal

Ứng dụng **Android IDE & Terminal toàn diện (Full Edition)** được xây dựng bằng **Kotlin + Android Native XML Views + JSch SSH/SFTP**, tối ưu hoàn toàn cho màn hình điện thoại (Mobile Adaptive UI) và đóng gói thành file `.apk` sẵn sàng cài đặt trên thiết bị Android (API 24+ / Android 7.0 đến Android 15+).

## Trọn bộ tính năng trong v3.0.0 Full Edition

### 1. Terminal Emulator + Kết nối SSH / SFTP thật (`>_ Term` & `🌐 SSH`)
- **Local Shell & Remote SSH Shell**: Chạy trực tiếp `/system/bin/sh` trên thiết bị Android hoặc kết nối tới máy chủ Linux VPS / Termux `sshd` (`127.0.0.1:8022` hoặc IP/Port bất kỳ) bằng mật khẩu hoặc SSH Private Key (sử dụng thư viện `com.github.mwiede:jsch`).
- **Lưu danh sách cấu hình SSH**: Lưu nhiều cấu hình server và kết nối lại chỉ với 1 chạm.
- **Tô màu ANSI 16/256 & Tự động hoàn thành (`TAB`)**: Hiển thị màu sắc chuẩn ANSI và tự động điền tên file/lệnh khi bấm `TAB`.
- **Upload & Chạy trực tiếp trên Server**: Khi đang bật SSH, nhấn `▶ Run` trong Code Editor sẽ tự động upload script qua SFTP lên server và thực thi từ xa.

### 2. Trình soạn thảo Đa Tab + Chia đôi màn hình + Xem trước Web (`</> Code`)
- **Multi-Tab Code Editor**: Mở nhiều file đồng thời trên thanh tab, hỗ trợ **Undo (`↶`) / Redo (`↷`)**, **Tìm & Thay thế (`🔍 Tìm`)**, và **Tự động lưu (Auto-Save)** khi chuyển tab.
- **Chế độ Chia đôi màn hình (`⬒ Split`)**: Vừa viết code ở nửa trên màn hình vừa xem kết quả chạy Terminal trực tiếp ở nửa dưới.
- **Xem trước Web / HTML / JS trực tiếp (`🌐 Web`)**: Tích hợp WebView hỗ trợ chạy thử giao diện `.html`, `.svg` và sandbox `.js` (`console.log`) ngay trong ứng dụng.
- **Kho mẫu Code nhanh (`🧩 Mẫu`)**: Chèn nhanh các đoạn mẫu Shell, Python, HTML5, Kotlin chỉ với 1 chạm.

### 3. Quản lý mã nguồn Git & Diff (`🌿 Git`)
- **4 thao tác Git trực quan**: Xem trạng thái thay đổi (`📊 Status`), xem so sánh dòng thêm/xóa tô màu xanh/đỏ (`📝 Diff (+/-)`), xem lịch sử (`📜 Lịch sử Commit`), tạo Commit (`✅ Commit`), và quản lý `Branch / Pull / Push / Clone`.
- **Hoạt động mọi lúc (Dual-Engine)**: Tự động dùng `git` trên máy / trên SSH server, đồng thời tích hợp bộ **Snapshot & Diff VCS nội bộ** giúp tính năng Status, Diff, Commit và Log hoạt động 100% ngay cả khi thiết bị Android offline chưa cài binary `git`.

### 4. Quản lý Tập tin + Tìm kiếm toàn Project + 6 Giao diện (`📁 Files` & `⚡ Tools`)
- Duyệt file, cấp quyền `+x`, upload SFTP lên server, tìm kiếm từ khóa xuyên suốt toàn bộ project (`🔎 Tìm trong Project`), và tùy chọn **6 bộ màu giao diện IDE** (`GitHub Dark`, `Dracula Purple`, `Monokai Warm`, `Matrix OLED Black`, `Solarized Deep Ocean`, `VS Code Dark+`).

## Hướng dẫn Build APK

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

File APK sau khi build nằm tại:
- Bản Release (đã ký v2): `TermIDE-release.apk` (`app/build/outputs/apk/release/app-release.apk`)
- Bản Debug: `TermIDE-debug.apk` (`app/build/outputs/apk/debug/app-debug.apk`)
