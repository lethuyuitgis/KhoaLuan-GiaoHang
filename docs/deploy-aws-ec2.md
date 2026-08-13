# Hướng dẫn deploy lên AWS EC2 (miễn phí — demo đồ án)

> ## ✅ ĐÃ DEPLOY XONG (05/08/2026)
>
> | | |
> |---|---|
> | Web Admin | **https://56-10-45-176.sslip.io/admin/** |
> | Mini App URL (dán vào BotFather) | **https://56-10-45-176.sslip.io/miniapp/** |
> | Instance | `i-0bedc54f9fc96b739` — **t3.micro**, ap-southeast-1 |
> | IP | `56.10.45.176` — **Elastic IP (cố định, không đổi khi Stop/Start)** |
> | SSH | `ssh -i ~/.ssh/giaohang.pem ubuntu@56.10.45.176` |
> | Cert HTTPS | Let's Encrypt, hết hạn **03/11/2026** |
> | Tài khoản AWS | Free plan mới — $100 credit, hạn 02/2027, **không thể bị trừ tiền vào thẻ** |
>
> Khác với hướng dẫn gốc bên dưới: dùng **sslip.io** thay cho DuckDNS (khỏi đăng ký — domain `56-10-45-176.sslip.io` tự trỏ về IP tương ứng), và code được **rsync từ máy local** thay vì clone GitHub (repo private). File cấu hình HTTPS nằm trên EC2: `infra/nginx/nginx-ssl.conf` + `infra/docker-compose.override.yml`.
>
> Nhờ Elastic IP, Stop/Start thoải mái — IP, domain, cert, URL BotFather đều giữ nguyên. Khi Terminate sau bảo vệ, nhớ **Release Elastic IP** (EC2 → Elastic IPs) để không bị tính phí IP treo.

---

Mục tiêu: đưa toàn bộ stack (backend + miniapp + webadmin + postgres + nginx)
lên 1 máy EC2, có **HTTPS** (bắt buộc — Telegram Mini App không chạy qua HTTP),
chi phí **0đ** bằng AWS Free Tier + DuckDNS + Let's Encrypt.

Kết quả cuối cùng:

| Thành phần | URL |
|---|---|
| Web Admin | `https://<tên-bạn-chọn>.duckdns.org/admin/` |
| Mini App (gắn vào bot Telegram) | `https://<tên-bạn-chọn>.duckdns.org/miniapp/` |
| API health | `https://<tên-bạn-chọn>.duckdns.org/actuator/health` |

Tổng thời gian: ~1–1.5 giờ (phần lớn là chờ build backend trên máy yếu).

---

## Bước 0 — Chuẩn bị

1. **Tài khoản AWS** (cần thẻ Visa/Mastercard để xác minh, không bị trừ tiền nếu ở trong Free Tier):
   - Tài khoản tạo **sau 15/07/2025**: Free Tier kiểu mới — được **$100 credit** (làm thêm vài activity được tối đa $100 nữa). Credit này dư sức chạy demo vài tháng.
   - Tài khoản cũ hơn: 750 giờ/tháng **t2.micro hoặc t3.micro** miễn phí trong 12 tháng đầu.
2. **BOT_TOKEN** Telegram (đã có sẵn trong `.env` local — sẽ copy sang).
3. Repo GitHub `lethuyuitgis/KhoaLuan-GiaoHang`. Nếu repo **private**, tạo Personal Access Token để clone: GitHub → Settings → Developer settings → Personal access tokens → Fine-grained token, quyền `Contents: Read` cho repo này.

---

## Bước 1 — Tạo EC2 instance

AWS Console → **EC2** → chọn region **ap-southeast-1 (Singapore)** (gần VN, ping thấp) → **Launch instance**:

| Mục | Chọn |
|---|---|
| Name | `giaohang-demo` |
| AMI | **Ubuntu Server 24.04 LTS** (64-bit x86) |
| Instance type | Xem ghi chú bên dưới |
| Key pair | **Create new key pair** → tên `giaohang` → tải file `giaohang.pem` về, cất kỹ |
| Network settings | Tích cả 3: **Allow SSH** (Source: *My IP*), **Allow HTTP**, **Allow HTTPS** (Anywhere) |
| Storage | **30 GiB, gp3** (mức tối đa Free Tier cho) |

