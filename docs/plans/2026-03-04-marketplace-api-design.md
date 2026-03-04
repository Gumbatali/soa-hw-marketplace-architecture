# Marketplace API Design

## Context
Нужно реализовать backend API маркетплейса по контрактному подходу: OpenAPI-first, codegen DTO/handler contracts, PostgreSQL + Flyway, JWT auth, RBAC, JSON API logs и бизнес-инварианты заказов.

## Assumptions
- Стек: Java 21, Spring Boot 3.3, Spring Security, JPA, Flyway, PostgreSQL.
- Идентификаторы: UUID.
- Все endpoint (кроме `/auth/*`) требуют JWT access token.
- В одном репозитории уже есть другой проект, поэтому решение создаётся изолированно в директории `marketplace-api/`.

## Architecture
- **OpenAPI-first**: спецификация `src/main/resources/openapi/marketplace.yaml`.
- **Codegen**: `openapi-generator-maven-plugin` генерирует Spring interfaces + models в `target/generated-sources`.
- **Web layer**: контроллеры реализуют сгенерированные interfaces; ручные request/response DTO не используются.
- **Application layer**: сервисы `AuthService`, `ProductService`, `OrderService`, `PromoCodeService` с транзакционной бизнес-логикой.
- **Persistence layer**: JPA repositories + pessimistic locking для stock/promocode consistency.
- **Security**: JWT access/refresh, role claim (`USER|SELLER|ADMIN`), фильтры security и ownership checks в сервисах.
- **Observability**: servlet filter генерирует `request_id`, логирует JSON payload и время, возвращает `X-Request-Id`.
- **Errors**: единый `ErrorResponse` + `@ControllerAdvice`, map на требуемые `error_code`.

## Data Model
- `users`, `refresh_tokens`, `products`, `orders`, `order_items`, `promo_codes`, `user_operations`.
- Enum types: product/order status, role, discount type, operation type.
- `products.status` index.
- `created_at` default now, `updated_at` через trigger `set_updated_at()`.

## Key Business Flows
- **Create Order**: rate limit -> active order check -> product existence/ACTIVE -> stock check -> reserve stock -> snapshot prices -> promo validation/calc -> create order/items -> log operation.
- **Update Order**: ownership -> state CREATED -> rate limit -> return old stock -> validate/reserve new items -> recalc totals/promo adjustments -> replace items -> log operation.
- **Cancel Order**: ownership -> state check -> return stock -> rollback promo usage -> set CANCELED.
- Все шаги каждого flow в одной transaction.

## Validation and Contract Enforcement
- Поля/ограничения задаются в OpenAPI (`minLength/maxLength`, `minimum`, `pattern`, `minItems/maxItems`).
- `@Valid` на generated models; validation failures map в `VALIDATION_ERROR` и `details` с полями.

## E2E Readiness
- `docker-compose.yml` в `marketplace-api/` поднимает PostgreSQL + API.
- README с командами: build/run, curl сценарии, SQL `SELECT` для защиты.

