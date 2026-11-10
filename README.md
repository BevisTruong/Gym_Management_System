# Gym Management System

Ứng dụng quản lý phòng gym gồm backend Spring Boot, cơ sở dữ liệu Supabase và frontend đang được triển khai.

## Trạng thái bàn giao

- Backend có các nhóm API cho xác thực, hội viên, gói tập, đăng ký/gia hạn, huấn luyện viên, lịch tập, tài khoản và portal theo vai trò.
- Cấu hình Supabase và JWT được đọc ở backend từ `gym_backend/.env` (tệp này không được đưa lên Git).
- `gym_frontend/src/api/` đã có các hàm gọi API. Giao diện và cấu hình chạy frontend cần được Thành viên 3 hoàn thiện.
- Bộ test backend đã được chạy trong giai đoạn trước. Luồng ghi dữ liệu hoàn chỉnh trên Supabase thật vẫn cần xác nhận khi tích hợp.

## Cấu trúc

```text
gym_backend/       Spring Boot REST API và kiểm thử
gym_frontend/      Frontend và các API adapter
supabase/          Migration và cấu hình Supabase
```

## Setup cho thành viên trong nhóm

### 1. Lấy mã nguồn

Cài Git, Java 21 và một editor Java phù hợp. Để chạy frontend sau khi scaffold hoàn tất, cài Node.js và npm.

```powershell
git clone <URL-repository>
cd GymManagementSystem
git switch -c feat/<ten-cong-viec>
```

Thay `<URL-repository>` bằng địa chỉ repo của nhóm. Nếu đã clone repo, cập nhật trước khi làm việc:

```powershell
git switch main
git pull
git switch -c feat/<ten-cong-viec>
```

Nếu nhánh mặc định không phải `main`, dùng đúng tên nhánh mặc định của repo.

### 2. Cấu hình riêng trên máy

Tệp `.env` không được lưu trong Git. Mỗi thành viên cần tự tạo `gym_backend/.env` từ `gym_backend/.env.example`, rồi điền thông tin kết nối được người quản lý dự án cung cấp qua kênh riêng:

```powershell
if (!(Test-Path gym_backend/.env)) {
    Copy-Item gym_backend/.env.example gym_backend/.env
}
```

Không commit hoặc gửi các khóa `SUPABASE_SECRET_KEY`, `APP_JWT_SECRET`, token bootstrap hay mật khẩu lên repo, README, issue hoặc mã frontend. Không ghi đè `.env` đang có nếu đã được cấu hình.

Frontend dùng địa chỉ backend local trong `gym_frontend/.env.local`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

Tệp này cũng là cấu hình riêng của máy. Không cần cấu hình Supabase trực tiếp trong frontend; trình duyệt chỉ gọi backend.

### 3. Database và quyền truy cập

Backend cần một Supabase project đã có schema theo `supabase/migrations/GymManagement_PostgreSQL_V2.sql`. Trước khi áp dụng migration, hãy hỏi người quản lý dự án xem schema đã được cài trên môi trường được cấp chưa. Không chạy lại migration hoặc thử thao tác ghi trên database thật khi chưa được thống nhất.

Các thành viên cần tài khoản và quyền truy cập project phù hợp để kiểm tra nghiệp vụ. Dùng dữ liệu thử nghiệm được nhóm chỉ định; không chia sẻ thông tin đăng nhập cá nhân trong repo.

### 4. Chạy và kiểm tra backend

```powershell
cd gym_backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Kiểm tra `GET http://localhost:8080/api/health`. Cấu hình `.env` phải hoàn tất trước khi khởi động backend.

### 5. Chạy frontend

Frontend hiện chưa có `package.json` riêng và chưa thể chạy độc lập ngay sau khi clone. Sau khi Thành viên 3 bổ sung cấu hình Vite/scripts, cài dependency và chạy theo scripts trong `gym_frontend/package.json` (thông thường là `npm ci` rồi `npm run dev`). Đặt `VITE_API_BASE_URL` như trên và đảm bảo backend đang chạy.

### 6. Quy ước làm việc nhóm

- Làm trên nhánh công việc riêng, cập nhật nhánh mặc định trước khi bắt đầu và tạo pull request theo quy trình nhóm.
- Chỉ commit mã nguồn, migration đã thống nhất và README; không commit `.env`, `.env.local`, `node_modules/`, `target/` hoặc dữ liệu cá nhân.
- Nếu thay đổi endpoint, payload hoặc phân quyền, cập nhật API adapter và README liên quan, rồi thông báo cho người đang tích hợp frontend/backend.
- Trước khi bàn giao, chạy lại test phù hợp và ghi rõ các bước đã kiểm tra cùng môi trường database được dùng.

## Chạy dự án

### Backend

Cần Java 21. Tạo `gym_backend/.env` từ `gym_backend/.env.example`, điền thông tin Supabase và các giá trị bí mật của môi trường riêng. Không chia sẻ hoặc commit tệp `.env`.

```powershell
cd gym_backend
if (!(Test-Path .env)) {
    Copy-Item .env.example .env
}
# Điền các giá trị thật vào .env
.\mvnw.cmd spring-boot:run
```

Backend mặc định chạy tại `http://localhost:8080`. Health check: `GET http://localhost:8080/api/health`.

### Frontend

Frontend đang trong giai đoạn hoàn thiện. Sau khi bổ sung cấu hình chạy ứng dụng, đặt `VITE_API_BASE_URL=http://localhost:8080/api` trong `gym_frontend/.env.local`, rồi chạy frontend theo scripts được khai báo trong `gym_frontend/package.json`.

## Quyền truy cập

- `ADMIN` và `STAFF`: nghiệp vụ quản lý hội viên, gói tập, đăng ký, huấn luyện viên và lịch tập.
- `ADMIN`: thêm quyền quản lý tài khoản và tạo/cập nhật gói tập.
- `MEMBER`: chỉ truy cập portal hội viên.
- `TRAINER`: chỉ truy cập portal lịch huấn luyện viên.

Backend kiểm tra quyền trên mọi API được bảo vệ. Ẩn trang hoặc nút trên frontend không thay thế kiểm tra quyền này.

## Tài liệu theo khu vực

- [Backend README](gym_backend/README.md)
- [Frontend README](gym_frontend/README.md)
- [Supabase migration](supabase/migrations/GymManagement_PostgreSQL_V2.sql)
