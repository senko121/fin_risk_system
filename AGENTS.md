# FinRisk System Context

## Stack

* Backend: Spring Boot
* Frontend: React
* Database: MySQL
* AI Service: Python
* Authentication: JWT
* Infra: Docker

## Architecture

* Layered architecture
* REST API communication
* Frontend uses Axios
* AI service communicates separately

## Security Flow

Transaction verification may include:

* PIN
* OTP
* FACE verification
* VOICE OTP

## Transaction States

Possible transaction states:

* PENDING\_PIN
* PENDING\_OTP
* PENDING\_FACE\_STATIC
* PENDING\_ALL\_IN\_ONE
* PENDING\_VOICE\_OTP
* PENDING\_PIN\_OTP
* PENDING\_PIN\_FACE
* PENDING\_PIN\_HIGH

## Important Backend Rules

* Never bypass transaction verification logic.
* Never remove audit logging.
* Always preserve transaction consistency.

## Frontend Rules

* Frontend must correctly interpret backend response codes.
* Always check Axios interceptors before blaming backend.
* Authentication issues may come from:

  * expired JWT
  * interceptor overwrite
  * CORS
  * missing Authorization header

API Contract Rules



Backend response structures may evolve frequently.

Always compare:

\- actual API response

\- frontend expected structure

\- HTTP status codes

\- interceptor transformations



Never assume frontend parsing logic is still compatible after backend updates.

Verification State Rules



Transaction states represent a security state machine.

Never:

\- skip transitions

\- force-complete verification

\- bypass required verification layers

\- mutate transaction status inconsistently



Always validate:

\- current state

\- allowed next state

\- verification ownership

Logging Rules



Always preserve:

\- transaction tracing

\- security audit logs

\- verification history



Never log:

\- raw PIN

\- OTP values

\- JWT secrets

\- biometric raw data

AI Service Rules



AI verification services may:

\- timeout

\- return inconsistent confidence scores

\- fail independently from backend



Always:

\- validate AI response format

\- handle fallback paths

\- verify timeout handling

\- preserve transaction consistency during AI failure

Root Cause Analysis



Never patch symptoms before identifying root cause.



Before suggesting fixes:

1\. identify affected layer

2\. inspect logs

3\. inspect request payload

4\. inspect response payload

5\. inspect authentication state

6\. inspect database state if necessary

Pragmatism



Avoid unnecessary abstractions.

Avoid excessive file fragmentation.

Prefer maintainable solutions over theoretical perfection.

Existing System Rules



Before refactoring:

\- understand current flow

\- preserve API compatibility when possible

\- identify integration dependencies

\- avoid breaking authentication or transaction flows

Database Rules



Transaction-related database updates must remain consistent.

Always consider:

\- transaction boundaries

\- rollback scenarios

\- concurrent updates

\- race conditions

Frontend State Rules



Authentication and transaction UI states may become stale.

Always verify:

\- async state updates

\- token refresh timing

\- interceptor side effects

\- React state synchronization

## Debugging Priority

When errors happen:

1. check logs
2. check API payload
3. check JWT/authentication
4. check DB state
5. check frontend interpretation

## Common Issue

Backend logic changes frequently.
Frontend may become incompatible with new response structures.
Always compare actual API response vs frontend expectation.

