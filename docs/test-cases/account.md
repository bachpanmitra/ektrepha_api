# Test Cases — Account (Identity, Login Methods, Notification Preferences, Delete)

Source: PRD "Parent-Side Profile & Booking Surfaces" §7 (Account) — the one screen in that PRD's
mapping table (§12) marked "Not built in this thread" at PRD-draft time. It has since been built:
`com.ektrepha.controller.UserController` (name/profile, `GET`/`PUT /api/v1/users/me`),
`com.ektrepha.account.controller.AccountController` (email/phone change, login methods,
password, delete — all under `/api/v1/users/me`), and
`com.ektrepha.notification.controller.NotificationController` (notification preferences +
device registration — the PRD flagged this as a schema gap needing a new table before it could
ship; `user_notification_preference` / `user_device` (migration 027) now exist).

No push/SMS/email delivery is wired to any of this yet — this module is preference/token storage
only (see `NotificationController`'s own comment, matches PRD §17's broader payment/notification
hard-blocker note). Automated coverage: `docs/postman/Ektrepha-ParentApp-Local.postman_collection.json`,
folder "Account (A1-A6)" and "Notification Preferences (A5)", exercises most of the P0 rows below
end-to-end against a running `Local` environment; no MockMvc test class exists yet for this module
(unlike `ParentControllerApiTest`/`BookingReadControllerApiTest` for the sibling modules).

Priority: P0 = blocks the screen if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Account Profile — Get / Update Name

`GET /api/v1/users/me`, `PUT /api/v1/users/me` (role: PARENT or NANNY — not role-gated)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-01 | Get own profile | Any logged-in user | GET `/users/me` | 200; `id`, `name`, `email`, `phone`, `emailVerified`, `phoneVerified`, `userSource`, `parentProfileComplete` | P0 — covered by Postman ("A1: GET /users/me") |
| AC-02 | `parentProfileComplete` reflects the Parent Profile module, not this one | No `parent` row, or one with a blank `firstName` | GET `/users/me` | `parentProfileComplete: false` — this field is a cross-module read (`ParentRepository.findByUserId(...).firstName` non-blank), not derived from anything on `users` itself | P1 — matches the "not implemented" gap noted in `docs/test-cases/parent-profile-and-addresses.md`'s Open Items; it **is** implemented, that note is stale |
| AC-03 | Update name | Any logged-in user | PUT `{"name": "Rahul Kumar"}` | 200; `name` updated on next GET too | P0 — covered by Postman ("A1/A2: PUT /users/me") |
| AC-04 | Update name to blank | — | PUT `{"name": ""}` | 400 | P0 — covered by Postman ("A2: PUT /users/me, blank name") |
| AC-05 | `PUT /users/me` cannot change email or phone | Any logged-in user | PUT with an `email`/`phone` key in the body (DTO has no such field) | 200; email/phone unchanged — deliberately impossible via this endpoint, per `UserController`'s comment (a stolen access token must not be able to silently swap the account's recovery address); the email/phone change+verify pair below is the only path | P0 — verified by construction, not exercised in Postman (JSON extra fields are just ignored, not rejected) |
| AC-06 | Get/update without auth token | — | Call either with no `Authorization` header | 401 | P0 |

## 2. Email Change

