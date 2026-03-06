# HW2: Marketplace API (OpenAPI + CRUD)

Репозиторий подготовлен под защиту ДЗ2: быстрый запуск, E2E-демо, SQL-проверки, негативные сценарии.

## Стек
- Java 21 + Spring Boot 3
- OpenAPI contract-first + codegen
- PostgreSQL 16 + Flyway
- JWT auth + RBAC (`USER`, `SELLER`, `ADMIN`)

## Что уже реализовано
- `/auth/register`, `/auth/login`, `/auth/refresh`
- `/products` CRUD + soft-delete (`ARCHIVED`)
- `/orders` create/get/update/cancel
- `GET /orders` (пагинация + фильтр)
- `POST /orders/{id}/status` (state machine)
- `/promo-codes` create
- Единый формат ошибок: `error_code`, `message`, `details`
- JSON-логирование API + `X-Request-Id`

---

## 1) Запуск перед защитой

`cd marketplace-api` — перейти в модуль API.
`docker compose down -v` — остановить и удалить контейнеры/volume для чистого старта.
`docker compose up --build` — пересобрать образ и поднять сервисы с логами.

```bash
cd marketplace-api
docker compose down -v
docker compose up --build
```

В отдельном терминале проверь:

`docker compose ps` — показать статус контейнеров и порты.

```bash
cd marketplace-api
docker compose ps
```

Ожидается:
- `marketplace-api` в `Up`
- `marketplace-postgres` в `Up (healthy)`

Swagger/контракт:
- `http://localhost:8081/swagger-ui/index.html`
- `http://localhost:8081/v3/api-docs`

---

## 2) E2E-сценарий

Все команды ниже вставляются **без строк-комментариев `# ...`**.

В начале фиксируем адрес API и создаём уникальную метку запуска. На её основе формируются логины и промокод, чтобы каждый прогон был изолирован и не конфликтовал с предыдущими данными в БД.

```bash
BASE="http://localhost:8081"
SUF=$(date +%s)
SELLER="seller_${SUF}"
USERN="user_${SUF}"
PASS="Strong123"
```

### 2.1 Регистрация

Эти два запроса создают тестовую пару пользователей под текущий прогон: продавца и покупателя.

```bash
curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
-d "{\"username\":\"$SELLER\",\"password\":\"$PASS\",\"role\":\"SELLER\"}"

curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
-d "{\"username\":\"$USERN\",\"password\":\"$PASS\",\"role\":\"USER\"}"
```

### 2.2 Логин и токены (без `jq`)

Сначала получаем JSON-ответы `/auth/login`, затем извлекаем из них `access_token` и сохраняем в переменные для следующих вызовов с `Authorization: Bearer ...`.

```bash
SELLER_LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
-d "{\"username\":\"$SELLER\",\"password\":\"$PASS\"}")

USER_LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
-d "{\"username\":\"$USERN\",\"password\":\"$PASS\"}")

SELLER_TOKEN=$(echo "$SELLER_LOGIN" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
USER_TOKEN=$(echo "$USER_LOGIN" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

### 2.3 Создание товара

Продавец создаёт товар, после чего из ответа сохраняется `PRODUCT_ID` — он потребуется для заказа.

```bash
PROD_RESP=$(curl -s -X POST "$BASE/products" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"iPhone 15","description":"128GB","price":1000.00,"stock":10,"category":"smartphones","status":"ACTIVE"}')

