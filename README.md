# LedgerLine

LedgerLine is a financial-transaction integrity demo built for the Snovasys Fresher Hiring Challenge. Its focus is not merely moving money, but proving that money movement remains balanced, authorized, idempotent, concurrent-safe, and auditable.

> Demo only. Use synthetic accounts and data. This is not a production banking system and must never handle real funds.

## Technology

- Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA
- PostgreSQL 16+ with Flyway
- React 19 and Vite 7
- H2 for fast isolated tests
- GitHub Actions for H2 tests, PostgreSQL integration tests, and frontend build

## Implemented functionality

- Database-backed authentication with BCrypt password hashing and ADMIN, ACCOUNT_HOLDER, and AUDITOR roles.
- Admin provisioning of account-holder and auditor identities; public self-registration and public admin creation are disabled.
- Customer account creation and account listing.
- Deposits, withdrawals, and transfers using integer minor units (INR uses paise).
- Double-entry postings: each transaction must contain exactly two signed entries whose sum is zero. External deposits/withdrawals use a system cash-clearing counter-account.
- PostgreSQL deferred constraint triggers enforce transaction balancing and reconcile customer/system balances against immutable ledger postings at commit.
- Pessimistic row locks for balance-changing operations and deterministic account-ID lock ordering for transfers.
- PostgreSQL transaction-scoped advisory locks serialize identical idempotency keys even when requests target different accounts.
- Idempotency keys are bound to SHA-256 request fingerprints. A replay returns the original transaction and original result-balance snapshot; key reuse for a different request is rejected.
- Runtime database role is separate from the migration owner. It cannot update/delete ledger or audit rows, and may update only balance columns on customer/system accounts.
- Server-side account ownership checks; client-supplied IDs are never treated as authorization.
- Account statements include posted debit/credit entries, initiator, running balance, and rejected attempts associated with that account.
- Successful business operations and rejected business/security requests are recorded in an append-only audit trail.
- Admin workspace for user provisioning and system-wide account/transaction/audit review.
- Read-only auditor workspace for account register, transaction postings, and audit events.

## Local setup

Requirements: JDK 21, Maven, Node.js 22+, npm, and PostgreSQL 16+.

### 1. Create separate migration and runtime roles

Use a local PostgreSQL superuser to create the database and two roles. Choose your own strong passwords.

```sql
CREATE ROLE ledgerline_migrator LOGIN PASSWORD 'REPLACE_WITH_MIGRATION_PASSWORD';
CREATE ROLE ledgerline_app LOGIN PASSWORD 'REPLACE_WITH_RUNTIME_PASSWORD';
CREATE DATABASE ledgerline_db OWNER ledgerline_migrator;
GRANT CONNECT ON DATABASE ledgerline_db TO ledgerline_app;
```

Flyway connects as `ledgerline_migrator` and owns the schema objects. The application connects as `ledgerline_app`, which receives only the runtime privileges granted by the migrations.

### 2. Start the backend

Windows Command Prompt:

```bat
cd backend
set "DB_URL=jdbc:postgresql://localhost:5432/ledgerline_db"
set "DB_USERNAME=ledgerline_app"
set "DB_PASSWORD=YOUR_RUNTIME_PASSWORD"
set "MIGRATION_DB_URL=jdbc:postgresql://localhost:5432/ledgerline_db"
set "MIGRATION_DB_USERNAME=ledgerline_migrator"
set "MIGRATION_DB_PASSWORD=YOUR_MIGRATION_PASSWORD"
set "BOOTSTRAP_ADMIN_USERNAME=admin"
set "BOOTSTRAP_ADMIN_PASSWORD=YOUR_STRONG_ONE_TIME_ADMIN_PASSWORD"
mvn spring-boot:run
```

Flyway applies migrations V1–V5. The bootstrap administrator is created only when both bootstrap variables are present and the username does not already exist. Remove the bootstrap password from the environment after the first successful startup. Never commit credentials.

### 3. Start the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/api/health`

The Vite development server proxies `/api` requests to the backend.

## API overview

All routes except `GET /api/health` require HTTP Basic authentication.

| Method | Endpoint | Access |
|---|---|---|
| GET | `/api/access/me` | Any authenticated user |
| GET | `/api/admin/users` | ADMIN |
| POST | `/api/admin/users` | ADMIN; creates ACCOUNT_HOLDER or AUDITOR |
| GET | `/api/admin/accounts` | ADMIN |
| GET | `/api/auditor/accounts` | AUDITOR or ADMIN |
| GET | `/api/auditor/transactions` | AUDITOR or ADMIN |
| GET | `/api/auditor/audit-events` | AUDITOR or ADMIN |
| POST | `/api/account-holder/accounts` | ACCOUNT_HOLDER |
| GET | `/api/account-holder/accounts` | ACCOUNT_HOLDER; own accounts only |
| POST | `/api/account-holder/transactions/deposit` | ACCOUNT_HOLDER; own account |
| POST | `/api/account-holder/transactions/withdraw` | ACCOUNT_HOLDER; own account |
| POST | `/api/account-holder/transactions/transfer` | ACCOUNT_HOLDER; owned source account |
| GET | `/api/account-holder/accounts/{id}/statement` | ACCOUNT_HOLDER; own account |

Money requests use positive integer `amountMinor` values, not floating-point amounts. Every money-changing request must include an `Idempotency-Key` header.

Example deposit:

```http
POST /api/account-holder/transactions/deposit
Authorization: Basic <credentials>
Idempotency-Key: deposit-2026-0001
Content-Type: application/json

{"accountId":"<account-uuid>","amountMinor":12500}
```

This represents INR 125.00 when the account currency is INR.

## Verification

### Full CI test suite

The GitHub Actions backend job starts an ephemeral PostgreSQL 16 service, provisions distinct migration/runtime roles, then runs:

```bash
cd backend
mvn --batch-mode test
```

The suite includes H2 workflow/RBAC tests and PostgreSQL-specific integration tests for:

- Flyway migration and schema validation.
- Deferred double-entry and balance-reconciliation constraints.
- Rejection of unbalanced postings and direct balance tampering.
- Append-only ledger/audit triggers and runtime database permissions.
- Concurrent duplicate idempotency keys across different accounts.
- Concurrent withdrawals and original idempotency response snapshots.
- Refused HTTP withdrawals appearing in the account statement without changing the balance.

For H2-only tests without a PostgreSQL service:

```bash
cd backend
mvn --batch-mode -Dtest=BankingWorkflowTest,RoleBasedAccessTest test
```

Build the frontend:

```bash
cd frontend
npm install
npm run build
```

Check the latest [GitHub Actions runs](https://github.com/Yogesh-Chandra-007/LedgerLine/actions) before treating a commit as verified.

## Security boundaries and remaining work

- HTTP Basic is used for this challenge demo. Use HTTPS in any deployed environment; never send Basic credentials over plaintext HTTP.
- The frontend keeps credentials in React memory for the current tab and clears them on sign-out. This does not replace a production session/token design and remains exposed to successful same-origin script compromise.
- The runtime PostgreSQL role is not the schema owner. Ledger and audit tables are insert/read only for the runtime role; balance columns are the only account columns it can update.
- Database triggers provide integrity controls, but a database superuser or migration owner can still change schema/trigger definitions. Protect migration credentials separately.
- Deployment, real-host PostgreSQL verification, independent penetration testing, rate limiting, production session management, backup/restore, and monitoring remain outstanding.
- Public registration, password reset/email delivery, multi-factor authentication, and account freeze/closure workflows are not included.
