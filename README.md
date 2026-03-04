# Marketplace Architecture & API Homework

Архитектурный учебный проект маркетплейса: от C4-декомпозиции до контрактного API с бизнес-логикой заказов.

## Что где лежит

| Ветка | Назначение |
|---|---|
| `main` | Архитектурная база: C4, контейнерная схема, базовый catalog-service |
| `hw1` | Домашнее задание №1 (архитектура) |
| `hw2` | Домашнее задание №2 (OpenAPI + CRUD + orders + auth + RBAC) |

Ссылки:
- [hw1](https://github.com/Gumbatali/soa-hw-marketplace-architecture/tree/hw1)
- [hw2](https://github.com/Gumbatali/soa-hw-marketplace-architecture/tree/hw2)

## Проектная идея

Маркетплейс с разделением по доменам:
- `User`
- `Catalog`
- `Feed`
- `Cart`
- `Order`
- `Payment`
- `Notification`

Подход: SOA/Microservices с явными границами ответственности и отдельным хранением данных по сервисам.

## Архитектурные акценты

- C4 Container (simple + extended)
- событийное взаимодействие через Kafka (в расширенной версии)
- Redis для ускорения ленты и каталога
- observability-контур (Prometheus/Grafana как целевое состояние)
- API Gateway как точка входа

## Что уже можно запустить из `main`

В `main` лежит базовый `catalog-service` с health-check.

```bash
docker compose up --build
curl http://localhost:8000/health
```

Ожидаемый ответ:

```json
{"status":"ok"}
```

## C4-диаграммы

- `c4/workspace.simple.dsl`
- `c4/workspace.extended.dsl`
- рендеры: `c4/site-simple/`, `c4/site-extended/`

## Материалы по ДЗ2

Полная реализация OpenAPI-first API находится в ветке `hw2`, включая:
- `POST/GET/PUT/DELETE /products`
- `POST/GET/PUT /orders`, `POST /orders/{id}/cancel`
- `POST /auth/register|login|refresh`
- `POST /promo-codes`
- PostgreSQL + Flyway
- JWT + роли `USER/SELLER/ADMIN`
- единый контракт ошибок

## Почему так организовано

`main` оставлен чистым как архитектурный “landing page”, а выполнение отдельных домашних заданий вынесено в тематические ветки. Так проще демонстрировать прогресс и защищать работу по этапам.
