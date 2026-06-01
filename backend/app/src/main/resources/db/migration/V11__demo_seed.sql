-- V11__demo_seed.sql — demo data for thesis defense (P9)
--
-- IDEMPOTENT: every INSERT uses ON CONFLICT DO NOTHING. Running this
-- migration on an already-seeded DB is a no-op (besides Flyway's own
-- bookkeeping, which only runs each version once anyway).
--
-- WARNING for real-world deploys:
--   Move this file out of db/migration/ before building a production image.
--   Alternative: set spring.flyway.target=V10 in application-prod.yml to stop
--   Flyway at V10 and skip V11.
--
-- Telegram user IDs use the "demo" range 9_000_000_001..9_000_000_999 so
-- they cannot collide with real Telegram user IDs (those stay <10 billion
-- but vary widely; the high-9-billion range is reserved for our demo).
--
-- Demo admin credentials:
--   email:    shop@example.com
--   password: admin123          (BCrypt-10, same hash as V5's admin@shop.local)


-- ─────────────────────────────────────────────────────────────
-- 1) Defensive uniqueness so we can ON CONFLICT (name)
-- ─────────────────────────────────────────────────────────────
-- Note: Some Testcontainer-reused DBs may have accumulated duplicate
-- product rows (e.g. multiple 'Test Product' inserts from earlier test
-- runs before this unique constraint existed). Deduplicate first
-- (keeping the lowest id per name) so the ALTER TABLE succeeds.
DELETE FROM product
WHERE id NOT IN (
    SELECT MIN(id) FROM product GROUP BY name
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'product_name_key'
    ) THEN
        ALTER TABLE product ADD CONSTRAINT product_name_key UNIQUE (name);
    END IF;
END$$;


-- ─────────────────────────────────────────────────────────────
-- 2) Demo admin user
-- ─────────────────────────────────────────────────────────────
INSERT INTO admin_user (email, password_hash, full_name)
VALUES (
    'shop@example.com',
    '$2a$10$8tbM0mvZZFQuz9KVhA6lOOZQfM2GxHIgmTWXbcVQ2ml353wyiYktO',
    'Demo Shop Owner'
)
ON CONFLICT (email) DO NOTHING;


-- ─────────────────────────────────────────────────────────────
-- 3) 10 demo products (Vietnamese names + VND prices)
-- ─────────────────────────────────────────────────────────────
INSERT INTO product (name, description, price, image_url, stock, is_active) VALUES
    ('Phở bò tái',        'Phở bò truyền thống, nước dùng đậm đà',  55000,  'https://picsum.photos/seed/pho-bo/400/300',  100, TRUE),
    ('Bún chả Hà Nội',    'Bún chả nướng than hoa, kèm rau sống',   60000,  'https://picsum.photos/seed/buncha/400/300',  80,  TRUE),
    ('Bánh mì pate',      'Bánh mì pate, dưa leo, rau thơm',        25000,  'https://picsum.photos/seed/banhmi/400/300',  150, TRUE),
    ('Cơm gà xối mỡ',     'Cơm gà giòn, sốt mắm tỏi',               65000,  'https://picsum.photos/seed/comga/400/300',   90,  TRUE),
    ('Bún bò Huế',        'Bún bò Huế cay nồng, giò heo',           70000,  'https://picsum.photos/seed/bunbo/400/300',   60,  TRUE),
    ('Trà sữa trân châu', 'Trà sữa size L, trân châu đường đen',    35000,  'https://picsum.photos/seed/trasua/400/300',  200, TRUE),
    ('Cà phê sữa đá',     'Cà phê phin, sữa đặc, đá',               20000,  'https://picsum.photos/seed/caphe/400/300',   200, TRUE),
    ('Nem rán Hà Nội',    'Nem rán giòn, kèm nước chấm chua ngọt',  45000,  'https://picsum.photos/seed/nemran/400/300',  120, TRUE),
    ('Chè bưởi',          'Chè bưởi mát lạnh, nước cốt dừa',        30000,  'https://picsum.photos/seed/chebuoi/400/300', 70,  TRUE),
    ('Bánh xèo miền Tây', 'Bánh xèo giòn, tôm thịt, rau sống',      50000,  'https://picsum.photos/seed/banhxeo/400/300', 50,  TRUE)
