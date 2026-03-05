# ДЗ2: Marketplace API (OpenAPI + CRUD)

Эта ветка (`hw2`) — рабочая история выполнения второго домашнего задания: что именно было сделано, в какой последовательности и где это лежит в коде.

## Зачем сделан этот репозиторий

Цель задания — спроектировать и реализовать API маркетплейса в контрактном подходе:
- сначала формализовать контракт (OpenAPI),
- затем реализовать бизнес-логику,
- обеспечить воспроизводимый запуск,
- показать проверяемый результат на E2E и уровне БД.

## Ключевые решения

- **Contract-first**: сначала OpenAPI-спецификация, затем код.
- **REST-ресурсная модель**: `/products`, `/orders`, `/promo-codes`, `/auth`.
- **Stateless**: каждый защищённый запрос самодостаточен (JWT в каждом запросе).
- **Единый формат ошибок**: `error_code`, `message`, `details`.
- **Транзакционные инварианты** для заказов: проверка остатков, резервирование, пересчёты, state transitions.

## Технический стек

- Java 21 + Spring Boot
- OpenAPI + code generation
- PostgreSQL + Flyway
- JWT (access/refresh)
- Роли: `USER`, `SELLER`, `ADMIN`

Порт API по умолчанию в этой ветке: `http://localhost:8081`.

---

## Как выполнялось ДЗ (прозрачная последовательность)

### Этап 1. Спецификация Product CRUD

**Что сделано**
- Описаны endpoint’ы:
  - `POST /products`
  - `GET /products/{id}`
  - `GET /products` (пагинация и фильтрация)
  - `PUT /products/{id}`
  - `DELETE /products/{id}` (мягкое удаление)

**Почему так**
- Это базовый REST CRUD и ожидаемая семантика методов.

**Где смотреть**
- `marketplace-api/src/main/resources/openapi/marketplace.yaml`

### Этап 2. Формализация схем данных

**Что сделано**
- Выделены отдельные схемы: `ProductCreate`, `ProductUpdate`, `ProductResponse`.
- Указаны `required`, `nullable`, `enum`, `format`.
- Добавлены ограничения полей.

**Почему так**
- Разделение create/update/response делает контракт понятнее и снижает риск ошибок интеграции.

**Где смотреть**
- `components/schemas` в `marketplace.yaml`

### Этап 3. Кодогенерация из OpenAPI

**Что сделано**
- Настроен генератор интерфейсов API и моделей.
- Ручные DTO для этих контрактов не используются.

**Почему так**
- Контракт и код не расходятся, сборка воспроизводима одной командой.

**Где смотреть**
- `marketplace-api/pom.xml` (`openapi-generator-maven-plugin`)
- `target/generated-sources/openapi`

### Этап 4. PostgreSQL + миграции + базовый CRUD

**Что сделано**
- Подключён PostgreSQL.
- Схема заведена через Flyway.
- Добавлен индекс `products.status`.
- `DELETE /products/{id}` реализован как `ARCHIVED`.

**Почему так**
- Выполняются требования задания, плюс это удобнее для аудита/восстановления данных.

**Где смотреть**
- `marketplace-api/src/main/resources/db/migration/V1__init.sql`
- `ProductService#deleteProduct`

### Этап 5. Контрактная обработка ошибок

**Что сделано**
- Единая структура ошибок: `error_code`, `message`, `details`.
- Коды ошибок синхронизированы с OpenAPI и реализацией.

**Почему так**
- Клиентам проще обрабатывать ошибки программно и предсказуемо.

**Где смотреть**
- OpenAPI: `ErrorResponse`, `ErrorCode`
- Код: `common/error/*`, `web/error/GlobalExceptionHandler.java`

### Этап 6. Контрактная валидация входных данных

**Что сделано**
- Ограничения зафиксированы в OpenAPI.
- Невалидные запросы отсекаются до бизнес-логики.
- Возвращается `VALIDATION_ERROR` с детализацией по полям.

**Почему так**
- Предсказуемое поведение API и меньше ошибок в доменной логике.

**Где смотреть**
- `marketplace.yaml` (constraints)
- generated contracts с `@Valid`
- `GlobalExceptionHandler`

