# Design — Voucher & Shipper Commission

**Date:** 2026-06-03
**Author:** Le Thi Tran Thuy (thuyltt@uitgis.vn)
**Status:** Draft awaiting user review
**Spawns plans:** Phase 14 (Voucher), Phase 15 (Shipper Commission & Earnings)

---

## 1. Goal

Bổ sung hai nhóm tính năng kinh doanh quan trọng vào hệ thống Telegram Mini App giao hàng:

1. **Voucher / Khuyến mãi** — chủ shop tạo mã giảm giá, khách áp mã ở Checkout để giảm phí ship hoặc giảm tiền hàng.
2. **Hoa hồng & thu nhập shipper** — khi đơn hoàn tất, hệ thống tự tính hoa hồng cho shipper, theo dõi số dư đối soát (ledger), và cho phép admin/shipper xem thống kê thu nhập theo ngày/tuần/tháng.

Cả hai tính năng được thiết kế **song song độc lập** nhưng có một điểm giao: hoa hồng shipper tính trên **phí ship gốc** (trước khi voucher SHIPPING giảm), nên Phase 14 (Voucher) phải merge trước Phase 15 để snapshot `delivery_fee_original` được lưu đúng vào đơn.

## 2. Non-goals

- Không xây dựng hệ thống loyalty/điểm thưởng tích luỹ cho khách (đẩy về future work).
- Không tự động phát voucher (auto-apply theo điều kiện) — chỉ code-based redemption.
- Không xây cổng thanh toán riêng để shop trả lương shipper — chỉ ghi nhận `SETTLEMENT_PAYOUT` entry, việc chuyển khoản thật làm thủ công.
- Không hỗ trợ stacking nhiều hơn 1 SHIPPING + 1 PRODUCTS voucher / đơn.
- Không hỗ trợ voucher giới hạn theo sản phẩm/danh mục cụ thể (chỉ áp toàn bộ subtotal hoặc delivery_fee).
- Không tính commission theo công thức phức tạp (chỉ % phí ship gốc, lấy từ `shop_config`).

## 3. Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│  Phase 14 — Voucher (Sub-project A)                         │
│    V14 migration: voucher + voucher_redemption tables       │
│    + orders.discount_products + orders.discount_shipping    │
│    + orders.delivery_fee_original (snapshot)                │
│    Backend: module promotion (mới)                          │
│    Mini App: 2 ô voucher ở Checkout                         │
│    Web Admin: 3 trang mới (Vouchers / Form / Detail)        │
└─────────────────────────────────────────────────────────────┘
                          ↓ (Voucher đã merge)
