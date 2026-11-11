# Frontend

Giao diện HTML/CSS/JavaScript đã được nối với backend trong `tv3-ui/`. `tv3-ui/index.html` là bản chạy API thật; `tv3-ui/demo.html` là preview cục bộ dùng dữ liệu mẫu. Các API adapter ban đầu vẫn nằm trong `src/api/`.

## API adapter hiện có

- `apiClient.js`: gọi REST API, gắn JWT, giải envelope response và chuẩn hóa lỗi.
- `authApi.js`: đăng nhập, lấy người dùng hiện tại, đổi mật khẩu và đăng xuất.
- `memberApi.js`, `planApi.js`, `membershipApi.js`: hội viên, gói tập, đăng ký/gia hạn.
- `trainerApi.js`, `sessionApi.js`: huấn luyện viên, lịch tập và portal lịch.
- `accountApi.js`: quản lý tài khoản Admin.

Ưu tiên dùng các module này khi xây dựng giao diện. Nếu API cần thay đổi, đối chiếu controller backend và thống nhất với Thành viên 4 trước khi đổi contract.

## Cấu hình kết nối

Tạo `gym_frontend/.env.local`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

Không đặt `SUPABASE_SECRET_KEY`, `APP_JWT_SECRET` hoặc thông tin bí mật backend vào biến `VITE_*`: biến Vite có thể được đóng gói và gửi tới trình duyệt.

Backend cho phép origin mặc định `http://localhost:5173` và `http://localhost:3000`. Nếu chọn cổng khác, cập nhật `CORS_ALLOWED_ORIGINS` trong cấu hình backend.

## Setup cho thành viên mới

Sau khi clone repo, cài Node.js và npm. Tệp `.env.local` là cấu hình riêng của máy, không commit:

```powershell
if (!(Test-Path gym_frontend/.env.local)) {
    Copy-Item gym_frontend/.env.example gym_frontend/.env.local
}
```

Sau đó mở `gym_frontend/.env.local` và đặt `VITE_API_BASE_URL=http://localhost:8080/api`. Backend cũng cần được cấu hình và chạy theo [hướng dẫn backend](../gym_backend/README.md).

## Chạy giao diện tích hợp

Khởi động backend theo [README backend](../gym_backend/README.md), sau đó mở terminal thứ hai:

```powershell
cd gym_frontend/tv3-ui
python -m http.server 5173
```

Mở `http://localhost:5173/` và đăng nhập bằng tài khoản thật. Giao diện mặc định gọi `http://localhost:8080/api`. Nếu backend ở URL khác, đặt `window.GYM_API_BASE_URL` trước khi nạp `api.js` trong `tv3-ui/index.html`. Chỉ cấu hình khóa Supabase ở backend; trình duyệt không kết nối trực tiếp đến Supabase.

## Xử lý đăng nhập và lỗi API

- Sau khi đăng nhập, `authApi` lưu access token trong `sessionStorage`; `apiClient` tự gửi token ở các request tiếp theo.
- Backend trả dữ liệu trong trường `data`; adapter đã tự unwrap trường này. Thành phần giao diện thường nhận trực tiếp dữ liệu nghiệp vụ.
- Khi có lỗi, `apiClient` ném `ApiError` gồm `message`, `status` và `payload`. Hiển thị thông điệp phù hợp thay vì chỉ ghi lỗi vào console.
- `401`: xóa token và đưa người dùng về trang đăng nhập.
- `403`: thông báo người dùng không đủ quyền; không cố gọi lại API bằng cách bỏ kiểm tra phân quyền.
- Tài khoản có `mustChangePassword=true` cần được đưa qua luồng đổi mật khẩu trước khi dùng các chức năng còn lại.

## Vai trò và màn hình

| Vai trò | Màn hình/nghiệp vụ |
| --- | --- |
| `ADMIN` | Quản lý tài khoản, gói tập, hội viên, đăng ký/gia hạn, huấn luyện viên và lịch tập. |
| `STAFF` | Quản lý hội viên, xem gói, đăng ký/gia hạn, huấn luyện viên và lịch tập. |
| `MEMBER` | Portal hồ sơ, trạng thái gói và lịch đã được phân công. |
| `TRAINER` | Portal lịch huấn luyện viên. |

Admin và Staff không được dùng thay tài khoản Member/Trainer để xem các portal riêng. Quyền truy cập phải theo role trả về trong thông tin người dùng; backend vẫn kiểm tra quyền độc lập.

## Luồng tích hợp cần trình diễn

1. Đăng nhập với tài khoản có sẵn.
2. Tạo hội viên, kiểm tra danh sách/chi tiết và trạng thái.
3. Chọn gói đang Available và đăng ký hội viên.
4. Tạo hoặc chọn huấn luyện viên; lập lịch và xử lý trường hợp trùng lịch.
5. Đăng nhập bằng tài khoản Member để xem hồ sơ, gói và lịch.
6. Đăng nhập bằng tài khoản Trainer để xem lịch được phân công.
7. Đăng nhập bằng Admin để kiểm tra quản lý tài khoản và gói tập.

Thử thao tác ghi trên Supabase thật chỉ với dữ liệu được nhóm cho phép. Kiểm tra quyền, lỗi nhập liệu, trạng thái đang tải và trường hợp danh sách rỗng trước buổi demo.
