# HW2: Marketplace API (OpenAPI + CRUD)

Этот репозиторий подготовлен для защиты ДЗ2.
Главная цель: показать, что API поднимается, проходит E2E-цепочку и данные реально пишутся в PostgreSQL.

## Что сделано
- Auth: `/auth/register`, `/auth/login`, `/auth/refresh`
- Products: CRUD + soft-delete (`ARCHIVED`)
- Orders: create/get/update/cancel + список заказов
- Promo codes: create
- RBAC: `USER`, `SELLER`, `ADMIN`
- Единый формат ошибок: `error_code`, `message`, `details`
- OpenAPI contract-first + code generation

## Технологии
- Java 21 + Spring Boot 3
- PostgreSQL 16 + Flyway
- OpenAPI 3 + openapi-generator
- JWT + Spring Security

## 1) Запуск перед демонстрацией

```bash
# Переходим в модуль с API
cd marketplace-api

# Полностью очищаем прошлый запуск (контейнеры + volume)
docker compose down -v

# Поднимаем систему и сразу видим логи
# (на защите это важно показать)
docker compose up --build
```

В новом терминале:

```bash
# Проверяем, что оба сервиса живы
cd marketplace-api
docker compose ps
```

Ожидается:
- `marketplace-api` -> `Up`
- `marketplace-postgres` -> `Up (healthy)`

Полезные ссылки:
- Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

---

## 2) E2E-сценарий (как показывать ассистенту)

### 2.0 Базовые переменные

```bash
# Адрес API и тестовые данные для демонстрации
BASE="http://localhost:8081"
SELLER="Drugdiller"
USERN="pmishnik"
PASS="iamloser"
PROMO_CODE="DSBABETTER"
```

### 2.1 Регистрация двух пользователей

```bash
# Регистрируем продавца
curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
-d "{\"username\":\"$SELLER\",\"password\":\"$PASS\",\"role\":\"SELLER\"}"

# Регистрируем покупателя
curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
-d "{\"username\":\"$USERN\",\"password\":\"$PASS\",\"role\":\"USER\"}"
```

Если запускаешь повторно и видишь `Username already exists`, это нормально: пользователь уже создан.

### 2.2 Логин и получение токенов (простой вариант)

```bash
# Логин продавца (в ответе будет access_token)
curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
-d "{\"username\":\"$SELLER\",\"password\":\"$PASS\"}"

# Логин покупателя (в ответе будет access_token)
curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
-d "{\"username\":\"$USERN\",\"password\":\"$PASS\"}"
```

Скопируй `access_token` из ответа и вставь:

```bash
# Вставь токены из ответов /auth/login
SELLER_TOKEN="<вставь access_token продавца>"
USER_TOKEN="<вставь access_token покупателя>"
```

### 2.3 Создание товара от лица продавца

```bash
# Создаем товар
curl -s -X POST "$BASE/products" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"iPhone 15","description":"128GB","price":1000.00,"stock":10,"category":"smartphones","status":"ACTIVE"}'
```

Скопируй `id` товара из ответа и сохрани:

```bash
# Вставь id товара из ответа предыдущей команды
PRODUCT_ID="<вставь id товара>"
```

### 2.4 Создание промокода от лица продавца

```bash
# Создаем промокод, который применим к заказу
curl -s -X POST "$BASE/promo-codes" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"code\":\"$PROMO_CODE\",\"discount_type\":\"PERCENTAGE\",\"discount_value\":10,\"min_order_amount\":100,\"max_uses\":100,\"valid_from\":\"2025-01-01T00:00:00Z\",\"valid_until\":\"2030-01-01T00:00:00Z\",\"active\":true}"
```

### 2.5 Создание заказа от лица покупателя

```bash
# Покупатель оформляет заказ на созданный товар
curl -s -X POST "$BASE/orders" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"product_id\":\"$PRODUCT_ID\",\"quantity\":1}],\"promo_code\":\"$PROMO_CODE\"}"
```

Скопируй `id` заказа из ответа:

```bash
# Вставь id заказа из ответа
ORDER_ID="<вставь id заказа>"
```

### 2.6 Проверка бизнес-операций (список -> статус -> отмена)

```bash
# Проверяем, что заказ появился в списке
curl -s -X GET "$BASE/orders?page=0&size=20" \
  -H "Authorization: Bearer $USER_TOKEN"

# Переводим заказ в PAYMENT_PENDING
curl -s -X POST "$BASE/orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"next_status":"PAYMENT_PENDING"}'

# Отменяем заказ (должен перейти в CANCELED)
curl -s -X POST "$BASE/orders/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $USER_TOKEN"
```

---

## 3) SQL-проверки в БД (обязательная часть на защите)

```bash
# Пользователи и роли

docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT username, role, created_at FROM users ORDER BY created_at DESC LIMIT 5;"

# Товары, остатки, статусы

docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT id, name, stock, status, seller_id FROM products ORDER BY created_at DESC LIMIT 5;"

# Заказы и суммы

docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT id, user_id, status, total_amount, discount_amount, promo_code_id FROM orders ORDER BY created_at DESC LIMIT 5;"

# Позиции заказа и snapshot цены

docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT order_id, product_id, quantity, price_at_order FROM order_items ORDER BY id DESC LIMIT 10;"

# Использование промокодов

docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT code, current_uses, max_uses, active FROM promo_codes ORDER BY id DESC LIMIT 5;"

# Фиксация user operations (rate limit)

docker exec marketplace-postgres psql -U marketplace -d marketplace -c "SELECT user_id, operation_type, created_at FROM user_operations ORDER BY created_at DESC LIMIT 10;"
```

---

## 4) Быстрые негативные сценарии

```bash
# 1) Ошибка валидации
curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"username":"a","password":"1"}'

# 2) Нет токена
curl -s -X POST "$BASE/orders" -H 'Content-Type: application/json' -d '{"items":[]}'

# 3) У USER нет права создавать товар
curl -s -X POST "$BASE/products" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"X","price":1.0,"stock":1,"category":"c","status":"ACTIVE"}'

# 4) Недопустимый переход статуса
curl -s -X POST "$BASE/orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"next_status":"SHIPPED"}'
```

---

## 5) Что открыть в коде на разборе
- Контракт: `marketplace-api/src/main/resources/openapi/marketplace.yaml`
- Миграции: `marketplace-api/src/main/resources/db/migration/V1__init.sql`
- Логика заказов: `marketplace-api/src/main/java/com/gumbatali/marketplace/service/OrderService.java`
- Безопасность: `marketplace-api/src/main/java/com/gumbatali/marketplace/security/`
- Ошибки: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/error/GlobalExceptionHandler.java`
- Логирование: `marketplace-api/src/main/java/com/gumbatali/marketplace/web/ApiLoggingFilter.java`
