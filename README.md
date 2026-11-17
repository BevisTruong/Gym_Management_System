# Gym Management System

Ứng dụng quản lý phòng gym phục vụ việc quản lý hội viên, gói tập, huấn luyện viên và lịch tập. Dự án gồm giao diện web, backend Spring Boot và cơ sở dữ liệu PostgreSQL trên Supabase Cloud.

## Chức năng chính

- Đăng nhập theo vai trò và đổi mật khẩu khi được cấp mật khẩu tạm.
- Quản lý hội viên, huấn luyện viên, gói tập, kỳ hội viên và tài khoản.
- Đăng ký, gia hạn gói tập và theo dõi các kỳ sắp hết hạn hoặc vừa hết hạn.
- Tạo, sửa, hủy lịch tập; kiểm tra điều kiện hội viên và trùng lịch.
- Portal riêng để hội viên xem hồ sơ, gói và lịch tập; HLV xem lịch được phân công.
- Hiển thị số điện thoại liên hệ của hội viên và HLV trong buổi tập liên quan.

| Vai trò | Phạm vi sử dụng |
| --- | --- |
| Admin | Quản lý tài khoản, gói tập và các chức năng quản lý phòng gym. |
| Staff | Quản lý hội viên, HLV, kỳ hội viên và lịch tập; xem gói tập. |
| Member | Xem thông tin, gói hội viên và lịch tập của mình. |
| Trainer | Xem lịch tập được phân công. |

Backend kiểm tra quyền truy cập cho từng API. Việc ẩn chức năng trên giao diện không thay thế kiểm tra quyền ở backend.

## Công nghệ và cấu trúc

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Java 21, Spring Boot, Spring Security |
| Frontend | HTML, CSS, JavaScript |
| Database | PostgreSQL trên Supabase Cloud |
| Chạy bằng container | Docker Compose, Nginx cho frontend |

```text
GymManagementSystem/
├── docker-compose.yaml              # Chạy backend và frontend bằng Docker
├── gym_backend/                     # API, cấu hình và test Java
│   ├── .env.example                 # Mẫu cấu hình; không chứa key thật
│   ├── Dockerfile
│   └── src/
├── gym_frontend/
│   ├── src/api/                     # Các API adapter
│   └── tv3-ui/                      # Giao diện đang kết nối API thật
│       ├── index.html               # Trang sử dụng backend và Supabase
│       ├── demo.html                # Bản xem thử dùng dữ liệu mẫu cục bộ
│       └── test/                    # Test giao diện bằng Node.js
└── supabase/                        # Schema và cấu hình Supabase
```

## 1. Chuẩn bị môi trường

Chọn một trong hai cách chạy:

| Cách chạy | Cần cài trên máy |
| --- | --- |
| Docker Compose | Git, Docker Desktop có Docker Compose |
| Trực tiếp | Git, Java 21, Python 3; Node.js nếu muốn chạy test frontend |

Cả hai cách đều cần kết nối mạng đến Supabase Cloud. Không cần cài PostgreSQL hay tạo database container trên máy cá nhân.

Các lệnh bên dưới dùng PowerShell trên Windows. Trừ khi được ghi khác, chạy từ thư mục gốc dự án.

### Lấy mã nguồn

```powershell
git clone "<URL_REPOSITORY>"
cd GymManagementSystem
```

Thay `<URL_REPOSITORY>` bằng địa chỉ repository của nhóm. Nếu đã clone, mở thư mục dự án hiện có và chuyển sang nhánh nhóm đang sử dụng.

### Chuẩn bị Supabase Cloud

Liên hệ người quản lý project để được cung cấp cấu hình kết nối và tài khoản ứng dụng. Project Supabase cần có schema và các hàm database mà backend sử dụng, gồm `update_member_profile` và `update_trainer_profile`.

