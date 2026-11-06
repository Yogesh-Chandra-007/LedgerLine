# LedgerLine

LedgerLine is a secure financial transaction management system focused on correctness, authorization, and traceability.

## Technology stack

- Java 21
- Spring Boot 3.5
- Maven
- Spring Security and Spring Data JPA
- PostgreSQL with Flyway migrations
- React 19 and Vite 7

## Day 1 — Foundation and security

The Day 1 backend foundation includes:

- Spring Boot application and public health endpoint.
- PostgreSQL datasource configuration through environment variables.
- Flyway migration for the initial application-user schema.
- Database-backed authentication using Spring Security HTTP Basic.
- BCrypt password hashing (work factor 12).
- Three application roles: ADMIN, ACCOUNT_HOLDER, and AUDITOR.
- Route-level authorization rules for each role.
- A one-time, environment-configured administrator bootstrap. It creates an administrator only when both bootstrap values are supplied and the username does not already exist.
- Automated tests for public health access, authenticated identity, authentication rejection, and role restrictions.
- Separate test profile using an in-memory H2 database.
- React foundation that checks the backend health endpoint.
- GitHub Actions workflow for backend tests and frontend production build.

### Security boundary

There is no public registration endpoint and no hard-coded production username or password. Do not commit database credentials or bootstrap credentials. HTTP Basic must only be used over HTTPS outside local development. Role authorization does not yet prove ownership of an individual financial account; ownership checks are part of the later account and security milestones.

## Run locally

Requirements: JDK 21, Maven, Node.js, npm, and PostgreSQL.

### 1. Create the PostgreSQL database

Run these commands in PostgreSQL as a database administrator, choosing your own strong password:

```sql
CREATE USER ledgerline_app WITH PASSWORD 'REPLACE_WITH_A_STRONG_LOCAL_PASSWORD';
CREATE DATABASE ledgerline_db OWNER ledgerline_app;
```

Do not put the real password in GitHub or commit it to any file.

### 2. Start the backend

Windows Command Prompt:

```bat
cd backend
set "DB_URL=jdbc:postgresql://localhost:5432/ledgerline_db"
set "DB_USERNAME=ledgerline_app"
set "DB_PASSWORD=YOUR_LOCAL_DATABASE_PASSWORD"
set "BOOTSTRAP_ADMIN_USERNAME=admin"
set "BOOTSTRAP_ADMIN_PASSWORD=YOUR_STRONG_ONE_TIME_ADMIN_PASSWORD"
mvn spring-boot:run
```

Linux/macOS shell:

```bash
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/ledgerline_db
export DB_USERNAME=ledgerline_app
export DB_PASSWORD='YOUR_LOCAL_DATABASE_PASSWORD'
export BOOTSTRAP_ADMIN_USERNAME=admin
export BOOTSTRAP_ADMIN_PASSWORD='YOUR_STRONG_ONE_TIME_ADMIN_PASSWORD'
mvn spring-boot:run
```

Flyway creates the initial app_users table on startup. The bootstrap administrator is created only if both bootstrap variables are present and that username is not already in the database. Remove the bootstrap password from the environment after the first successful startup.

Health endpoint: `http://localhost:8080/api/health`

### 3. Exercise the authentication routes

Use the administrator credentials configured above:

```bash
curl -u admin:YOUR_ADMIN_PASSWORD http://localhost:8080/api/access/me
curl -u admin:YOUR_ADMIN_PASSWORD http://localhost:8080/api/admin/status
```

Role-protected routes:

- `GET /api/admin/status` — ADMIN
- `GET /api/auditor/status` — AUDITOR or ADMIN
- `GET /api/account-holder/status` — ACCOUNT_HOLDER or ADMIN
- `GET /api/access/me` — any authenticated user

No account-holder or auditor provisioning API is included yet. The tests create temporary test identities in the isolated H2 database.

### 4. Run backend tests

```bash
cd backend
mvn test
```

Tests use the test profile and an in-memory H2 database; they do not require the local PostgreSQL database.

### 5. Start the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Vite development URL: `http://localhost:5173`

The Vite development server proxies `/api` to the backend on port 8080.

## Six-day implementation roadmap

1. **Day 1 — Foundation and security:** project setup, initial identity schema, authentication, and role-based access control.
2. **Day 2 — Core banking operations:** accounts, deposits, withdrawals, transfers, and double-entry ledger.
3. **Day 3 — Transaction reliability:** idempotency, concurrency control, database transactions, and overdraft protection.
4. **Day 4 — Security and traceability:** append-only audit logs, account ownership checks, security tests, and THREATS.md.
5. **Day 5 — Frontend and deployment:** React dashboard, API integration, and deployment.
6. **Day 6 — Final verification:** end-to-end testing, bug fixes, demo, documentation, and submission.

## Current limitations

The Day 1 authentication and role foundation is implemented in source but still requires a successful CI/local build to be considered verified. Account creation, account ownership enforcement, deposits, withdrawals, transfers, double-entry ledger enforcement, idempotency, concurrency protection, audit logging, and deployment are not yet implemented. These remain assigned to later days in the roadmap.
