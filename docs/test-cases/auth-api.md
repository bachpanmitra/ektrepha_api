# Test Cases — Authentication (Sign Up, Register, Login, Password Reset)

Source: EKTREPHA — Authentication PRD (Sign Up, Sign In, Register, Login — Use Cases, Request/Response Contracts).

Endpoint paths below use the PRD's `/api/auth/...` naming; the implemented API is versioned as `/api/v1/auth/...` (see `docs/postman/Ektrepha-Local.postman_collection.json`) — same routes otherwise. Where a case is already exercised by the Postman collection or `docs/postman/test-local.sh`, that's noted in the Notes column instead of re-describing the assertion.

Priority: P0 = blocks core auth flow if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Sign Up — Google Account

`POST /api/auth/signup/google`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| SU-G-01 | New user signs up with valid Google ID token | No account exists for the Google email | POST with valid `idToken`, `role: PARENT` | 201; response has `userId`, `email`, `name`, `role`, `accessToken`, `refreshToken`, `isNewUser: true`, `passwordSetupEmailSent: true` | P0 |
| SU-G-02 | Sign up with expired/invalid Google ID token | — | POST with malformed/expired `idToken` | 400; error body per common error format | P0 — covered by Postman ("Signup - Google (expect 400...)") |
| SU-G-03 | Sign up when email already registered via email/password | Account exists for same email via email+password path | POST valid Google `idToken` for that email | 409 (conflict) | P0 |
| SU-G-04 | Sign up when email already registered via phone+password | Account exists for same email via phone path (if phone signup captures email) | POST valid Google `idToken` for that email | 409 | P1 |
| SU-G-05 | Password-setup email is actually sent on successful Google signup | Valid new signup | Complete SU-G-01, inspect outbound email/log | `passwordSetupEmailSent: true` and email dispatch is triggered (or logged, per current `EmailServiceImpl` stub) | P1 |
| SU-G-06 | Role omitted from request | No account exists for the Google email | POST with `idToken` only, no `role` | Per PRD, role selection may happen in a follow-up step — clarify whether backend defaults `role` to `null` or rejects with 400 | P2 — **open question, needs decision before test can be finalized** |

## 2. Sign Up — Phone Number + Password

`POST /api/auth/signup/phone/initiate` → `POST /api/auth/signup/phone/verify`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| SU-P-01 | Initiate signup with new phone number | Phone not registered | POST `phoneNumber`, `password` to `/initiate` | 200; `otpSentTo`, `otpExpiresInSeconds: 300`, `signupSessionId` present | P0 — covered by Postman ("Signup - Phone, Step 1") |
| SU-P-02 | Initiate signup with already-registered phone number | Phone already registered | POST `/initiate` with that phone | 409 | P0 |
| SU-P-03 | Initiate signup with weak password | Phone not registered | POST `/initiate` with password failing strength policy | 400 | P1 |
| SU-P-04 | Verify with correct OTP before expiry | Valid `signupSessionId` from SU-P-01, OTP known (via log/SMS stub) | POST `signupSessionId`, `otp` to `/verify` | 201; `userId`, `phoneNumber`, `role: null`, `accessToken`, `refreshToken` | P0 — covered by Postman ("Signup - Phone, Step 2") |
| SU-P-05 | Verify with incorrect OTP | Valid `signupSessionId` | POST wrong `otp` | 400 | P0 |
| SU-P-06 | Verify with expired OTP | `signupSessionId` older than `otpExpiresInSeconds` | POST correct `otp` after expiry | 400 | P1 |
| SU-P-07 | Verify with unknown/expired `signupSessionId` | Session id not found or already consumed | POST `/verify` with bad session id | 400 | P1 |
| SU-P-08 | Repeated OTP verify attempts beyond max attempts | Configured OTP max-attempts exceeded (`OTP_MAX_ATTEMPTS`, currently 5 per `application.yml`) | POST `/verify` with wrong OTP repeatedly | Session should lock/invalidate rather than allow unlimited guesses — confirm exact status code (400 vs 429) | P1 |
| SU-P-09 | Race: two verify calls for the same session | Valid session, single correct OTP | Fire two concurrent `/verify` requests | Exactly one succeeds (201); the other gets a defined error (400/409), no duplicate user created | P2 |

## 3. Sign Up — Email + Password

