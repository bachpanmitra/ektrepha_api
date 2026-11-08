# Ektrepha Nanny App — API Reference (for ektrepha-nanny-ui)

Handoff doc for the frontend team building `ektrepha-nanny-ui` against this backend. Companion to
the live OpenAPI spec — when the two disagree, the live spec wins, same rule as the parent-app
doc (`docs/API_REFERENCE.md`).

## Live spec (source of truth)

- **Interactive Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **Raw OpenAPI JSON**: `http://localhost:8080/v3/api-docs` — feed into `openapi-typescript`/`orval` for a typed client.

## Base URL & auth

- Local dev: `http://localhost:8080`
- Every endpoint below requires `Authorization: Bearer <accessToken>` from a **NANNY**-role account, except where noted.
- Login is the same mobile-OTP flow as the parent app (`POST /api/v1/auth/mobile/otp/request` then `/verify`, see `docs/API_REFERENCE.md`) — the difference is server-side (auto-signup there always creates `PARENT`; a nanny account is provisioned by ops, not self-signup). Access tokens are short-lived (15 min); refresh via `POST /api/v1/auth/refresh`.

## Error shape (every 4xx/5xx)

```json
{
  "timestamp": "2026-10-29T10:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Wrong code. 3 attempts left before a 15-minute lock.",
  "path": "/api/v1/nanny-bookings/7656/check-in",
  "code": "WRONG_OTP"
}
```

Read `message` for the user-facing reason. `code` is the machine-readable discriminator — branch on it, not on `message` text (translations/wording can change).

---

## Today's shift & Live Care (`/api/v1/nanny-bookings`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/nanny-bookings/today` | The caller's own booking for today, or `200` with an empty body if nothing is assigned. `{id, status, child, startTime, endTime, address, careNotes, todayAttendance}` — `status` is the booking lifecycle (`CONFIRMED`/`IN_PROGRESS`/...); `todayAttendance` is `{date, status, checkedInAt, checkedOutAt}`, `null` until today's `booking_attendance` row exists. This is a thin, nanny-app-specific shape — no price/review/`availableActions` fields exist here, don't expect them |
| GET | `/nanny-bookings/{id}` | Same shape as above, for one specific booking id (own bookings only — 404 otherwise) |
| POST | `/nanny-bookings/{id}/start` | Marks the shift's `Booking.status` as started (`IN_PROGRESS`). This is the lifecycle flag, **separate from** the daily OTP check-in below — a live-in nanny starts the booking once but checks in/out daily |
| POST | `/nanny-bookings/{id}/complete` | Marks the shift completed |

## Daily attendance check-in / check-out (`/api/v1/nanny-bookings/{id}`)

One `booking_attendance` row per booking per calendar day, hashed OTP codes (never sent to this app in plain text — only the parent's app ever sees the plain code), 200m geofence, 5-wrong-attempts lockout.

| Method | Path | Purpose |
|---|---|---|
| POST | `/nanny-bookings/{id}/check-in` | `{otp, lat, lng}` → `{bookingId, date, status, checkedInAt, checkedOutAt, checkInMethod, checkOutMethod, minutes}`. `otp` is the code the parent reads out loud or shows in their app. `lat`/`lng` must be within ~200m of the booking's address or this 422s with `TOO_FAR` — get a real device fix before submitting, don't submit `(0,0)` or a stale cached location |
| POST | `/nanny-bookings/{id}/check-out` | Same body/response shape. Requires an open check-in first; for a shift that starts before midnight and ends after, check-out still resolves against the **start day's** row — don't try to compute "today" yourself if the shift crosses midnight, just call this and let the server resolve it |
| GET | `/nanny-bookings/{id}/attendance?month=2026-10` | `{bookingId, month, days: [{date, status, checkedInAt, checkedOutAt, minutes, checkInMethod, checkOutMethod, manualByUserId}], totals: {daysWorked, offDays, absent}}`. One entry per scheduled day that month — a date simply absent from `days` was never scheduled (weekly off), not a miss. `manualByUserId` is always `null` here (only populated on ops' own admin view) |

### Attendance error codes

All returned as `422` unless noted, same error shape as above:

| `code` | Meaning | What to show |
|---|---|---|
| `WRONG_OTP` | Code doesn't match today's hash (includes using yesterday's code, or using the check-in code for check-out) | "Wrong code" + `message` (which includes attempts remaining) |
| `OTP_LOCKED` | **423**, 5 wrong tries in a row | 15-minute lockout — even the correct code is rejected until it clears; show a countdown if you have one, otherwise just the message |
| `TOO_FAR` | Device location is outside the ~200m geofence | "You're too far from the address to check in/out" — this is checked before the code, so a wrong-location attempt never burns one of the 5 attempts |
| `OFF_DAY` / `LEAVE_DAY` | No shift scheduled today, or an approved leave covers this date | There's nothing to check in against — this should be rare since `/today` already wouldn't return a booking in this state |

### What this app never sees

The plain OTP code is never present anywhere in a nanny-facing response or log — not on `/today`, `/{id}`, or the attendance month view. If a nanny needs the code, it comes from the parent (their app shows it via `GET /bookings/{id}/attendance/today`, see `docs/API_REFERENCE.md`).

---

## My verification documents (`/api/v1/nanny-verification`)

Self-service read access to the caller's own onboarding/verification records (KYC, background check, references, etc. — submission still goes through ops' existing upload flow, see Swagger for `POST /documents`).

| Method | Path | Purpose |
|---|---|---|
| GET | `/nanny-verification/documents` | The caller's own documents, one row per type (latest submission wins on a resubmission): `[{id, type, status, rejectionReason, createdAt, reviewedAt, expiryDate}]`. `type` ∈ `ID_PROOF`, `BACKGROUND_CHECK`, `ADDRESS_PROOF`, `EDUCATION`, `LIVENESS_SELFIE`; `status` ∈ `PENDING`/`VERIFIED`/`REJECTED`/`EXPIRED`. `rejectionReason` is only non-null when `status` is `REJECTED` — show it next to a "resubmit" CTA |
| GET | `/nanny-verification/documents/{id}/view-url` | `{viewUrl, expiresInSec}` — a short-lived (15 min) presigned URL to view one of the caller's own uploaded documents (e.g. "view what I submitted"). `404`s for a document id that isn't the caller's own — same 404 either way, doesn't reveal whether the id exists at all. Every call here is recorded in an access-log for audit, so don't poll this speculatively — fetch it only when the user actually taps "view" |

Note: in local/dev, `viewUrl` can come back as a non-functional placeholder if the backend's GCS bucket connectivity is disabled (`app.gcs.enabled=false`, the default for a dev machine with no bucket IAM grant) — this is a backend environment setting, not an app bug, if links don't resolve locally.

---

## Conventions shared with the parent app

Same as `docs/API_REFERENCE.md` §"Conventions worth knowing before you build": pagination shape, money as plain `BigDecimal` JSON numbers, ISO-8601 UTC timestamps, enums sent as name strings not codes.

## Questions / spec drift

If Swagger UI shows something this doc doesn't mention, trust Swagger UI. This file is hand-maintained and can go stale; the generated spec can't.
