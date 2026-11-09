# LedgerLine data model

## Identity

`app_users` stores a UUID identity, unique username, BCrypt password hash, enabled flag, and one role (ADMIN, ACCOUNT_HOLDER, or AUDITOR).

## Customer accounts

`bank_accounts` stores an account UUID, unique display account number, owner username, ISO-style three-letter currency code, cached balance in integer minor units, status, and creation time. A database check prevents negative customer balances. The API resolves the authenticated username from Spring Security and checks ownership in the service layer.

## System ledger accounts

`system_ledger_accounts` contains internal counter-accounts. The initial `CASH_CLEARING` account represents the external cash side of customer deposits and withdrawals. It is not exposed as a customer account.

## Posted transactions and entries

`ledger_transactions` stores a unique idempotency key, SHA-256 request fingerprint, transaction type, actor, and timestamp, together with the original result-account and result-balance snapshot used for idempotent replay.

`ledger_entries` stores signed integer minor-unit postings. Exactly one of `account_id` or `system_account_code` must be set. Each posted operation creates two entries whose signed sum is zero:

- Deposit: customer account +amount; cash clearing -amount.
- Withdrawal: customer account -amount; cash clearing +amount.
- Transfer: source customer account -amount; destination customer account +amount.

The customer account balance is updated in the same database transaction as the ledger transaction and entries. Deferred PostgreSQL constraint triggers reconcile customer and system account balances against their ledger postings at transaction commit. This is stronger than relying only on a periodic reconciliation job; an independent operational reconciliation report can still be useful for monitoring and incident response.

PostgreSQL triggers reject updates and deletes to transaction and ledger-entry rows. Corrections must be represented by compensating entries rather than editing history.