**Chọn instance type:**
- Tài khoản có **$100 credit** (Free Tier mới) → chọn **t3.small** (2 GB RAM, ~$0.023/giờ ≈ $17/tháng, trừ vào credit). Build nhanh và chạy thoải mái. **Khuyên dùng.**
- Tài khoản Free Tier 12 tháng kiểu cũ → chọn **t3.micro** (hoặc t2.micro nếu console gắn nhãn "Free tier eligible" cho nó). Chỉ 1 GB RAM — **bắt buộc làm bước swap** ở dưới, build sẽ chậm (~20–30 phút) nhưng chạy được.

Bấm **Launch instance**. Vào trang instance, ghi lại **Public IPv4 address** (ví dụ `54.169.xx.xx`).

> Lưu ý: IP public này nằm trong Free Tier (750 giờ IPv4/tháng) khi gắn với instance đang chạy. Nhưng nó **đổi mỗi lần Stop/Start** instance — lúc đó phải cập nhật lại DuckDNS (Bước 5).

---

## Bước 2 — SSH vào máy, cài Docker + swap

Trên máy Mac:

```bash
chmod 400 ~/Downloads/giaohang.pem
ssh -i ~/Downloads/giaohang.pem ubuntu@<PUBLIC_IP>
```

Từ đây trở đi, mọi lệnh chạy **trên EC2**.

**2.1. Tạo swap 4 GB** (bắt buộc với t3.micro; t3.small cũng nên làm cho an toàn):

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h   # kiểm tra: dòng Swap phải hiện 4.0Gi
```

**2.2. Cài Docker:**

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit
```

SSH vào lại (để nhóm `docker` có hiệu lực), kiểm tra:

```bash
ssh -i ~/Downloads/giaohang.pem ubuntu@<PUBLIC_IP>
docker --version && docker compose version
```

---

## Bước 3 — Clone repo, tạo `.env`

```bash
cd ~
# Repo public:
git clone https://github.com/lethuyuitgis/KhoaLuan-GiaoHang.git
# Repo private (thay <TOKEN> bằng PAT ở Bước 0):
# git clone https://lethuyuitgis:<TOKEN>@github.com/lethuyuitgis/KhoaLuan-GiaoHang.git
cd KhoaLuan-GiaoHang
```

Tạo file `.env` ở gốc repo:

```bash
nano .env
```

Dán nội dung sau, **điền giá trị thật** (lấy từ file `.env` trên máy Mac của bạn — các dòng BOT_* và VNPAY_*):

```bash
# Telegram bot — copy từ .env local
BOT_TOKEN=<token thật của bot shop_giaohang_bot>
BOT_USERNAME=shop_giaohang_bot
BOT_MODE=polling
BOT_WEBHOOK_URL=
BOT_WEBHOOK_SECRET=

# Sinh chuỗi mới bằng lệnh:  openssl rand -base64 48
JWT_SECRET=<chuỗi ngẫu nhiên dài>

# VNPay sandbox — copy từ .env local, SỬA domain thành DuckDNS (Bước 5)
VNPAY_TMN_CODE=<mã sandbox>
VNPAY_HASH_SECRET=<secret sandbox>
VNPAY_RETURN_URL=https://<tên-bạn-chọn>.duckdns.org/api/payment/vnpay/return
VNPAY_IPN_URL=https://<tên-bạn-chọn>.duckdns.org/api/payment/vnpay/ipn

# Database (nội bộ docker, đặt mật khẩu mới bất kỳ)
DB_HOST=postgres
DB_PORT=5432
DB_NAME=shop_delivery
DB_USER=app
DB_PASSWORD=<mật khẩu mới>

# Cấu hình shop — giữ như local
SHOP_PICKUP_LAT=21.0285
SHOP_PICKUP_LNG=105.8542
SHOP_PICKUP_ADDRESS=Demo shop (Hoàn Kiếm)
SHOP_FEE_BASE=15000
SHOP_FEE_PER_KM=5000
SHOP_FEE_FREE_KM=1.0
```

Lưu (`Ctrl+O`, Enter, `Ctrl+X`).

> `BOT_MODE=polling` nghĩa là bot tự kéo tin nhắn về — không cần cấu hình webhook, chạy được ngay sau khi container lên.

---

## Bước 4 — Build và chạy stack

Build **từng service một** để không hết RAM (backend build Maven nặng nhất):

```bash
cd ~/KhoaLuan-GiaoHang
docker compose -f infra/docker-compose.yml --env-file .env build backend    # ~20-30 phút trên t3.micro
docker compose -f infra/docker-compose.yml --env-file .env build miniapp webadmin
docker compose -f infra/docker-compose.yml --env-file .env up -d
```

