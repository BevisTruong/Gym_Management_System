# Hướng dẫn bàn giao UI prototype cho TV4

> Gói mã GitHub của TV3: mã giao diện nằm trong `gym_frontend/tv3-ui/`, theo cấu trúc thư mục người dùng đã cung cấp. Các nhận định về phiên bản Java và API bên dưới được kiểm tra từ source ZIP nhóm đã cung cấp trước đó, chưa xác nhận lại trên repository GitHub hiện tại. TV4 cần đối chiếu với backend mới nhất. Các API đề xuất không được coi là đã triển khai. Bản bàn giao này là mã HTML/CSS/JavaScript trực tiếp; không phải mã xuất từ Anima. Word được giữ ngoài gói mã này.


Tài liệu thao tác này viết bằng tiếng Việt cho nhóm; tên màn hình và nội dung giao diện giữ bằng tiếng Anh. Bản bàn giao là giao diện web có dữ liệu trình diễn cục bộ. Ngoài `/api/health`, các API dưới đây là **đề xuất cần TV4 xác nhận và triển khai**, chưa có trong source ZIP đã nhận.

Prototype hiện tại **không gọi API nào** theo thiết kế. Các thao tác dùng JavaScript và dữ liệu cục bộ; việc mở prototype không kiểm tra cả health endpoint. Các tệp thực tế trong `gym_frontend/tv3-ui/` là `index.html` (khung trang), `styles.css` (trình bày), `core.js` (quy tắc và dữ liệu demo), `app.js` (màn hình và tương tác). Chỉ cần bốn tệp này để chạy prototype; không có bước cài npm hoặc build.

## 1 Chốt kiến trúc trước khi nối dữ liệu

Source hiện tại khai báo Java 21 và Spring Boot 4.1.2, không dùng Swing hay JavaFX. Vì vậy giao diện HTML CSS JavaScript chạy trên trình duyệt phù hợp với hướng web. Không cần nhúng HTML vào ứng dụng desktop.

Mở `gym_backend/pom.xml` trong VS Code để kiểm tra phiên bản với TV4. Mở `src/main/java/com/gym/gym_backend/controller/HealthController.java`; endpoint duy nhất được cung cấp là `GET /api/health`. Chưa có endpoint cho hội viên, gói, lịch hoặc đăng nhập. PDF kiến trúc nói đến nhánh `feature/backend-development`; hãy yêu cầu TV4 xác nhận snapshot mới nhất từ nhánh đó trước khi tích hợp.

TV1 và TV2 đang mô tả console Java 17 với database nhúng hoặc SQL Server, còn PDF backend chọn Spring Boot Java 21 và PostgreSQL Supabase. Nhóm cần sửa tài liệu kiến trúc cho thống nhất. Giữ các tên thực thể và business rule đã thống nhất; không chạy script T-SQL của TV2 trên Supabase PostgreSQL. Không tự tạo lại hoặc xoá bảng Cloud.

## 2 Chạy và nhận bộ UI

1. Giải nén bộ bàn giao vào một thư mục mới để bảo toàn source gốc.
2. Mở thư mục prototype trong VS Code bằng **File > Open Folder**.
3. Mở tệp `index.html` của prototype bằng Chrome hoặc Edge để xem bản offline. Dữ liệu chỉ ở trình duyệt; thay đổi này không cập nhật Java hoặc database.
4. Chọn **Account → Continue** để vào giao diện. Mở **Help → About this preview** để chỉnh **Preview date and time** hoặc **Restore sample data**. Dùng **Switch account** khi cần đổi vai trò. Các tên nút gọn hơn không thay đổi giới hạn dữ liệu cục bộ hoặc việc mô phỏng tài khoản.
5. Xem thư mục ảnh minh chứng, design specification, navigation flow, wireframe và các chương báo cáo để so sánh giao diện.

## 3 Phương án phục vụ giao diện bằng Spring Boot

Với giao diện không cần công cụ build, sao chép `index.html`, CSS, JavaScript và thư mục tài nguyên của prototype vào `gym_backend/src/main/resources/static/`, giữ nguyên đường dẫn tương đối. Không sao chép scripts kiểm thử hoặc tài liệu vào thư mục public này.

Sau khi TV4 xác nhận build chạy được, mở terminal tại `gym_backend` và chạy `./mvnw.cmd spring-boot:run` trên Windows. Mở `http://localhost:8080/` để xem giao diện và `http://localhost:8080/api/health` để xem health route. Đây là hướng dẫn cần chạy tại máy nhóm; audit hiện tại chưa khởi động được backend vì wrapper chưa tải được Maven.

Nếu TV4 phục vụ frontend ở cổng khác, cấu hình CORS bằng origin cụ thể ở backend. Bản prototype mở bằng `file://` chỉ dùng cho demo offline; khi gọi API thật hãy phục vụ bằng HTTP. Cùng origin là phương án đơn giản hơn cho lần tích hợp đầu.

## 4 Hợp đồng API đề xuất

