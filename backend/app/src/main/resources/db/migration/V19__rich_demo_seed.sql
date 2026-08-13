-- V19 — seed dữ liệu demo giống vận hành thật cho toàn dự án.
--
--   1) Xóa 30 đơn V11 lộ rõ là giả ('Khách demo #n', 'Phố demo, Hà Nội').
--   2) +10 sản phẩm (đủ 20; thêm đồ uống/tráng miệng cho filter cân đối) — ảnh Wikimedia thật.
--   3) +10 khách tên thật + địa chỉ đã lưu; +3 shipper có hồ sơ.
--   4) ~70 đơn rải 35 ngày gần nhất: địa chỉ Hà Nội thật, 1–3 món/đơn, tiền tính từ giá thật,
--      chuỗi status_history đầy đủ, delivery_assignment, đánh giá sao, sổ hoa hồng/COD của shipper.
--      Timestamps dùng NOW() - interval nên dữ liệu tự "trôi" theo ngày chạy migration.

-- ─────────────────────────────────────────────────────────────
-- 1) Dọn đơn demo cũ (ledger chặn ON DELETE RESTRICT nên xóa trước)
-- ─────────────────────────────────────────────────────────────
DELETE FROM shipper_ledger      WHERE order_id IN (SELECT id FROM orders WHERE code LIKE 'DEMO-2026-%');
DELETE FROM payment             WHERE order_id IN (SELECT id FROM orders WHERE code LIKE 'DEMO-2026-%');
DELETE FROM voucher_redemption  WHERE order_id IN (SELECT id FROM orders WHERE code LIKE 'DEMO-2026-%');
DELETE FROM orders WHERE code LIKE 'DEMO-2026-%';

