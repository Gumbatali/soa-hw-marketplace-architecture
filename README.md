# Отчет по домашнему заданию №2: Marketplace API

## Общая информация
Этот репозиторий фиксирует, как я реализовал ДЗ2 по теме `Marketplace: OpenAPI + CRUD`.

Цель работы:
- спроектировать API в подходе `Contract First`;
- реализовать CRUD для товаров и бизнес-логику заказов;
- сделать проект воспроизводимым для запуска и E2E-проверки;
- показать проверяемый результат не только через API, но и через `SELECT` в БД.

Основная реализация находится в модуле [`marketplace-api`](marketplace-api).

## Технологический стек
- Язык: `Java 21`
- Фреймворк: `Spring Boot 3`
- Контракты: `OpenAPI 3.0.3`
- Codegen: `openapi-generator-maven-plugin`
- БД: `PostgreSQL 16` (через Docker Compose)
- Миграции: `Flyway`
- Безопасность: `Spring Security + JWT (access/refresh)`
- Тесты: `JUnit 5 + Mockito`

## Как я выполнял задание

### Шаг 1. Зафиксировал контракт (Contract First)
Сначала описал API в `marketplace.yaml`, а уже потом писал реализацию.

Что включено в контракт:
- `/auth/register`, `/auth/login`, `/auth/refresh`
- `/products` (`POST`, `GET`), `/products/{id}` (`GET`, `PUT`, `DELETE` soft-delete)
- `/orders` (`POST`, `GET`), `/orders/{id}` (`GET`, `PUT`), `/orders/{id}/cancel` (`POST`)
- `/orders/{id}/status` (`POST`) — дополнительный функционал
- `/promo-codes` (`POST`)

Файл: [`marketplace.yaml`](marketplace-api/src/main/resources/openapi/marketplace.yaml)

### Шаг 2. Настроил кодогенерацию
Код контроллерных интерфейсов и DTO генерируется на фазе `generate-sources`.
Ручные DTO для контрактов не использую.

Конфигурация: [`pom.xml`](marketplace-api/pom.xml)

### Шаг 3. Поднял БД и миграции
Схема создается Flyway-миграциями.

Ключевые моменты:
- таблицы: `users`, `products`, `orders`, `order_items`, `promo_codes`, `user_operations`, `refresh_tokens`
- индекс `products.status`
- soft-delete товара через перевод в `ARCHIVED`
- `created_at/updated_at` ведутся автоматически

Миграции: [`V1__init.sql`](marketplace-api/src/main/resources/db/migration/V1__init.sql)

### Шаг 4. Реализовал бизнес-логику заказов
В `OrderService` реализованы инварианты из задания:
- rate limit по `user_operations`
- запрет второго активного заказа
- проверка доступности товара и стока
- резервирование остатков в транзакции
- snapshot `price_at_order`
- проверка и применение промокода
- пересчет заказа при обновлении
- возврат стока/промокода при отмене

Сервис: [`OrderService.java`](marketplace-api/src/main/java/com/gumbatali/marketplace/service/OrderService.java)

### Шаг 5. Добавил auth + RBAC + единый формат ошибок
- JWT access/refresh
- роли: `USER`, `SELLER`, `ADMIN`
- единый контракт ошибок `ErrorResponse`
- коды ошибок синхронизированы между OpenAPI и кодом

## Что реализовано по критериям

1. OpenAPI CRUD для `Product`: выполнено.
2. Схемы `ProductCreate/ProductUpdate/ProductResponse`: выполнено.
3. Codegen из OpenAPI: выполнено.
4. PostgreSQL + Flyway + soft-delete + индекс: выполнено.
5. Контрактная обработка ошибок: выполнено.
6. Контрактная валидация: выполнено.
7. Сложная логика заказов: выполнено.
8. Логирование API в JSON + `X-Request-Id`: выполнено.
9. JWT-авторизация: выполнено.
10. RBAC: выполнено.

## Дополнительный функционал (сверх базового минимума)
Добавлено:
- `GET /orders` — пагинация + фильтр по статусу (`USER` видит свои, `ADMIN` все)
- `POST /orders/{id}/status` — явный переход по state machine
  - `CREATED -> PAYMENT_PENDING -> PAID -> SHIPPED -> COMPLETED`
  - недопустимые переходы дают `INVALID_STATE_TRANSITION`

