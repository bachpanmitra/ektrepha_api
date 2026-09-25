# Test Cases — Mobile-Number OTP Login & Auto-Signup

Source: Mobile OTP login/auto-signup PRD (backend-only implementation — no app screens in this workstream).

Endpoints: `POST /api/v1/auth/mobile/otp/request`, `POST /api/v1/auth/mobile/otp/verify`. Both unauthenticated (`/api/v1/auth/**`), combined login-or-signup — there is no separate "does this number exist" check exposed to the client.

Automated coverage: `src/test/java/com/ektrepha/auth/AuthControllerApiTest.java` (HTTP-level, real Postgres). Case IDs below note which are already automated (`[auto]`) vs. still manual/exploratory.

Priority: P0 = blocks core login/signup flow if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Request OTP

`POST /mobile/otp/request`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| REQ-01 | Request OTP for a fresh number | Number has no prior/active challenge | POST `{mobileNumber}` | 200; `challengeId` (non-null, opaque), `expiresInSeconds: 300`, `resendAfterSeconds: 30`, `notice: null` (real SMS attempted) | P0 — `[auto]` |
| REQ-02 | Bare 10-digit Indian number is normalized | — | POST `{mobileNumber: "9876543210"}` (no `+91`) | 200; the created challenge's stored number is `+919876543210` — verify via a matching `/verify` call or DB read | P0 |
| REQ-03 | Immediate resend is cooldown-blocked | One request already made for this number, <30s ago | POST again immediately | 429; `message` mentions waiting a number of seconds | P0 — `[auto]` |
| REQ-04 | Resend after cooldown elapses invalidates the previous challenge | First challenge's `created_at` is ≥30s old | POST again | 200, new distinct `challengeId`; a subsequent `/verify` against the **old** `challengeId` (even with its correct code) now 400s as invalid | P0 — `[auto]` |
| REQ-05 | 6th request for the same number within the window is rate-limited | 5 prior requests already made for this number (regardless of whether each hit cooldown) | POST a 6th time | 429; `message` distinguishes this from the cooldown message (e.g. "Too many OTP requests for this number") | P0 — `[auto]` |
| REQ-06 | Per-IP rate limit on top of per-number | Requests for many distinct numbers from one IP exceed `app.mobile-otp.request-limit-per-ip` (default 20/60min) | POST for a new number after the IP cap is exhausted | 429 | P1 |
| REQ-07 | Dev/stage fixed-OTP allowlisted number returns the test-mode notice | `app.mobile-otp.fixed-otp.enabled=true` and number is in `allowed-numbers` (dev default: `+919999999999`) | POST for that number | 200; `notice: "Test mode: SMS is not sent."`; no real SMS attempted (no MSG91 call in logs) | P0 |
| REQ-08 | Non-allowlisted number in dev still attempts a real SMS | Fixed-OTP enabled, but number not in `allowed-numbers` | POST for a different number | 200; `notice: null`; MSG91 send is attempted (best-effort — failure with placeholder dev credentials is swallowed, not surfaced to the caller) | P1 |
| REQ-09 | Malformed/empty `mobileNumber` | — | POST `{mobileNumber: ""}` or omit the field | 400 | P1 |
| REQ-10 | Response never reveals whether the number is already registered | One number with an existing account, another without | POST both | Identical response shape for both (`challengeId`/`expiresInSeconds`/`resendAfterSeconds` only) — no `isExistingUser`-type field at this step | P0 |

## 2. Verify OTP

