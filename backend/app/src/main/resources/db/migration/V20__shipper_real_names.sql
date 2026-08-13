-- V20 — họ tên đầy đủ kiểu Việt cho shipper demo.
--
-- Trước đây first_name/last_name kiểu Tây ("Dũng" + "Phạm") nên mọi chỗ ghép
-- "first last" hiển thị ngược ("Dũng Phạm") và cụt ("Em Hoàng"). Mọi code ghép
-- tên đều null-safe, nên đưa cả họ tên vào first_name và bỏ last_name.
-- (customer_name snapshot trên các đơn cũ giữ nguyên — đó là ảnh chụp lịch sử.)

UPDATE telegram_user SET first_name = 'Phạm Tiến Dũng',  last_name = NULL WHERE id = 9000000101;
UPDATE telegram_user SET first_name = 'Hoàng Đức Thịnh', last_name = NULL WHERE id = 9000000102;
UPDATE telegram_user SET first_name = 'Đỗ Thanh Phong',  last_name = NULL WHERE id = 9000000103;
UPDATE telegram_user SET first_name = 'Nguyễn Anh Tuấn', last_name = NULL WHERE id = 9000000104;
UPDATE telegram_user SET first_name = 'Vũ Xuân Hòa',     last_name = NULL WHERE id = 9000000105;
UPDATE telegram_user SET first_name = 'Đặng Thái Sơn',   last_name = NULL WHERE id = 9000000106;