Các đường dẫn, JSON và HTTP status trong mục này là thiết kế bàn giao, không phải API đã được kiểm tra tồn tại. Giữ tiền tố `/api`, dùng JSON, ngày `YYYY-MM-DD`, giờ `HH:mm:ss`, ID bất biến do backend sinh. Chốt múi giờ nghiệp vụ cùng TV4; ngày demo cố định của prototype phải tách khỏi đồng hồ thật.

| Thao tác UI | API đề xuất | Kết quả cần có |
| --- | --- | --- |
| Đăng nhập, xem người hiện tại, đăng xuất | `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout` | Phiên đăng nhập và role do backend xác thực. |
| Đổi mật khẩu | `POST /api/auth/change-password` | Kiểm tra mật khẩu hiện tại, tối thiểu 8 ký tự và trạng thái bắt buộc đổi. |
| Danh sách, thêm, sửa hội viên | `GET /api/members`, `POST /api/members`, `PUT /api/members/{id}` | Trả ID, chuẩn hóa trường, bắt lỗi phone/email trùng; tên và ngày sinh trùng chỉ cảnh báo. |
| Thay đổi trạng thái hội viên | `PATCH /api/members/{id}/status` | Đồng bộ trạng thái tài khoản trong cùng thao tác nhất quán. |
| Danh sách, thêm, sửa gói | `GET /api/plans`, `POST /api/plans`, `PUT /api/plans/{id}` | Chỉ gói Available dùng cho đăng ký mới; không đổi giá các đăng ký cũ. |
| Xem đăng ký của hội viên | `GET /api/members/{id}/memberships` | Lịch sử, ngày hết hạn, giá tại lúc đăng ký, trạng thái được tính. |
| Đăng ký, gia hạn | `POST /api/memberships`, `POST /api/memberships/{id}/renew` | Backend tự tính ngày kết thúc, sao giá, xác định bản mới nhất, không trùng kỳ hoặc gia hạn hai lần. |
| Huấn luyện viên | `GET /api/trainers`, `POST /api/trainers`, `PUT /api/trainers/{id}`, `PATCH /api/trainers/{id}/status` | Kiểm tra phone/email và trạng thái, đồng bộ khóa tài khoản. |
| Lịch tập | `GET /api/sessions`, `POST /api/sessions`, `PUT /api/sessions/{id}`, `POST /api/sessions/{id}/cancel` | Kiểm tra gói phủ ngày tập, nhân sự Active, 05:00-22:00, 30-120 phút, không trùng lịch. |
| Danh sách gần hết hạn | `GET /api/expirations` | Tham số lọc 7 ngày/30 ngày theo yêu cầu; loại bản đã gia hạn theo BR-07. |
| Portal hội viên và HLV | `GET /api/portal/member`, `GET /api/portal/trainer` | Backend giới hạn hồ sơ và lịch theo tài khoản; không tin ID do client gửi. |
| Quản lý tài khoản | `GET /api/accounts`, `POST /api/accounts`, `PATCH /api/accounts/{id}/status`, `POST /api/accounts/{id}/reset-password` | Admin thực hiện theo quyền; không khóa Admin Active cuối cùng. |

Status HTTP đề xuất: `200` khi đọc/sửa thành công, `201` khi tạo, `400` cho dữ liệu sai, `401` chưa đăng nhập, `403` không có quyền, `404` ID không có, `409` trùng dữ liệu hoặc xung đột lịch. TV4 cần xác nhận cách ánh xạ cuối cùng.

Ví dụ request tạo hội viên, dùng snake_case theo tên cột TV2:

```json
{
  "full_name": "Bui Thi Hoa",
  "gender": "Female",
  "date_of_birth": "2002-06-10",
  "phone": "0901000008",
  "email": "hoa.demo@example.com",
  "address": "Ho Chi Minh City",
  "training_objective": "Improve strength",
  "internal_note": "",
  "status": "Active"
}
```

Ví dụ lỗi đề xuất:

```json
{
  "code": "SESSION_CONFLICT",
  "message": "The trainer already has a session during this time.",
  "field_errors": {"start_time": "Choose a different time."}
}
```

## 5 Ánh xạ dữ liệu

