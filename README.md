# Homework #2: Marketplace API (OpenAPI + CRUD)

Этот branch (`hw2`) подготовлен под защиту ДЗ2: контрактный REST API маркетплейса с валидацией, бизнес-логикой заказов, PostgreSQL/Flyway и JWT/RBAC.

## Что проверяется на защите

- запуск системы через Docker
- E2E-запросы через всю цепочку
- проверка факта записи данных через `SELECT` в БД
- альтернативные/некорректные сценарии
- объяснение архитектурных решений в коде

## Быстрый запуск

```bash
cd marketplace-api
./mvnw clean test

docker compose up --build
```

API: `http://localhost:8081`

## Покрытие требований (чек-лист под баллы)

| Требование | Статус | Где смотреть |
|---|---|---|
| OpenAPI CRUD для Product | done | `marketplace-api/src/main/resources/openapi/marketplace.yaml` |
| Product схемы (`ProductCreate`/`ProductUpdate`/`ProductResponse`) | done | OpenAPI components/schemas |
| Codegen из OpenAPI, без ручных DTO | done | `marketplace-api/pom.xml` + generated APIs/models |
| PostgreSQL + Flyway + soft delete + index(status) | done | `db/migration/V1__init.sql` + `DELETE /products/{id}` |
| Единый контракт ошибок | done | `ErrorResponse` в OpenAPI + `GlobalExceptionHandler` |
| Контрактная валидация | done | OpenAPI constraints + `@Valid` generated contracts |
| Сложная логика заказов | done | `OrderService` (create/update/cancel, stock/promo/rate-limit) |
| JSON API logging + `X-Request-Id` | done | `ApiLoggingFilter` |
| JWT access/refresh | done | `/auth/*`, `JwtService`, `JwtAuthenticationFilter` |
| RBAC USER/SELLER/ADMIN | done | `SecurityConfig`, `@PreAuthorize`, ownership checks |

## E2E-сценарий для демонстрации

### 1) Регистрация и логин

```bash
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"admin","password":"Strong123","role":"ADMIN"}'
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"seller1","password":"Strong123","role":"SELLER"}'
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"user1","password":"Strong123","role":"USER"}'

SELLER_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login -H 'Content-Type: application/json' -d '{"username":"seller1","password":"Strong123"}' | jq -r .access_token)
USER_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login -H 'Content-Type: application/json' -d '{"username":"user1","password":"Strong123"}' | jq -r .access_token)
```

### 2) Продукт + промокод + заказ

```bash
PRODUCT_ID=$(curl -s -X POST http://localhost:8081/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Laptop","description":"16GB RAM","price":1200.00,"stock":10,"category":"electronics","status":"ACTIVE"}' | jq -r .id)

curl -X POST http://localhost:8081/promo-codes \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"PROMO10","discount_type":"PERCENTAGE","discount_value":10,"min_order_amount":100,"max_uses":100,"valid_from":"2025-01-01T00:00:00Z","valid_until":"2030-01-01T00:00:00Z","active":true}'

curl -X POST http://localhost:8081/orders \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"product_id\":\"$PRODUCT_ID\",\"quantity\":2}],\"promo_code\":\"PROMO10\"}"
```

### 3) SQL-подтверждение в БД

```sql
SELECT id, username, role, created_at FROM users;
SELECT id, name, stock, status, seller_id FROM products;
SELECT id, user_id, status, total_amount, discount_amount, promo_code_id FROM orders;
SELECT id, order_id, product_id, quantity, price_at_order FROM order_items;
SELECT id, code, current_uses, max_uses, active FROM promo_codes;
SELECT id, user_id, operation_type, created_at FROM user_operations;
```

## Что важно уметь объяснить (коротко и по сути)

- API строится вокруг **ресурсов**, а не вокруг действий (`/products`, `/orders`, `/promo-codes`).
- REST здесь **stateless**: каждый запрос самодостаточен (JWT в каждом request).
- CRUD маппится на HTTP-методы: `POST/GET/PUT/DELETE`; `PUT` и `DELETE` идемпотентны.
- OpenAPI используется как **контракт** между клиентом и сервером (contract-first).
- Codegen снижает дрейф контракта: handler interfaces + DTO генерируются из spec.
- Ошибки и валидация контрактны: предсказуемые `error_code` и структурированный `details`.
- Синхронные вызовы просты, но увеличивают связанность и чувствительность к latency/timeout.
- Для межсервисных высоконагруженных сценариев часто выгоднее event-driven/async обмен.

## Частые ошибки на защите (и как не попасть)

- Показывать только API-ответы без `SELECT` в БД.
- Объяснять «как будто удаляем», но забыть, что `DELETE /products/{id}` здесь soft-delete (`ARCHIVED`).
- Путать `TOKEN_INVALID`, `TOKEN_EXPIRED`, `REFRESH_TOKEN_INVALID`.
- Не показать, что invalid input отсекается валидацией до бизнес-логики.
- Не уметь объяснить, почему выбран contract-first и зачем нужны idempotent операции.

## Навигация по коду

- OpenAPI: `marketplace-api/src/main/resources/openapi/marketplace.yaml`
- Миграции: `marketplace-api/src/main/resources/db/migration/V1__init.sql`
- Security/JWT/RBAC: `marketplace-api/src/main/java/com/gumbatali/marketplace/security/*`
- Ошибки: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/error/GlobalExceptionHandler.java`
- Бизнес-логика заказов: `marketplace-api/src/main/java/com/gumbatali/marketplace/service/OrderService.java`
- Логирование API: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/ApiLoggingFilter.java`
