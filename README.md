# TermIDE Studio - Android IDE & Terminal

Ứng dụng **Android IDE & Terminal** (Kotlin + Android Native XML Views) đóng gói thành file `.apk` sẵn sàng cài đặt trên thiết bị Android (API 24+ / Android 7.0 trở lên).

## Tính năng chính

### 1. Terminal Emulator đa phiên (`>_ Terminal`)
- **Multi-Session Shell**: Mở nhiều phiên shell (`#1 bash-1`, `#2 bash-2`, `+ Session`, `✕` đóng phiên) chạy độc lập.
- **Thực thi lệnh hệ thống & Script**: Chạy trực tiếp `/system/bin/sh` thông qua `ProcessBuilder`, theo dõi thư mục hiện hành (`cd`, `pwd`), hỗ trợ biến môi trường (`export KEY=VALUE`), lịch sử câu lệnh (`history`) và lọc mã màu ANSI.
- **Thanh phím tắt chuyên dụng (Termux-style Extra Keys Bar)**:
  - `CTRL+C` (ngắt tiến trình đang chạy), `CLEAR`, `↑` / `↓` (duyệt lịch sử lệnh), `TAB`, `/`, `-`, `|`, `~`, `$`, `&&`, `>`, `ls -la`, `pwd`.

### 2. Trình soạn thảo mã nguồn (`</> Code Editor`)
- **Syntax Highlighting thời gian thực**: Tô màu từ khóa, chuỗi ký tự, chú thích (`#`, `//`, `/* */`), số, biến (`$VAR`) và lời gọi hàm cho `Shell (.sh)`, `Python (.py)`, `Kotlin (.kt)`, `Java (.java)`, `JavaScript (.js)`, `JSON`, `Markdown`.
- **Đánh số dòng (Line Numbers Gutter) & Thanh trạng thái**: Hiển thị vị trí con trỏ `Ln / Col`, tổng số ký tự, tổng số dòng và ngôn ngữ hiện tại.
- **Thanh ký tự code nhanh (Quick Symbol Bar)**: Chèn nhanh `TAB`, `{`, `}`, `(`, `)`, `[`, `]`, `;`, `"`, `'`, `=`, `$`, `#`, `|`, `->`, `#!/system/bin/sh`.
- **Tìm kiếm & Thay thế (`🔍 Tìm`)**: Tìm vị trí chuỗi hoặc thay thế hàng loạt trong file đang mở.
- **Chạy trực tiếp 1 chạm (`▶ Chạy` / `▶ Run`)**: Tự động lưu file, cấp quyền thực thi (`+x` cho `.sh`), chuyển sang tab Terminal và thực thi script ngay lập tức.

### 3. Quản lý dự án & Tập tin (`📁 Files`)
- **Workspace khởi tạo sẵn**: Tự động tạo thư mục `workspace/` kèm các file mẫu:
  - `hello.sh`: Script chào mừng và kiểm tra môi trường shell.
  - `sysinfo.sh`: Script chẩn đoán phần cứng, nhân Linux, Android SDK, RAM & bộ nhớ.
  - `script.py`: Mẫu mã nguồn Python.
  - `Main.kt`: Mẫu mã nguồn Kotlin.
  - `README.md`: Tài liệu hướng dẫn nhanh.
- **Thao tác tập tin đầy đủ**: Tạo file mới (`+ Tạo File`), tạo thư mục (`+ Thư mục`), điều hướng thư mục cha (`⬆ ..`), mở Terminal tại thư mục hiện tại (`>_ Shell`), đổi tên, xóa, và bật/tắt quyền thực thi (`chmod +x`).

### 4. Công cụ chẩn đoán nhanh (`⚡ Tools`)
- Chạy nhanh kiểm tra thông tin hệ thống (`sysinfo.sh`), danh sách tiến trình (`ps`), cấu hình mạng (`ip addr` / `ifconfig`), dung lượng ổ đĩa (`df -h`), biến môi trường (`env`), và tăng/giảm cỡ chữ (`10sp` – `22sp`).

## Cấu trúc dự án

- `app/src/main/AndroidManifest.xml`: Cấu hình ứng dụng và quyền truy cập.
- `app/src/main/java/com/termide/android/MainActivity.kt`: Giao diện chính và điều phối giữa 3 phân hệ Terminal, Code Editor, File Explorer.
- `app/src/main/java/com/termide/android/TerminalSession.kt`: Quản lý phiên shell, tiến trình nền, lịch sử lệnh và thư mục làm việc.
- `app/src/main/java/com/termide/android/SyntaxHighlighter.kt`: Bộ tô màu cú pháp và sinh lệnh chạy tự động theo ngôn ngữ.
- `app/src/main/java/com/termide/android/WorkspaceInitializer.kt`: Khởi tạo workspace mặc định và các script mẫu.
- `app/src/main/java/com/termide/android/FileAdapter.kt`: Hiển thị danh sách tập tin/thư mục kèm quyền `rwx`, dung lượng và ngày sửa đổi.
- `app/src/main/res/layout/activity_main.xml`: Bố cục giao diện chuẩn Dark IDE.

## Hướng dẫn Build APK

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

File APK sau khi build nằm tại:
- Bản Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Bản Release (đã ký): `app/build/outputs/apk/release/app-release.apk`
