# Hệ thống Hỗ trợ Quản lý Giao hàng

Khóa luận tốt nghiệp — Đề tài 3.

Hệ thống quản lý giao hàng cho shop online, sử dụng Telegram Mini App + Bot cho khách & shipper, và Web Admin cho chủ shop. Thanh toán COD + VNPay.

## Cấu trúc

- `backend/` — Spring Boot 3 multi-module (8 bounded contexts)
- `frontend/` — Mini App (React + Vite) + Web Admin (React + Vite)
- `infra/` — Docker Compose, Nginx, Postgres init
- `docs/` — Spec (`docs/superpowers/specs/`) + Plans (`docs/superpowers/plans/`) + Thesis chapters (`docs/thesis/`)

## Quick start (dev)

```bash
# 1. Start Postgres
docker compose -f infra/docker-compose.dev.yml up -d

# 2. Build & run backend
cd backend && ./mvnw spring-boot:run -pl app -am -Dspring-boot.run.profiles=dev
```

Backend chạy ở `http://localhost:8080`. Health check: `http://localhost:8080/actuator/health`.

## Tài liệu

- [Design Spec](docs/superpowers/specs/2026-05-19-he-thong-quan-ly-giao-hang-design.md)
- [Implementation Plans](docs/superpowers/plans/)
