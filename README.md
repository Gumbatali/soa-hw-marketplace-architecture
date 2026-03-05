# Homework #2: Marketplace (OpenAPI + CRUD)

Этот branch (`hw2`) оформлен под защиту ДЗ2 в ожидаемой логике проверки: сначала запуск/E2E/SQL, затем разбор реализации по пунктам 1–10.

## 0) Цель и стек

**Цель:** спроектировать и реализовать контрактный REST API маркетплейса с бизнес-логикой заказов.

**Стек реализации:**
- Java 21 + Spring Boot
- OpenAPI (contract-first) + code generation
- PostgreSQL + Flyway
- JWT (access/refresh)
- Role-based access (`USER`, `SELLER`, `ADMIN`)

**Порт API в этом branch:** `http://localhost:8081`

## 1) Порядок защиты (как показывать за 20 минут)

### Шаг 1. Поднять систему

```bash
cd marketplace-api
./mvnw clean test
docker compose up --build
```

### Шаг 2. Показать E2E-цепочку

1. Регистрация/логин пользователей
2. Создание товара (`SELLER`)
3. Создание промокода (`SELLER`)
4. Создание заказа (`USER`)
5. (опционально) update/cancel заказа

### Шаг 3. Показать данные в БД через SELECT

```sql
SELECT id, username, role, created_at FROM users;
SELECT id, name, stock, status, seller_id FROM products;
SELECT id, user_id, status, total_amount, discount_amount, promo_code_id FROM orders;
SELECT id, order_id, product_id, quantity, price_at_order FROM order_items;
SELECT id, code, current_uses, max_uses, active FROM promo_codes;
SELECT id, user_id, operation_type, created_at FROM user_operations;
```

### Шаг 4. Альтернативные/ошибочные сценарии

- невалидные payload → `VALIDATION_ERROR`
- недостаток stock → `INSUFFICIENT_STOCK`
- повторное создание заказа слишком часто → `ORDER_LIMIT_EXCEEDED`
- запрещённые операции по роли → `ACCESS_DENIED`
- неверный token → `TOKEN_INVALID` / `TOKEN_EXPIRED`

---

## 2) Как выполнено ДЗ по пунктам требований (1 → 10)

### 1. OpenAPI CRUD для Product

**Сделано:**
- `POST /products`
- `GET /products/{id}`
- `GET /products` (page/size/status/category)
- `PUT /products/{id}`
- `DELETE /products/{id}` (soft delete → `ARCHIVED`)

**Где:** `marketplace-api/src/main/resources/openapi/marketplace.yaml`

### 2. Схемы данных в OpenAPI

**Сделано:**
- `ProductCreate`, `ProductUpdate`, `ProductResponse`
- `required`, `nullable`, `enum`
- `format` для `decimal`/`date-time`
- ограничения длины/минимумов

**Где:** `components/schemas` в `marketplace.yaml`

### 3. Code generation из OpenAPI

**Сделано:**
- генерация интерфейсов контроллеров и DTO одной сборкой Maven
- ручные request/response DTO не используются

**Где:**
- `marketplace-api/pom.xml` (`openapi-generator-maven-plugin`)
- generated sources в `target/generated-sources/openapi`

### 4. PostgreSQL + базовый CRUD

**Сделано:**
- PostgreSQL подключён
- миграции через Flyway
- индекс `products.status`
- auto `created_at/updated_at`
- soft delete продукта через статус `ARCHIVED`

**Где:**
- `marketplace-api/src/main/resources/db/migration/V1__init.sql`
- `ProductService#deleteProduct`

### 5. Контрактная обработка ошибок

**Сделано:**
- единый формат: `error_code`, `message`, `details`
- коды ошибок из ТЗ заведены в контракте и в обработчике

**Где:**
- OpenAPI: `ErrorResponse` + `ErrorCode`
- код: `common/error/*`, `web/error/GlobalExceptionHandler.java`

### 6. Контрактная валидация входных данных

**Сделано:**
- ограничения полей заданы в OpenAPI и применяются до бизнес-логики
- при нарушениях возвращается `VALIDATION_ERROR` + детализация по полям

