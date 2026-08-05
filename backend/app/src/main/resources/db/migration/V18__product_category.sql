-- V18 — product category (thay cho mapping giả id % 3 phía miniapp)
--       + ảnh minh họa thật từ Wikimedia Commons cho 10 sản phẩm demo
--       (thay placehold.co chữ trắng nền màu).

ALTER TABLE product ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'food';
ALTER TABLE product ADD CONSTRAINT product_category_check
    CHECK (category IN ('food', 'drink', 'dessert'));

-- Gán danh mục đúng cho dữ liệu seed (V11). Sản phẩm tạo sau mặc định 'food'.
UPDATE product SET category = 'drink'   WHERE name IN ('Trà sữa trân châu', 'Cà phê sữa đá');
UPDATE product SET category = 'dessert' WHERE name IN ('Chè bưởi');

-- Ảnh thật (Wikimedia Commons, giấy phép tự do, URL thumb ổn định).
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/96/Pho-Beef-Noodle-Soup-2008.jpg/960px-Pho-Beef-Noodle-Soup-2008.jpg'
    WHERE name = 'Phở bò tái';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/7/7f/Bun-cha-hanoi.jpg/960px-Bun-cha-hanoi.jpg'
    WHERE name = 'Bún chả Hà Nội';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/B%C3%A1nh_mi_Sandwich%28Takadanobaba%29IMG_20220215_104413_02.jpg/960px-B%C3%A1nh_mi_Sandwich%28Takadanobaba%29IMG_20220215_104413_02.jpg'
    WHERE name = 'Bánh mì pate';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/0b/C%C6%A1m_g%C3%A0_chi%C3%AAn_%E1%BB%9F_%C4%90%C3%B4ng_H%C3%A0_n%C4%83m_2017_%282%29.jpg/960px-C%C6%A1m_g%C3%A0_chi%C3%AAn_%E1%BB%9F_%C4%90%C3%B4ng_H%C3%A0_n%C4%83m_2017_%282%29.jpg'
    WHERE name = 'Cơm gà xối mỡ';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Bun-Bo-Hue-from-Huong-Giang-2011.jpg/960px-Bun-Bo-Hue-from-Huong-Giang-2011.jpg'
    WHERE name = 'Bún bò Huế';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/Pearl_Milk_Tea_in_Chun_Shui_Tang_%28cropped%29.jpg/960px-Pearl_Milk_Tea_in_Chun_Shui_Tang_%28cropped%29.jpg'
    WHERE name = 'Trà sữa trân châu';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/bb/Vietnamese_iced_coffee_-_Jan_31%2C_2018.jpg/960px-Vietnamese_iced_coffee_-_Jan_31%2C_2018.jpg'
    WHERE name = 'Cà phê sữa đá';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/5/51/Vietnamese_fried_spring_rolls_in_Ho_Chi_Minh_City%2C_Vietnam.jpg/960px-Vietnamese_fried_spring_rolls_in_Ho_Chi_Minh_City%2C_Vietnam.jpg'
    WHERE name = 'Nem rán Hà Nội';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Ch%C3%A8_b%C3%A0_ba.jpg/960px-Ch%C3%A8_b%C3%A0_ba.jpg'
    WHERE name = 'Chè bưởi';
UPDATE product SET image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/B%C3%A1nh_x%C3%A8o_1.jpg/960px-B%C3%A1nh_x%C3%A8o_1.jpg'
    WHERE name = 'Bánh xèo miền Tây';