`POST /api/auth/signup/email`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| SU-E-01 | Sign up with new, valid email + strong password | Email not registered | POST `email`, `password` | 201; `userId`, `email`, `emailVerified: false`, `accessToken`, `refreshToken`, `verificationEmailSent: true` | P0 — covered by Postman ("Signup - Email") |
| SU-E-02 | Sign up with already-registered email | Email registered (any method) | POST same `email` | 409 | P0 — covered by Postman ("Signup - Email, duplicate") |
| SU-E-03 | Sign up with invalid email format | — | POST malformed `email` (e.g. `foo@bar`) | 400 | P0 |
| SU-E-04 | Sign up with weak password | Valid new email | POST password failing strength policy | 400 | P0 |
| SU-E-05 | Verification email actually dispatched | Valid new signup | Complete SU-E-01, check email/log | Verification email/link generated and sent (or logged, per stub) | P1 |

## 4. Register (Full Profile Creation)

`POST /api/auth/register`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| REG-01 | Register with all valid fields (PARENT) | Email and phone not registered | POST `email`, `name`, `password`, `phoneNumber`, `role: PARENT` | 201; all response fields present, `emailVerified: false`, `phoneVerified: false`, `verificationEmailSent: true`, `otpSentToPhone: true` | P0 — covered by Postman ("Register (full profile)") |
| REG-02 | Register with all valid fields (NANNY) | Email and phone not registered | Same as REG-01 with `role: NANNY` | 201; `role: "NANNY"` in response | P0 |
| REG-03 | Register with duplicate email | Email already registered | POST with that email, new phone | 409 | P0 |
| REG-04 | Register with duplicate phone | Phone already registered | POST with that phone, new email | 409 | P0 |
| REG-05 | Register with both email and phone already registered (different accounts) | Both taken by different users | POST with both | 409 — confirm which conflict is reported first/whether both are surfaced | P1 |
| REG-06 | Register with missing required field | — | POST omitting `password` (or any required field) | 400 | P0 |
| REG-07 | Register with invalid phone format | — | POST `phoneNumber: "12345"` | 400 | P1 |
| REG-08 | Register with invalid/unsupported `role` value | — | POST `role: "SUPERADMIN"` | 400 — matches existing hardening in `test-local.sh` ("Register as ADMIN (blocked)" expects 400); extend same check to arbitrary invalid role strings | P0 — partially covered by `test-local.sh` |

## 5. Login — Google Account

`POST /api/auth/login/google`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| LI-G-01 | Login with valid Google ID token, existing account | Account previously created via Google signup | POST valid `idToken` | 200; `userId`, `email`, `name`, `role`, `accessToken`, `refreshToken` | P0 |
| LI-G-02 | Login with invalid/expired Google ID token | — | POST malformed `idToken` | 400 | P0 — covered by Postman ("Login - Google (expect 400...)") |
| LI-G-03 | Login with valid Google token but no matching account | No account for that Google email | POST valid `idToken` | 404, prompting sign up | P0 |

## 6. Login — Phone Number + Password

`POST /api/auth/login/phone`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| LI-P-01 | Login with correct phone + password | Verified phone account exists | POST correct `phoneNumber`, `password` | 200; `userId`, `phoneNumber`, `role`, `accessToken`, `refreshToken` | P0 — covered by Postman ("Login - Phone") |
| LI-P-02 | Login with wrong password | Account exists | POST correct phone, wrong password | 401 | P0 |
| LI-P-03 | Login with unregistered phone number | — | POST unknown `phoneNumber` | 401 (per PRD — does not distinguish unknown user from wrong password) | P0 |
| LI-P-04 | Login against locked/inactive account | Account locked (e.g. after repeated failures) or admin-deactivated | POST correct credentials | 403 | P1 |
| LI-P-05 | Repeated failed logins trigger temporary lockout | Fresh account, no prior failures | POST wrong password `LOGIN_LOCKOUT_MAX_FAILURES` times (5, per `application.yml`), then again | 429 on the attempt(s) after the threshold; account locked for `LOGIN_LOCKOUT_MINUTES` (15) | P0 — validates `LoginAttemptService` |
| LI-P-06 | Lockout auto-clears after lockout window | Account locked per LI-P-05 | Wait out (or fast-forward) the lockout window, retry with correct password | 200 — login succeeds again | P1 |

## 7. Login — Email + Password

