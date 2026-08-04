# Thiết kế — Lưu địa chỉ thường dùng + autocomplete (#2)

> Ngày: 2026-08-04 · Pha mở rộng KLTN GiaoHang.

## Quyết định (đã chốt với người dùng)
- **Tự động lưu khi đặt đơn** (dedup theo toạ độ) — không cần nút "Lưu".
- **Autocomplete tích hợp vào ô tìm kiếm sẵn có** của `AddressPicker`: gõ → địa chỉ
  đã lưu khớp hiện **trên đầu** gợi ý (trên kết quả Nominatim); focus khi rỗng → hiện
  vài địa chỉ lưu gần đây. Chọn địa chỉ đã lưu → điền thẳng (không geocode lại).

## Backend (module order)
- Migration `V16__saved_address.sql` (chỉ app; order IT dùng ddl-auto):
  `saved_address(id, customer_id→telegram_user, address, lat, lng, use_count,
  last_used_at, created_at)`, UNIQUE `(customer_id, lat, lng)`, index `(customer_id, last_used_at DESC)`.
- `SavedAddress` entity (customerId là Long thuần, theo pattern `Order.customerId`).
- `SavedAddressRepository`: `findByCustomerIdOrderByLastUsedAtDesc` (Pageable),
  `findByCustomerIdAndLatAndLng`.
- `SavedAddressService`:
  - `recordUse(customerId, address, lat, lng)` — upsert: có → bump `use_count` + `last_used_at`
    (+ cập nhật address mới nhất); chưa → tạo.
  - `list(customerId, limit)` · `delete(id, customerId)` (kiểm tra sở hữu).
- `SavedAddressListener` — `@TransactionalEventListener(AFTER_COMMIT)` trên `OrderCreatedEvent`
  → load order → `recordUse(...)`. **AFTER_COMMIT** nên lỗi lưu địa chỉ không rollback đơn.
- `SavedAddressResponse` DTO + `SavedAddressController` (`/api/addresses`, `@CurrentUser`):
  `GET` list · `DELETE /{id}`.

## Frontend (shared + miniapp)
- shared: type `SavedAddress` + `listSavedAddresses(client)` + `deleteSavedAddress(client, id)`.
- `AddressPicker`: prop mới `savedAddresses?: SavedAddress[]`, `onDeleteSaved?`. Trộn vào
  dropdown gợi ý: mục "Đã lưu" (icon ⭐) trên đầu; focus rỗng → hiện saved; gõ → lọc saved
  khớp query lên trước Nominatim; chọn → `applyLocation(lat,lng,address)` trực tiếp.
- `CheckoutPage`: `useQuery(['me','saved-addresses'])` → truyền vào `AddressPicker`.

## Test (mã hoá ý định)
- BE: `recordUse` tạo mới khi chưa có; bump use_count/last_used_at khi trùng toạ độ;
  `delete` từ chối khi không phải chủ sở hữu; listener gọi recordUse với dữ liệu đơn.
- FE: AddressPicker hiện địa chỉ đã lưu khi focus, lọc theo query, chọn điền thẳng không geocode.

## Tiêu chí done
Khách đặt đơn → địa chỉ tự vào danh sách đã lưu; lần sau mở checkout, focus ô tìm kiếm
→ thấy địa chỉ cũ, chọn 1 phát điền xong (không cần search lại); xoá được địa chỉ thừa.
BE `mvnw test` + FE `pnpm test` xanh.