echo "$PROD_RESP"
PRODUCT_ID=$(echo "$PROD_RESP" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
```

### 2.4 Создание промокода

Промокод также делаем уникальным для текущего прогона и создаём его от лица продавца.

```bash
PROMO_CODE="PROMO${SUF}"

PROMO_RESP=$(curl -s -X POST "$BASE/promo-codes" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"code\":\"$PROMO_CODE\",\"discount_type\":\"PERCENTAGE\",\"discount_value\":10,\"min_order_amount\":100,\"max_uses\":100,\"valid_from\":\"2025-01-01T00:00:00Z\",\"valid_until\":\"2030-01-01T00:00:00Z\",\"active\":true}")

echo "$PROMO_RESP"
```

### 2.5 Создание заказа

Покупатель оформляет заказ на созданный товар с применением промокода. Из ответа сохраняем `ORDER_ID` для последующих операций.

```bash
ORDER_RESP=$(curl -s -X POST "$BASE/orders" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"product_id\":\"$PRODUCT_ID\",\"quantity\":1}],\"promo_code\":\"$PROMO_CODE\"}")

echo "$ORDER_RESP"
ORDER_ID=$(echo "$ORDER_RESP" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
```

### 2.6 Проверка бизнес-операций

Далее последовательно проверяем, что заказ виден в списке, что переход статуса работает по правилам, и что отмена возвращает систему в корректное состояние.

```bash
curl -s -X GET "$BASE/orders?page=0&size=20" \
  -H "Authorization: Bearer $USER_TOKEN"

curl -s -X POST "$BASE/orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"next_status":"PAYMENT_PENDING"}'

curl -s -X POST "$BASE/orders/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $USER_TOKEN"
```

---

## 3) SQL-проверки в БД (обязательно на защите)

1. `users` — проверить, что пользователи созданы с нужными ролями.
2. `products` — проверить товар, статус и остаток.
3. `orders` — проверить статус, итоговую сумму и скидку.
4. `order_items` — проверить снапшот цены и количество.
5. `promo_codes` — проверить `current_uses` после создания/отмены.
6. `user_operations` — проверить фиксацию операций rate-limit.

```bash
docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT username, role, created_at FROM users ORDER BY created_at DESC LIMIT 5;"
docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT id, name, stock, status, seller_id FROM products ORDER BY created_at DESC LIMIT 5;"
docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT id, user_id, status, total_amount, discount_amount, promo_code_id FROM orders ORDER BY created_at DESC LIMIT 5;"
docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT order_id, product_id, quantity, price_at_order FROM order_items ORDER BY id DESC LIMIT 10;"
docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT code, current_uses, max_uses, active FROM promo_codes ORDER BY id DESC LIMIT 5;"
docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT user_id, operation_type, created_at FROM user_operations ORDER BY created_at DESC LIMIT 10;"
```

---

## 4) Альтернативные сценарии (быстрые команды)

1. Валидация payload:
```bash
curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"username":"a","password":"1"}'
```
Ожидается: `VALIDATION_ERROR`.

2. Нет токена:
```bash
curl -s -X POST "$BASE/orders" -H 'Content-Type: application/json' -d '{"items":[]}'
```
Ожидается: `TOKEN_INVALID`.

3. Недостаточно прав (`USER` -> create product):
```bash
curl -s -X POST "$BASE/products" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"X","price":1.0,"stock":1,"category":"c","status":"ACTIVE"}'
```
Ожидается: `ACCESS_DENIED`.

4. Недопустимый переход статуса:
```bash
curl -s -X POST "$BASE/orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"next_status":"SHIPPED"}'
```
Ожидается: `INVALID_STATE_TRANSITION`.

---

## 5) Что открыть в коде на разборе
- Контракт: `marketplace-api/src/main/resources/openapi/marketplace.yaml`
- Миграции: `marketplace-api/src/main/resources/db/migration/V1__init.sql`
- Заказы: `marketplace-api/src/main/java/com/gumbatali/marketplace/service/OrderService.java`
- Security: `marketplace-api/src/main/java/com/gumbatali/marketplace/security/`
- Ошибки: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/error/GlobalExceptionHandler.java`
- Логирование: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/ApiLoggingFilter.java`

---

## 6) Короткие ответы для теории
- REST stateless: сервер не хранит сессию, каждый запрос самодостаточен (JWT).
- Contract-first: сначала OpenAPI, потом код; снижает рассинхрон между клиентом и сервером.
- Идемпотентность: `GET/PUT/DELETE` по смыслу идемпотентны, `POST` обычно нет.
- Почему транзакции в заказах: stock/order/items/promo должны изменяться атомарно.
- Почему soft-delete: аудит и восстановляемость, вместо физического удаления.