`POST /api/auth/login/email`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| LI-E-01 | Login with correct email + password | Verified email account exists | POST correct `email`, `password` | 200; `userId`, `email`, `role`, `accessToken`, `refreshToken` | P0 — covered by Postman ("Login - Email") |
| LI-E-02 | Login with wrong password | Account exists | POST correct email, wrong password | 401 | P0 — covered by Postman ("Login - Email, wrong password") |
| LI-E-03 | Login with unregistered email | — | POST unknown `email` | 401 | P0 |
| LI-E-04 | Login against locked/inactive account | Account locked/deactivated | POST correct credentials | 403 | P1 |
| LI-E-05 | Repeated failed logins trigger temporary lockout | Fresh account | Same pattern as LI-P-05 for email login | 429 after threshold | P0 |
| LI-E-06 | Login before email verification | Account signed up via email, `emailVerified: false` | POST correct credentials | Per open question #2 in PRD (§8) — behavior undefined: confirm whether login is blocked, allowed with a flag, or allowed unconditionally | P1 — **blocked on PRD decision** |

## 8. Password Reset

`POST /api/auth/password/forgot` → `POST /api/auth/password/reset`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| PW-F-01 | Forgot-password for existing email | Email registered | POST `email` | 200; `message`, `resetLinkSentTo` — covered by Postman ("Forgot Password") | P0 |
| PW-F-02 | Forgot-password for non-existent email | Email not registered | POST unknown `email` | 200 with same generic message (must NOT leak whether the email exists, per PRD note) | P0 — security-sensitive, must be explicitly asserted |
| PW-F-03 | Forgot-password for Google-only account (no password set yet) | Account created via Google signup, no password set | POST that email | 200 — this is the intended path for Google users to set an initial password | P1 |
| PW-R-01 | Reset password with valid, unexpired token | Valid `resetToken` obtained via PW-F-01 | POST `resetToken`, `newPassword` (meets strength policy) | 200; `message`, `userId`; subsequent login must use new password | P0 — covered by Postman ("Reset Password") |
| PW-R-02 | Reset password with invalid/garbage token | — | POST bogus `resetToken` | 400 | P0 |
| PW-R-03 | Reset password with expired token | Token past its TTL | POST expired `resetToken` | 400 | P1 |
| PW-R-04 | Reset password with already-used token | Token already consumed by a prior successful reset | POST same `resetToken` again | 410 | P0 — explicitly called out in PRD error cases, distinct from generic 400 |
| PW-R-05 | Reset password with weak new password | Valid token | POST `resetToken`, weak `newPassword` | 400 | P0 |
| PW-R-06 | Existing sessions/refresh tokens invalidated after reset | User has an active refresh token, then resets password | Reset password, then attempt `/api/auth/refresh` with the old refresh token | Old refresh token should be rejected (401) — confirm this is actually implemented, PRD doesn't state it explicitly but "Please log in again" message implies it | P1 — **verify against implementation, not explicit in PRD** |

## 9. Cross-cutting

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| XC-01 | All success responses include `userId`, `accessToken`, `refreshToken` | Any successful signup/login | Inspect response body | Fields present and correctly typed per §6 | P0 |
| XC-02 | All error responses match common error format | Any 4xx from any endpoint above | Inspect response body | `timestamp`, `status`, `error`, `message`, `path` all present per §7 | P0 |
| XC-03 | `role` is one of PARENT/NANNY/ADMIN or null, never arbitrary string | Any response containing `role` | Inspect response body | Enforced server-side (matches `test-local.sh` "Register as ADMIN (blocked)" precedent for rejecting disallowed self-assigned roles) | P0 |
| XC-04 | Access token from any of the three signup/login paths works identically against protected routes | Token from Google vs phone vs email flow | Call `/api/v1/bookings` (or another protected route) with each | Same authorization behavior regardless of signup method used | P1 |

## Open items blocking full coverage (from PRD §8)

These need a product decision before the corresponding test cases above can be finalized:
- SU-G-06 — whether `role` is required on Google signup.
- LI-E-06 — whether unverified email blocks login.
- Rate-limit thresholds (assumed 5/15min per LI-P-05/LI-E-05 — matches current `application.yml` defaults, but PRD marks this as still a placeholder to confirm).
- Whether Register fully replaces the step-by-step Sign Up flows (affects whether SU-* and REG-* cases both need to stay in the suite long-term).
