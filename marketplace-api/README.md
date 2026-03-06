# Marketplace API (HW2)

Модуль с реализацией ДЗ2 `Marketplace: OpenAPI + CRUD`.

## Цель
Собрать рабочий backend маркетплейса в подходе `Contract First`:
- сначала OpenAPI-контракт;
- затем реализация сервисов, безопасности и бизнес-правил;
- воспроизводимый запуск для E2E-демонстрации.

## Стек
- `Java 21`, `Spring Boot 3`
- `OpenAPI 3.0.3` + `openapi-generator`
- `PostgreSQL 16`, `Flyway`
- `Spring Security + JWT`

## Ключевые возможности
- Auth: `/auth/register`, `/auth/login`, `/auth/refresh`
- Product CRUD с soft-delete
- Заказы: create/get/update/cancel
- Промокоды: create
- JSON-логирование API + `X-Request-Id`
- RBAC: `USER`, `SELLER`, `ADMIN`
- Единый формат ошибок: `error_code`, `message`, `details`

## Дополнительно
Сверх базового минимума добавлены:
- `GET /orders` — список заказов с пагинацией и фильтром `status`
- `POST /orders/{id}/status` — переход по state machine

## Быстрый запуск
```bash
./mvnw clean test
docker compose down -v
docker compose up --build
```

Порты:
- API: `http://localhost:8081`
- PostgreSQL: `localhost:5433`

Swagger UI:
- `http://localhost:8081/swagger-ui/index.html`

## Где смотреть контракт и логику
- OpenAPI: [`src/main/resources/openapi/marketplace.yaml`](src/main/resources/openapi/marketplace.yaml)
- Миграции: [`src/main/resources/db/migration/V1__init.sql`](src/main/resources/db/migration/V1__init.sql)
- Заказы: [`src/main/java/com/gumbatali/marketplace/service/OrderService.java`](src/main/java/com/gumbatali/marketplace/service/OrderService.java)
- Безопасность: [`src/main/java/com/gumbatali/marketplace/security`](src/main/java/com/gumbatali/marketplace/security)
