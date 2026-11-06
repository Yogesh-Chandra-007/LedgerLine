# LedgerLine

LedgerLine is a secure financial transaction management system focused on correctness, authorization, and traceability.

## Technology stack

- Java 21
- Spring Boot 3.5
- Maven
- PostgreSQL (planned for the persistence milestone)
- React 19
- Vite 7

## Day 1 repository foundation

The repository now contains an initial Java/Spring Boot API and React/Vite interface:

- Spring Boot application entry point.
- `GET /api/health` health endpoint.
- A Spring MockMvc test for the health endpoint.
- React landing page that checks the backend health endpoint.
- Vite development proxy from `/api` to `http://localhost:8080`.
- Git ignore rules for build output, dependencies, IDE files, local databases, and secrets.
- `PROMPTS.md` for transparent AI assistance and developer decisions.

**Verification status:** These source files were generated and committed to GitHub. They have not yet been built or executed in this environment. The presence of a test file is not a claim that the test has passed. Run the checks below in a development environment and record the actual result.

## Run locally

### Backend

Requirements: JDK 21 and Maven.

```bash
cd backend
mvn spring-boot:run
```

API base URL: `http://localhost:8080`

Health endpoint: `http://localhost:8080/api/health`

Run backend tests:

```bash
mvn test
```

### Frontend

Requirements: Node.js and npm.

```bash
cd frontend
npm install
npm run dev
```

Vite development URL: `http://localhost:5173`

The frontend requests `/api/health`; while developing, Vite proxies that path to the backend on port 8080. Start both applications to see the connected status.

## Engineering roadmap

1. Define users, accounts, ledger entries, and transaction domain models.
2. Implement authentication, role-based access control, and account ownership checks.
3. Implement deposits, withdrawals, and transfers within database transactions.
4. Enforce double-entry accounting and money-conservation invariants.
5. Prevent overdrafts with server-side validation and concurrency-safe database operations.
6. Add idempotency keys to prevent duplicate transaction processing.
7. Add an append-only audit trail and transaction statements.
8. Write automated unit, integration, security, and concurrency tests.
9. Integrate the React dashboard with the backend APIs.
10. Configure deployment and verify the live application.

## Security principles

- Never commit passwords, tokens, private keys, or local environment files.
- Enforce authentication, authorization, ownership, and validation on the server.
- Never trust client-supplied balances, roles, or account ownership.
- Use database transactions and concurrency controls for money movement.
- Keep transaction and audit records consistent and traceable.

## Current limitations

The current code is a Day 1 scaffold, not a complete banking or ledger application. PostgreSQL persistence, authentication/RBAC, account management, deposits, withdrawals, transfers, double-entry ledger enforcement, idempotency, concurrency protection, audit logging, and deployment remain unimplemented.
