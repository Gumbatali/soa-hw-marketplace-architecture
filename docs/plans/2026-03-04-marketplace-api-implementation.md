# Marketplace API Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Реализовать контрактный API маркетплейса (products CRUD + orders business logic + auth/rbac + promo-codes + logging) на Spring Boot с OpenAPI codegen.

**Architecture:** OpenAPI-first. Контракты и DTO генерируются из `marketplace.yaml`; контроллеры реализуют generated interfaces. Бизнес-инварианты реализованы в сервисах и транзакциях JPA/Flyway.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Security, jjwt, Spring Data JPA, Flyway, PostgreSQL, OpenAPI Generator, Docker Compose.

---

### Task 1: Scaffold module and build tooling

**Files:**
- Create: `marketplace-api/pom.xml`
- Create: `marketplace-api/.gitignore`
- Create: `marketplace-api/src/main/java/com/gumbatali/marketplace/MarketplaceApplication.java`
- Create: `marketplace-api/src/main/resources/application.yml`

**Step 1: Write the failing test**
- Add a smoke context test that fails because app/module does not exist.

**Step 2: Run test to verify it fails**
- Run: `./mvnw -q test`
- Expected: build/test failure.

**Step 3: Write minimal implementation**
- Add Maven project + Spring Boot starter + test starter + basic app class.

**Step 4: Run test to verify it passes**
- Run: `./mvnw -q test`
- Expected: smoke test pass.

**Step 5: Commit**
- `git add marketplace-api`
- `git commit -m "chore: scaffold marketplace spring boot module"`

### Task 2: Define OpenAPI contract and code generation

**Files:**
- Create: `marketplace-api/src/main/resources/openapi/marketplace.yaml`
- Modify: `marketplace-api/pom.xml`

**Step 1: Write failing test**
- Add test that references generated `ProductResponse` class (fails until generation configured).

**Step 2: Run to verify RED**
- `./mvnw -q test -Dtest=GeneratedModelsSmokeTest`

**Step 3: Minimal implementation**
- Add full OpenAPI schemas/endpoints/errors/security and configure generator plugin.

**Step 4: Verify GREEN**
- `./mvnw -q test -Dtest=GeneratedModelsSmokeTest`

**Step 5: Commit**
- `git commit -m "feat: add openapi contract and generator setup"`

### Task 3: Flyway schema + entities + repositories

**Files:**
- Create: `marketplace-api/src/main/resources/db/migration/V1__init.sql`
- Create/Modify: entities and repositories under `.../domain` and `.../infra/repository`

**Step 1: Failing test**
- Integration test verifying Flyway creates products index and tables.

**Step 2: Verify RED**
- Run migration/schema test.

**Step 3: Minimal implementation**
- SQL migration with enums/tables/triggers/indexes and JPA mappings.

**Step 4: Verify GREEN**
- Re-run integration test.

**Step 5: Commit**
- `git commit -m "feat: add flyway schema and persistence layer"`

### Task 4: Auth + JWT + RBAC

**Files:**
- Create: security configs, JWT utility/filter, auth service/controllers, user/refresh-token repos
- Modify: OpenAPI controllers implementations

**Step 1: Failing tests**
- register/login/refresh tests + 401 TOKEN_INVALID/TOKEN_EXPIRED + role-based 403 tests.

**Step 2: Verify RED**
- Run auth/security tests.

**Step 3: Minimal implementation**
- Implement `/auth/*`, JWT parsing, security chain, role restrictions.

**Step 4: Verify GREEN**
- Re-run auth/security suite.

**Step 5: Commit**
- `git commit -m "feat: implement jwt auth and role-based access control"`

### Task 5: Product CRUD with soft delete

**Files:**
- Create/Modify: product service/controller/mapper and exception handling

**Step 1: Failing tests**
- product CRUD + filtering/pagination + seller ownership checks + soft delete assertions.

**Step 2: Verify RED**
- Run product API tests.

**Step 3: Minimal implementation**
- Implement create/read/list/update/delete(archive) flows.

**Step 4: Verify GREEN**
- Re-run product tests.

**Step 5: Commit**
- `git commit -m "feat: implement products crud with soft-delete"`

### Task 6: Orders business logic and promo codes

**Files:**
- Create/Modify: order/promo services/controllers, repositories with locking, mappers

**Step 1: Failing tests**
- create/update/cancel order happy paths + error cases: active order, stock, invalid promo, min amount, ownership, rate limit.

**Step 2: Verify RED**
- Run order business tests.

**Step 3: Minimal implementation**
- Implement transactional logic exactly per spec, including user_operations writes and promo usage adjustments.

**Step 4: Verify GREEN**
- Re-run order tests.

**Step 5: Commit**
- `git commit -m "feat: implement order lifecycle and promo code rules"`

### Task 7: Contract errors + request logging + docs and docker

**Files:**
- Create/Modify: `GlobalExceptionHandler`, `ApiLoggingFilter`, `docker-compose.yml`, `README.md`, `Dockerfile`

**Step 1: Failing tests**
- validation error response structure + request-id header + JSON log smoke.

**Step 2: Verify RED**
- Run web error/log tests.

**Step 3: Minimal implementation**
- Map all error codes/statuses and implement logging contract.

**Step 4: Verify GREEN**
- Re-run test suite.

**Step 5: Commit**
- `git commit -m "feat: finalize error contract, api logging, and deployment docs"`

### Task 8: Final verification

**Files:**
- Modify: if needed after verification

**Step 1: Run full verification**
- `./mvnw clean test`
- `docker compose up --build`
- Smoke curl flows for auth/products/orders and SQL selects.

**Step 2: Confirm output**
- Ensure no failing tests, app starts, migrations applied, contract responses correct.

**Step 3: Finalize**
- Summarize readiness for push to `git@github.com:Gumbatali/soa-hw-marketplace-architecture.git`.