Изменения в коде:
- [`OrderRepository.java`](marketplace-api/src/main/java/com/gumbatali/marketplace/domain/repository/OrderRepository.java)
- [`OrderService.java`](marketplace-api/src/main/java/com/gumbatali/marketplace/service/OrderService.java)
- [`OrdersController.java`](marketplace-api/src/main/java/com/gumbatali/marketplace/web/OrdersController.java)
- [`OrderServiceTest.java`](marketplace-api/src/test/java/com/gumbatali/marketplace/service/OrderServiceTest.java)

## Структура модуля
- `marketplace-api/src/main/resources/openapi` — контракт
- `marketplace-api/src/main/resources/db/migration` — миграции
- `marketplace-api/src/main/java/.../web` — контроллеры
- `marketplace-api/src/main/java/.../service` — бизнес-логика
- `marketplace-api/src/main/java/.../security` — JWT, фильтры, доступ
- `marketplace-api/src/main/java/.../web/error` — обработка ошибок
- `marketplace-api/src/test/java/.../service` — unit-тесты

## Запуск

### 1) Проверка проекта
```bash
cd marketplace-api
./mvnw clean test
```

### 2) Запуск в Docker
```bash
docker compose down -v
docker compose up --build
```

Порты в этой ветке:
- API: `http://localhost:8081`
- PostgreSQL: `localhost:5433`

Swagger:
- `http://localhost:8081/swagger-ui/index.html`

## E2E-сценарий для демонстрации

### 1) Регистрация пользователей
```bash
curl -s -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"admin","password":"Strong123","role":"ADMIN"}'
curl -s -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"seller1","password":"Strong123","role":"SELLER"}'
curl -s -X POST http://localhost:8081/auth/register -H 'Content-Type: application/json' -d '{"username":"user1","password":"Strong123","role":"USER"}'
```

### 2) Логин
```bash
SELLER_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login -H 'Content-Type: application/json' -d '{"username":"seller1","password":"Strong123"}' | jq -r .access_token)
USER_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login -H 'Content-Type: application/json' -d '{"username":"user1","password":"Strong123"}' | jq -r .access_token)
```

### 3) Создание товара и промокода
```bash
PRODUCT_ID=$(curl -s -X POST http://localhost:8081/products \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"iPhone 15","description":"128GB","price":1000.00,"stock":10,"category":"smartphones","status":"ACTIVE"}' | jq -r .id)

curl -s -X POST http://localhost:8081/promo-codes \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"PROMO10","discount_type":"PERCENTAGE","discount_value":10,"min_order_amount":100,"max_uses":100,"valid_from":"2025-01-01T00:00:00Z","valid_until":"2030-01-01T00:00:00Z","active":true}'
```

### 4) Создание заказа
```bash
ORDER_ID=$(curl -s -X POST http://localhost:8081/orders \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"product_id\":\"$PRODUCT_ID\",\"quantity\":1}],\"promo_code\":\"PROMO10\"}" | jq -r .id)
```

### 5) Демонстрация дополнительного функционала
```bash
curl -s -X GET "http://localhost:8081/orders?page=0&size=20" \
  -H "Authorization: Bearer $USER_TOKEN"

curl -s -X POST "http://localhost:8081/orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"next_status":"PAYMENT_PENDING"}'
```

### 6) Отмена заказа
```bash
curl -s -X POST "http://localhost:8081/orders/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $USER_TOKEN"
```

## Что показать из БД на демонстрации
```sql
SELECT id, username, role, created_at FROM users;
SELECT id, name, stock, status, seller_id FROM products;
SELECT id, user_id, status, total_amount, discount_amount, promo_code_id FROM orders;
SELECT id, order_id, product_id, quantity, price_at_order FROM order_items;
SELECT id, code, current_uses, max_uses, active FROM promo_codes;
SELECT id, user_id, operation_type, created_at FROM user_operations;
```

## Что удобно объяснить при разборе кода
- Почему `Contract First` уменьшает расхождения между документацией и реализацией.
- Почему `stateless` + JWT упрощает горизонтальное масштабирование API.
- Почему для заказов нужна транзакционность (stock, order_items, promo usage).
- Почему soft-delete (`ARCHIVED`) лучше физического удаления для аудита.
- Где проверяются роли и ownership.
