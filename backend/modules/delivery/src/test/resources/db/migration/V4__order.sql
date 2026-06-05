-- V4__order.sql — order module tables
-- Owns: product, orders, order_item, status_history
-- Used by: miniapp (customer CRUD), webadmin (admin CRUD), delivery (assignment), payment (link to order)

CREATE TABLE product (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    price           NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    image_url       TEXT,
    stock           INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_active ON product(is_active) WHERE is_active = TRUE;

-- Note: "order" is a SQL reserved word — use "orders" plural to avoid quoting hell.
-- Entity will be @Entity Order @Table(name = "orders")
CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(32) NOT NULL UNIQUE,
    customer_id         BIGINT NOT NULL REFERENCES telegram_user(id),
    customer_name       VARCHAR(128),
    customer_phone      VARCHAR(32),
    pickup_lat          NUMERIC(10, 7) NOT NULL,
    pickup_lng          NUMERIC(10, 7) NOT NULL,
    delivery_address    TEXT NOT NULL,
    delivery_lat        NUMERIC(10, 7) NOT NULL,
    delivery_lng        NUMERIC(10, 7) NOT NULL,
    distance_km         NUMERIC(8, 3) NOT NULL CHECK (distance_km >= 0),
    subtotal            NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    delivery_fee        NUMERIC(12, 2) NOT NULL CHECK (delivery_fee >= 0),
    total               NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    payment_method      VARCHAR(16) NOT NULL,            -- COD | VNPAY
    payment_status      VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING | SUCCESS | FAILED | REFUNDED
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    note                TEXT,
    version             INT NOT NULL DEFAULT 0,          -- @Version for optimistic lock
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);

CREATE TABLE order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES product(id),
    quantity        INT NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),  -- snapshot từ product.price tại thời điểm tạo order
    subtotal        NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX idx_order_item_order ON order_item(order_id);

CREATE TABLE status_history (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status         VARCHAR(16),                     -- nullable (initial create)
    to_status           VARCHAR(16) NOT NULL,
    changed_by_user_id  BIGINT,                          -- nullable (system events)
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    note                TEXT
);

CREATE INDEX idx_status_history_order ON status_history(order_id, changed_at);