┌─────────────────────────────────────────────────────────────┐
│  Phase 15 — Shipper Commission & Earnings (Sub-project B)   │
│    V15 migration: shop_config.shipper_commission_pct +      │
│      orders.shipper_commission + shipper_ledger table       │
│    Backend: listener OrderDeliveredEvent → ledger entries   │
│    Mini App shipper: 4 trang mới + redesign 2 trang cũ      │
│      (bottom tab nav: Earnings / Đơn / Ví / Profile)        │
│    Web Admin: ShipperEarnings + ShipperDetail (mới)         │
│      + cập nhật ShippersPage + OrderDetailPage              │
│    Settings: thêm field "Tỉ lệ hoa hồng (%)"                │
└─────────────────────────────────────────────────────────────┘
```

Mỗi phase commit độc lập, có thể merge/demo từng phần. Ước lượng 10–14 ngày làm việc cho cả 2 phase.

## 4. Design decisions (đã chốt với user)

| # | Quyết định | Lựa chọn | Lý do |
|---|---|---|---|
| Q1 | Phase order | A trước, B sau | Voucher snapshot `delivery_fee_original` phải có trước khi B tính commission |
| Q2 | Công thức hoa hồng | % của phí ship gốc | Đơn giản, công bằng với shipper, dễ giải thích trong báo cáo |
| Q3 | Nơi cấu hình tỉ lệ | Trong `shop_config` (1 field áp cho mọi shipper) | Khoá luận dùng demo gọn; không cần per-shipper override |
| Q4 | Settlement model | Ledger entries (append-only) | Audit trail đầy đủ; demo "shipper xem lịch sử thu nhập từng đơn" |
| Q5 | Mini App shipper UI | Full — 4 trang mới + redesign 2 trang cũ | App giao hàng cần UI shipper chuyên nghiệp |
| Q6 | Web Admin shipper UI | Full — 2 trang mới + 2 trang update | Cân với UI shipper |
| Q7 | Kiểu giảm giá | FIXED hoặc PERCENT+cap (radio) | Sát thực tế VN |
| Q8 | Cách áp voucher | Code-based | Đơn giản, demo "wow" |
| Q9 | Constraints | Full: dates + min_order + max_uses (total + per_customer) | Đủ chống lạm dụng |
| Q10 | Stacking | Max 1 SHIPPING + 1 PRODUCTS / đơn | Sát Shopee/Be |
| Q11 | Admin voucher UI | Full pages (list + form + detail) | Cân với độ chuyên nghiệp shipper UI |
| Q12 | Voucher × commission | Commission tính trên phí ship GỐC | Shop ăn voucher, không "phạt oan" shipper |
| Q13 | Checkout UI | 2 ô input rõ ràng (SHIPPING + PRODUCTS) | Minh bạch nhất với khách |

## 5. Database schema

### 5.1. V14 — `voucher.sql` (Phase 14)

```sql
-- Bảng voucher: định nghĩa khuyến mãi
CREATE TABLE voucher (
    id                     BIGSERIAL PRIMARY KEY,
    code                   VARCHAR(32) NOT NULL UNIQUE,
    name                   VARCHAR(128) NOT NULL,
    target                 VARCHAR(16) NOT NULL
        CHECK (target IN ('SHIPPING', 'PRODUCTS')),
    discount_type          VARCHAR(16) NOT NULL
        CHECK (discount_type IN ('FIXED', 'PERCENT')),
    discount_value         NUMERIC(12, 2) NOT NULL CHECK (discount_value > 0),
    max_discount           NUMERIC(12, 2)
        CHECK (max_discount IS NULL OR max_discount > 0),
    min_order_amount       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    valid_from             TIMESTAMPTZ NOT NULL,
    valid_until            TIMESTAMPTZ NOT NULL,
    max_uses_total         INT,
    max_uses_per_customer  INT NOT NULL DEFAULT 1,
    used_count             INT NOT NULL DEFAULT 0,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_until > valid_from),
    CHECK (discount_type = 'PERCENT' OR max_discount IS NULL)
);
CREATE INDEX idx_voucher_code_active ON voucher(code) WHERE is_active = TRUE;
CREATE INDEX idx_voucher_validity     ON voucher(valid_from, valid_until);

