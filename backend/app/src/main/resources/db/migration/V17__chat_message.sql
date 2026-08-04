-- V17__chat_message.sql — Tin nhắn chat ẩn danh khách ↔ shipper (qua Bot).
--
-- Owns: chat_message
-- Used by: bot (relay 2 chiều qua BotSender), delivery (ChatMessageService lưu +
--          truy vấn), webadmin (audit qua GET /api/admin/orders/{id}/chat).
--
-- Bot làm trung gian: không bên nào thấy Telegram account/SĐT của bên kia. Mỗi
-- dòng ghi vai trò người gửi (CUSTOMER/SHIPPER), gắn theo assignment đang giao.

CREATE TABLE chat_message (
    id              BIGSERIAL PRIMARY KEY,
    assignment_id   UUID NOT NULL REFERENCES delivery_assignment(id) ON DELETE CASCADE,
    sender_role     VARCHAR(16) NOT NULL,   -- CUSTOMER | SHIPPER
    sender_user_id  BIGINT NOT NULL,        -- telegram user id (nội bộ, không lộ ra bên kia)
    body            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_message_assignment ON chat_message(assignment_id, created_at);