Schema gốc nằm tại [supabase/migrations/GymManagement_PostgreSQL_V2.sql](supabase/migrations/GymManagement_PostgreSQL_V2.sql). File này không bao gồm hai hàm cập nhật hồ sơ nói trên. Với project Cloud dùng chung, xác nhận các hàm đã được cài; không tự chạy lại schema hoặc migration trên database chung. Nếu tạo project Supabase mới, cần lấy script hàm RPC từ người quản lý trước khi dùng chức năng cập nhật hội viên và HLV.

## 2. Cấu hình backend

Mỗi thành viên tạo `gym_backend/.env` từ file mẫu và điền thông tin môi trường được cấp riêng:

```powershell
if (!(Test-Path gym_backend/.env)) {
    Copy-Item gym_backend/.env.example gym_backend/.env
}
```

Nếu `.env` đã tồn tại, lệnh trên giữ nguyên file đó. Mở file để cấu hình; không dùng nguyên các giá trị mẫu cho kết nối thật.

| Biến | Ý nghĩa |
| --- | --- |
| `SERVER_PORT` | Cổng backend khi chạy trực tiếp; mặc định `8080`. Docker Compose cố định cổng trong container là `8080`. |
| `SUPABASE_URL` | URL của project Supabase Cloud. |
| `SUPABASE_PUBLISHABLE_KEY` | Publishable key của project; hiện có trong cấu hình để tham chiếu. |
| `SUPABASE_SECRET_KEY` | Secret key dành cho backend truy cập Supabase. |
| `APP_JWT_SECRET` | Khóa ký token ổn định, ngẫu nhiên, tối thiểu 32 byte. |
| `APP_JWT_EXPIRATION_SECONDS` | Thời hạn token tính bằng giây; mặc định `28800`. |
| `APP_BOOTSTRAP_TOKEN` | Token tùy chọn để khởi tạo Admin đầu tiên khi database chưa có tài khoản. Có thể để trống khi không dùng. |
| `APP_TIME_ZONE` | Múi giờ tính nghiệp vụ; mặc định `Asia/Ho_Chi_Minh`. |
| `APP_SIMULATED_NOW` | Thời gian giả lập cho kiểm thử; để trống khi chạy bình thường. |
| `CORS_ALLOWED_ORIGINS` | Các origin frontend được phép gọi API, cách nhau bằng dấu phẩy. Giữ `http://localhost:5173` khi dùng hướng dẫn dưới đây. |

File `.env` chứa bí mật và đã được Git bỏ qua. Không commit file này, không đặt secret key trong frontend và không chia sẻ key hoặc mật khẩu qua repository, issue hay ảnh chụp màn hình.

## 3. Khởi chạy chương trình

### Cách A — Docker Compose

1. Khởi động Docker Desktop và chờ Docker Engine sẵn sàng.
2. Kiểm tra `gym_backend/.env` đã được cấu hình.
3. Từ thư mục gốc dự án, chạy:

```powershell
docker compose up --build
```

Lần đầu có thể mất vài phút để tải image và thư viện. Khi frontend và backend đã khởi động, mở:

- **Giao diện:** [http://localhost:5173/](http://localhost:5173/)
- **Kiểm tra backend:** [http://localhost:8080/api/health](http://localhost:8080/api/health)

Frontend có thể sẵn sàng trước backend; nếu đăng nhập ngay bị lỗi kết nối, đợi backend khởi động xong và thử lại. Lệnh build image bỏ qua việc chạy test; có thể chạy test riêng theo mục kiểm thử bên dưới.

Nhấn `Ctrl + C` để dừng các container đang chạy trong terminal. Để dừng và gỡ các container của dự án từ terminal khác:

```powershell
docker compose down
```

Cấu hình Compose hiện ánh xạ cổng vào `127.0.0.1`, vì vậy hai địa chỉ trên được truy cập từ chính máy chạy Docker. Dữ liệu vẫn nằm trên Supabase Cloud.

### Cách B — Chạy trực tiếp

**Terminal 1 — backend:**

```powershell
cd gym_backend
.\mvnw.cmd spring-boot:run
```

Chạy từ thư mục `gym_backend` để Spring đọc được `.env`. Giữ terminal mở và đợi thông báo backend khởi động thành công. Kiểm tra [http://localhost:8080/api/health](http://localhost:8080/api/health).

**Terminal 2 — frontend:** mở một PowerShell mới từ thư mục gốc dự án:

```powershell
cd gym_frontend/tv3-ui
python -m http.server 5173
```

Giữ terminal mở và truy cập [http://localhost:5173/](http://localhost:5173/). Nhấn `Ctrl + C` ở từng terminal để dừng.

### Đăng nhập và dữ liệu

Dùng tài khoản ứng dụng do người quản lý hoặc Admin cấp; không đưa mật khẩu thật vào tài liệu hay source code. Tài khoản Member, Trainer hoặc nhân viên mới có thể được cấp mật khẩu tạm và phải đổi mật khẩu ở lần đăng nhập đầu tiên.

Trang `/` sử dụng API backend và dữ liệu Supabase thật. Trang `/demo.html` dùng dữ liệu mẫu cục bộ trong trình duyệt, phù hợp để xem thử giao diện và không chứng minh kết nối Supabase.

## 4. Kiểm thử

**Backend**, từ `gym_backend`:

```powershell
.\mvnw.cmd test
```

**Frontend**, từ thư mục gốc dự án, cần Node.js:

```powershell
node --test gym_frontend/tv3-ui/test/live-app.test.cjs
```

Bộ test thông thường kiểm tra nghiệp vụ và giao diện bằng dữ liệu mô phỏng. Kết quả test không thay thế việc chạy thử trên trình duyệt và xác minh tích hợp với project Supabase cụ thể. Nếu cần thử luồng ghi trên database chung, dùng dữ liệu thử nghiệm đã được nhóm thống nhất.

## 5. Các điểm cần lưu ý

- **Dữ liệu dùng chung:** chạy local hay Docker đều ghi vào project Supabase được cấu hình trong `.env`. Thống nhất dữ liệu thử trước khi tạo, sửa hoặc hủy.
- **Cổng đang được sử dụng:** dừng bản chạy trực tiếp trước khi chạy Docker nếu cả hai cùng dùng cổng `8080` và `5173`. Có thể kiểm tra trên Windows bằng `Get-NetTCPConnection -State Listen -LocalPort 8080,5173`.
- **Health API:** `/api/health` cho biết backend phản hồi; endpoint này không chứng minh kết nối hoặc đăng nhập Supabase thành công.
- **Cấu hình API:** giao diện `tv3-ui` mặc định gọi `http://localhost:8080/api`. Nếu đổi địa chỉ backend, cần cấu hình `window.GYM_API_BASE_URL` trước khi nạp `api.js` và cập nhật CORS tương ứng.
- **Cấu hình thay đổi:** sau khi sửa `.env`, khởi động lại backend hoặc chạy lại Compose để container nhận giá trị mới. Sau khi sửa source trong image, chạy lại `docker compose up --build`.
- **Docker lỗi khởi động:** kiểm tra Docker Desktop/Engine, file `gym_backend/.env`, cổng, sau đó xem `docker compose logs backend` hoặc `docker compose logs frontend`. Không đăng công khai log hoặc ảnh chứa thông tin bí mật.
- **Giới hạn hiện tại:** giao diện tải tối đa 200 hội viên và 200 HLV cho danh sách quản lý; tìm kiếm trên giao diện chỉ xét dữ liệu đã tải. Đây là giới hạn đã chấp nhận cho phạm vi demo.

## API và tài liệu chi tiết

Các nhóm API chính gồm `/api/auth`, `/api/members`, `/api/trainers`, `/api/plans`, `/api/memberships`, `/api/sessions`, `/api/expirations`, `/api/accounts` và `/api/portal`. Backend trả dữ liệu thành công trong trường `data` của phản hồi JSON.

- [Hướng dẫn backend](gym_backend/README.md)
- [Hướng dẫn frontend và API adapter](gym_frontend/README.md)
- [Hướng dẫn giao diện tích hợp](gym_frontend/tv3-ui/README.md)
- [Schema PostgreSQL gốc](supabase/migrations/GymManagement_PostgreSQL_V2.sql)