> **Luôn kèm `--env-file .env`** khi chạy từ gốc repo — vì `-f infra/...` làm compose tìm `.env` trong thư mục `infra/` chứ không phải gốc repo.

Chờ ~1–2 phút rồi kiểm tra:

```bash
docker compose -f infra/docker-compose.yml --env-file .env ps   # 5 container, tất cả (healthy)
curl -s http://localhost/healthz            # → ok
curl -s http://localhost/actuator/health    # → {"status":"UP",...}
```

Mở trình duyệt: `http://<PUBLIC_IP>/admin/` → thấy trang đăng nhập Web Admin là ổn.
Đăng nhập thử: `shop@example.com` / `Demo@Shop2026!` (dữ liệu seed sẵn).

---

## Bước 5 — Tên miền miễn phí (DuckDNS)

Telegram Mini App bắt buộc HTTPS, mà Let's Encrypt không cấp cert cho IP trần → cần 1 tên miền miễn phí.

1. Vào **https://www.duckdns.org** → đăng nhập bằng GitHub/Google.
2. Ô "sub domain": gõ tên (ví dụ `giaohang-kltn`) → **add domain**.
3. Cột "current ip": điền **Public IP của EC2** → **update ip**.
4. Kiểm tra từ máy Mac: `ping giaohang-kltn.duckdns.org` phải ra đúng IP EC2.

> Mỗi lần Stop/Start EC2 làm đổi IP → vào lại trang DuckDNS cập nhật IP mới (30 giây).

---

## Bước 6 — HTTPS miễn phí (Let's Encrypt)

**6.1. Xin chứng chỉ** (dùng chế độ standalone, cần port 80 trống nên tạm dừng nginx):

```bash
cd ~/KhoaLuan-GiaoHang
docker compose -f infra/docker-compose.yml --env-file .env stop nginx

sudo docker run --rm -p 80:80 \
  -v /etc/letsencrypt:/etc/letsencrypt \
  certbot/certbot certonly --standalone \
  -d <tên-bạn-chọn>.duckdns.org \
  --agree-tos -m thuyltt@uitgis.vn --no-eff-email
```

Thành công sẽ báo cert nằm ở `/etc/letsencrypt/live/<tên>.duckdns.org/`.
Cert hạn **90 ngày** — quá đủ tới ngày bảo vệ; nếu cần gia hạn thì chạy lại đúng lệnh trên.

**6.2. Tạo config nginx bản HTTPS** (không sửa file gốc trong repo — tạo file mới):

```bash
cp infra/nginx/nginx.conf infra/nginx/nginx-ssl.conf
nano infra/nginx/nginx-ssl.conf
```

Tìm block `server {` (dòng ~48) và sửa **đúng 2 chỗ**:

**(a)** Ngay TRƯỚC dòng `server {` hiện có, thêm 1 server nhỏ redirect HTTP → HTTPS:

```nginx
    server {
        listen 80 default_server;
        server_name _;
        location /.well-known/acme-challenge/ { root /var/www/certbot; }
        location / { return 301 https://$host$request_uri; }
    }
```

**(b)** Block `server` gốc: đổi dòng `listen 80 default_server;` thành:

```nginx
        listen 443 ssl default_server;
        http2 on;
        ssl_certificate     /etc/letsencrypt/live/<tên-bạn-chọn>.duckdns.org/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/<tên-bạn-chọn>.duckdns.org/privkey.pem;
```

**6.3. Tạo file override để mở port 443 + mount cert** (compose tự đọc file này):

```bash
nano infra/docker-compose.override.yml
```

```yaml
services:
  nginx:
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx-ssl.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
```

**6.4. Khởi động lại nginx:**

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.override.yml --env-file .env up -d nginx
docker logs shop_delivery_nginx --tail 20   # không được có dòng [emerg]
```

Kiểm tra từ máy Mac:

```bash
curl -s https://<tên-bạn-chọn>.duckdns.org/healthz          # → ok
curl -s https://<tên-bạn-chọn>.duckdns.org/actuator/health  # → {"status":"UP"}
```

Mở `https://<tên-bạn-chọn>.duckdns.org/admin/` → phải có ổ khóa 🔒 trên trình duyệt.

---

## Bước 7 — Trỏ bot Telegram vào Mini App

Trên điện thoại/Telegram Desktop, chat với **@BotFather**:

1. `/mybots` → chọn `@shop_giaohang_bot`
2. **Bot Settings** → **Menu Button** → **Configure menu button**
3. Gửi URL: `https://<tên-bạn-chọn>.duckdns.org/miniapp/`
4. Gửi tên nút: ví dụ `🛒 Đặt hàng`