ON CONFLICT (name) DO NOTHING;


-- ─────────────────────────────────────────────────────────────
-- 4) Demo Telegram users (3 customers + 3 shippers)
-- ─────────────────────────────────────────────────────────────
INSERT INTO telegram_user (id, username, first_name, last_name, phone, language_code) VALUES
    (9000000001, 'demo_khach_an',  'An',     'Nguyễn', '0901234001', 'vi'),
    (9000000002, 'demo_khach_binh','Bình',   'Trần',   '0901234002', 'vi'),
    (9000000003, 'demo_khach_chi', 'Chi',    'Lê',     '0901234003', 'vi'),
    (9000000101, 'demo_ship_dung', 'Dũng',   'Phạm',   '0902345101', 'vi'),
    (9000000102, 'demo_ship_em',   'Em',     'Hoàng',  '0902345102', 'vi'),
    (9000000103, 'demo_ship_phong','Phong',  'Đỗ',     '0902345103', 'vi')
ON CONFLICT (id) DO NOTHING;

-- Customer role for the 3 customers
INSERT INTO user_role (telegram_user_id, role, status) VALUES
    (9000000001, 'CUSTOMER', 'ACTIVE'),
    (9000000002, 'CUSTOMER', 'ACTIVE'),
    (9000000003, 'CUSTOMER', 'ACTIVE'),
    (9000000101, 'SHIPPER',  'ACTIVE'),
    (9000000102, 'SHIPPER',  'ACTIVE'),
    (9000000103, 'SHIPPER',  'PENDING')   -- the one awaiting admin approval
ON CONFLICT (telegram_user_id, role) DO NOTHING;

-- Shipper profile (vehicle / rating tracking — populated by P5)
INSERT INTO shipper_profile (user_id, vehicle_type, license_plate, current_state, rating_avg, rating_count, total_deliveries) VALUES
    (9000000101, 'MOTORBIKE', '29-X1 12345', 'ONLINE',  4.80, 12, 15),
    (9000000102, 'MOTORBIKE', '29-X2 23456', 'OFFLINE', 4.50, 8,  10),
    (9000000103, 'BICYCLE',   '',            'OFFLINE', 0.00, 0,  0)
