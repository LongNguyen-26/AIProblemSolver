# SETUP.md — Hướng dẫn cài đặt

## Yêu cầu hệ thống

| Thành phần | Phiên bản tối thiểu |
|---|---|
| Java JDK | 16+ (khuyên dùng 17 LTS) |
| Maven | 3.8+ |
| Python | 3.10+ |
| pip | 23+ |
| g++ (cho C++ sandbox) | GCC 9+ |
| Tesseract OCR | 4.1+ |

---

## 1. Cài đặt Tesseract OCR

**Windows:**
```
Tải installer: https://github.com/UB-Mannheim/tesseract/wiki
Thêm vào PATH: C:\Program Files\Tesseract-OCR
```

**macOS:**
```bash
brew install tesseract
```

**Ubuntu/Debian:**
```bash
sudo apt install tesseract-ocr
```

---

## 2. Cài đặt Python service

```bash
cd ai_service
pip install -r requirements.txt
cp .env.example .env
```

Mở `.env` và điền `GROQ_API_KEY`:
```
GROQ_API_KEY=gsk-...
GROQ_MODEL=openai/gpt-oss-120b
GROQ_BASE_URL=https://api.groq.com/openai/v1
```

Chạy service:
```bash
python main.py
# → Uvicorn running on http://0.0.0.0:8000
```

Kiểm tra: mở `http://localhost:8000/health` → `{"status":"ok"}`
Swagger UI: `http://localhost:8000/docs`

---

## 3. Cài đặt Java app

```bash
mvn clean install
mvn javafx:run
```

---

## 4. Chạy song song

Mở 2 terminal:

**Terminal 1** (Python service):
```bash
cd ai_service && python main.py
```

**Terminal 2** (Java app):
```bash
mvn javafx:run
```

Trạng thái "● Online" màu xanh = kết nối thành công.

---

# USAGE.md — Hướng dẫn sử dụng

## Workflow cơ bản

### Bước 1: Nhập đề bài
- **Text**: Paste đề bài trực tiếp vào ô textarea bên trái
- **Ảnh**: Chọn radio "Ảnh" → "Chọn ảnh..." → chọn file PNG/JPG

### Bước 2: Cấu hình
- **Số test case**: Nhập số lượng muốn sinh (mặc định 10)
- **Edge cases**: Tick để bao gồm các trường hợp biên

### Bước 3: Phân tích
- Click **"Phân tích đề"**
- Chờ spinner → Tab "Phân tích" hiển thị kết quả parse
- Tab "Test Cases" hiển thị danh sách test

### Bước 4: Xem và quản lý Test Cases
- Tab **"Test Cases"**: Xem bảng, click row để xem chi tiết
- **"Thêm thủ công"**: Thêm test case tự viết
- **"Xóa đã chọn"**: Xóa test case không cần thiết
- **"Export ZIP"**: Tải về file ZIP chứa input/output files

### Bước 5: Sinh và kiểm tra code
- Tab **"Code & Kết quả"**
- Chọn loại code: **AC** (đúng) / **WA** (sai) / **TLE** (chậm)
- Chọn ngôn ngữ: **cpp** / **python** / **java**
- Click **"Sinh code"** → code xuất hiện trong editor
- Có thể sửa code trực tiếp trong editor
- Click **"Chạy tất cả test"** → kết quả hiển thị từng dòng

### Bước 6: Xuất báo cáo
- Click **"Xuất báo cáo"** → chọn nơi lưu file `.html`
- Mở bằng browser để xem báo cáo đẹp

---

## Màu sắc Verdict

| Màu | Ý nghĩa |
|---|---|
| 🟢 Xanh lá (AC) | Accepted — output đúng |
| 🔴 Đỏ (WA) | Wrong Answer — output sai |
| 🟠 Cam (TLE) | Time Limit Exceeded — quá 5 giây |
| 🟣 Tím (CE) | Compile Error — lỗi biên dịch |
| ⚪ Xám (RE) | Runtime Error — lỗi khi chạy |

---

## Xử lý lỗi thường gặp

**"Python AI Service: Offline"**
→ Chạy `python main.py` trong thư mục `ai_service/`

**"CE: g++ not found"**
→ Cài GCC. Windows: MinGW / MSYS2. Thêm vào PATH.

**"OCR trả về text rỗng"**
→ Ảnh mờ hoặc Tesseract chưa cài. Thử dùng Text input thay thế.

**Timeout khi gọi AI**
→ Kiểm tra GROQ_API_KEY, kiểm tra kết nối mạng.
