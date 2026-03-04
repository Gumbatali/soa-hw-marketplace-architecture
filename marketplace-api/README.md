# Marketplace API (Homework #2)

Реализация ДЗ `Marketplace: OpenAPI + CRUD` на `Spring Boot + PostgreSQL + Flyway + OpenAPI codegen`.

## Что реализовано

- OpenAPI-first контракт в `src/main/resources/openapi/marketplace.yaml`.
- Генерация controller contracts + DTO из OpenAPI (ручные DTO не используются).
- PostgreSQL + Flyway migrations, включая индекс `products.status` и soft-delete через статус `ARCHIVED`.
- JWT auth (`/auth/register`, `/auth/login`, `/auth/refresh`) с access/refresh токенами.
- RBAC: `USER`, `SELLER`, `ADMIN`.
- Бизнес-логика заказов: rate limit, active-order guard, stock reservation, snapshot prices, promo checks, cancel/update rules.
- Единый контракт ошибок `ErrorResponse` (`error_code`, `message`, `details`).
- JSON API logging + `X-Request-Id`.

## Запуск локально

### 1. Проверка и сборка

```bash
./mvnw clean test
```

### 2. Запуск через Docker Compose

```bash
docker compose up --build
```

Сервис будет доступен на `http://localhost:8080`.

## OpenAPI

Спецификация: `src/main/resources/openapi/marketplace.yaml`

Кодогенерация выполняется автоматически при сборке (`generate-sources` фазой Maven).

## Быстрый E2E-сценарий

### 1) Зарегистрировать пользователей

```bash
curl -s -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Strong123","role":"ADMIN"}'

curl -s -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"seller1","password":"Strong123","role":"SELLER"}'

curl -s -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"user1","password":"Strong123","role":"USER"}'
```

### 2) Логин и получение токена

```bash
SELLER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"seller1","password":"Strong123"}' | jq -r .access_token)

USER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user1","password":"Strong123"}' | jq -r .access_token)
```

### 3) Создать товар (SELLER)

```bash
PRODUCT_ID=$(curl -s -X POST http://localhost:8080/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Laptop","description":"16GB RAM","price":1200.00,"stock":10,"category":"electronics","status":"ACTIVE"}' | jq -r .id)
```

### 4) Создать промокод

```bash
curl -s -X POST http://localhost:8080/promo-codes \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"PROMO10","discount_type":"PERCENTAGE","discount_value":10,"min_order_amount":100,"max_uses":100,"valid_from":"2025-01-01T00:00:00Z","valid_until":"2030-01-01T00:00:00Z","active":true}'
```

### 5) Создать заказ (USER)

```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"product_id\":\"$PRODUCT_ID\",\"quantity\":2}],\"promo_code\":\"PROMO10\"}"
```

## SQL-проверки для защиты

```sql
SELECT id, username, role, created_at FROM users;
SELECT id, name, stock, status, seller_id FROM products;
SELECT id, user_id, status, total_amount, discount_amount, promo_code_id FROM orders;
SELECT id, order_id, product_id, quantity, price_at_order FROM order_items;
SELECT id, code, current_uses, max_uses, active FROM promo_codes;
SELECT id, user_id, operation_type, created_at FROM user_operations;
```

## Error codes

Поддерживаемые коды ошибок соответствуют ТЗ:
`PRODUCT_NOT_FOUND`, `PRODUCT_INACTIVE`, `ORDER_NOT_FOUND`, `ORDER_LIMIT_EXCEEDED`, `ORDER_HAS_ACTIVE`, `INVALID_STATE_TRANSITION`, `INSUFFICIENT_STOCK`, `PROMO_CODE_INVALID`, `PROMO_CODE_MIN_AMOUNT`, `ORDER_OWNERSHIP_VIOLATION`, `VALIDATION_ERROR`, `TOKEN_EXPIRED`, `TOKEN_INVALID`, `REFRESH_TOKEN_INVALID`, `ACCESS_DENIED`.

