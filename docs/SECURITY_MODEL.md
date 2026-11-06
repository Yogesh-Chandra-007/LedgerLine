# Day 1 — Authentication and authorization model

## Authentication

- Spring Security HTTP Basic authentication is used for the initial API foundation.
- Credentials are looked up in PostgreSQL through DatabaseUserDetailsService.
- Passwords are verified using BCrypt with work factor 12.
- Unknown usernames return the same generic authentication failure as an incorrect password.
- The API is stateless; it does not create server-side login sessions.
- HTTP Basic must only be used over HTTPS outside local development. A production deployment must terminate TLS and protect credentials in transit.

## Roles

| Role | Intended responsibility |
|---|---|
| ADMIN | Administrative and user-provisioning operations |
| ACCOUNT_HOLDER | Operations on accounts owned by the authenticated user |
| AUDITOR | Read-only audit and compliance access |

## Initial route policy

| Route | Policy |
|---|---|
| GET /api/health | Public |
| /api/admin/** | ADMIN |
| /api/auditor/** | AUDITOR or ADMIN |
| /api/account-holder/** | ACCOUNT_HOLDER or ADMIN |
| /api/access/** | Any authenticated user |
| All other routes | Authentication required |

The /api/access/me endpoint returns only the authenticated username and granted authorities. The role status endpoints are minimal authorization probes, not business operations.

## Important boundary

Role checks do not replace resource ownership checks. When account APIs are introduced on Day 2 and Day 4, the service layer must verify that the authenticated account holder owns the requested account. A client-supplied account ID must never be treated as proof of ownership.

No public user-registration endpoint or hard-coded production user is included. A secure administrative provisioning flow is still required before the system can be used with real users.
