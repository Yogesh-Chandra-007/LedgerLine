# LedgerLine

LedgerLine is a secure financial transaction management system focused on correctness, authorization, and traceability.

## Technology stack

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- React
- Vite

## Current implementation status

**Foundation stage — 6 November 2026**

The project has been set up locally with the following foundation work reported:

- React + Vite frontend scaffold
- Java 21 + Maven backend scaffold
- Spring Boot application setup
- PostgreSQL database and application user configuration
- Initial Spring Security configuration
- Health-check endpoint: `GET /api/health`

The local application source has not yet been pushed to this repository. The items below are planned work, not claims of completed functionality.

## Engineering roadmap

1. Model users, accounts, ledger entries, and transactions.
2. Implement authentication, role-based access control, and account ownership checks.
3. Implement deposits, withdrawals, and transfers within database transactions.
4. Enforce double-entry accounting and money-conservation invariants.
5. Prevent overdrafts with server-side validation and concurrency-safe database operations.
6. Add idempotency keys to prevent duplicate transaction processing.
7. Add an append-only audit trail and transaction statements.
8. Write automated unit, integration, security, and concurrency tests.
9. Integrate the React dashboard with the backend APIs.
10. Document, deploy, and verify the application.

## Local development

### Backend

From the Spring Boot project directory:

```bat
set "DB_PASSWORD=YOUR_LOCAL_DATABASE_PASSWORD"
mvnw.cmd spring-boot:run
```

Backend base URL: `http://localhost:8080`

Health endpoint: `http://localhost:8080/api/health`

### Frontend

From the frontend directory:

```bash
npm install
npm run dev
```

Vite development URL: `http://localhost:5173`

These are setup notes for the current scaffold. Confirm the local project paths and configuration before running them.

## Security principles

- Never commit passwords, tokens, private keys, or local environment files.
- Enforce authentication, authorization, ownership, and validation on the server.
- Never trust client-supplied balances, roles, or account ownership.
- Use database transactions and concurrency controls for money movement.
- Keep transaction and audit records consistent and traceable.

## Project status

Core financial operations, complete RBAC, double-entry ledger enforcement, idempotency, concurrency protection, audit logging, automated tests, frontend integration, and deployment are still pending implementation and verification.
