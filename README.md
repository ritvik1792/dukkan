# Dukkan API

Spring Boot 3 marketplace API. The database is **PostgreSQL**. Schema is applied with **Flyway** SQL migrations on startup. Hibernate is set to `validate` only — it does not create tables.

## Stack

| Piece | What it is |
| --- | --- |
| Database | PostgreSQL 16 |
| Schema | SQL files in `src/main/resources/db/migration/` (`V1__` … `V5__`) |
| ORM | Spring Data JPA / Hibernate (`ddl-auto: validate`) |
| Passwords | BCrypt (`BCryptPasswordEncoder`), column `app_users.password_hash` |
| Accounts | Created by **signup** (UI or `POST /api/auth/signup`). No env-var user seed. |

There is no Prisma or Drizzle. `dukkan-ui/src/data/seed.ts` is frontend mock catalogue data, not the database.

## Run Postgres locally

From this repo (`dukkan`):

```bash
docker compose up -d postgres
```

That starts `postgres:16-alpine` on `localhost:5432` with:

- database: `dukkan`
- user / password: `dukkan` / `dukkan`

Health check: `pg_isready -U dukkan -d dukkan`.

Copy env (never commit `.env`):

```bash
cp .env.example .env
```

Do **not** set `DUKKAN_SEED_*` passwords. That path is gone. Create accounts in the UI after the API is up. For local phone OTP without SMS, keep `DUKKAN_OTP_DEV_CODE=true` in `.env` (it is **false** in production).

## Run the API (migrates only)

Two profiles. The process always listens on `PORT` (default 8080).

**Local** — localhost Postgres (docker compose), CORS for the local UI. Does not use Cloud SQL, even if `DUKKAN_DB_URL` is set in `.env`.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Same as `make local`. API: `http://localhost:8080`. Point `dukkan-ui` `NEXT_PUBLIC_API_URL` at that (Cloud Build `_API_URL` is the prod Cloud Run API).

**Prod** — Cloud SQL + Secret Manager env. No passwords in the repo.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Same as `make prod`. Requires `DUKKAN_DB_URL` (Cloud SQL JDBC), `DUKKAN_DB_USER`, `DUKKAN_DB_PASSWORD`, `DUKKAN_JWT_SECRET`, `DUKKAN_CORS_ORIGINS`. Optional: `DUKKAN_FRONTEND_BASE_URL`, `DUKKAN_PUBLIC_BASE_URL` (defaults to `https://dukkan-api-zh6npfj54a-el.a.run.app`). Cloud Run already sets these and the image default profile is `prod`.

On boot Flyway runs `V1`–`V5` against PostgreSQL (tables plus categories / neighborhoods / default settings). **No login rows** are inserted.

Check:

```bash
curl http://localhost:8080/api/health
```

You want `"status":"ok"` and `"database":"up"`.

## First user (local or production)

Sign up in the UI (`dukkan-ui`, `npm run dev` → http://localhost:3000/signup) or:

```bash
curl -s http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"name":"You","email":"you@yourdomain.com","password":"your-real-password"}'
```

The password is stored as BCrypt. Then open `/console/login`. Signup creates a **buyer**; promote in SQL if you need admin:

```bash
docker compose exec postgres psql -U dukkan -d dukkan -c \
  "UPDATE app_users SET role = 'ADMIN' WHERE email = 'you@yourdomain.com';"
```

Sign in again after the role change (JWT is issued at login).

```bash
docker compose exec postgres psql -U dukkan -d dukkan -c \
  "SELECT id, email, role, left(password_hash, 7) AS hash_prefix FROM app_users;"
```

`hash_prefix` should look like `$2a$10`, never the raw password.

To wipe local data and start over:

```bash
docker compose down -v
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Google Cloud

See **[DEPLOY.md](DEPLOY.md)** for the split:

- **Secret Manager** — JWT secret, Cloud SQL user/password (not storefront logins)
- **Cloud SQL instance** — managed Postgres server
- **Database `dukkan`** — one database on that instance
- **Cloud Run `dukkan-api`** and **Cloud Run `dukkan-ui`**

Production must use `DUKKAN_OTP_DEV_CODE=false` and a long `DUKKAN_JWT_SECRET` from Secret Manager. `.env` is gitignored; only `.env.example` is committed.
