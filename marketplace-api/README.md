# Marketplace API (HW2)

Модуль с API-сервисом маркетплейса для домашнего задания №2.

## Коротко о цели
Сделать понятный backend по подходу `contract-first`:
1. Сначала описать контракт в OpenAPI.
2. Потом реализовать сервер по этому контракту.
3. Подготовить проект так, чтобы его можно было показать на защите шаг за шагом.

## Что внутри
- `Auth`: регистрация, логин, refresh токена
- `Products`: CRUD + мягкое удаление (`ARCHIVED`)
- `Orders`: create/get/update/cancel + переход статусов
- `Promo codes`: создание и применение
- `RBAC`: `USER`, `SELLER`, `ADMIN`
- `Flyway`: миграции базы

## Быстрый запуск

```bash
# 1) Проверяем, что проект собирается и тесты проходят
./mvnw clean test

# 2) Запускаем БД и API
docker compose up --build
```

## Куда заходить после запуска
- Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
- PostgreSQL: `localhost:5433`

## Базовые переменные для демонстрации

```bash
BASE="http://localhost:8081"
SELLER="Drugdiller"
USERN="pmishnik"
PASS="iamloser"
PROMO_CODE="DSBABETTER"
```

## Где смотреть ключевые части кода
- OpenAPI: [`src/main/resources/openapi/marketplace.yaml`](src/main/resources/openapi/marketplace.yaml)
- Flyway migration: [`src/main/resources/db/migration/V1__init.sql`](src/main/resources/db/migration/V1__init.sql)
- Order service: [`src/main/java/com/gumbatali/marketplace/service/OrderService.java`](src/main/java/com/gumbatali/marketplace/service/OrderService.java)
- Security: [`src/main/java/com/gumbatali/marketplace/security`](src/main/java/com/gumbatali/marketplace/security)
