# @shop/zaloapp — Zalo Mini App (preview / scaffold)

Triển khai song song với Telegram Mini App (`@shop/miniapp`). Reuse ~80% UI code,
chỉ thay layer auth + SDK + brand color.

## Trạng thái

### Chạy được local (đã làm)
- [x] Package structure đầy đủ (Vite + React 18 + Tailwind + Vitest)
- [x] Reuse types + API helpers từ `@shop/shared` (workspace dep)
- [x] 6 page chính: Splash, Catalog, Cart, Checkout, Orders, OrderDetail
- [x] Mock Zalo SDK (`window.ZaloJavaScriptInterface` check) trong `src/lib/zalo.ts`
- [x] Dev-bypass header `X-Dev-User-Id` (cùng cơ chế với Telegram miniapp)
- [x] Backend `ZaloAuthFilter` (stub) — chấp nhận header `X-Zalo-Access-Token`
- [x] Brand color `zalo` (#0068FF) thay cho orange của miniapp
- [x] Smoke tests cho SDK wrapper + cart store
- [x] `pnpm --filter @shop/zaloapp dev` chạy ở port `5182` (miniapp dùng 5173, webadmin 5181)

### Cần Zalo Developer Account (chưa làm — out of thesis scope)
- [ ] Đăng ký Zalo Official Account tại https://oa.zalo.me/ (mất ~1 tuần phê duyệt)
- [ ] Tạo Zalo Mini App tại https://mini.zalo.me/devtools/
- [ ] Lấy `ZaloAppId` + `AppSecret`
- [ ] Cài SDK thật: `pnpm --filter @shop/zaloapp add zmp-sdk zmp-ui`
- [ ] Replace mock trong `src/lib/zalo.ts` bằng real `zmp-sdk` calls
- [ ] Backend `ZaloAuthFilter` call Zalo OpenAPI `/v2/me` thật để verify token
- [ ] Build qua `zmp-cli` (`pnpm zmp build`) sinh `.zmp` package
- [ ] Upload `.zmp` lên Zalo Mini App Studio để publish
- [ ] Test trên Zalo Mini App Studio Simulator + thiết bị thật
- [ ] Replace placeholder live-tracking block trong `OrderDetailPage` bằng Zalo location SDK
      (miniapp version dùng react-leaflet + WebSocket — có thể port lại với zmp-sdk `getLocation`)

## Tại sao

- Zalo có ~75M MAU tại Việt Nam — base lớn hơn Telegram ở thị trường nội địa
- Reuse ~80% UI code giữa hai platform — chỉ thay auth + SDK + brand color
- Mục tiêu: một backend Spring Boot phục vụ cả Telegram và Zalo qua filter chain riêng

## Architecture

```
                   ┌─ Telegram Mini App (miniapp/) ── auth: X-Telegram-Init-Data
Khách hàng VN ─────┤
                   └─ Zalo Mini App     (zaloapp/) ── auth: X-Zalo-Access-Token

Backend Spring Boot:
  TelegramAuthFilter ─┐
                      ├─→ set request attribute "currentUser"
  ZaloAuthFilter ─────┘    (cùng key, cùng entity TelegramUser cho V1)

Controller dùng @CurrentUser TelegramUser không thay đổi.
Filter chain order: JwtAuthFilter → TelegramAuthFilter → ZaloAuthFilter
```

V1 lưu cả Zalo user trong bảng `telegram_user` (tái dùng entity) với
prefix ID > 0 cho Telegram, prefix khác cho Zalo. V2 sẽ split bảng riêng
nếu cần phân biệt rõ.

## Dev

```bash
pnpm install
pnpm --filter @shop/zaloapp dev         # http://localhost:5182
pnpm --filter @shop/zaloapp build       # static SPA build (chưa qua zmp-cli)
pnpm --filter @shop/zaloapp test        # vitest run
pnpm --filter @shop/zaloapp type-check  # tsc --noEmit
```

Ngoài Zalo (browser thường) ứng dụng dùng dev-bypass header
`X-Dev-User-Id: 9000000001` → backend trả về demo customer `demo_cust_thuy`
từ V11 seed data (giống Telegram miniapp).

## Production checklist

(Khi đã có Zalo Developer Account)

1. `pnpm --filter @shop/zaloapp add zmp-sdk zmp-ui`
2. Replace mock trong `src/lib/zalo.ts` bằng `import { getAccessToken, openWebview } from 'zmp-sdk'`
3. Set env `VITE_ZALO_APP_ID` trong `.env`
4. Backend: set `ZALO_APP_ID` + `ZALO_APP_SECRET` env, enable real verify trong `ZaloAuthFilter`
5. Test trên Zalo Mini App Studio Simulator
6. `pnpm zmp build` → sinh `.zmp` package
7. Upload to https://mini.zalo.me/devtools/
8. Submit for review

## Sơ đồ thư mục

```
zaloapp/
├── package.json                         (name: @shop/zaloapp, port 5182)
├── vite.config.ts                       (alias @ → ./src)
├── tsconfig.json
├── tailwind.config.js                   (brand color "zalo": #0068FF)
├── index.html
├── src/
│   ├── main.tsx                         (React 18 root)
│   ├── App.tsx                          (Router + Providers)
│   ├── lib/
│   │   ├── api.ts                       (uses @shop/shared createApiClient)
│   │   ├── auth.ts                      (X-Zalo-Access-Token + dev bypass)
│   │   ├── zalo.ts                      (MOCK SDK wrapper — replace later)
│   │   └── zalo.test.ts                 (smoke tests cho wrapper)
│   ├── providers/
│   │   ├── QueryProvider.tsx            (TanStack Query)
│   │   └── ZaloProvider.tsx             (mirror TelegramProvider)
│   ├── components/                      (copy from miniapp — Zalo color tweaks)
│   │   ├── ErrorBoundary.tsx
│   │   ├── Layout.tsx
│   │   ├── OrderStatusBadge.tsx
│   │   ├── ProductCard.tsx
│   │   └── Toast.tsx
│   ├── features/
│   │   ├── cart/
│   │   │   ├── cart-store.ts            (Zustand + persist, key "shop-cart-zalo")
│   │   │   ├── cart-store.test.ts
│   │   │   └── use-cart.ts
│   │   └── payment/use-pay-with-vnpay.ts
│   ├── pages/
│   │   ├── SplashPage.tsx               (Zalo blue brand)
│   │   ├── CatalogPage.tsx
│   │   ├── CartPage.tsx
│   │   ├── CheckoutPage.tsx
│   │   ├── OrdersPage.tsx
│   │   ├── OrderDetailPage.tsx          (live-tracking placeholder — needs zmp-sdk)
│   │   └── NotFoundPage.tsx
│   ├── styles/globals.css
│   └── vite-env.d.ts
└── README.md
```

## Khác biệt vs `@shop/miniapp`

| Aspect             | miniapp                              | zaloapp                                          |
|--------------------|--------------------------------------|--------------------------------------------------|
| Auth header        | `X-Telegram-Init-Data` (HMAC SHA-256)| `X-Zalo-Access-Token` (Zalo OpenAPI /v2/me)      |
| SDK                | `@twa-dev/sdk` (real)                | mock `zalo.ts` (placeholder for `zmp-sdk`)       |
| Brand color        | orange (#F97316 family)              | Zalo blue (#0068FF)                              |
| Live tracking map  | leaflet + WebSocket (full impl)      | placeholder block (cần zmp-sdk `getLocation`)    |
| Dev port           | 5173                                 | 5182                                             |
| Cart persist key   | `shop-cart`                          | `shop-cart-zalo`                                 |
| `tg.showAlert`     | uses Telegram WebApp popup           | thay bằng `Toast` (no native modal qua mock)     |

## Caveats

- **Chưa publish được**: chưa có Zalo Mini App ID + secret → bản này chỉ chạy local.
- **Không cài `zmp-sdk` thật**: package real require Zalo dev console credentials
  để fetch bản phù hợp; cài bừa sẽ break build. Khi có credential, làm theo
  "Production checklist".
- **Live tracking giảm scope**: `OrderDetailPage` không nhúng leaflet để giữ
  package nhẹ — thay bằng banner "đơn đang giao". Khi go-live có thể port lại
  bằng cách add `leaflet` + `react-leaflet` deps + copy `OrderTrackingMap` từ
  miniapp.
- **Chỉ role CUSTOMER**: bản preview không có shipper routes (miniapp có
  `/shipper/assignments`) — thực tế shipper VN ít dùng Zalo Mini App nên
  defer sang giai đoạn sau.