CREATE TABLE voucher_redemption (
    id                BIGSERIAL PRIMARY KEY,
    voucher_id        BIGINT NOT NULL REFERENCES voucher(id),
    order_id          UUID NOT NULL REFERENCES orders(id),
    customer_id       BIGINT NOT NULL REFERENCES telegram_user(id),
    discount_applied  NUMERIC(12, 2) NOT NULL CHECK (discount_applied >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (voucher_id, order_id)
);
CREATE INDEX idx_redemption_customer ON voucher_redemption(customer_id, voucher_id);

-- Cập nhật orders: 2 cột giảm giá + snapshot phí ship gốc
ALTER TABLE orders
    ADD COLUMN discount_products      NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (discount_products >= 0),
    ADD COLUMN discount_shipping      NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (discount_shipping >= 0),
    ADD COLUMN delivery_fee_original  NUMERIC(12, 2);

-- Backfill: với đơn hiện có, delivery_fee_original = delivery_fee
UPDATE orders SET delivery_fee_original = delivery_fee WHERE delivery_fee_original IS NULL;

ALTER TABLE orders
    ALTER COLUMN delivery_fee_original SET NOT NULL,
    ADD CONSTRAINT chk_delivery_fee_original_nonneg
        CHECK (delivery_fee_original >= 0);
```

### 5.2. V15 — `shipper_ledger.sql` (Phase 15)

```sql
ALTER TABLE shop_config
    ADD COLUMN shipper_commission_pct NUMERIC(5, 2) NOT NULL DEFAULT 80.00
        CHECK (shipper_commission_pct >= 0 AND shipper_commission_pct <= 100);

ALTER TABLE orders
    ADD COLUMN shipper_commission  NUMERIC(12, 2)
        CHECK (shipper_commission IS NULL OR shipper_commission >= 0);

CREATE TABLE shipper_ledger (
    id            BIGSERIAL PRIMARY KEY,
    shipper_id    BIGINT NOT NULL REFERENCES shipper_profile(id),
    entry_type    VARCHAR(32) NOT NULL CHECK (entry_type IN (
        'COMMISSION',
        'COD_OWED',
        'SETTLEMENT_PAYOUT',
        'SETTLEMENT_DEPOSIT'
    )),
    amount        NUMERIC(12, 2) NOT NULL,
    order_id      UUID REFERENCES orders(id),
    note          TEXT,
    created_by    VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ledger_shipper_date ON shipper_ledger(shipper_id, created_at DESC);
CREATE INDEX idx_ledger_order        ON shipper_ledger(order_id) WHERE order_id IS NOT NULL;
```

### 5.3. Ví dụ ledger entries (đơn COD 300k, phí ship gốc 30k, commission 80%)

| # | entry_type | amount | order_id | note | balance |
|---|---|---|---|---|---|
| 1 | COMMISSION | +24 000 | ord-abc | "Hoa hồng đơn DEMO-2026-0042" | +24 000 |
| 2 | COD_OWED | −300 000 | ord-abc | "Shipper đã thu COD" | −276 000 |
| 3 | SETTLEMENT_DEPOSIT | +276 000 | (null) | "Shipper nộp tiền 2026-06-10" | 0 |

## 6. Backend

### 6.1. Module mới — `promotion` (Phase 14)

```
backend/modules/promotion/
├── domain/
│   ├── Voucher.java
│   ├── VoucherRedemption.java
│   ├── VoucherTarget.java         (enum: SHIPPING, PRODUCTS)
│   ├── DiscountType.java          (enum: FIXED, PERCENT)
│   └── VoucherValidationResult.java
├── repo/
│   ├── VoucherRepository.java
│   └── VoucherRedemptionRepository.java
├── service/
│   ├── VoucherService.java
│   └── VoucherCalculator.java
└── api/
    ├── CustomerVoucherController.java
    └── AdminVoucherController.java
```

### 6.2. Module update — `delivery` (Phase 15)

```
backend/modules/delivery/
├── domain/
│   ├── ShipperLedgerEntry.java       (NEW)
│   └── LedgerEntryType.java          (NEW)
├── repo/
│   └── ShipperLedgerRepository.java  (NEW)
├── service/
│   ├── CommissionCalculator.java     (NEW)
│   ├── ShipperLedgerService.java     (NEW)
│   └── OrderCompletionListener.java  (NEW)
└── api/
    ├── ShipperEarningsController.java     (NEW)
    └── AdminShipperLedgerController.java  (NEW)
```

### 6.3. Endpoints — Phase 14

| Method | Path | Auth | Body / Query | Response |
|---|---|---|---|---|
| POST | `/api/customer/vouchers/validate` | Telegram | `{code, subtotal, deliveryFee}` | `{voucher, discountAmount}` / `400 INVALID_VOUCHER` |
| POST | `/api/customer/orders` (cập nhật) | Telegram | thêm `voucherCodes: {products?, shipping?}` | `OrderResponse` đã có discount |
| GET | `/api/admin/vouchers` | JWT | `?status=&target=&page=` | `Page<VoucherSummary>` |
| GET | `/api/admin/vouchers/{id}` | JWT | — | `VoucherDetail` + `recentRedemptions` |
| POST | `/api/admin/vouchers` | JWT | `CreateVoucherRequest` | `Voucher` |
| PUT | `/api/admin/vouchers/{id}` | JWT | `UpdateVoucherRequest` | `Voucher` |
| DELETE | `/api/admin/vouchers/{id}` | JWT | — | soft delete, `204` |

### 6.4. Endpoints — Phase 15

| Method | Path | Auth | Body / Query | Response |
|---|---|---|---|---|
| GET | `/api/shipper/me/earnings/summary` | Telegram | `?date=YYYY-MM-DD` | `{today, week, month, balance}` |
| GET | `/api/shipper/me/earnings/daily` | Telegram | `?from=&to=` | `Array<{date, ordersCount, commission}>` |
| GET | `/api/shipper/me/ledger` | Telegram | `?from=&to=&page=` | `Page<LedgerEntry>` |
| GET | `/api/shipper/me/profile` | Telegram | — | `{name, phone, ratingAvg, totalOrders, joinedAt}` |
| GET | `/api/admin/shippers/{id}/ledger` | JWT | `?from=&to=&page=` | `Page<LedgerEntry>` |
| GET | `/api/admin/shippers/{id}/balance` | JWT | — | `{balance, lastSettledAt}` |
| POST | `/api/admin/shippers/{id}/settle` | JWT | `{type: 'PAYOUT'\|'DEPOSIT', amount, note}` | `LedgerEntry` |
| GET | `/api/admin/reports/shipper-earnings` | JWT | `?from=&to=&groupBy=shipper\|day` | `Array<EarningsBucket>` |

### 6.5. Voucher validation rules (theo thứ tự, fail-fast)

1. `code` tồn tại + `is_active=true`
2. `valid_from ≤ NOW() ≤ valid_until`
3. `subtotal ≥ min_order_amount`
4. `max_uses_total` chưa đạt (nếu set)
5. `max_uses_per_customer` chưa đạt (đếm trong `voucher_redemption`)
6. Tính `discountAmount`:
   - FIXED: `MIN(discount_value, base)`
   - PERCENT: `MIN(base × discount_value / 100, max_discount ?? Infinity)`

Redeem dùng `@Transactional` + `SELECT … FOR UPDATE` trên row voucher để tránh race condition khi 2 đơn cùng tiêu lần dùng cuối.

### 6.6. Event flow khi đơn DELIVERED (Phase 15)

```
OrderService.markDelivered(orderId)
   ├── Update orders.status = 'DELIVERED'
   ├── Update orders.delivered_at = NOW()
   ├── Publish OrderDeliveredEvent(orderId, shipperId, deliveryFeeOriginal,
   │                               total, paymentMethod)
   └── COMMIT
        ↓ @TransactionalEventListener(AFTER_COMMIT)
OrderCompletionListener.onDelivered(event)
   ├── commission = deliveryFeeOriginal × shop_config.shipper_commission_pct / 100
   │   (dùng GỐC, KHÔNG trừ discount_shipping)
   ├── Update orders.shipper_commission = commission
   ├── INSERT shipper_ledger (COMMISSION, +commission, order_id)
   └── if paymentMethod == 'COD':
          INSERT shipper_ledger (COD_OWED, -order.total, order_id)
```

## 7. Frontend

### 7.1. Mini App customer — Phase 14

**Update `CheckoutPage`** — thêm section "Khuyến mãi" với 2 input rõ ràng + chip hiển thị voucher đã áp + breakdown row giảm giá.

Component mới:
- `VoucherInput.tsx` — input + button + chip + error message
- `useVoucherValidation` hook — gọi `/api/customer/vouchers/validate` với debounce

### 7.2. Mini App shipper — Phase 15

**Bottom Tab Navigator** (`ShipperLayout.tsx`): Earnings / Đơn / Ví / Profile.

Trang mới:
1. **`EarningsPage` (`/shipper/earnings`)** — hero card "Hôm nay bạn kiếm được X đ", 4 KPI tiles, biểu đồ cột 7 ngày, danh sách 5 đơn mới nhất.
2. **`EarningsDetailPage` (`/shipper/earnings/:date`)** — date picker, tổng + từng đơn của ngày.
3. **`WalletPage` (`/shipper/wallet`)** — số dư, filter chip ledger, danh sách entries.
4. **`ShipperProfilePage` (`/shipper/profile`)** — info, rating, KPI tổng.

Redesign:
- `ShipperAssignmentsPage` — card với status tag + badge "+X đ dự kiến".
- `ShipperAssignmentDetailPage` — box hoa hồng dự kiến / đã ghi nhận.

### 7.3. Web Admin

**Phase 14 — 3 trang mới:**
- `VouchersPage` (list + filter)
- `VoucherFormPage` (form + preview)
- `VoucherDetailPage` (info + biểu đồ + redemptions)

**Phase 15 — 2 trang mới + 3 update:**
- `ShipperEarningsPage` (reports-style với charts)
- `ShipperDetailPage` (info + ledger + settle modal)
- Update `ShippersPage` (cột "Số dư" + "Thu nhập 7 ngày")
- Update `OrderDetailPage` (section "Hoa hồng & thanh toán")
- Update `SettingsPage` (field "Tỉ lệ hoa hồng (%)")

## 8. Testing strategy

**Phase 14 — 21 test mới**:

| Loại | Count | Nội dung |
|---|---|---|
| Unit | 8 | `VoucherCalculator`: FIXED + PERCENT + cap + min_order; edge cases |
| Integration (Testcontainers) | 7 | Race condition, max_uses enforcement, stacking, reject duplicate target |
| Controller slice | 4 | validate 400 cases, admin CRUD permissions |
| Frontend (Vitest) | 2 | `VoucherInput` component |

**Phase 15 — 24 test mới**:

| Loại | Count | Nội dung |
|---|---|---|
| Unit | 6 | `CommissionCalculator`: dùng phí ship gốc; rounding HALF_UP |
| Integration | 9 | AFTER_COMMIT, idempotent, COD vs VNPay, settle endpoint |
| Controller slice | 5 | `/api/shipper/me/*` chỉ trả data chính chủ; admin yêu cầu JWT |
| Frontend (Vitest) | 4 | `EarningsPage`, `WalletPage` |

Tổng số test toàn dự án sau khi merge: **274 → 319**.

## 9. Risks & mitigations

| Rủi ro | Phase | Mitigation |
|---|---|---|
| Race condition khi 2 đơn redeem cùng voucher "lần cuối" | 14 | `SELECT … FOR UPDATE` trên row voucher; integration test cover bằng 2 thread |
| Commission tính sai khi đơn có voucher SHIPPING | 15 | Cột `orders.delivery_fee_original` snapshot ở Phase 14; commission luôn đọc cột này |
| Ledger entry không tạo khi event publish fail | 15 | `@TransactionalEventListener(AFTER_COMMIT)`; warning log khi commission ngoài [0,total] |
| FE Mini App bottom tab nav che footer trên trình duyệt thấp | 15 | `safe-area-inset-bottom` + test iOS WebKit |
| Admin quên đặt `shipper_commission_pct` → default 80% bất ngờ | 15 | V15 default 80% (giá trị an toàn); Settings có disclaimer |
| Báo cáo khoá luận đã in xong | both | Viết chương 6 bổ sung `chuong-6-mo-rong.md` — "Hướng phát triển đã hiện thực sau bảo vệ" |

## 10. Documentation updates

| File | Update |
|---|---|
| `chuong-4-cai-dat.md` | 4.4.2/4.4.3 thêm trang mới; 4.5 thêm V14+V15; 4.3 thêm module `promotion` |
| `chuong-5-ket-qua-va-ket-luan.md` | Bảng 5.2: 15 migrations, ~28 trang FE, ~319 test, ~22 ảnh chụp |
| `06-danh-muc-hinh.md` | Thêm Hình 4.6 → 4.13 |
| `screenshots/` | Capture: voucher form/detail, earnings, wallet, profile, shipper detail admin, assignments redesigned |
| `RUNBOOK.md` | Bước demo voucher + demo earnings |
| `diem-noi-bat-va-huong-phat-trien.md` | Bỏ Voucher + Shipper earnings khỏi "hướng phát triển" |
| `chuong-6-mo-rong.md` (NEW) | Mô tả 2 tính năng đã thêm sau khi báo cáo bản in xong |

## 11. GSD workflow per phase

Mỗi phase tuân theo quy trình GSD đã thiết lập (9 phase trước):
1. `gsd-discuss-phase` — sàng lọc assumption
2. `gsd-research-phase` — RESEARCH.md
3. `gsd-plan-phase` — PLAN.md + plan-check
4. `gsd-execute-phase` — atomic commits theo wave
5. `gsd-code-review` — REVIEW.md với severity classification
6. `gsd-code-review-fix` — auto-fix critical/important
7. `gsd-verify-work` — UAT thủ công

## 12. Next steps

1. User review spec này.
2. Sau khi user approve, gọi `writing-plans` skill để biến design này thành implementation plan chi tiết cho **Phase 14** trước (Phase 15 sẽ plan riêng sau khi Phase 14 merge).
