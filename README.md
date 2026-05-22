# 🏦 BankNotify

**Giám sát giao dịch ngân hàng trực tiếp từ thông báo điện thoại Android.**

BankNotify lắng nghe thông báo (notification) từ ứng dụng ngân hàng trên điện thoại, tự động parse nội dung thành giao dịch có cấu trúc, lưu trữ local, expose REST API và gửi webhook đến server của bạn — giống như giải pháp Sepay nhưng chạy hoàn toàn trên thiết bị Android.

---

## 📲 Cài đặt

### Yêu cầu
- Android 7.0+ (API 24)
- Quyền **Notification Access** (Xem thông báo)

### Các bước

| Bước | Thao tác |
|------|----------|
| **1** | Tải APK phiên bản mới nhất từ [Releases](https://github.com/ecosyntech68vn/BankNotify-App/releases) |
| **2** | Mở file APK đã tải → chọn **Cài đặt** (nếu chưa cho phép cài ứng dụng từ nguồn này, vào Settings → bật) |
| **3** | Mở app BankNotify |
| **4** | Màn hình chào → chọn **Cấp quyền ngay** → đưa bạn đến Settings → **Notification Access** → bật **BankNotify** |
| **5** | Quay lại app → đã sẵn sàng! |

> ⚠️ Bước 4 là quan trọng nhất: nếu không cấp quyền Notification Access, app sẽ không thấy được thông báo ngân hàng.

---

## ✨ Tính năng V2 — Quản trị tài chính cá nhân

| Tính năng | Mô tả |
|-----------|-------|
| **Dashboard tổng quan** | Tổng tài sản, thu nhập vs chi tiêu tháng này, biểu đồ dòng tiền |
| **Chi tiêu theo danh mục** | Tự động phân loại giao dịch vào 18 danh mục (Ăn uống, Mua sắm, Di chuyển, Tiện ích...) |
| **Quản lý tài khoản/ví** | Theo dõi số dư từng tài khoản ngân hàng và ví điện tử |
| **Tồn đầu kỳ** | Nhập số dư ban đầu cho từng tài khoản |
| **Báo cáo dòng tiền** | Biểu đồ thu/chi theo tháng, xanh cho dương, đỏ cho âm |
| **Hỗ trợ ví điện tử** | Momo, ZaloPay, ShopeePay, VNPay |

---

## 🎯 Luồng hoạt động

```
📨 App ngân hàng gửi thông báo
       ↓
🔔 BankNotify đọc thông báo (qua NotificationListenerService)
       ↓
🔍 Parse nội dung → rút trích: số tiền, tài khoản, nội dung, người gửi, số dư, mã GD
       ↓
💾 Lưu vào SQLite (Room)
       ↓
├── 📋 Hiển thị trên giao diện app
├── 🔄 Gửi webhook HTTP POST đến server của bạn
├── 🌐 REST API local (cổng 8765) cho hệ thống ngoài gọi
└── 🔔 Thông báo giao dịch mới trên điện thoại
```

---

## 🧭 Giao diện & Tính năng

### Màn hình chính — Dashboard
#### V1 (nhánh `main`)
- Biểu đồ cột thống kê theo tháng
- Tổng số giao dịch, tổng số tiền trong kỳ

#### V2 (nhánh `v2`) — Quản trị tài chính cá nhân
- **Tổng tài sản** — tổng hợp số dư tất cả tài khoản ngân hàng, ví điện tử
- **Thu nhập — Chi tiêu** tháng này, kèm thanh tỷ lệ trực quan
- **Chi tiêu theo danh mục** — 18 danh mục tự động phân loại (SALARY, FOOD, SHOPPING, TRANSPORT...)
- **Biểu đồ dòng tiền** — xanh cho thu nhập, đỏ cho chi tiêu theo tháng
- **Danh sách tài khoản/ví** — số dư hiện tại của từng tài khoản

### Menu (góc trên bên phải)

| Mục | Chức năng |
|-----|-----------|
| **Bảng điều khiển** | Dashboard thống kê |
| **Bảo mật** | Bật/tắt khoá vân tay / Face Unlock |
| **Xuất dữ liệu** | Export JSON / CSV → chia sẻ qua bất kỳ app nào |
| **Bản quyền** | Nhập license key để active / xem hạn dùng |
| **API Docs** | Hướng dẫn REST API (trang web) |
| **Giới thiệu** | Thông tin app |

### Khoá vân tay / Face Unlock
Vào menu → **Bảo mật** → bật **Khoá ứng dụng**. Lần sau mở app sẽ yêu cầu xác thực sinh trắc học.

### Xuất dữ liệu
Menu → **Xuất dữ liệu** → chọn JSON hoặc CSV → chọn app nhận (Zalo, Telegram, Email, Gmail…).

---

## 🌐 REST API

API server chạy trên thiết bị tại `http://<địa-chỉ-IP>:8765`, bind localhost (chỉ app trên cùng thiết bị hoặc máy cùng mạng mới gọi được).

Khi app chạy, một foreground notification hiển thị "API Server đang chạy" (có thể vuốt ẩn nhưng không tắt được service — tắt service là tắt luôn API).

### Endpoints

#### Health check
```bash
GET /health
# → { "status": "ok", "timestamp": 1712345678000 }
```

#### Giao dịch
```bash
# Danh sách giao dịch (có filter)
GET /transactions?limit=20&offset=0&bank_code=VCB

# Giao dịch gần đây
GET /transactions/recent

# Chi tiết giao dịch
GET /transactions/123

# Xác nhận giao dịch
POST /transactions/123/confirm

# Xoá giao dịch
DELETE /transactions/123

# Thống kê
GET /transactions/stats
# → { "total_count": 150, "total_amount": 75000000.0 }

# Giao dịch chưa đọc
GET /transactions/unread
# → { "unread_count": 5 }
```

#### Filter `/transactions`

| Param | Kiểu | Ví dụ |
|-------|------|-------|
| `bank_code` | string | `VCB`, `TCB`, `MB` |
| `status` | string | `PENDING`, `CONFIRMED` |
| `from_date` | long | timestamp (ms) |
| `to_date` | long | timestamp (ms) |
| `min_amount` | double | `100000` |
| `max_amount` | double | `5000000` |
| `search` | string | tìm trong nội dung |
| `limit` | int | 1-200 (mặc định 50) |
| `offset` | int | phân trang |

#### Webhook
```bash
# Xem cấu hình
GET /webhook

# Cập nhật URL + secret
POST /webhook
Content-Type: application/json
{ "url": "https://your-server.com/webhook", "secret": "my-signing-secret" }

# Test gửi webhook mẫu
POST /webhook/test
```

#### Cấu hình server
```bash
# Xem cấu hình
GET /config

# Cập nhật
POST /config
Content-Type: application/json
{ "port": 8765, "api_key": "my-secret-key" }
```

#### OTA Update
```bash
# Kiểm tra bản cập nhật
GET /update/check

# Xem URL cập nhật
GET /update/url

# Đặt URL cập nhật
POST /update/url
Content-Type: application/json
{ "url": "https://example.com/update.json" }
```

### Authentication

Nếu đã cấu hình `api_key`, tất cả request phải gửi kèm:
```
Header: X-API-Key: my-secret-key
```
hoặc
```
Header: Authorization: Bearer my-secret-key
```

---

## 🏷️ Danh mục tự động (V2)

App tự động phân loại giao dịch vào danh mục dựa trên nội dung. Cấu hình trong `CategoryEngine.kt`:

| Danh mục | Từ khoá | Loại |
|----------|---------|------|
| `SALARY` | lương, salary | Thu nhập |
| `FOOD` | ăn uống, food, cafe, trà sữa | Chi tiêu |
| `SHOPPING` | mua hàng, shopping, thanh toán | Chi tiêu |
| `TRANSPORT` | xăng, taxi, grab, xe bus | Chi tiêu |
| `UTILITIES` | điện, nước, internet, tiền nhà | Chi tiêu |
| `ENTERTAINMENT` | game, music, netflix, phim | Chi tiêu |
| `HEALTH` | bệnh viện, thuốc, hospital | Chi tiêu |
| `EDUCATION` | học phí, tuition, education | Chi tiêu |
| `INVESTMENT` | đầu tư, chứng khoán | Khác |
| `TRANSFER` | chuyển tiền, ck | Khác |
| và 8 danh mục khác... | — | — |

---

## 🔔 Webhook

Khi có giao dịch mới, server gửi HTTP POST đến URL bạn cấu hình.

### Payload mẫu
```json
{
  "bank_code": "VCB",
  "bank_name": "Vietcombank",
  "account_number": "1012345678",
  "amount": 500000.0,
  "balance": 5000000.0,
  "content": "Chuyen tien mua hang",
  "sender_name": "NGUYEN VAN A",
  "reference_number": "REF123456",
  "transaction_date": 1712345678000,
  "status": "PENDING"
}
```

### HMAC Signature
Nếu có `secret`, request sẽ kèm header:
```
X-Webhook-Signature: sha256=12ab34cd56ef...
```
Bạn verify signature ở server nhận để đảm bảo dữ liệu không bị giả mạo.

---

## 🔐 Bản quyền (License)

App hỗ trợ:
- **Dùng thử 14 ngày**
- **Nhập license key** để kích hoạt vĩnh viễn

License key được tạo bằng thuật toán RSA-2048, nhúng public key trong APK. Mỗi key gắn với email người dùng, có thời hạn.

> Liên hệ tác giả để mua license.

---

## 🏦 Ngân hàng hỗ trợ

| Code | Ngân hàng | Gói ứng dụng |
|------|-----------|-------------|
| VCB | Vietcombank | `com.vietcombank`, `com.vietcombank.vcb` |
| TCB | Techcombank | `com.techcombank`, `vn.techcombank` |
| MB | MB Bank | `com.mbbank`, `com.mb.mbbank` |
| ACB | ACB | `com.acb.acb`, `vn.acb` |
| VPB | VPBank | `com.vpbank`, `vn.vpbank` |
| TPB | TPBank | `com.tpbank`, `vn.tpbank` |
| VIB | VIB | `com.vib`, `vn.vib` |
| BIDV | BIDV | `com.bidv`, `vn.bidv` |
| CTG | VietinBank | `com.vietinbank`, `vn.vietinbank` |
| STB | Sacombank | `com.sacombank`, `vn.sacombank` |
| HDB | HDBank | `com.hdbank`, `vn.hdbank` |
| OCB | OCB | `com.ocb`, `vn.ocb` |
| MSB | MSB | `com.msb`, `vn.msb` |
| SHB | SHB | `com.shb`, `vn.shb` |

### Ví điện tử (V2)

| Code | Ví | Gói ứng dụng |
|------|----|-------------|
| MOMO | Momo | `com.mservice.momotransfer`, `vn.momo` |
| ZLP | ZaloPay | `com.zalopay`, `vn.zalopay` |
| SPAY | ShopeePay | `com.shopee`, `com.shopee.sg` |
| VNPAY | VNPay | `com.vnpay`, `vn.vnpay` |

> 🔄 Bạn có thể tự thêm config trong `BankParserRegistry.kt`.

---

## ⚙️ Kỹ thuật

### Stack

| Thành phần | Công nghệ |
|-----------|-----------|
| Ngôn ngữ | Kotlin, Java |
| Build | Gradle KTS, AGP 8.5.2, Kotlin 2.0.0 |
| Database | Room, SQLite |
| API Server | Ktor 3.0 (CIO engine) — coroutine-native |
| DI | Dagger Hilt |
| UI | Material 3, ViewBinding |
| Parse | Regex config-driven (BaseBankParser) |
| Webhook | HTTP + HMAC-SHA256 |
| License | RSA-2048 |
| Storage | EncryptedSharedPreferences (AES-256 GCM) |
| Paging | Paging 3 (Room PagingSource) |
| Biometric | AndroidX Biometric (vân tay / Face Unlock) |
| Minify | R8 full mode (ProGuard) |
| Finance (V2) | Category engine, Account balance, Cash flow reports |

### Bảo mật
- API server chỉ bind **127.0.0.1** (localhost), không expose ra ngoài
- FileProvider chỉ cho phép truy cập thư mục `updates/`
- Webhook HTTP bị từ chối khi có secret (chỉ HTTPS)
- OTA update bắt buộc HTTPS
- APK signature verification (chống APK giả)
- EncryptedSharedPreferences cho secret keys
- ProGuard loại bỏ log/debug code
- `allowBackup=false` — chặn ADB backup lộ dữ liệu

---

## 📦 Build & Release

```bash
# Build debug APK
./gradlew assembleDebug

# Build signed release APK (cần keystore)
KEYSTORE_PATH=/path/to/keystore.jks \
KEYSTORE_PASSWORD=xxx \
KEY_ALIAS=banknotify \
KEY_PASSWORD=xxx \
./gradlew assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/`
Release APK: `app/build/outputs/apk/release/`

---

## 🧪 Testing

```bash
./gradlew testDebugUnitTest
```

51 unit tests (parser + database + API routes + license + biometric + export).

---

## 🛠️ Cấu trúc mã nguồn

### V1 — Nhánh `main`
```
app/src/main/java/com/banknotify/
├── BankNotifyApp.kt                  # Application (@HiltAndroidApp)
├── BootReceiver.kt                   # Auto-start sau khi boot
├── core/
│   ├── BankNotifyApp.kt              # Hằng số, instance
│   ├── SecurePrefs.kt                # EncryptedSharedPreferences
│   ├── CrashReporter.kt              # Bắt crash + ghi log
│   ├── db/
│   │   ├── AppDatabase.kt            # Room database
│   │   ├── DatabaseHelper.kt         # Helper class
│   │   └── TransactionDao.kt         # DAO interface
│   ├── model/
│   │   └── Transaction.kt            # Transaction + TransactionFilter + MonthlyStat
│   ├── license/
│   │   └── LicenseManager.kt         # RSA-2048 license verification
│   ├── biometric/
│   │   └── BiometricLock.kt          # Vân tay / Face Unlock
│   └── export/
│       └── DataExporter.kt           # Export JSON/CSV → share
├── parser/
│   ├── BankParser.kt                 # Interface
│   ├── BankParserConfig.kt           # Config bank
│   ├── BaseBankParser.kt             # Parser duy nhất (config-driven)
│   └── BankParserRegistry.kt         # Registry 14 ngân hàng + 4 ví điện tử
├── service/
│   ├── listener/
│   │   └── BankNotificationListener.kt   # NotificationListenerService
│   └── server/
│       ├── ApiRoutes.kt              # REST API routes
│       ├── KtorApiServer.kt          # Ktor server object
│       └── ApiServerService.kt       # Foreground service
│   └── webhook/
│       └── WebhookManager.kt         # Webhook + HMAC
├── ui/
│   ├── MainActivity.kt               # Danh sách giao dịch
│   ├── DashboardActivity.kt          # Dashboard + biểu đồ
│   ├── WebhookSettingsActivity.kt    # Cấu hình webhook
│   ├── OnboardingActivity.kt         # Hướng dẫn cấp quyền
│   ├── view/
│   │   └── SimpleBarChart.kt         # Biểu đồ cột custom
│   └── adapter/
│       └── TransactionAdapter.kt     # RecyclerView adapter (PagingData)
└── update/
    ├── UpdateInfo.kt                 # Model OTA update
    ├── UpdateManager.kt              # Check + download + install
    └── ApkVerifier.kt               # Xác thực chữ ký APK
```

### V2 bổ sung — Nhánh `v2`
```
core/
├── CategoryEngine.kt                 # Auto-categorize 18 danh mục
├── model/
│   └── Transaction.kt                # + accountType, category, tags, note
│                                      # + Account entity, CategorySummary,
│                                      #   CashFlow, AccountBalance
├── db/
│   ├── AppDatabase.kt                # + Account entity, v2 migration
│   ├── TransactionDao.kt             # + AccountDao, category/cash flow queries
│   └── DatabaseHelper.kt             # + Account/financial report methods
├── parser/
│   ├── BankParserConfig.kt           # + accountType field
│   └── BankParserRegistry.kt         # + Momo, ZaloPay, ShopeePay, VNPay
ui/
├── DashboardActivity.kt              # V2: net worth, income/expense, category breakdown
└── res/layout/item_category_row.xml  # Category bar row
```

---

## 🌿 Nhánh phát triển

| Nhánh | Trạng thái | Mô tả |
|-------|-----------|-------|
| `main` | ✅ Ổn định | V1 — giám sát ngân hàng, API, webhook |
| `v2` | 🚧 Phát triển | V2 — quản trị tài chính cá nhân, danh mục, ví điện tử |

Sử dụng nhánh `v2` để build bản V2:
```bash
git checkout v2
./gradlew assembleDebug
```

---

## 📄 Giấy phép

**MIT License** — bạn có thể sử dụng, sửa đổi, phân phối lại, kể cả thương mại.

Tuy nhiên **license key** (RSA-2048) là tính năng thương mại riêng biệt, không thuộc phạm vi MIT.
