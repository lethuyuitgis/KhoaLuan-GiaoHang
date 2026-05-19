# Backend (Spring Boot 3 Multi-module)

## Modules

| Module | Trách nhiệm |
|---|---|
| `shared` | Base entity, exceptions, common utils |
| `auth` | Telegram user, role, JWT |
| `order` | Product, order, order_item |
| `delivery` | Assignment, location_ping |
| `payment` | VNPay integration |
| `notification` | Event listener → Telegram + WS |
| `bot` | Telegram bot webhook & handlers |
| `miniapp` | REST + WS cho Mini App |
| `webadmin` | REST + WS cho Web Admin |
| `app` | Boot module (Application.java) |

## Build

```bash
./mvnw clean verify
```

## Run dev

```bash
./mvnw spring-boot:run -pl app -am -Dspring-boot.run.profiles=dev
```
