# BankNotify

Ứng dụng Android giám sát thông báo ngân hàng, tự động parse giao dịch và expose REST API + Webhook.

## Tính năng

- **Giám sát thông báo** qua `NotificationListenerService` — tự động phát hiện giao dịch từ ứng dụng ngân hàng
- **Parse giao dịch** — rút trích số tiền, số tài khoản, người gửi, nội dung, số dư, mã giao dịch
- **Lưu trữ local** SQLite — tra cứu, lọc, thống kê
- **REST API** (NanoHTTPD) — tích hợp với hệ thống bên ngoài (giống Sepay)
- **Webhook** — gửi HTTP POST khi có giao dịch mới
- **OTA Update** — cập nhật APK từ xa
- **Auto-start** — khởi động cùng máy

## Hỗ trợ ngân hàng

| Code | Ngân hàng | Package |
|------|-----------|---------|
| VCB | Vietcombank | `com.vietcombank`, `com.vietcombank.vcb`, `vcb.app.com.vcb` |
| TCB | Techcombank | `com.techcombank`, `com.techcombank.techcombankapp`, `vn.techcombank` |
| MB | MB Bank | `com.msb.android`, `com.mbbank`, `com.mb.mbbank` |
| ACB | ACB | `com.acb.acb`, `vn.acb`, `com.acb.app` |
| VPB | VPBank | `com.vpbank`, `vn.vpbank`, `com.vpbank.app` |
| TPB | TPBank | `com.tpbank`, `vn.tpbank`, `com.tpbank.app` |
| VIB | VIB | `com.vib`, `vn.vib`, `com.vib.app` |
| BIDV | BIDV | `com.bidv`, `vn.bidv`, `com.bidv.app` |
| CTG | VietinBank | `com.vietinbank`, `vn.vietinbank`, `com.vietinbank.app` |
| STB | Sacombank | `com.sacombank`, `vn.sacombank`, `com.sacombank.app` |
| HDB | HDBank | `com.hdbank`, `vn.hdbank`, `com.hdbank.app` |
| OCB | OCB | `com.ocb`, `vn.ocb`, `com.ocb.app`, `com.ocb.ocbapp` |
| MSB | MSB | `com.msb`, `vn.msb`, `com.msb.app` |
| SHB | SHB | `com.shb`, `vn.shb`, `com.shb.app` |

## Yêu cầu

- Android 7.0+ (API 24)
- Quyền **Notification Access** — vào Settings → Notification Access → bật BankNotify
- Quyền **Install Unknown Apps** (nếu dùng OTA update)

## Cài đặt

1. Build APK: `./gradlew assembleRelease` (hoặc download release APK)
2. Cài đặt APK trên thiết bị
3. Mở app → cấp quyền Notification Listener
4. API server tự động chạy ở `http://<device-ip>:8765`

## REST API

Mặc định chạy tại `http://<device-ip>:8765`, hỗ trợ cả `/api/v1` và `/api`.

### Endpoints

| Method | Path | Mô tả |
|--------|------|-------|
| `GET` | `/health` | Health check |
| `GET` | `/transactions` | Danh sách giao dịch (có filter) |
| `GET` | `/transactions/recent` | Giao dịch gần đây |
| `GET` | `/transactions/unread` | Số giao dịch chưa đọc |
| `GET` | `/transactions/stats` | Thống kê (tổng số, tổng tiền) |
| `GET` | `/transactions/:id` | Chi tiết giao dịch |
| `POST` | `/transactions/:id/confirm` | Xác nhận giao dịch |
| `DELETE` | `/transactions/:id` | Xoá giao dịch |
| `GET` | `/webhook` | Xem cấu hình webhook |
| `POST` | `/webhook` | Cập nhật webhook |
| `POST` | `/webhook/test` | Test webhook |
| `GET` | `/config` | Xem cấu hình server |
| `POST` | `/config` | Cập nhật cấu hình |
| `GET` | `/update/check` | Kiểm tra OTA update |
| `GET` | `/update/info` | Thông tin update |
| `GET` | `/update/url` | Xem URL cập nhật |
| `POST` | `/update/url` | Đặt URL cập nhật |

### Query params cho `/transactions`

| Param | Kiểu | Mô tả |
|-------|------|-------|
| `bank_code` | string | Lọc theo ngân hàng (VD: `VCB`) |
| `status` | string | `PENDING`, `CONFIRMED`, `EXPIRED`, `FAILED` |
| `from_date` | long | Timestamp từ |
| `to_date` | long | Timestamp đến |
| `min_amount` | double | Số tiền tối thiểu |
| `max_amount` | double | Số tiền tối đa |
| `search` | string | Tìm kiếm nội dung |
| `limit` | int | Số lượng (1-200, mặc định 50) |
| `offset` | int | Phân trang |

### Xác thực

API hỗ trợ auth bằng API key (cấu hình qua `POST /config`):
- Header: `X-API-Key: your-key` hoặc `Authorization: Bearer your-key`

## Webhook

Khi có giao dịch mới, webhook sẽ nhận HTTP POST với JSON:

```json
{
  "bank_code": "VCB",
  "bank_name": "Vietcombank",
  "account_number": "1012345678",
  "amount": 500000,
  "balance": 5000000,
  "content": "Chuyen tien",
  "sender_name": "Nguyen Van A",
  "reference_number": "REF123456",
  "transaction_date": 1712345678000,
  "status": "PENDING"
}
```

## Build

```bash
git clone https://github.com/ecosyntech68vn/BankNotify-App.git
cd BankNotify-App
./gradlew assembleDebug
```

APK sẽ ở `app/build/outputs/apk/debug/`.

## Cấu trúc thư mục

```
app/src/main/java/com/banknotify/
├── BootReceiver.kt              # Auto-start sau boot
├── core/
│   ├── BankNotifyApp.kt         # Application class
│   ├── db/DatabaseHelper.kt     # SQLite
│   └── model/Transaction.kt     # Data models
├── parser/
│   ├── BankParser.kt            # Interface
│   ├── BankParserRegistry.kt    # Registry & routing
│   ├── VietcombankParser.kt     # Parser cho từng bank
│   └── ...                      # 14 parsers
├── service/
│   ├── listener/BankNotificationListener.kt
│   ├── server/
│   │   ├── ApiServer.kt         # NanoHTTPD REST API
│   │   └── ApiServerService.kt  # Foreground service
│   └── webhook/WebhookManager.kt
├── ui/
│   ├── MainActivity.kt
│   ├── WebhookSettingsActivity.kt
│   └── adapter/TransactionAdapter.kt
└── update/
    ├── UpdateInfo.kt
    └── UpdateManager.kt
```

## License

MIT