`POST /api/v1/users/me/email/change` → `POST /api/v1/users/me/email/verify`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-07 | Request change to a new, unused email | Logged-in user | POST `{"newEmail": "..."}` | 202; `sentTo`, `retryAfterSeconds: 30` | P0 — covered by Postman ("A2: POST email/change") |
| AC-08 | Request change to an email already used by another account | Another user owns that email | POST that `newEmail` | 409; "An account with email ... already exists" | P0 — not in Postman (needs a second seeded account); logic in `AccountServiceImpl.assertEmailAvailable` |
| AC-09 | Request change to the caller's own current email | — | POST `newEmail` == caller's existing email | 200/202 — `assertEmailAvailable` only rejects when the match belongs to a *different* user id, so re-requesting your own address is allowed, not a 409 | P2 |
| AC-10 | Verify with correct OTP | Valid pending change from AC-07, OTP known (via log/stub) | POST `{"newEmail", "otp"}` | 200; `email` updated, `emailVerified: true` on next `GET /users/me` | P0 — not exercised in Postman (collection only drives the wrong-OTP path since there's no way to read the real OTP through the API); logic in `AccountServiceImpl.confirmEmailChange` |
| AC-11 | Verify with wrong OTP | Pending change exists | POST wrong `otp` | 400 | P0 — covered by Postman ("A3: POST email/verify, wrong OTP") |
| AC-12 | Verify with an OTP issued for a *different* user's email change | Two users each requested a change; OTP from user B's flow, `newEmail` from user A's request | POST as user A with user B's `otp`/`newEmail` combo | 400; "This code was not issued for your account" | P1 — verified by construction (`AccountServiceImpl.confirmEmailChange`'s explicit ownership check, called out as closing a gap `OtpService` itself doesn't cover) |
| AC-13 | Old email untouched until verification succeeds | Pending change requested, never verified | GET `/users/me` before calling `/verify` | `email` still the old value — a request that's never verified must not partially commit | P1 — verified by construction, matches the inline comment "abandon mid-change never partially commits" |
| AC-14 | `newEmail` mismatch between change and verify calls | Change requested for email A | POST `/verify` with `newEmail` = email B (never requested) | Verification will not match a pending OTP issued for email A — falls through the same "wrong OTP" 400 path (`OtpService.verify` keys on `phoneOrEmail`) | P2 |

## 3. Phone Change

`POST /api/v1/users/me/phone/change`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-15 | Change to a new phone number | Client has already completed Firebase phone-auth for the new number client-side (see `PhoneChangeRequest` javadoc — this is a single-step endpoint, not change+verify like email) | POST `{"firebaseIdToken": "<valid>"}` | 200; `phone` updated, `phoneVerified: true` | P0 — not exercised in Postman (needs a real Firebase token, can't be scripted); logic in `AccountServiceImpl.changePhone` |
| AC-16 | Invalid/expired Firebase token | — | POST `{"firebaseIdToken": "not-a-real-token"}` | 400 | P0 — covered by Postman ("A2/A3: POST phone/change, invalid Firebase token") |
| AC-17 | Change to a phone number already registered to another account | Another user owns that phone (verified via their own Firebase token) | POST that identity's token | 409; "An account with phone number ... already exists" | P0 — not in Postman (needs two real Firebase identities); logic in `AccountServiceImpl.changePhone` |
| AC-18 | Change to the caller's own current phone number | — | POST a token identifying the caller's own existing phone | 200 — same "match is caller's own id" allowance as AC-09 | P2 |

## 4. Login Methods

`GET /api/v1/users/me/login-methods`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-19 | Login methods for an email/password account | Account signed up via email+password, no Google link | GET | 200; `googleConnected: false`, `googleEmail: null`, `emailVerified`/`email` populated, `passwordSet: true` | P0 — covered by Postman ("A4: GET login-methods") |
| AC-20 | Login methods for a Google-signup account | Account created via Google OAuth, no password ever set | GET | `googleConnected: true`, `googleEmail` = the Google account's email, `passwordSet: false` — drives the A4 "Google-only → Set a password" UI vs "Change password" | P1 — verified by construction (`getLoginMethods`'s `userSource == GOOGLE` branch), not exercised in Postman (collection's seed account is email/password) |
| AC-21 | `googleEmail` is null for a non-Google account even if `email` is set | Email/password account with a verified email | GET | `googleEmail: null` — deliberately distinct from `email`, since `email` may differ from whatever a since-linked/unlinked Google account used | P2 |

## 5. Set / Change Password

`POST /api/v1/users/me/password`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-22 | Change password with correct current password | Account already has a password | POST `{"currentPassword": "<correct>", "newPassword": "..."}` | 200; subsequent login must use the new password | P0 — covered by Postman ("A4: POST password, correct currentPassword") |
| AC-23 | Change password with wrong current password | Account already has a password | POST wrong `currentPassword` | 401 | P0 — covered by Postman ("A4: POST password, wrong currentPassword") |
| AC-24 | Set initial password for a Google-only account (no password yet) | `user.password == null` | POST with `currentPassword` omitted, valid `newPassword` | 200 — `currentPassword` check is skipped entirely when none exists yet (`AccountServiceImpl.setPassword`'s `if (user.getPassword() != null)` guard); this is the A4 "Google-only → Set a password" path | P0 — verified by construction, not exercised in Postman (seed account already has a password) |
| AC-25 | `newPassword` fails strength policy | — | POST `newPassword` under 8 chars | 400 — `@Size(min = 8, max = 100)` on `SetPasswordRequest` | P1 |
| AC-26 | Set password while omitting `currentPassword` when one already exists | Account has a password | POST `{"newPassword": "..."}`, no `currentPassword` | 401 — `currentPassword == null` fails the `!passwordEncoder.matches(...)` branch the same as a wrong password would | P1 |

## 6. Delete Account

`DELETE /api/v1/users/me`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-27 | Delete with correct confirmation, no active bookings | No parent row, or parent row with no PENDING/CONFIRMED/IN_PROGRESS bookings | DELETE `{"confirmation": "DELETE"}` | 204; `user.status = DELETED`, `deletedAt` set, `active = false`; all refresh tokens revoked | P0 — covered by Postman ("A6: DELETE /users/me, correct confirmation", full demo flow) |
| AC-28 | Wrong/missing confirmation text | — | DELETE `{"confirmation": "nope"}` (or `"delete"` lowercase — comparison is exact-match `"DELETE".equals(...)`) | 400; "Type DELETE to confirm account deletion" | P0 — covered by Postman ("A6: DELETE /users/me, wrong confirmation") |
| AC-29 | Delete blocked by an active booking | Parent has ≥1 booking in PENDING/CONFIRMED/IN_PROGRESS | DELETE `{"confirmation": "DELETE"}` | 409; "Cancel or complete your N upcoming booking(s) first" — count and singular/plural phrasing both correct | P0 — not in Postman (needs a seeded active booking); this is the PRD §7 cascade decision resolved as "lock access, block deletion until resolved" rather than orphaning/cascading the booking rows |
| AC-30 | Delete succeeds once blocking bookings reach a terminal state | Same parent as AC-29, all bookings now COMPLETED/CANCELLED | DELETE `{"confirmation": "DELETE"}` | 204 | P1 |
| AC-31 | Old refresh token rejected after deletion | Account deleted per AC-27 | POST `/auth/refresh` with the pre-deletion refresh token | 401 | P0 — covered by Postman ("A6 demo: refresh token now rejected") |
| AC-32 | Login blocked after deletion | Account deleted per AC-27 | POST `/auth/login/email` with the account's (still-correct) credentials | 401 | P0 — covered by Postman ("A6 demo: login now blocked") |
| AC-33 | Deleted account's booking rows are not deleted or anonymized | Account had a COMPLETED booking before deletion | Inspect `booking` table (or query as an admin/internal path) after AC-27 | Booking row still exists, unmodified — PRD §7 explicitly flags "does deletion also anonymize child data, or just lock access?" as needing legal/compliance sign-off; current implementation is access-lock only, no anonymization, no cascade delete | P1 — verified by construction (`deleteAccount` never touches `booking`/`children` rows), but the **anonymization question itself is still an open product/legal decision per the PRD**, not something this test can close out |
| AC-34 | Delete a user with no `parent` row at all | Account never created a parent row (never called `PUT /parents/me`, never added a child/address) | DELETE `{"confirmation": "DELETE"}` | 204 — `parentRepository.findByUserId(...)` is an `Optional`, active-booking check is simply skipped when absent | P1 |

## 7. Notification Preferences

`GET /api/v1/users/me/notification-preferences`, `PUT /api/v1/users/me/notification-preferences`

Schema gap the PRD (§7) flagged as blocking this screen — `user_notification_preference`
(migration 027) now exists, so this is built. No delivery channel (push/SMS/email) is actually
wired to read these yet; this is preference storage only.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-35 | Get preferences with no saved rows (defaults) | Fresh account, never called PUT | GET | 200; all 6 `NotificationCategory` values present, each defaulting to `pushEnabled/smsEnabled/emailEnabled: true`; `editable: false` for the 3 transactional categories | P0 — covered by Postman ("A5: GET notification-preferences (defaults)") |
| AC-36 | Attempt to disable a transactional (non-editable) category | — | PUT `{"preferences": [{"category": "BOOKING_CONFIRMATION", "pushEnabled": false, "smsEnabled": false, "emailEnabled": false}]}` | 200 (not rejected) — but the response shows all three channels forced back to `true` for that category; silent server-side override, not a 400, since a client showing this toggle at all is a client bug per the inline comment | P0 — covered by Postman ("A5: PUT — try to disable a transactional category (forced back to true)") |
| AC-37 | Disable a fully-toggleable category | — | PUT with `category: "PROMOTIONS"`, all channels `false` | 200; `PROMOTIONS` actually shows `pushEnabled/smsEnabled/emailEnabled: false` on this and subsequent GETs | P0 — covered by Postman ("A5: PUT — disable PROMOTIONS") |
| AC-38 | Update touches only the categories included in the request | Preferences previously set for `PROMOTIONS` only | PUT with only `REMINDERS` in the `preferences` array | 200; `PROMOTIONS`'s prior state is untouched, `REMINDERS` updated — this is a partial-array upsert per category, not a full-replace of the whole preference set | P1 — verified by construction (`updatePreferences` loops the request's own list, never touches categories absent from it) |
| AC-39 | `preferences` array empty | — | PUT `{"preferences": []}` | 400 — `@NotEmpty` on `NotificationPreferenceUpdateRequest.preferences` | P1 |
| AC-40 | Missing a required boolean field on one entry | — | PUT with an entry omitting `smsEnabled` | 400 — `@NotNull` on each `CategoryUpdate` field | P2 |
| AC-41 | Unknown category value | — | PUT with `category: "NOT_A_REAL_CATEGORY"` | 400 — JSON enum deserialization failure | P2 |
| AC-42 | Get/update without auth token | — | Call either with no token | 401 | P0 |

## 8. Device Registration

`POST /api/v1/users/me/devices`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-43 | Register a new push token | Fresh `pushToken` | POST `{"pushToken": "<guid>", "platform": "android"}` | 201; `id`, `platform: "android"`, `lastActiveAt` set | P0 — covered by Postman ("A5: POST devices (register push token)") |
| AC-44 | Invalid platform value | — | POST `{"pushToken": "<guid>", "platform": "windows"}` | 400 — `@Pattern(regexp = "ios\|android")` on `DeviceRegisterRequest` | P0 — covered by Postman ("A5: POST devices, invalid platform") |
| AC-45 | Re-register an existing `pushToken` (device re-registers, e.g. after reinstall/relogin) | `pushToken` already registered, possibly to a *different* user (e.g. shared/reset device) | POST the same `pushToken` under a different user's auth | 201 — `deviceRepository.findByPushToken(...)` re-associates the existing row to the new `user`/`platform`/`lastActiveAt` rather than rejecting on the `unique` constraint; the old owner silently loses this token | P1 — verified by construction (`registerDevice`'s find-or-create-by-token logic); worth confirming this reassignment behavior is actually the intended product decision for a shared/reset device, since it's a silent takeover with no ownership check |
| AC-46 | Missing `pushToken` | — | POST `{"platform": "android"}` | 400 — `@NotBlank` | P1 |
| AC-47 | Register without auth token | — | POST with no `Authorization` header | 401 | P0 |

## 9. Cross-cutting

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AC-48 | None of this module's endpoints are role-gated (PARENT vs NANNY) | Valid NANNY token | Call every endpoint above with it instead of a PARENT token | Same behavior as for a PARENT — Account is identity-level, not role-scoped, unlike `ParentProfileController`/`BookingReadController` which explicitly 403 a NANNY token | P1 — verified by construction (no `@PreAuthorize`/role check anywhere in `UserController`/`AccountController`/`NotificationController`), not exercised in Postman |
| AC-49 | Every endpoint above rejects no-auth requests | — | Call each with no token | 401 on all | P0 |

## Related endpoint, not in this PRD's scope

`POST /api/v1/users/identify` (`UserController.identify`) — find-or-create-by-email/phone for an
anonymous visitor filling out a pre-signup form (e.g. price quote). Not mentioned anywhere in the
PRD; no password is set on the resulting user, so it cannot itself be used to log in. Left out of
the numbered cases above; write a separate test-case file for it if/when a PRD covers the guest
quote flow it belongs to.

## Cross-cutting note on the PRD's open questions (§7, §13)

- **Account deletion semantics** — the PRD explicitly calls this an open product/legal decision
  ("does deletion also anonymize child data, or just lock access?"). AC-33 documents what's
  *actually implemented* (lock-only, no anonymization) — this is a description of current
  behavior, not confirmation that the product/legal question has been resolved.
- **Notification preferences schema gap** — the PRD flags this as blocking Account's "manage
  notifications" section pending a new table. That table (`user_notification_preference`,
  migration 027) exists and is covered in §7 above; delivery (actually sending a push/SMS/email
  when one of these fires) is still unbuilt, per the PRD's own §17/§14 payment-adjacent
  dependencies and the inline "no push/SMS/email delivery is wired to any of this yet" comment in
  `NotificationController`.

## Open items / not yet covered

- No MockMvc test class exists for this module yet (unlike `ParentControllerApiTest`/
  `BookingReadControllerApiTest`) — everything not marked "covered by Postman" above was verified
  by reading `AccountServiceImpl`/`NotificationServiceImpl` source, not by running a request.
- Cross-account cases (AC-08, AC-17) need a second seeded account in the Postman flow, which the
  collection doesn't currently set up for the Account folder (it does for `parent-profile-and-addresses`'s
  ownership checks — same pattern could be reused here).
- AC-15/AC-24 (real Firebase phone token, Google-only account) can't be scripted against a Local
  Postman run without a real external identity — needs either a Firebase emulator or a manually
  captured token to actually execute, not just verify by construction.