`POST /mobile/otp/verify`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| VER-01 | Correct code, brand-new number → signup | No user exists for this number; valid unexpired challenge | POST `{challengeId, otp}` | 200; `isNewUser: true`, `profileComplete: false`, `accessToken`/`refreshToken`/`sessionExpiresAt` present, `user.mobileVerified: true`. New `users` row: `userType=PARENT`, `userSource=PHONE`, `password=null`, `phoneVerified=true` | P0 — `[auto]` |
| VER-02 | Correct code, existing number → login, no duplicate account | User already exists for this number; valid unexpired challenge | POST `{challengeId, otp}` | 200; `isNewUser: false`, `user.id` equals the existing user's id; exactly one `users` row for that phone before and after | P0 — `[auto]` |
| VER-03 | Wrong code increments the attempt counter | Valid unexpired, unused challenge, `attemptCount=0` | POST with an incorrect `otp` | 400 ("Incorrect code."); challenge's `attempt_count` is now 1, still unused | P0 — `[auto]` |
| VER-04 | 5th wrong attempt invalidates the challenge | Challenge already at `attemptCount=4` | POST with an incorrect `otp` | 400 ("Incorrect code."); challenge is now `used=true`, `attempt_count=5` — a follow-up call with the **correct** code also 400s ("invalid or expired") | P0 — `[auto]` |
| VER-05 | Expired challenge is rejected | Challenge's `expires_at` is in the past | POST with the correct `otp` | 400 ("...expired...") | P0 — `[auto]` |
| VER-06 | Replay after a successful verify is rejected | Challenge already consumed by a prior successful verify | POST the same `{challengeId, otp}` again | 400 ("invalid or expired") — no second login/signup side effect | P0 — `[auto]` |
| VER-07 | Unknown `challengeId` | — | POST a random UUID as `challengeId` | 400, generic "invalid or expired" message (doesn't distinguish "never existed" from "expired/used") | P1 — `[auto]` |
| VER-08 | Concurrent verify for the same number via two different valid challenges never creates two accounts | Two separate, both-valid, both-correct challenges exist for the same number (edge case — e.g. two devices) | Fire both `/verify` calls concurrently | Both return 200 with the **same** `user.id`; exactly one `users` row exists afterward (unique constraint + retry-on-conflict path, not two half-created accounts) | P0 — `[auto]` |
| VER-09 | Deactivated/blocked account is rejected without logging in | A `users` row exists for this number with `is_active=false` / `status=DEACTIVATED` (or `DELETED`) | POST a valid, correct `{challengeId, otp}` | 401; no tokens issued; account status unchanged | P0 — `[auto]` |
| VER-10 | Successful verify sets `phoneVerified=true` even for a pre-existing unverified row | Existing user row has `phoneVerified=false` (e.g. created via some other path) | POST valid, correct `{challengeId, otp}` | 200; user's `phoneVerified` is now `true` | P1 |
| VER-11 | New account is never given a name, email, or password | Fresh signup via VER-01 | Inspect the created user | `name`, `email`, `password` all null — no fabricated values | P0 |

## 3. Session lifecycle

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| SESS-01 | `sessionExpiresAt` is ~72h from the verify call | Successful VER-01/VER-02 | Inspect `sessionExpiresAt` in the response | Equals login time + `app.mobile-otp.session-hours` (default 72h), within a few seconds | P0 |
| SESS-02 | Refreshing does not extend the session ceiling | Valid refresh token from a mobile-OTP login | `POST /auth/refresh` with that `refreshToken` | 200, new access+refresh token pair; the new refresh token's underlying `session_expires_at` (DB) is **identical** to the original, not recomputed | P0 |
| SESS-03 | Refresh is rejected once the session ceiling has passed | Refresh token whose `session_expires_at` is in the past (even if the token's own `expires_at` — 7d default — has not passed) | `POST /auth/refresh` | 401 | P0 |
| SESS-04 | Logout revokes the session | Valid refresh token from a mobile-OTP login | `POST /auth/logout` with that `refreshToken`, then `POST /auth/refresh` with the same token | Logout 200; the subsequent refresh attempt 401s | P0 |
| SESS-05 | Other login methods (Google/email/phone-password) are unaffected | — | Log in via `/login/google` or `/login/email`, inspect refresh behavior | No session-ceiling enforcement for these — refresh continues to work up to the token's own TTL, matching pre-existing behavior | P0 — regression guard |

## 4. Fixed-OTP / environment safety

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| ENV-01 | App refuses to start with the dev fixed-OTP bypass enabled under the prod profile | `app.mobile-otp.fixed-otp.enabled=true`, `spring.profiles.active=prod` | Start the app | Startup fails with a clear `IllegalStateException` from `MobileOtpFixedCodeGuard` — app never becomes ready | P0 — covered by `MobileOtpFixedCodeGuard`; add a dedicated unit test if not already present |
| ENV-02 | App starts normally with the dev fixed-OTP bypass enabled under dev/stage | `app.mobile-otp.fixed-otp.enabled=true`, profile `dev` or `stage` | Start the app | Starts normally; a warning is logged naming the allowlisted numbers | P0 |
| ENV-03 | Fixed code is never returned in any API response | Any request/verify call, either bypass or neither | Inspect every response body | The literal code (`123456` or configured value) never appears in `notice` or anywhere else in the JSON | P0 — security-sensitive |
| ENV-04 | OTP codes are never written to logs | Any request/verify call | Grep application logs after a full request+verify cycle | No 6-digit code appears in log output, hashed or otherwise (hash is fine in DB, never in logs) | P0 — security-sensitive |
| ENV-05 | Review-account bypass **is** allowed to start under the prod profile | `app.mobile-otp.review-account.enabled=true` with a valid code + 1-3 numbers, `spring.profiles.active=prod` | Start the app | Starts normally; a warning is logged naming the exact reviewer numbers, every boot | P0 |
| ENV-06 | Review-account bypass with missing code/numbers fails startup in any profile | `app.mobile-otp.review-account.enabled=true`, `code` blank or `allowed-numbers` empty | Start the app | Startup fails with a clear `IllegalStateException` — refuses to boot on obviously-broken config, in dev/stage too, not just prod | P0 |
| ENV-07 | Review-account allowlist is capped | `app.mobile-otp.review-account.allowed-numbers` has 4+ entries | Start the app | Startup fails validation (`@Size(max=3)`) — this bypass must stay small by construction, not just convention | P1 |
| ENV-08 | Review-account number gets no "test mode" notice | Number is in `review-account.allowed-numbers`, bypass enabled | `POST /mobile/otp/request` for that number | 200; `notice: null` (looks identical to a real send) — verified request/verify still succeeds with the configured code; no MSG91 call attempted (check logs) | P0 |
| ENV-09 | Review-account bypass still enforces expiry/attempts/single-use | Same as ENV-08 | Let the challenge expire, or exceed max attempts, or replay after success | Behaves exactly like a normal challenge — no special leniency for the reviewer number | P0 |

## 5. Cross-cutting

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| XC-01 | All error responses match the common error format | Any 4xx from either endpoint | Inspect response body | `timestamp`, `status`, `error`, `message`, `path` all present | P0 |
| XC-02 | Existing email/Google/phone-password login and signup are unaffected | — | Re-run existing `AuthServiceImpl`/`AuthController` test suite | All pre-existing auth tests still pass unchanged | P0 — regression guard, verified: full suite green (163/163) alongside this feature's tests |
| XC-03 | `users.phone` unique constraint holds under load | — | Load-test concurrent `/request`+`/verify` pairs across many distinct numbers | No duplicate-phone rows ever committed; constraint violations (if any) are handled per VER-08, never surfaced as a raw 500 | P1 |

## Notes / follow-ups (not blocking this feature)

- **Pre-existing bug found and fixed for this flow, but not for email OTP**: `OtpService.verify()` (email OTP — password reset, email change) had the same "attempt count never actually persists" bug this PRD's implementation initially had too (`@Transactional` rolling back the failed-attempt write alongside the exception used to report it). Fixed here via a `REQUIRES_NEW` transaction in the new mobile-OTP path; the fix was **reverted** for `OtpService.verify()` specifically because it broke two existing `AccountControllerApiTest` cases that rely on `@Transactional` test-rollback semantics (a `REQUIRES_NEW` nested transaction can't see data seeded earlier in an uncommitted outer test transaction). The email-OTP max-attempts limit is therefore still not enforced today — worth a dedicated follow-up ticket.
- MSG91 is the SMS provider (`app.sms.msg91.*`) — real send has not been exercised against a live MSG91 account/template in this work, only the request/response contract and the dev fixed-OTP bypass. First real-number send should be verified against a real MSG91 auth key + DLT-approved template before relying on it in stage/prod.