| Thực thể | Trường chuyển giữa UI và API | Quy tắc bàn giao |
| --- | --- | --- |
| Member | `member_id`, `full_name`, `gender`, `date_of_birth`, `phone`, `email`, `address`, `training_objective`, `internal_note`, `status` | `internal_note` chỉ Admin/Staff. Không trả cho Member/Trainer. Email rỗng chuyển thành null trước lưu. |
| Membership Plan | `plan_id`, `name`, `duration_months`, `price`, `benefits`, `status` | Giá dương, duration nguyên dương; currency chưa được tài liệu quy định, cần nhóm xác nhận. |
| Membership | `membership_id`, `member_id`, `plan_id`, `start_date`, `end_date`, `price_at_registration`, `previous_membership_id` | Các trường ngày kết thúc và giá snapshot do backend tính; status Upcoming/Active/Expired là trường response tính toán, không thêm cột status vào bảng. |
| Trainer | `trainer_id`, `full_name`, `phone`, `email`, `specialization`, `years_of_experience`, `status` | Email bắt buộc; năm kinh nghiệm không âm. |
| Training Session | `session_id`, `member_id`, `trainer_id`, `session_date`, `start_time`, `end_time`, `note`, `status` | Chỉ lưu Scheduled/Cancelled; Completed tính khi đọc. Không có FK trực tiếp từ session sang membership. |
| Account | `account_id`, `username`, `full_name`, `role`, `status`, `must_change_password`, `member_id`, `trainer_id` | Không trả `password_hash` hoặc `salt` cho trình duyệt. Username của Member/Trainer được khởi tạo từ phone theo BR-18. |

Nếu mã frontend dùng camelCase, giữ một adapter duy nhất chuyển `fullName` sang `full_name`, `startDate` sang `start_date`, tương tự cho các trường khác. Không sửa tên cột database chỉ để khớp tên biến UI.

Bản prototype bàn giao thực tế dùng snake_case cho các trường nghiệp vụ. Riêng khóa chính của mỗi danh sách dùng tên nội bộ `id`; adapter API cần chuyển `member_id`, `plan_id`, `membership_id`, `trainer_id`, `session_id`, `account_id` của bản ghi gốc thành `id` khi đọc vào đúng danh sách và chuyển ngược khi cần. Các khóa ngoại `member_id`, `plan_id`, `trainer_id`, `previous_membership_id` vẫn giữ nguyên ý nghĩa. Không ánh xạ mọi `member_id` thành `id` một cách đệ quy vì sẽ làm sai khóa ngoại.

Supabase PostgreSQL cần migration riêng đã được TV4/TV2 duyệt: `NVARCHAR` sang kiểu text/varchar tương đương, `IDENTITY` sang identity PostgreSQL, `BIT` sang boolean, và các filtered unique index sang cú pháp PostgreSQL tương ứng. Các thao tác `CREATE DATABASE`, `USE`, `GO`, `DBCC` của SQL Server không dùng nguyên văn. Đây là phần chuyển đổi backend/database, chưa được thực hiện trong gói TV3.

## 6 Thay kho dữ liệu demo bằng API thật

1. Tìm chỗ frontend đọc/ghi dữ liệu mẫu và gom vào một module adapter.
2. Giữ phiên bản dữ liệu mẫu mặc định cho trình diễn UI. Thông tin về môi trường hiện nằm trong **Help → About this preview**. Chỉ tạo chế độ API riêng sau khi TV4 cung cấp backend chạy được; giao diện hiện chưa có bộ chuyển Demo/API.
3. Trong API mode, thay thao tác đọc/ghi cục bộ bằng `fetch('/api/...')`; xử lý trạng thái đang tải, lỗi, không có dữ liệu, `401`, `403`, `409`. Không âm thầm quay về mock data khi API lỗi vì có thể khiến người demo hiểu nhầm đã lưu thật.
4. Với đăng ký/gia hạn, gửi lựa chọn và nhận lại kết quả tính từ backend. Không tin giá hoặc end date do client cung cấp.
5. Sau ghi thành công, nạp lại dữ liệu từ API. Chỉ hiển thị thông báo thành công khi backend trả kết quả thành công.
6. Không để secret Supabase trong JavaScript hoặc biến môi trường frontend. Browser chỉ gọi backend theo kiến trúc đã chọn; backend giữ thông tin truy cập dịch vụ.
7. Kiểm tra dữ liệu cùng lúc ở màn hình UI và database bằng quyền phù hợp của TV4. Lưu ảnh/log không có secret làm bằng chứng tích hợp.

## 7 Kiểm tra trước khi TV4 nhận hoàn tất

- TV4 xác nhận branch và API contract bằng văn bản trong repository.
- Frontend mở được qua Spring Boot, không lỗi thiếu CSS/JS hoặc tài nguyên.
- Thêm/sửa hội viên hiển thị dữ liệu thật sau refresh; phone/email trùng hiện lỗi rõ.
- Đăng ký/gia hạn tạo một bản ghi đúng ngày, đúng giá snapshot, đúng chuỗi previous membership; request lặp không tạo gia hạn trùng.
- Tạo lịch trùng bị backend từ chối; lịch tiếp giáp được phép; lịch của member không có gói phù hợp bị chặn.
- Đổi Inactive đồng bộ tài khoản Locked; restore Active tuân theo yêu cầu nhóm đã chốt.
- Member/Trainer không đọc được hồ sơ hoặc ghi chú nội bộ của người khác bằng cách đổi URL/request.
- Nút bị ẩn/khóa trên UI có kiểm tra quyền tương ứng tại backend.
- Tách kết quả kiểm thử prototype và kiểm thử backend; chỉ đánh dấu API integration hoàn thành khi các kiểm tra thật đã chạy.
