# Backend

REST API được xây dựng bằng Java 21 và Spring Boot. Dữ liệu ứng dụng được đọc/ghi qua Supabase; backend phát hành JWT để xác thực người dùng.

## Cấu hình môi trường

Tạo `gym_backend/.env` từ `.env.example` và điền giá trị môi trường cá nhân:

| Biến | Mục đích |
| --- | --- |
| `SERVER_PORT` | Cổng HTTP; mặc định `8080`. |
| `SUPABASE_URL` | URL project Supabase. |
| `SUPABASE_PUBLISHABLE_KEY` | Publishable key; hiện có trong cấu hình để tham chiếu. |
| `SUPABASE_SECRET_KEY` | Khóa server-only để backend truy cập dữ liệu. Không đưa vào frontend hoặc Git. |
| `APP_JWT_SECRET` | Khóa ký JWT ổn định, ngẫu nhiên, tối thiểu 32 byte. |
| `APP_JWT_EXPIRATION_SECONDS` | Thời hạn token, mặc định 28800 giây. |
| `APP_BOOTSTRAP_TOKEN` | Token dùng cho chức năng bootstrap tài khoản ban đầu. |
| `APP_TIME_ZONE` | Múi giờ nghiệp vụ; mặc định `Asia/Ho_Chi_Minh`. |
| `APP_SIMULATED_NOW` | Ngày/giờ giả lập tùy chọn cho mục đích kiểm thử. Để trống khi chạy bình thường. |
| `CORS_ALLOWED_ORIGINS` | Danh sách origin frontend, phân cách bằng dấu phẩy. |

`application.properties` nạp `.env` theo đường dẫn tương đối đến thư mục làm việc. Hãy chạy lệnh từ `gym_backend` để cấu hình đúng.

### Setup sau khi clone repo

Mỗi thành viên tự tạo file cấu hình local; không đưa `.env` vào commit. Từ thư mục gốc:

```powershell
if (!(Test-Path gym_backend/.env)) {
    Copy-Item gym_backend/.env.example gym_backend/.env
}
```

Điền thông tin Supabase được cấp riêng cho môi trường nhóm. Nếu `gym_backend/.env` đã tồn tại, không ghi đè; cập nhật các giá trị cần thiết trong file hiện có. Không chạy migration lên database dùng chung trước khi xác nhận với người quản lý project.

## Chạy backend

Cần cài Java 21. Từ thư mục `gym_backend`:

```powershell
if (!(Test-Path .env)) {
    Copy-Item .env.example .env
}
# Điền cấu hình thật vào .env
.\mvnw.cmd spring-boot:run
```

Kiểm tra server bằng `GET http://localhost:8080/api/health`.

## Kiểm thử

Chạy bộ test tự động:

```powershell
cd gym_backend
.\mvnw.cmd test
```

Lệnh trên được chạy từ thư mục `gym_backend`. Cần Java 21.

Test thông thường dùng mock/fake để kiểm tra nghiệp vụ và API. Các kiểm tra kết nối Supabase thật cần cấu hình môi trường phù hợp; chỉ chạy khi chủ động muốn kiểm tra project dữ liệu thật.

## API chính

Các API thành công trả envelope `{ "success": true, "data": ..., "message": ... }`. Lỗi có mã HTTP tương ứng và thông điệp trong `message`. Frontend adapter hiện tại tự lấy trường `data`.

| Nhóm | Endpoint | Quyền |
| --- | --- | --- |
| Health | `GET /api/health` | Công khai |
| Xác thực | `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/change-password`, `POST /api/auth/logout` | Login công khai; các API khác cần đăng nhập |
| Hội viên | `/api/members` | `ADMIN`, `STAFF` |
| Gói tập | `/api/plans` | Đọc: `ADMIN`, `STAFF`; tạo/cập nhật/trạng thái: `ADMIN` |
| Đăng ký/gia hạn | `/api/memberships/register`, `/api/memberships/renew`, `/api/memberships/member/{memberId}` | `ADMIN`, `STAFF` |
| Huấn luyện viên | `/api/trainers` | `ADMIN`, `STAFF` |
| Lịch tập | `/api/sessions`, `POST /api/sessions/available-trainers` | `ADMIN`, `STAFF` |
| Sắp hết hạn | `GET /api/expirations` | `ADMIN`, `STAFF` |
| Tài khoản | `/api/accounts` | `ADMIN` |
| Portal hội viên | `GET /api/portal/member` | `MEMBER` |
| Portal huấn luyện viên | `GET /api/portal/trainer/schedule` | `TRAINER` |

Các route quản lý có những thao tác `GET`, `POST`, `PUT` hoặc `PATCH` tùy tài nguyên. Xem controller tương ứng trong `src/main/java/com/gym/gym_backend/` để biết chi tiết từng thao tác.

## Database

Schema nằm trong `../supabase/migrations/GymManagement_PostgreSQL_V2.sql`. Migration cần được áp dụng vào project Supabase trước khi chạy backend với database đó. Không chạy migration hoặc luồng ghi thử trên dữ liệu thật nếu chưa xác định đúng project và dữ liệu được phép dùng.

## Phân quyền cần biết khi tích hợp

- Token được gửi qua `Authorization: Bearer <accessToken>`.
- `ADMIN` không được phép gọi portal dành riêng cho `MEMBER` hoặc `TRAINER`; API trả `403` nếu sai vai trò.
- Tạo hội viên và huấn luyện viên trả mật khẩu tạm thời để nhân viên bàn giao cho người dùng. Chỉ hiển thị thông tin này ngay sau khi tạo và không lưu vào log frontend.
- Khi đổi mật khẩu, backend yêu cầu mật khẩu mới và xác nhận mật khẩu.
