# Ektrepha Parent App — API Reference (for ektrepha-ui)

This is the handoff doc for the frontend team building `ektrepha-ui` against this backend. It's a **companion** to the live, always-accurate OpenAPI spec — when the two disagree, the live spec wins, because this file is hand-maintained and the spec isn't.

## Live spec (source of truth)

- **Interactive Swagger UI**: `http://localhost:8080/swagger-ui/index.html` — browse every endpoint, try requests live, see exact request/response schemas.
- **Raw OpenAPI JSON**: `http://localhost:8080/v3/api-docs` — feed this into `openapi-typescript`, `orval`, or any codegen tool to generate a typed API client instead of hand-writing fetch calls.
- Both are public (no auth needed to view the docs themselves — you still need a token to call the actual endpoints).

Run `openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.ts` (or point your codegen tool of choice at that URL) to get typed request/response interfaces for every endpoint below, generated directly from the server's DTOs — never hand-transcribed, never stale.

## Base URL & auth

- Local dev: `http://localhost:8080`
- Every endpoint except `/api/v1/auth/**`, `/api/health`, `/api/version` requires `Authorization: Bearer <accessToken>`.
- Get a token: `POST /api/v1/auth/signup/email` or `POST /api/v1/auth/login/email` (also `/phone`, `/google`, and mobile-OTP variants below — see Swagger UI for full request/response shapes, auth wasn't built in this workstream).
- Access tokens are short-lived (15 min default). Refresh via `POST /api/v1/auth/refresh` with the `refreshToken`.

## Error shape (every 4xx/5xx)

```json
{
  "timestamp": "2026-09-19T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "firstName: must not be blank",
  "path": "/api/v1/parents/me"
}
```

Always read `message` for the user-facing reason — never parse `error` (it's just the HTTP reason phrase).

## Mobile OTP Login/Signup (`/api/v1/auth/mobile/otp`)

Combined login-or-signup by mobile number — no separate signup step. **Unauthenticated** (like every other `/api/v1/auth/**` route).

| Method | Path | Purpose |
|---|---|---|
| POST | `/mobile/otp/request` | `{mobileNumber}` (any format; normalized server-side to E.164) → 200 `{challengeId, expiresInSeconds, resendAfterSeconds, notice}`. Calling this again for the same number **is** the resend action — it invalidates the previous challenge and returns a new `challengeId`. Before `resendAfterSeconds` has elapsed since the last call, or if the per-number/per-IP rate limit is hit, this 429s instead — read `message` for which. `notice` is non-null (`"Test mode: SMS is not sent."`) only in dev/stage fixed-OTP test mode; treat it as free text to display verbatim when present, otherwise show nothing (a real SMS was sent) |
| POST | `/mobile/otp/verify` | `{challengeId, otp}` → 200 `{accessToken, refreshToken, sessionExpiresAt, isNewUser, profileComplete, user: {id, mobileNumber, mobileVerified}}`. Wrong/expired/already-used/max-attempts-exceeded codes all 400 with a `message` explaining which — the client only needs to distinguish 400 (retry with a new code) from 429 (from the request step, wait) |

Notes for the client:
- `isNewUser: true` means an account was just auto-created (role `PARENT`, no name/email/password) — send it straight to Home, not an onboarding form; use `profileComplete` (same meaning as `parentProfileComplete` elsewhere) to decide whether to show the "complete your profile" nudge later, same as any other login method.
- `sessionExpiresAt` is a hard server-enforced ceiling ~72h out (`app.mobile-otp.session-hours`) — `POST /auth/refresh` keeps working up to that instant but never extends it. Store it and stop silently retrying `/refresh` once passed; treat it like any other "session expired, please log in again" case.
- Never prefill or hint at any OTP value in the UI, even in dev — the dev fixed-code bypass is a backend-only allowlist (specific test numbers), not something the app should know about or special-case.

## What's NOT built yet — don't wire up screens for these

- **Payments**: no itemized price breakdown anywhere. Every booking response has a single `totalAmount`, nothing else. No receipts/invoices.
- **Notification delivery**: `GET/PUT /users/me/notification-preferences` and `POST /users/me/devices` exist and work, but nothing actually sends a push/SMS/email. Toggling a preference has no observable effect yet.
- **Multi-guardian**: `GET /children/{id}/guardians` is read-only. No "invite a co-parent" flow exists.
- **Photo upload**: no endpoint for parent or child photos. `photoUrl` fields will always be `null`.
- **Booking history is a live join, not a snapshot**: if a parent renames a child, old booking cards will show the new name. Don't build any "as it was at booking time" UI expecting historical accuracy on child/nanny names.

---

## Account (`/api/v1/users/me`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/users/me` | Identity summary — name, email/phone + verified flags, `parentProfileComplete` (drives "complete your profile" nudge) |
| PUT | `/api/v1/users/me` | Update `name` only. Email/phone go through the change+verify flow below — this endpoint 400s if you send them |
| POST | `/api/v1/users/me/email/change` | `{newEmail}` → 202, sends an OTP to the **new** address. Response includes `retryAfterSeconds` — use it for the resend cooldown countdown, don't hardcode 30s |
| POST | `/api/v1/users/me/email/verify` | `{newEmail, otp}` → 200, applies the change. Old email stays active until this succeeds |
| POST | `/api/v1/users/me/phone/change` | `{firebaseIdToken}` — **one step, not two**. The client completes Firebase phone-auth for the new number first, then posts the resulting ID token here. (Unrelated to the mobile-OTP *login* flow below — this endpoint still uses Firebase, not backend SMS, and is unaffected by that work.) |
| GET | `/api/v1/users/me/login-methods` | `{googleConnected, googleEmail, phoneVerified, phone, emailVerified, email, passwordSet}` — use `passwordSet` to decide whether to show "Set a password" vs "Change password" |
| POST | `/api/v1/users/me/password` | `{currentPassword?, newPassword}` — `currentPassword` required only if `passwordSet` was `true` |
| DELETE | `/api/v1/users/me` | `{confirmation: "DELETE"}` (must be that exact string) → 204. 409s with a count ("Cancel or complete your 2 upcoming bookings first") if active bookings exist |

## Notification Preferences (`/api/v1/users/me`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/notification-preferences` | Always returns exactly 6 rows (one per category), even if the user never touched settings — defaults are all-enabled |
| PUT | `/notification-preferences` | `{preferences: [{category, pushEnabled, smsEnabled, emailEnabled}, ...]}` |
| POST | `/devices` | `{pushToken, platform: "ios"|"android"}` — register a push token. No-op today (no delivery pipeline), safe to call anyway |

Categories: `BOOKING_CONFIRMATION`, `NANNY_EN_ROUTE`, `CARE_START_END` (non-negotiable — `editable: false`, server forces these to enabled no matter what you send), `REMINDERS`, `REVIEWS`, `PROMOTIONS` (freely toggleable). **Don't render a toggle at all for the non-editable three** — showing a toggle that silently snaps back to on is worse UX than not showing one.

## Parent Profile (`/api/v1/parents/me`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/parents/me` | `{id, firstName, lastName, photoUrl, primaryCity, primaryState}`. Returns `id: null` and everything else `null` if the profile was never completed — **treat this as an empty state, not an error** |
| PUT | `/api/v1/parents/me` | `{firstName, lastName?}` — upsert, creates the row on first call. `lastName` is optional (single-name users) |

## Address Book (`/api/v1/parents/me/addresses`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/addresses` | List, primary sorts first. Empty array if none — no parent profile needed to call this |
| POST | `/addresses` | `{label: "HOME"|"WORK"|"OTHER", addressLine1, addressLine2?, landmark?, accessNotes?, pincode, city, state, lat?, lng?, makePrimary?}`. **First address for a parent always becomes primary**, ignoring `makePrimary`. Saves even for a non-serviceable pincode — check the response's `serviceable` flag and show the waitlist CTA instead of blocking the save |
| PUT | `/addresses/{id}` | Full update, same body shape |
| PUT | `/addresses/{id}/primary` | Atomically swaps primary — exactly one address is ever primary |
| DELETE | `/addresses/{id}` | 409 if a non-terminal booking (pending/confirmed/in-progress) references it — show "edit instead of delete" |

`accessNotes` is a real field, worth its own input on the form — it's what actually gets a nanny to the right door.

## Child Profiles (`/api/v1/parents/me/children`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/children` | List. Each item has `ageDisplay` (server-computed: "14 months" under 24mo, "5 years" after — don't recompute this client-side) and `allergies` (chip-worthy on the list card, not just detail) |
| GET | `/children/{id}` | Full detail incl. `careNotes`, `guardianCount`, `relationship`, `primaryContact` |
| POST | `/children` | `{firstName, lastName?, dob, gender?}`. First child for a parent auto-becomes `primaryContact: true` |
| PUT | `/children/{id}` | Update basic info — does not touch care notes |
| PUT | `/children/{id}/care-notes` | `{allergies?, medicalNotes?, routine?, comfort?, doNot?}` — **omitting `allergies` leaves it untouched; only an explicit array (including `[]`) changes it.** This is deliberate — a partial update must never silently wipe a safety-critical field. Don't "clean up" a payload by dropping fields the user didn't touch if `allergies` is one of them |
| GET | `/children/{id}/guardians` | Read-only list of everyone linked to this child |
| DELETE | `/children/{id}` | Unlinks from this parent only — the child record survives (needed so past bookings keep rendering). 409 if a non-terminal booking references this child |

A request for a child not linked to the caller returns **403**, not 404 — don't special-case 404 for "not my child," it won't happen.

## Elder Profiles (`/api/v1/parents/me/elders`)

Elder Care's care-recipient profile — the senior_care counterpart of Child Profiles above, same multi-guardian shape (a `parent_elder` join table, not a direct owner FK).

| Method | Path | Purpose |
|---|---|---|
| GET | `/elders` | List: `{id, firstName, lastName, photoUrl, age, gender, relationship, mobility, conditions}`. Soft-deleted elders never appear here |
| GET | `/elders/{id}` | Full detail: list fields + `{allergies, medicationNotes, preferredLanguage, emergencyContactName, emergencyContactPhone, doctorName, doctorPhone}` |
| POST | `/elders` | `{firstName, lastName?, age, gender?, relationship?, mobility, conditions?, allergies?, medicationNotes?, preferredLanguage?, emergencyContactName?, emergencyContactPhone?, doctorName?, doctorPhone?}`. `age` must be 40–120. First elder for a parent auto-becomes the primary contact (not surfaced in the response, same as the internal-only bookkeeping on Child Profiles) |
| PUT | `/elders/{id}` | Full update, same body shape as POST |
| DELETE | `/elders/{id}` | Soft delete (sets `removedAt` server-side) — 409 if a non-terminal booking references this elder |

`relationship` is one of `FATHER, MOTHER, GRANDFATHER, GRANDMOTHER, FATHER_IN_LAW, MOTHER_IN_LAW, OTHER` (optional). `mobility` is one of `INDEPENDENT, NEEDS_SUPPORT, WHEELCHAIR, BEDRIDDEN` (required). A request for an elder not linked to the caller returns **403**, same convention as Child Profiles.

### Elder Care bookings (`/api/v1/hourly-care/**`)

The existing Hourly/Daily/Monthly care endpoints (availability, price, quote, bookings, monthly/availability, monthly/bookings) now take two additional **optional** fields, appended at the end of each request body: `serviceTypeCode` (`"childcare"` or `"senior_care"` — omit/null for `"childcare"`, today's behavior) and `elderId`. For `senior_care`, `elderId` is required and `childId` must be omitted; for `childcare`, `elderId` must be omitted. A mismatched combination is a 400. Senior care prices identically to childcare in every zone that offers it, and is excluded from the FIRST5_FEE_WAIVER free-trial — that trial never widened past childcare.

Every booking status/card/detail response (`BookingStatusResponse`, `BookingCardResponse`, `BookingDetailResponse`) now carries an `elder: {id, firstName, age, relationship, mobility}` field alongside the existing `child` field — `null` for a childcare booking. `GET /bookings` also accepts an optional `?serviceTypeCode=` filter.

### Live-in care (`/api/v1/hourly-care/live-in/**`, role `PARENT`)

An in-house helper who lives in the family's home to look after a child (`childcare`) or an elder (`senior_care`), booked in whole months (1–3). Same pay-first, team-assigns flow as Monthly care: reserve → pay → ops assigns a helper. `childId`/`elderId`/`serviceTypeCode` follow the Elder Care rules above.

| Method | Path | Purpose |
|---|---|---|
| POST | `/live-in/availability` | `{addressId, startDate, months, weeklyOffDay?, childId?, serviceTypeCode?, elderId?}` → `{available, summary: {months, startDate, endDate, onDutyDays, monthlyRate, subtotal, platformFee, platformFeeGst, total, currency}, unavailableReason}`. `available: false` when the zone has no live-in rate configured |
| POST | `/live-in/bookings` | Above + `{dutyStartTime, dutyEndTime, accommodation: PRIVATE_ROOM\|SHARED_ROOM, mealsProvided, careNotes?}` → 201 `{id, status, onDutyDays, totalAmount, priceBreakdown, arrangement}`. Pay `id` via the existing `/bookings/{id}/payment`, poll it via `/bookings/{id}/status` |
| GET | `/live-in/bookings/{id}` | The stay's terms and price, from any day id of the stay. 404 for a non-live-in booking |

- **Price** = the zone's `liveInMonthlyPrice` × `months`, plus the usual platform fee + GST. The weekly off day does not reduce it. Not eligible for the FIRST5_FEE_WAIVER trial.
- **Dates**: `endDate = startDate + months − 1 day` (a 1-month stay from 15 Jan runs to 14 Feb). `startDate` must not be in the past. `weeklyOffDay` is a `DayOfWeek` (`MONDAY`…`SUNDAY`), or null for no fixed weekly off.
- **Storage**: one booking row per on-duty day (00:00–24:00 IST, `frequency: LIVE_IN`), linked by `recurrenceGroupId`. The whole stay is billed on the first (anchor) day; every other day carries 0.
- **Ops** (`/api/v1/admin/hourly-care/bookings/{id}/assign` and `/reassign`, unchanged paths): assigning any day of a stay assigns that helper to every day still awaiting assignment (409 if they're booked elsewhere during the stay). Reassigning a day swaps that day and every later not-yet-started day; earlier days stay with the original helper.
- **Pricing admin**: `liveInMonthlyPrice` is a new optional field on the zone pricing create/update/bulk requests and responses, and `LIVE_IN` is a new `pricingMode`. On update/bulk save, omitting `liveInMonthlyPrice` **keeps** the stored value, so older admin clients can't wipe it.
- **Nanny app**: `GET /api/v1/nanny-bookings/today` and `/{id}` now also return `elder` and `liveIn: {startDate, endDate, months, weeklyOffDay, dutyStartTime, dutyEndTime, accommodation, mealsProvided}` (null on a visiting booking).

## Bookings (`/api/v1/bookings`)

### Reads

| Method | Path | Purpose |
|---|---|---|
| GET | `/bookings?scope=active\|history&page=&pageSize=` | `scope` is **required** — omitting it is a 400, not "show everything." `active` = pending/confirmed/in_progress, sorted soonest-first. `history` = completed/cancelled, sorted most-recent-first |
| GET | `/bookings/{id}` | Full detail. `elapsedSeconds` is non-null only when `IN_PROGRESS`, server-clock-derived — never compute this from the device clock. `availableActions` is a server-computed array (`["CANCEL"]`, `["CANCEL","CONTACT"]`, or `[]`) — **drive your primary action button off this array, not off `status` string matching**, or the app and backend will drift the moment a business rule changes |

### Writes

| Method | Path | Purpose |
|---|---|---|
| POST | `/bookings` | `{nannyId, serviceTypeId, childId?, addressId, startTime, endTime}` — `childId` is required when the service type resolves to `childcare` (400 if missing). Prices live via the pricing engine; `totalAmount` in the 201 response is real, not a placeholder. 409 if the nanny is already booked in that window — **this is a normal race, show "pick another slot," not a generic error** |
| POST | `/bookings/{id}/cancel` | `{reason?}` (optional, free text) → 200 with the updated booking. Immediate, no fee tiers — **no payment was ever captured, so there's nothing to refund.** 409 if already `IN_PROGRESS` ("use end-care-early instead," which doesn't exist yet) or already `COMPLETED`/`CANCELLED` |
| GET | `/bookings/{id}/contact` | `{nannyPhone}` — **403 unless the booking is `CONFIRMED` or `IN_PROGRESS`.** There's no number-masking vendor integrated; this is the real phone number, gated purely on booking status. Don't show a "call" button on a pending booking card |
| GET | `/bookings/{id}/rebook-context` | Prefill data for H4's rebook flow: `{nanny, nannyAvailable, unavailableReason, child, childStillLinked, address, addressStillExists, previousTotal, currentEstimate, priceChangeNote}`. If `nannyAvailable` is `false`, show `unavailableReason` and offer "find similar" instead of proceeding. If `currentEstimate` differs from `previousTotal`, show `priceChangeNote` — **never let a price change be silent** |

## Nanny Profile & Verification & Reviews (`/api/v1/nannies/{id}`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/nannies/{id}` | Public profile — bio, skills, languages, `verified` (boolean), `ratingAvg`/`reviewCount`, `recentReviews` (max 5, masked reviewer names like "Anjali M.") |
| GET | `/nannies/{id}/verification` | `{overallStatus, completedCount, totalCount, items: [{type, label, description, status, verifiedAt}]}` — always 5 items (one per verification type), even for types never submitted (shown as `PENDING`). **Never expect or render raw document data** — the server deliberately never sends `s3Key`, `vendorReferenceId`, or rejection details |
| GET | `/nannies/{id}/reviews?page=&pageSize=` | Paginated review list, same reviewer-name masking as above |

A malformed or missing `{id}` (e.g. `/nannies/` with nothing after the slash, or a non-numeric id) now returns a clean `400`/`404` — safe to build error-state UI around normal HTTP semantics here, no special-casing needed for garbage input.

---

## Conventions worth knowing before you build

- **Pagination** shape is consistent everywhere it appears: `{items: [...], page, pageSize, totalElements, totalPages}`.
- **Money** fields are `BigDecimal` → JSON numbers with 2 decimal places (e.g. `1023.75`). Format currency client-side; the API never sends a formatted string.
- **Timestamps** are ISO-8601 UTC instants (`"2026-09-25T10:00:00Z"`). Convert to the device's local timezone for display — the API never assumes one.
- **Enums** (booking `status`, address `label`, verification `type`/`status`) are sent as their name strings (`"PENDING"`, `"HOME"`), not codes. Match on the string.
- **Ownership errors are deliberately inconsistent-looking on purpose**: some return 400 ("no such address"), some 403 ("doesn't belong to you"), some 404 ("no booking found"). This isn't sloppiness — each matches an existing convention for that resource type, chosen so a non-owner can't distinguish "doesn't exist" from "not yours." Don't try to unify these into one status code client-side; branch on the specific endpoint's documented behavior.

## Questions / spec drift

If Swagger UI shows something this doc doesn't mention, trust Swagger UI — it's generated from the code and can't go stale the way this file can. File a note back to the API team if the two genuinely disagree; that's a bug in this doc, not in the API.