Xong. Mở chat với bot → bấm nút menu → Mini App mở lên với HTTPS, đăng nhập tự động qua initData.

> Bot chạy chế độ polling ngay trên EC2 rồi, nên `/start` và thông báo đơn hàng hoạt động luôn, không cần làm gì thêm.

---

## Bước 7b — Nhận thông báo đơn mới qua Telegram (chủ shop)

Bot chỉ nhắn "🆕 Đơn mới" cho tài khoản Telegram có vai trò `SHOP_OWNER` (bảng `user_role`).
Tài khoản đăng nhập Web Admin (email) là hệ riêng, không tự nhận thông báo Telegram.

Cách gán: người đó nhắn `/start` với bot trước (để có mặt trong `telegram_user`),
rồi chạy trên EC2 (thay `<TELEGRAM_ID>` — xem id trong log backend hoặc bot @userinfobot):

```bash
sudo docker exec shop_delivery_postgres psql -U app -d shop_delivery \
  -c "INSERT INTO user_role (telegram_user_id, role, status) VALUES (<TELEGRAM_ID>, 'SHOP_OWNER', 'ACTIVE') ON CONFLICT (telegram_user_id, role) DO UPDATE SET status='ACTIVE';"
```

Có hiệu lực ngay, không cần restart. (Đã gán sẵn cho tài khoản của bạn: `1951735745`.)

---

## Bước 8 — Test luồng hoàn chỉnh

1. **Điện thoại (khách):** mở bot → Mini App → chọn món → đặt đơn COD.
2. **Laptop (admin):** `https://<tên>.duckdns.org/admin/` → đăng nhập `shop@example.com` / `Demo@Shop2026!` → đơn mới hiện realtime → Xác nhận → Gán shipper.
3. **Điện thoại 2 (shipper):** mở bot bằng tài khoản Telegram đã đăng ký shipper → nhận đơn → bắt đầu giao → hoàn tất.
4. **Khách** đánh giá sao → admin xem Dashboard/Báo cáo cập nhật.

---

## Chi phí & dọn dẹp

- **Khi không demo:** EC2 Console → chọn instance → **Instance state → Stop** (không tính giờ chạy; ổ đĩa 30 GB vẫn trong Free Tier). Khi Start lại: IP đổi → cập nhật DuckDNS (Bước 5), xong `docker compose ... up -d` tự chạy lại (restart: unless-stopped nên thường container tự lên).
- **Sau khi bảo vệ xong:** **Instance state → Terminate** để xóa hẳn, khỏi phát sinh phí.
- **Đặt cảnh báo tiền:** AWS Console → Billing → Budgets → tạo budget $1/tháng, nhập email — nếu lỡ vượt Free Tier sẽ có mail báo ngay.

---

## Xử lý sự cố

| Triệu chứng | Nguyên nhân / cách sửa |
|---|---|
| Build backend bị kill giữa chừng (`exit code 137`) | Hết RAM. Kiểm tra `free -h` — swap phải bật (Bước 2.1). Build lại, chỉ 1 service mỗi lần. |
| `docker compose ps` báo backend `unhealthy` | `docker logs shop_delivery_backend --tail 50`. Thường do `.env` thiếu/sai `BOT_TOKEN` hoặc `JWT_SECRET`. Sửa `.env` rồi `up -d` lại. |
| Compose báo thiếu biến môi trường | Quên `--env-file .env` (khi chạy từ gốc repo). |
| Trình duyệt không vào được qua IP/domain | Security Group phải mở port 80 và 443 (Anywhere). EC2 Console → Security → Edit inbound rules. |
| Certbot lỗi "Connection refused" | Port 80 đang bị nginx chiếm → `docker compose ... stop nginx` trước, hoặc Security Group chưa mở port 80. |
| Domain không ra IP mới sau khi Stop/Start | Cập nhật lại IP trên duckdns.org; đợi 1–2 phút cho DNS cache hết hạn. |
| Mini App trắng trang trong Telegram | URL menu button phải là **https** và kết thúc bằng `/miniapp/`. Kiểm tra cert còn hạn: `curl -vI https://<tên>.duckdns.org 2>&1 \| grep expire`. |
| Build quá chậm, muốn nhanh hơn | Cách khác: build image trên Mac với `docker buildx build --platform linux/amd64`, push lên Docker Hub (free), rồi trên EC2 chỉ `docker pull`. Chỉ cần khi build trên EC2 thất bại. |