### Этап 7. Бизнес-логика заказов

**Что сделано**
- `POST /orders`: rate-limit, active-order guard, проверка каталога и stock, резервирование, snapshot цен, promo-расчёт.
- `PUT /orders/{id}`: ownership, state check, возврат старых резервов, резервы новых, пересчёт promo.
- `POST /orders/{id}/cancel`: ownership, допустимые состояния, возврат stock, rollback promo usage.

**Почему так**
- Все инварианты из ТЗ соблюдаются в транзакциях.

**Где смотреть**
- `service/OrderService.java`
- таблицы в `V1__init.sql`

### Этап 8. Логирование API

**Что сделано**
- JSON-лог каждого запроса.
- Пишутся поля: `request_id`, `method`, `endpoint`, `status_code`, `duration_ms`, `user_id`, `timestamp`.
- Для `POST/PUT/DELETE` логируется body с маскированием чувствительных данных.
- `X-Request-Id` возвращается в ответе.

**Где смотреть**
- `web/ApiLoggingFilter.java`

### Этап 9. JWT-авторизация

**Что сделано**
- `/auth/register`, `/auth/login`, `/auth/refresh`.
- Access и refresh токены.
- Ошибки: `TOKEN_INVALID`, `TOKEN_EXPIRED`, `REFRESH_TOKEN_INVALID`.

**Почему так**
- Стандартная модель короткоживущего access и долгоживущего refresh.

**Где смотреть**
- `service/AuthService.java`
- `security/JwtService.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java`

### Этап 10. Ролевая модель доступа

**Что сделано**
- Реализованы роли `USER`, `SELLER`, `ADMIN`.
- Ограничения по матрице доступа.
- `SELLER` управляет только своими товарами через `seller_id`.
- Проверка владения заказом для user-операций.

**Где смотреть**
- `security/SecurityConfig.java`
- `@PreAuthorize` в контроллерах
- ownership checks в сервисах

---

## Как воспроизвести у себя

```bash
cd marketplace-api
./mvnw clean test
docker compose up --build
```

API: `http://localhost:8081`

---

## E2E: рабочий сквозной сценарий

### 1) Регистрация и логин

```bash
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"admin","password":"Strong123","role":"ADMIN"}'
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"seller1","password":"Strong123","role":"SELLER"}'
curl -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"user1","password":"Strong123","role":"USER"}'

SELLER_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login -H 'Content-Type: application/json' -d '{"username":"seller1","password":"Strong123"}' | jq -r .access_token)
USER_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login -H 'Content-Type: application/json' -d '{"username":"user1","password":"Strong123"}' | jq -r .access_token)
```

### 2) Создание товара, промокода, заказа

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

### 3) Проверка состояния БД

```sql
SELECT id, username, role, created_at FROM users;
SELECT id, name, stock, status, seller_id FROM products;
SELECT id, user_id, status, total_amount, discount_amount, promo_code_id FROM orders;
SELECT id, order_id, product_id, quantity, price_at_order FROM order_items;
SELECT id, code, current_uses, max_uses, active FROM promo_codes;
SELECT id, user_id, operation_type, created_at FROM user_operations;
```

---

## Негативные сценарии, которые стоит прогнать

- Невалидный payload → `VALIDATION_ERROR`
- Недостаточный stock → `INSUFFICIENT_STOCK`
- Слишком частое создание/обновление заказа → `ORDER_LIMIT_EXCEEDED`
- Недостаточные права → `ACCESS_DENIED`
- Невалидный/просроченный access token → `TOKEN_INVALID` / `TOKEN_EXPIRED`

---

## Карта кода

- OpenAPI: `marketplace-api/src/main/resources/openapi/marketplace.yaml`
- Миграции: `marketplace-api/src/main/resources/db/migration/V1__init.sql`
- Security/JWT/RBAC: `marketplace-api/src/main/java/com/gumbatali/marketplace/security/*`
- Ошибки: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/error/GlobalExceptionHandler.java`
- Логика заказов: `marketplace-api/src/main/java/com/gumbatali/marketplace/service/OrderService.java`
- Логирование API: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/ApiLoggingFilter.java`