-- ─────────────────────────────────────────────────────────────
-- 2) 10 sản phẩm mới (tổng 20)
-- ─────────────────────────────────────────────────────────────
INSERT INTO product (name, description, price, image_url, stock, is_active, category) VALUES
    ('Bún riêu cua',        'Bún riêu cua đồng, đậu rán, giấm bỗng',          50000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f0/B%C3%BAn_ri%C3%AAu_cua%2C_crab_roe_pate_and_fried_shallots.jpg/960px-B%C3%BAn_ri%C3%AAu_cua%2C_crab_roe_pate_and_fried_shallots.jpg', 80,  TRUE, 'food'),
    ('Bánh cuốn Thanh Trì', 'Bánh cuốn nóng, chả quế, hành phi',              40000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b2/B%C3%A1nh_cu%E1%BB%91n_Thanh_Tr%C3%AC.jpg/960px-B%C3%A1nh_cu%E1%BB%91n_Thanh_Tr%C3%AC.jpg', 90,  TRUE, 'food'),
    ('Xôi gà',              'Xôi nếp dẻo, gà xé, lạp xưởng, trứng non',       45000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/X%C3%B4i_G%C3%A0_Tr%E1%BB%A9ng_Non_L%E1%BA%A1p_X%C6%B0%E1%BB%9Bng_%28Sticky_rice_with_chicken_and_eggs%2C_and_chinese_sausages%29.jpg/960px-X%C3%B4i_G%C3%A0_Tr%E1%BB%A9ng_Non_L%E1%BA%A1p_X%C6%B0%E1%BB%9Bng_%28Sticky_rice_with_chicken_and_eggs%2C_and_chinese_sausages%29.jpg', 70,  TRUE, 'food'),
    ('Gỏi cuốn tôm thịt',   'Gỏi cuốn tươi, chấm tương đậu phộng (5 cuốn)',   40000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b2/Summer_rolls_with_peanut_sauce.jpg/960px-Summer_rolls_with_peanut_sauce.jpg', 100, TRUE, 'food'),
    ('Cơm tấm sườn nướng',  'Cơm tấm, sườn nướng mật ong, trứng ốp la',       60000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Com-Tam-2008.jpg/960px-Com-Tam-2008.jpg', 85,  TRUE, 'food'),
    ('Nước mía',            'Nước mía ép nguyên chất, thêm tắc',              15000, 'https://upload.wikimedia.org/wikipedia/commons/6/63/Sugarcanejuice.jpg', 200, TRUE, 'drink'),
    ('Sinh tố bơ',          'Sinh tố bơ sáp Đắk Lắk, sữa đặc',                40000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/a/a5/Sinh_t%E1%BB%91_b%C6%A1.jpg/960px-Sinh_t%E1%BB%91_b%C6%A1.jpg', 120, TRUE, 'drink'),
    ('Nước cam ép',         'Cam sành vắt nguyên chất, không đường',          30000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/960px-Orange_juice_1_edit1.jpg', 150, TRUE, 'drink'),
    ('Chè thập cẩm',        'Chè đậu đỏ, đậu xanh, thạch, nước cốt dừa',      30000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Chendol2.jpg/960px-Chendol2.jpg', 90,  TRUE, 'dessert'),
    ('Bánh flan',           'Bánh flan trứng sữa, caramel đắng nhẹ (2 hộp)',  25000, 'https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Cr%C3%A8me_caramel_2.jpg/960px-Cr%C3%A8me_caramel_2.jpg', 110, TRUE, 'dessert')
ON CONFLICT (name) DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- 3) 10 khách + 3 shipper mới
-- ─────────────────────────────────────────────────────────────
INSERT INTO telegram_user (id, username, first_name, last_name, phone, language_code) VALUES
    (9000001001, 'hung_tran88',   'Hùng',   'Trần',   '0912456001', 'vi'),
    (9000001002, 'ha_pham92',     'Hà',     'Phạm',   '0912456002', 'vi'),
    (9000001003, 'duc_le99',      'Đức',    'Lê',     '0912456003', 'vi'),
    (9000001004, 'mai_vu95',      'Mai',    'Vũ',     '0912456004', 'vi'),
    (9000001005, 'nam_hoang90',   'Nam',    'Hoàng',  '0912456005', 'vi'),
    (9000001006, 'lan_do87',      'Lan',    'Đỗ',     '0912456006', 'vi'),
    (9000001007, 'huy_bui01',     'Huy',    'Bùi',    '0912456007', 'vi'),
    (9000001008, 'tam_ngo93',     'Tâm',    'Ngô',    '0912456008', 'vi'),
    (9000001009, 'quynh_dang96',  'Quỳnh',  'Đặng',   '0912456009', 'vi'),
    (9000001010, 'khanh_duong89', 'Khánh',  'Dương',  '0912456010', 'vi'),
    (9000000104, 'ship_tuan_nv',  'Tuấn',   'Nguyễn', '0902345104', 'vi'),
    (9000000105, 'ship_hoa_vt',   'Hòa',    'Vũ',     '0902345105', 'vi'),
    (9000000106, 'ship_son_dv',   'Sơn',    'Đặng',   '0902345106', 'vi')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_role (telegram_user_id, role, status)
SELECT id, 'CUSTOMER', 'ACTIVE' FROM telegram_user WHERE id BETWEEN 9000001001 AND 9000001010
ON CONFLICT (telegram_user_id, role) DO NOTHING;

INSERT INTO user_role (telegram_user_id, role, status) VALUES
    (9000000104, 'SHIPPER', 'ACTIVE'),
    (9000000105, 'SHIPPER', 'ACTIVE'),
    (9000000106, 'SHIPPER', 'ACTIVE')
ON CONFLICT (telegram_user_id, role) DO NOTHING;

INSERT INTO shipper_profile (user_id, vehicle_type, license_plate, current_state, rating_avg, rating_count, total_deliveries) VALUES
    (9000000104, 'MOTORBIKE', '29-B1 456.78', 'AVAILABLE', 0, 0, 0),
    (9000000105, 'MOTORBIKE', '30-F5 789.12', 'AVAILABLE', 0, 0, 0),
    (9000000106, 'MOTORBIKE', '29-C1 234.56', 'OFFLINE',   0, 0, 0)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO saved_address (customer_id, address, lat, lng, use_count, last_used_at) VALUES
    (9000001001, '68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',    21.0119, 105.7965, 4, NOW() - INTERVAL '2 days'),
    (9000001002, '25 Láng Hạ, Thành Công, Ba Đình, Hà Nội',          21.0170, 105.8135, 6, NOW() - INTERVAL '1 day'),
    (9000001003, '191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội',  21.0125, 105.8494, 2, NOW() - INTERVAL '5 days'),
    (9000001004, '54 Nguyễn Chí Thanh, Láng Thượng, Đống Đa, Hà Nội',21.0227, 105.8085, 3, NOW() - INTERVAL '3 days'),
    (9000001005, '89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội',   21.0453, 105.7936, 5, NOW() - INTERVAL '12 hours')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- 4) ~70 đơn hàng rải 35 ngày
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE
    -- Địa chỉ Hà Nội thật + tọa độ xấp xỉ (quanh nội thành).
    addrs TEXT[] := ARRAY[
        '68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',
        '25 Láng Hạ, Thành Công, Ba Đình, Hà Nội',
        '191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội',
        '54 Nguyễn Chí Thanh, Láng Thượng, Đống Đa, Hà Nội',
        '89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội',
        '302 Cầu Giấy, Dịch Vọng, Cầu Giấy, Hà Nội',
        '15 Kim Mã, Ngọc Khánh, Ba Đình, Hà Nội',
        '78 Tây Sơn, Quang Trung, Đống Đa, Hà Nội',
        '210 Nguyễn Trãi, Thượng Đình, Thanh Xuân, Hà Nội',
        '46 Hàng Bông, Hàng Gai, Hoàn Kiếm, Hà Nội',
        '120 Bạch Mai, Cầu Dền, Hai Bà Trưng, Hà Nội',
        '33 Giảng Võ, Cát Linh, Đống Đa, Hà Nội',
        '285 Đội Cấn, Liễu Giai, Ba Đình, Hà Nội',
        '19 Duy Tân, Dịch Vọng Hậu, Cầu Giấy, Hà Nội'
    ];
    lats NUMERIC[] := ARRAY[21.0119,21.0170,21.0125,21.0227,21.0453,21.0330,21.0311,21.0083,20.9938,21.0301,21.0006,21.0281,21.0367,21.0288];
    lngs NUMERIC[] := ARRAY[105.7965,105.8135,105.8494,105.8085,105.7936,105.7940,105.8188,105.8232,105.8112,105.8480,105.8503,105.8286,105.8210,105.7829];
    custs BIGINT[] := ARRAY[9000000001,9000000002,9000000003,9000001001,9000001002,9000001003,9000001004,9000001005,9000001006,9000001007,9000001008,9000001009,9000001010];
    ships BIGINT[] := ARRAY[9000000101,9000000102,9000000104,9000000105];
    comments TEXT[] := ARRAY[
        'Giao nhanh, món còn nóng!', 'Shipper thân thiện, đúng giờ', 'Đồ ăn ngon, sẽ ủng hộ tiếp',
        'Giao hơi trễ nhưng shipper có gọi báo trước', 'Đóng gói cẩn thận', 'Ổn', NULL, NULL,
        'Món ngon, giao đúng giờ', 'Rất hài lòng', NULL, 'Lần sau sẽ đặt nữa'
    ];
    o_id UUID; o_code TEXT; o_ts TIMESTAMPTZ; ai INT; ci BIGINT; c_name TEXT; c_phone TEXT;
    dist NUMERIC; fee NUMERIC; sub NUMERIC; disc NUMERIC; tot NUMERIC;
    pay TEXT; pay_status TEXT; st TEXT; ship_id BIGINT; commission NUMERIC;
    t_confirm TIMESTAMPTZ; t_assign TIMESTAMPTZ; t_accept TIMESTAMPTZ; t_start TIMESTAMPTZ; t_done TIMESTAMPTZ;
    n_items INT; stars INT; r NUMERIC; day_off NUMERIC;
    pr RECORD;
BEGIN
    FOR i IN 1..70 LOOP
        -- 4 đơn cuối để hôm nay (dashboard "hôm nay" có số); còn lại rải 35 ngày, giờ 8h–21h.
        day_off := CASE WHEN i > 66 THEN 0 ELSE 1 + floor(random() * 34) END;
        o_ts := date_trunc('day', NOW()) - (day_off || ' days')::interval
                + ((8 + floor(random() * 13)) || ' hours')::interval
                + (floor(random() * 60) || ' minutes')::interval;
        IF o_ts > NOW() THEN o_ts := NOW() - (floor(random() * 3) + 1 || ' hours')::interval; END IF;

        ai := 1 + floor(random() * array_length(addrs, 1))::int;
        ci := custs[1 + floor(random() * array_length(custs, 1))::int];
        SELECT COALESCE(last_name || ' ' || first_name, first_name), phone INTO c_name, c_phone
          FROM telegram_user WHERE id = ci;

        dist := round((1 + random() * 7)::numeric, 3);
        fee  := 15000 + GREATEST(0, ceil(dist - 1)) * 5000;

        -- Trạng thái: đơn cũ chỉ còn terminal; đơn hôm nay có cả trạng thái đang chạy.
        r := random();
        IF day_off = 0 THEN
            st := (ARRAY['PENDING','CONFIRMED','DELIVERING','DELIVERED'])[1 + floor(random()*4)::int];
        ELSIF r < 0.90 THEN st := 'DELIVERED';
        ELSE st := 'CANCELLED';
        END IF;

        pay := CASE WHEN random() < 0.72 THEN 'COD' ELSE 'VNPAY' END;
        pay_status := CASE WHEN pay = 'VNPAY' AND st <> 'CANCELLED' THEN 'SUCCESS' ELSE 'PENDING' END;

        o_id := gen_random_uuid();
        o_code := 'DH' || to_char(o_ts, 'YYYYMMDD') || '-' || upper(substr(md5(random()::text), 1, 5));

        -- 1–3 món ngẫu nhiên, subtotal cộng từ giá thật.
        n_items := 1 + floor(random() * 3)::int;
        sub := 0;

        INSERT INTO orders (id, code, customer_id, customer_name, customer_phone,
                            pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng,
                            distance_km, subtotal, delivery_fee, delivery_fee_original, total,
                            payment_method, payment_status, status, note, created_at, updated_at)
        VALUES (o_id, o_code, ci, c_name, c_phone,
                21.0285, 105.8542, addrs[ai], lats[ai] + (random()-0.5)*0.002, lngs[ai] + (random()-0.5)*0.002,
                dist, 0, fee, fee, 0,
                pay, pay_status, st,
                CASE WHEN random() < 0.25 THEN (ARRAY['Gọi trước khi giao giúp em','Để ở sảnh chung cư, gọi em xuống lấy','Không hành','Thêm ớt','Giao giờ trưa giúp mình'])[1 + floor(random()*5)::int] ELSE NULL END,
                o_ts, o_ts);

        FOR pr IN SELECT id, price FROM product WHERE is_active ORDER BY random() LIMIT n_items LOOP
            DECLARE qty INT := 1 + floor(random() * 3)::int;
            BEGIN
                INSERT INTO order_item (order_id, product_id, quantity, unit_price, subtotal)
                VALUES (o_id, pr.id, qty, pr.price, pr.price * qty);
                sub := sub + pr.price * qty;
            END;
        END LOOP;

        tot := sub + fee;
        UPDATE orders SET subtotal = sub, total = tot WHERE id = o_id;

        -- Chuỗi lịch sử + assignment theo trạng thái.
        INSERT INTO status_history (order_id, from_status, to_status, changed_by_user_id, changed_at, note)
        VALUES (o_id, NULL, 'PENDING', ci, o_ts, 'Đơn được tạo');

        IF st IN ('CONFIRMED','ASSIGNED','DELIVERING','DELIVERED') THEN
            t_confirm := o_ts + ((3 + floor(random()*10)) || ' minutes')::interval;
            INSERT INTO status_history (order_id, from_status, to_status, changed_by_user_id, changed_at, note)
            VALUES (o_id, 'PENDING', 'CONFIRMED', NULL, t_confirm, 'Shop xác nhận');
            UPDATE orders SET updated_at = t_confirm WHERE id = o_id;
        END IF;

        IF st IN ('DELIVERING','DELIVERED') THEN
            ship_id := ships[1 + floor(random() * array_length(ships, 1))::int];
            t_assign := t_confirm + ((2 + floor(random()*6)) || ' minutes')::interval;
            t_accept := t_assign  + ((1 + floor(random()*4)) || ' minutes')::interval;
            t_start  := t_accept  + ((4 + floor(random()*8)) || ' minutes')::interval;
            INSERT INTO status_history (order_id, from_status, to_status, changed_by_user_id, changed_at, note)
            VALUES (o_id, 'CONFIRMED', 'ASSIGNED', NULL, t_assign, 'Gán shipper'),
                   (o_id, 'ASSIGNED', 'DELIVERING', ship_id, t_start, 'Shipper bắt đầu giao');

            IF st = 'DELIVERED' THEN
                t_done := t_start + ((10 + dist * 4 + floor(random()*15)) || ' minutes')::interval;
                INSERT INTO status_history (order_id, from_status, to_status, changed_by_user_id, changed_at, note)
                VALUES (o_id, 'DELIVERING', 'DELIVERED', ship_id, t_done, 'Giao thành công');
                INSERT INTO delivery_assignment (id, order_id, shipper_id, status, assigned_at, accepted_at, started_at, delivered_at)
                VALUES (gen_random_uuid(), o_id, ship_id, 'COMPLETED', t_assign, t_accept, t_start, t_done);
                UPDATE orders SET updated_at = t_done WHERE id = o_id;

                -- Sổ shipper: hoa hồng 80% phí ship (+), COD shipper giữ hộ tiền (−).
                commission := round(fee * 0.8, 2);
                UPDATE orders SET shipper_commission = commission WHERE id = o_id;
                INSERT INTO shipper_ledger (shipper_id, entry_type, amount, order_id, note, created_by, created_at)
                VALUES (ship_id, 'COMMISSION', commission, o_id, 'Hoa hồng đơn ' || o_code, 'system', t_done);
                IF pay = 'COD' THEN
                    UPDATE orders SET payment_status = 'SUCCESS' WHERE id = o_id;
                    INSERT INTO shipper_ledger (shipper_id, entry_type, amount, order_id, note, created_by, created_at)
                    VALUES (ship_id, 'COD_OWED', -tot, o_id, 'Shipper đã thu COD đơn ' || o_code, 'system', t_done);
                END IF;

                -- ~75% đơn giao xong có đánh giá, lệch về 4–5 sao.
                IF random() < 0.75 THEN
                    r := random();
                    stars := CASE WHEN r < 0.55 THEN 5 WHEN r < 0.85 THEN 4 WHEN r < 0.94 THEN 3 WHEN r < 0.98 THEN 2 ELSE 1 END;
                    INSERT INTO rating (order_id, customer_id, shipper_id, stars, comment, created_at)
                    VALUES (o_id, ci, ship_id, stars,
                            comments[1 + floor(random() * array_length(comments, 1))::int],
                            t_done + ((5 + floor(random()*120)) || ' minutes')::interval);
                END IF;
            ELSE
                INSERT INTO delivery_assignment (id, order_id, shipper_id, status, assigned_at, accepted_at, started_at)
                VALUES (gen_random_uuid(), o_id, ship_id, 'STARTED', t_assign, t_accept, t_start);
                UPDATE orders SET updated_at = t_start WHERE id = o_id;
            END IF;
        END IF;

        IF st = 'CANCELLED' THEN
            t_confirm := o_ts + ((5 + floor(random()*30)) || ' minutes')::interval;
            INSERT INTO status_history (order_id, from_status, to_status, changed_by_user_id, changed_at, note)
            VALUES (o_id, 'PENDING', 'CANCELLED', NULL, t_confirm,
                    (ARRAY['Khách đổi ý','Hết món khách chọn','Không liên lạc được với khách'])[1 + floor(random()*3)::int]);
            UPDATE orders SET updated_at = t_confirm WHERE id = o_id;
        END IF;
    END LOOP;

    -- Đồng bộ chỉ số hồ sơ shipper với dữ liệu vừa sinh.
    UPDATE shipper_profile sp SET
        total_deliveries = (SELECT COUNT(*) FROM delivery_assignment da WHERE da.shipper_id = sp.user_id AND da.status = 'COMPLETED'),
        rating_avg   = COALESCE((SELECT ROUND(AVG(rt.stars)::numeric, 2) FROM rating rt WHERE rt.shipper_id = sp.user_id), 0),
        rating_count = (SELECT COUNT(*) FROM rating rt WHERE rt.shipper_id = sp.user_id);

    -- 2 lần đối soát ~2 tuần trước cho sổ cái đỡ trống: shop trả hoa hồng, shipper nộp tiền COD.
    INSERT INTO shipper_ledger (shipper_id, entry_type, amount, order_id, note, created_by, created_at)
    SELECT shipper_id, 'SETTLEMENT_PAYOUT', -SUM(amount) * 0.6, NULL, 'Đối soát kỳ 1 — shop thanh toán hoa hồng', 'admin', NOW() - INTERVAL '14 days'
      FROM shipper_ledger WHERE entry_type = 'COMMISSION' AND created_at < NOW() - INTERVAL '14 days'
     GROUP BY shipper_id HAVING SUM(amount) > 0;
    INSERT INTO shipper_ledger (shipper_id, entry_type, amount, order_id, note, created_by, created_at)
    SELECT shipper_id, 'SETTLEMENT_DEPOSIT', -SUM(amount) * 0.6, NULL, 'Đối soát kỳ 1 — shipper nộp tiền COD', 'admin', NOW() - INTERVAL '14 days'
      FROM shipper_ledger WHERE entry_type = 'COD_OWED' AND created_at < NOW() - INTERVAL '14 days'
     GROUP BY shipper_id HAVING SUM(amount) < 0;
END $$;