ON CONFLICT (user_id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────
-- 5) 30 demo orders rải đều 30 ngày
-- ─────────────────────────────────────────────────────────────
-- Strategy:
--   - Fixed UUIDs ('a0000000-...-NN') so ON CONFLICT (id) is deterministic.
--   - Subqueries SELECT product.id by name (BIGSERIAL isn't known at seed time).
--   - Subqueries SELECT total/subtotal as product.price * qty — no hard-coded math drift.
--   - One order_item per order to keep this readable (real demos can edit).
--   - created_at uses NOW() - INTERVAL '<n> days' so the data slides with time.

-- Shop pickup point (Hoàn Kiếm) reused as the pickup_lat/lng for every order.
-- Delivery coords vary by ±0.01° (~1 km) so the map shows meaningful spread.

-- 5 PENDING (last 7 days, COD)
INSERT INTO orders (id, code, customer_id, customer_name, customer_phone,
                    pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng,
                    distance_km, subtotal, delivery_fee, total,
                    payment_method, payment_status, status, note, created_at, updated_at)
SELECT
    ('a0000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
    'DEMO-2026-' || lpad(n::text, 4, '0'),
    9000000001 + (n % 3)::bigint,
    'Khách demo #' || n,
    '0901234' || lpad((100 + n)::text, 3, '0'),
    21.0285, 105.8542,
    'Số ' || n || ', Phố demo, Hà Nội',
    21.0285 + (n - 15) * 0.001,
    105.8542 + (n - 15) * 0.001,
    2.5,
    (SELECT price FROM product WHERE name = 'Phở bò tái'),
    15000,
    (SELECT price FROM product WHERE name = 'Phở bò tái') + 15000,
    'COD', 'PENDING', 'PENDING',
    'Đơn demo PENDING #' || n,
    NOW() - (n || ' days')::interval,
    NOW() - (n || ' days')::interval
FROM generate_series(1, 5) n
ON CONFLICT (id) DO NOTHING;

-- 4 CONFIRMED (VNPAY paid, no shipper yet) — n=6..9
INSERT INTO orders (id, code, customer_id, customer_name, customer_phone,
                    pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng,
                    distance_km, subtotal, delivery_fee, total,
                    payment_method, payment_status, status, note, created_at, updated_at)
SELECT
    ('a0000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
    'DEMO-2026-' || lpad(n::text, 4, '0'),
    9000000001 + (n % 3)::bigint,
    'Khách demo #' || n,
    '0901234' || lpad((100 + n)::text, 3, '0'),
    21.0285, 105.8542,
    'Số ' || n || ', Phố demo, Hà Nội',
    21.0285 + (n - 15) * 0.001,
    105.8542 + (n - 15) * 0.001,
    3.0,
    (SELECT price FROM product WHERE name = 'Bún chả Hà Nội'),
    15000,
    (SELECT price FROM product WHERE name = 'Bún chả Hà Nội') + 15000,
    'VNPAY', 'SUCCESS', 'CONFIRMED',
    'Đơn demo CONFIRMED #' || n,
    NOW() - (n || ' days')::interval,
    NOW() - (n || ' days')::interval
FROM generate_series(6, 9) n
ON CONFLICT (id) DO NOTHING;

-- 3 ASSIGNED (VNPAY, assigned to shipper-1/shipper-2) — n=10..12
INSERT INTO orders (id, code, customer_id, customer_name, customer_phone,
                    pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng,
                    distance_km, subtotal, delivery_fee, total,
                    payment_method, payment_status, status, note, created_at, updated_at)
SELECT
    ('a0000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
    'DEMO-2026-' || lpad(n::text, 4, '0'),
    9000000001 + (n % 3)::bigint,
    'Khách demo #' || n,
    '0901234' || lpad((100 + n)::text, 3, '0'),
    21.0285, 105.8542,
    'Số ' || n || ', Phố demo, Hà Nội',
    21.0285 + (n - 15) * 0.001,
    105.8542 + (n - 15) * 0.001,
    2.0,
    (SELECT price FROM product WHERE name = 'Bánh mì pate') * 2,
    15000,
    (SELECT price FROM product WHERE name = 'Bánh mì pate') * 2 + 15000,
    'VNPAY', 'SUCCESS', 'ASSIGNED',
    'Đơn demo ASSIGNED #' || n,
    NOW() - (n - 5 || ' hours')::interval,
    NOW() - (n - 5 || ' hours')::interval
FROM generate_series(10, 12) n
ON CONFLICT (id) DO NOTHING;

-- 2 DELIVERING (VNPAY, in flight) — n=13..14
-- (BLOCKER fix B2: only 2 DELIVERING so each maps to a distinct active shipper —
--  the V8 partial unique index `uq_assignment_shipper_started` rejects two STARTED
--  rows for the same shipper. Demo still shows the live-tracking feature with one
--  in-flight order; the 3rd shipper slot is PENDING and not yet ACTIVE.)
INSERT INTO orders (id, code, customer_id, customer_name, customer_phone,
                    pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng,
                    distance_km, subtotal, delivery_fee, total,
                    payment_method, payment_status, status, note, created_at, updated_at)
SELECT
    ('a0000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
    'DEMO-2026-' || lpad(n::text, 4, '0'),
    9000000001 + (n % 3)::bigint,
    'Khách demo #' || n,
    '0901234' || lpad((100 + n)::text, 3, '0'),
    21.0285, 105.8542,
    'Số ' || n || ', Phố demo, Hà Nội',
    21.0285 + (n - 15) * 0.001,
    105.8542 + (n - 15) * 0.001,
    4.5,
    (SELECT price FROM product WHERE name = 'Cơm gà xối mỡ'),
    20000,
    (SELECT price FROM product WHERE name = 'Cơm gà xối mỡ') + 20000,
    'VNPAY', 'SUCCESS', 'DELIVERING',
    'Đơn demo DELIVERING #' || n,
    NOW() - (n - 12 || ' hours')::interval,
    NOW() - (n - 12 || ' minutes')::interval  -- updated recently
FROM generate_series(13, 14) n
ON CONFLICT (id) DO NOTHING;

-- 13 DELIVERED (mix COD/VNPAY, spread across 30 days) — n=15..27
INSERT INTO orders (id, code, customer_id, customer_name, customer_phone,
                    pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng,
                    distance_km, subtotal, delivery_fee, total,
                    payment_method, payment_status, status, note, created_at, updated_at)
SELECT
    ('a0000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
    'DEMO-2026-' || lpad(n::text, 4, '0'),
    9000000001 + (n % 3)::bigint,
    'Khách demo #' || n,
    '0901234' || lpad((100 + n)::text, 3, '0'),
    21.0285, 105.8542,
    'Số ' || n || ', Phố demo, Hà Nội',
    21.0285 + (n - 15) * 0.001,
    105.8542 + (n - 15) * 0.001,
    3.5,
    (SELECT price FROM product WHERE name = 'Bún bò Huế'),
    17500,
    (SELECT price FROM product WHERE name = 'Bún bò Huế') + 17500,
    CASE WHEN n % 2 = 0 THEN 'VNPAY' ELSE 'COD' END,
    CASE WHEN n % 2 = 0 THEN 'SUCCESS' ELSE 'SUCCESS' END,  -- COD delivered = paid on delivery
    'DELIVERED',
    'Đơn demo DELIVERED #' || n,
    NOW() - ((n - 5) || ' days')::interval,
    NOW() - ((n - 5) || ' days')::interval + INTERVAL '2 hours'
FROM generate_series(15, 27) n
ON CONFLICT (id) DO NOTHING;

-- 3 CANCELLED (mix recent + old) — n=28..30
INSERT INTO orders (id, code, customer_id, customer_name, customer_phone,
                    pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng,
                    distance_km, subtotal, delivery_fee, total,
                    payment_method, payment_status, status, note, created_at, updated_at)
SELECT
    ('a0000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
    'DEMO-2026-' || lpad(n::text, 4, '0'),
    9000000001 + (n % 3)::bigint,
    'Khách demo #' || n,
    '0901234' || lpad((100 + n)::text, 3, '0'),
    21.0285, 105.8542,
    'Số ' || n || ', Phố demo, Hà Nội',
    21.0285 + (n - 15) * 0.001,
    105.8542 + (n - 15) * 0.001,
    1.5,
    (SELECT price FROM product WHERE name = 'Trà sữa trân châu'),
    15000,
    (SELECT price FROM product WHERE name = 'Trà sữa trân châu') + 15000,
    'COD', 'PENDING', 'CANCELLED',
    'Khách huỷ — đổi ý',
    NOW() - ((n - 10) || ' days')::interval,
    NOW() - ((n - 10) || ' days')::interval + INTERVAL '15 minutes'
FROM generate_series(28, 30) n
ON CONFLICT (id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────
-- 6) order_item rows (1 item per order — use product subqueries)
-- ─────────────────────────────────────────────────────────────
-- Map each order to a product by deterministic n -> product round-robin.
-- BIGSERIAL PK means ON CONFLICT on PK isn't meaningful; use NOT EXISTS anti-join.
INSERT INTO order_item (order_id, product_id, quantity, unit_price, subtotal)
SELECT
    o.id,
    (SELECT id FROM product ORDER BY id LIMIT 1 OFFSET ((right(o.code, 4)::int - 1) % 10)),
    1,
    (SELECT price FROM product ORDER BY id LIMIT 1 OFFSET ((right(o.code, 4)::int - 1) % 10)),
    (SELECT price FROM product ORDER BY id LIMIT 1 OFFSET ((right(o.code, 4)::int - 1) % 10))
FROM orders o
WHERE o.code LIKE 'DEMO-2026-%'
  AND NOT EXISTS (SELECT 1 FROM order_item oi WHERE oi.order_id = o.id);


-- ─────────────────────────────────────────────────────────────
-- 7) delivery_assignment rows for ASSIGNED/DELIVERING/DELIVERED orders
-- ─────────────────────────────────────────────────────────────
INSERT INTO delivery_assignment (id, order_id, shipper_id, status, assigned_at, accepted_at, started_at, delivered_at)
SELECT
    -- UUID last segment must be exactly 12 hex chars: "00000000" (8) + right(code,4) (4) = 12
    ('b0000000-0000-0000-0000-00000000' || right(o.code, 4))::uuid,
    o.id,
    CASE WHEN (right(o.code, 4)::int) % 2 = 0 THEN 9000000101 ELSE 9000000102 END,
    -- BLOCKER fix B1: AssignmentStatus enum is {OFFERED, ACCEPTED, REJECTED, STARTED, COMPLETED, CANCELLED}.
    -- The order's status values map to assignment values like this:
    --   order.ASSIGNED   → assignment.ACCEPTED  (shipper accepted, hasn't pressed "start")
    --   order.DELIVERING → assignment.STARTED   (shipper pressed "start", sharing live location)
    --   order.DELIVERED  → assignment.COMPLETED (delivery confirmed)
    CASE o.status
        WHEN 'ASSIGNED'   THEN 'ACCEPTED'
        WHEN 'DELIVERING' THEN 'STARTED'
        WHEN 'DELIVERED'  THEN 'COMPLETED'
    END,
    o.created_at + INTERVAL '5 minutes',
    CASE WHEN o.status IN ('DELIVERING','DELIVERED') THEN o.created_at + INTERVAL '10 minutes' END,
    CASE WHEN o.status IN ('DELIVERING','DELIVERED') THEN o.created_at + INTERVAL '20 minutes' END,
    CASE WHEN o.status = 'DELIVERED' THEN o.updated_at END
FROM orders o
WHERE o.code LIKE 'DEMO-2026-%'
  AND o.status IN ('ASSIGNED', 'DELIVERING', 'DELIVERED')
  AND NOT EXISTS (SELECT 1 FROM delivery_assignment da WHERE da.order_id = o.id);


-- ─────────────────────────────────────────────────────────────
-- 8) rating rows for 10 of the 13 DELIVERED orders
-- ─────────────────────────────────────────────────────────────
-- 8 rated 4-5 stars, 2 rated 3 stars, 3 unrated.
-- Modulo selection picks deterministically.
INSERT INTO rating (order_id, customer_id, shipper_id, stars, comment, created_at)
SELECT
    o.id,
    o.customer_id,
    da.shipper_id,
    CASE
        WHEN right(o.code, 4)::int % 5 = 0 THEN 3   -- ~20% give 3 stars
        WHEN right(o.code, 4)::int % 5 = 1 THEN 5
        WHEN right(o.code, 4)::int % 5 = 2 THEN 4
        WHEN right(o.code, 4)::int % 5 = 3 THEN 5
        ELSE 4
    END,
    CASE
        WHEN right(o.code, 4)::int % 3 = 0 THEN 'Giao nhanh, đồ ăn còn nóng. Cảm ơn shipper!'
        WHEN right(o.code, 4)::int % 3 = 1 THEN 'Shipper thân thiện, tới đúng giờ.'
        ELSE NULL
    END,
    o.updated_at + INTERVAL '5 minutes'
FROM orders o
JOIN delivery_assignment da ON da.order_id = o.id
WHERE o.code LIKE 'DEMO-2026-%'
  AND o.status = 'DELIVERED'
  AND right(o.code, 4)::int <= 24   -- skip 25..27 → 3 unrated DELIVERED demo orders (10 rated total)
ON CONFLICT (order_id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────
-- 9) payment rows for VNPAY orders (SUCCESS) — observability completeness
-- ─────────────────────────────────────────────────────────────
INSERT INTO payment (id, order_id, method, amount, status,
                     vnp_txn_ref, vnp_transaction_no, vnp_response_code, paid_at, created_at, updated_at)
SELECT
    -- UUID last segment must be exactly 12 hex chars: "00000000" (8) + right(code,4) (4) = 12
    ('c0000000-0000-0000-0000-00000000' || right(o.code, 4))::uuid,
    o.id,
    'VNPAY',
    o.total,
    'SUCCESS',
    o.code || '-' || extract(epoch from o.created_at)::bigint,
    'DEMO-VNPAY-TXN-' || right(o.code, 4),
    '00',
    o.created_at + INTERVAL '2 minutes',
    o.created_at,
    o.created_at + INTERVAL '2 minutes'
FROM orders o
WHERE o.code LIKE 'DEMO-2026-%'
  AND o.payment_method = 'VNPAY'
  AND o.payment_status = 'SUCCESS'
ON CONFLICT (id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────
-- 10) status_history rows for each demo order (initial PENDING transition)
-- ─────────────────────────────────────────────────────────────
-- Schema check:
--   status_history columns vary by phase (V4 baseline). We only fill what
--   V4 declared as NOT NULL: order_id, to_status, changed_at.
-- BLOCKER fix B3: column is `changed_by_user_id` (V4 schema), not `actor_user_id`.
INSERT INTO status_history (order_id, from_status, to_status, changed_by_user_id, note, changed_at)
SELECT
    o.id,
    NULL,
    'PENDING',
    o.customer_id,
    'Đơn được tạo (demo)',
    o.created_at
FROM orders o
WHERE o.code LIKE 'DEMO-2026-%'
  AND NOT EXISTS (
      SELECT 1 FROM status_history sh
      WHERE sh.order_id = o.id AND sh.to_status = 'PENDING'
  );


-- ─────────────────────────────────────────────────────────────
-- 11) Update shipper_profile.total_deliveries from actual data
-- ─────────────────────────────────────────────────────────────
UPDATE shipper_profile sp
SET total_deliveries = sub.cnt,
    rating_count     = sub.rc,
    rating_avg       = COALESCE(sub.ravg, 0)
FROM (
    SELECT
        da.shipper_id,
        COUNT(*) FILTER (WHERE da.status = 'COMPLETED')      AS cnt,
        COUNT(r.id)                                          AS rc,
        ROUND(AVG(r.stars), 2)                               AS ravg
    FROM delivery_assignment da
    LEFT JOIN rating r ON r.order_id = da.order_id
    WHERE da.shipper_id IS NOT NULL
    GROUP BY da.shipper_id
) sub
WHERE sp.user_id = sub.shipper_id;


-- Done. After this migration runs once:
--   - 1  demo admin
--   - 10 products
--   - 6  telegram users (3 customers + 3 shippers, 1 PENDING)
--   - 30 orders (5 PENDING, 4 CONFIRMED, 3 ASSIGNED, 2 DELIVERING, 13 DELIVERED, 3 CANCELLED)
--   - 30 order_items
--   - 18 delivery_assignments (3 ACCEPTED + 2 STARTED + 13 COMPLETED)
--   - 10 ratings (on 10 of the 13 DELIVERED orders)
--   - 11 payment rows (every VNPAY-SUCCESS order)
--   - 30 status_history rows (PENDING transition)