**Где:**
- `marketplace.yaml` (constraints)
- generated `@Valid` contracts
- `GlobalExceptionHandler`

### 7. Сложная бизнес-логика заказов

**Сделано:**
- create/update/cancel заказа по заданным инвариантам
- rate limit через `user_operations`
- active order guard
- stock check + reserve в транзакции
- snapshot цен в `order_items.price_at_order`
- promo logic + пересчёт + rollback uses при отмене/условиях
- state transition checks

**Где:**
- `service/OrderService.java`
- схема таблиц в `V1__init.sql`

### 8. Логирование API

**Сделано:**
- JSON-лог на каждый запрос
- `request_id`, `method`, `endpoint`, `status_code`, `duration_ms`, `user_id`, `timestamp`
- для mutating запросов логируется body (с маскированием чувствительных полей)
- `X-Request-Id` в ответе

**Где:** `web/ApiLoggingFilter.java`

### 9. JWT-авторизация

**Сделано:**
- `/auth/register`, `/auth/login`, `/auth/refresh`
- access + refresh token
- `TOKEN_INVALID`, `TOKEN_EXPIRED`, `REFRESH_TOKEN_INVALID`

**Где:**
- `service/AuthService.java`
- `security/JwtService.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java`

### 10. Ролевая модель доступа

**Сделано:**
- роли: `USER`, `SELLER`, `ADMIN`
- ограничения по матрице доступа
- seller управляет только своими товарами (`seller_id`)
- ownership checks по заказам

**Где:**
- `security/SecurityConfig.java`
- `@PreAuthorize` в контроллерах
- ownership-проверки в сервисах

---

## 3) E2E-команды (готовый сценарий для демонстрации)

### Регистрация + логин

```bash
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"admin","password":"Strong123","role":"ADMIN"}'
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"seller1","password":"Strong123","role":"SELLER"}'
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"user1","password":"Strong123","role":"USER"}'

SELLER_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login -H 'Content-Type: application/json' -d '{"username":"seller1","password":"Strong123"}' | jq -r .access_token)
USER_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login -H 'Content-Type: application/json' -d '{"username":"user1","password":"Strong123"}' | jq -r .access_token)
```

### Product + Promo + Order

```bash
PRODUCT_ID=$(curl -s -X POST http://localhost:8081/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Laptop","description":"16GB RAM","price":1200.00,"stock":10,"category":"electronics","status":"ACTIVE"}' | jq -r .id)

curl -X POST http://localhost:8081/promo-codes \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"PROMO10","discount_type":"PERCENTAGE","discount_value":10,"min_order_amount":100,"max_uses":100,"valid_from":"2025-01-01T00:00:00Z","valid_until":"2030-01-01T00:00:00Z","active":true}'

ORDER_ID=$(curl -s -X POST http://localhost:8081/orders \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"product_id\":\"$PRODUCT_ID\",\"quantity\":2}],\"promo_code\":\"PROMO10\"}" | jq -r .id)
```

---

## 4) Теоретические акценты (кратко, что проговорить на защите)

- REST — архитектурный стиль: важно соблюдать семантику методов/кодов, а не просто «HTTP + JSON».
- Stateless упрощает масштабирование: сервер не хранит сессию между запросами.
- Идемпотентность (`PUT`, `DELETE`) снижает риски при ретраях.
- Contract-first (OpenAPI) уменьшает расхождения между клиентом и сервером.
- Синхронные вызовы удобны, но увеличивают связанность и чувствительность к задержкам/отказам.
- Для high-load inter-service взаимодействий часто нужны async/event-driven паттерны.

---

## 5) Навигация по коду

- OpenAPI контракт: `marketplace-api/src/main/resources/openapi/marketplace.yaml`
- Миграции: `marketplace-api/src/main/resources/db/migration/V1__init.sql`
- JWT/Security/RBAC: `marketplace-api/src/main/java/com/gumbatali/marketplace/security/*`
- Ошибки: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/error/GlobalExceptionHandler.java`
- Бизнес-логика заказов: `marketplace-api/src/main/java/com/gumbatali/marketplace/service/OrderService.java`
- API logging: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/ApiLoggingFilter.java`
