# LedgerLine

LedgerLine is a secure financial transaction management demo focused on money conservation, authorization, idempotency, and traceability.

## Technology

- Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA
- PostgreSQL with Flyway migrations
- React 19 and Vite 7
- H2 for isolated backend tests

## Implemented functionality

- Database-backed authentication with BCrypt password hashing and ADMIN, ACCOUNT_HOLDER, and AUDITOR roles.
- Administrator provisioning of account-holder and auditor identities. Public self-registration and public administrator creation are intentionally disabled.
- Customer account creation and account listing.
- Deposits, withdrawals, and transfers using integer minor units (for INR, paise).
- Double-entry posting: every posted transaction creates two signed ledger entries whose sum is zero. Deposits and withdrawals use a system cash-clearing counter-account.
- Pessimistic database row locks around balance-changing operations, with deterministic account lock ordering for transfers.
- Idempotency keys and request fingerprints to make retries safe and reject key reuse for a different operation.
- Server-side account ownership checks; client-supplied IDs are never treated as authorization.
- Account statements backed by append-only ledger entries.
- PostgreSQL triggers reject updates and deletes to posted transaction and ledger-entry rows.

## Run locally

Requirements: JDK 21, Maven, Node.js, npm, and PostgreSQL.

Create a local database (choose your own password):

```sql
CREATE USER ledgerline_app WITH PASSWORD 'REPLACE_WITH_A_STRONG_LOCAL_PASSWORD';
CREATE DATABASE ledgerline_db OWNER ledgerline_app;
```

Start the backend from Windows Command Prompt:

```bat
cd backend
set "DB_URL=jdbc:postgresql://localhost:5432/ledgerline_db"
set "DB_USERNAME=ledgerline_app"
set "DB_PASSWORD=YOUR_LOCAL_DATABASE_PASSWORD"
set "BOOTSTRAP_ADMIN_USERNAME=admin"
set "BOOTSTRAP_ADMIN_PASSWORD=YOUR_STRONG_ONE_TIME_ADMIN_PASSWORD"
mvn spring-boot:run
```

Flyway applies the schema migrations at startup. The bootstrap administrator is created only when both bootstrap variables are present and the username does not already exist. Remove the bootstrap password from the environment after the first successful startup. Never commit credentials.

Start the frontend in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/api/health`

## API overview

All routes except `GET /api/health` require HTTP Basic authentication.

| Method | Endpoint | Access |
|---|---|---|
| GET | `/api/access/me` | Any authenticated user |
| POST | `/api/admin/users` | ADMIN; creates ACCOUNT_HOLDER or AUDITOR |
| POST | `/api/account-holder/accounts` | ACCOUNT_HOLDER |
| GET | `/api/account-holder/accounts` | ACCOUNT_HOLDER; own accounts only |
| POST | `/api/account-holder/transactions/deposit` | ACCOUNT_HOLDER; own account |
| POST | `/api/account-holder/transactions/withdraw` | ACCOUNT_HOLDER; own account |
| POST | `/api/account-holder/transactions/transfer` | ACCOUNT_HOLDER; owned source account |
| GET | `/api/account-holder/accounts/{id}/statement` | ACCOUNT_HOLDER; own account |

Money requests use integer `amountMinor` values, not floating-point amounts. Every money-changing request must include a unique `Idempotency-Key` header.

Example deposit:

```http
POST /api/account-holder/transactions/deposit
Authorization: Basic <credentials>
Idempotency-Key: deposit-2026-0001
Content-Type: application/json

{"accountId":"<account-uuid>","amountMinor":12500}
```

This example represents INR 125.00 when the account currency is INR.

## Verification

Run backend tests:

```bash
cd backend
mvn test
```

Build the frontend:

```bash
cd frontend
npm install
npm run build
```

The tests include identity/RBAC checks and banking workflow cases for balanced entries, idempotent retries, overdraft rejection, ownership enforcement, transfers, currency mismatch, and concurrent withdrawals. GitHub Actions passed the backend test and frontend production-build jobs for commit `16572bb`; the subsequent dashboard retry-safety update is being checked by CI. Run the commands locally and verify the latest workflow before the final demo.

## Security and demo boundaries

- HTTP Basic is used for the current stateless demo. Use HTTPS in any deployed environment; do not send Basic credentials over plaintext HTTP.
- The frontend holds credentials in memory for the current tab and clears them on sign-out. This does not replace a production session/token design and remains exposed to successful same-origin script compromise.
- The system is for synthetic demo data, not real funds or production banking.
- PostgreSQL migration/trigger behavior, live concurrency under PostgreSQL, deployment, and a full independent security review must be verified before claiming production readiness.
- Public user registration, password reset, email delivery, and a complete auditor/admin dashboard are not included.
