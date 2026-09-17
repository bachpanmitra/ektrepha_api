# Ektrepha API — Project Overview

A living snapshot of everything built in this repo so far. Generated 2026-09-16 from the actual code (not from memory) — regenerate/update this doc whenever a module changes shape, rather than trusting it blindly after a big refactor.

## Tech stack

- Java 17, Spring Boot 4.1.1, Maven (`./mvnw`)
- PostgreSQL 16 (Liquibase migrations), Redis in `docker-compose.yml` (not yet wired into the app — see "Known gaps" below)
- Spring Web, Spring Data JPA, Spring Validation, Spring Boot Actuator
- Spring Security (stateless, JWT-based) + OAuth2 client, BCrypt, JJWT 0.12.6
- Firebase Admin SDK (phone OTP verification), Google API client (Google Sign-In token verification)
- AWS S3 SDK, Caffeine (in-memory caches), Brevo (transactional email, via plain `RestClient`)
- Lombok

## Domain model at a glance

The app is a childcare/nanny-booking marketplace with three user roles — `PARENT`, `NANNY`, `ADMIN` — plus a `GUEST` user source for anonymous leads. Core entities: `User`, `Nanny`, `NannyServiceArea`, `NannyVerification`, `Booking`/`BookingRequest`, `Review`, `Children`/`ParentChild`, `ServiceabilityPincode`/`ZoneArea`, `ZonePricingRule`/`ZoneServicePricing`/`DynamicPricingConfig`, `WaitlistSignup`, `Otp`/`RefreshToken`.

---

## 1. Auth module (`com.ektrepha.auth`)

Base path `/api/v1/auth`, all endpoints `permitAll` (see Security section). Four independent identity channels feed one `User` table and one JWT/refresh-token session model.

### Sign up
| Endpoint | What it does |
|---|---|
| `POST /signup/google` | Verifies a Google ID token, creates (or idempotently recognizes) a user tied to the Google identity. Sends a "set a password" email if the account has no password yet. Blocks `role=ADMIN` self-registration. |
| `POST /signup/phone` | Verifies a Firebase ID token (phone OTP already completed client-side), creates a password-protected account, marks phone verified. |
| `POST /signup/email` | Email + password signup; sends an email-verification link (UUID token, not yet consumed by a verify endpoint). |
| `POST /register` | Combined email+phone+password signup (checks both aren't already taken). |

### Login
| Endpoint | What it does |
|---|---|
| `POST /login/google` | Looks up by Google ID; 404s if no prior signup. |
| `POST /login/phone` | Password login by normalized phone (`+91XXXXXXXXXX`), gated by per-identifier lockout. |
| `POST /login/email` | Password login by email, same lockout gate. |

### Session lifecycle
- `POST /refresh` — rotates an opaque refresh token (old one revoked, new pair issued); rejects revoked/expired tokens.
- `POST /logout` — revokes a refresh token (no-op success if already unknown/revoked).

### Password
- `POST /password/forgot` — emails a 6-digit OTP for `RESET_PASSWORD` purpose.
- `POST /password/forgot/phone` — **stubbed**: confirms the account exists, then explicitly throws "not supported yet, use email" (no SMS provider wired up).
- `POST /password/reset` — verifies the OTP, sets new password, revokes all the user's active refresh tokens (forces re-login everywhere).
- `POST /password/reset/phone` — resets via a fresh Firebase ID token instead of an OTP (phone already re-verified client-side by Firebase).

### Supporting services (`auth.impl` / `auth.security`)
- **`OtpServiceImpl`** — generates a 6-digit numeric OTP, BCrypt-hashes it before storing, TTL + max-attempt limit both from `app.otp.*` config. `verify()` enforces: OTP exists → not expired → under attempt cap → code matches, invalidating (`used=true`) on success or on hitting the attempt cap.
- **`EmailServiceImpl`** (extends `AbstractEmailSender`) — sends OTP emails, password-setup emails, password-reset emails, verification emails, all through Brevo.
- **`AbstractEmailSender`** (`com.ektrepha.email`) — shared Brevo wiring (`https://api.brevo.com/v3/smtp/email`) plus one branded HTML shell (`Ektrepha` header, cream/dark-green palette) that every email body gets wrapped in. Sends are **best-effort**: a Brevo failure is logged and swallowed, never fails the triggering operation (signup, booking, etc.), and `send()` returns a boolean so callers don't log a false "sent" message when it actually failed.
- **`LoginAttemptServiceImpl`** — in-memory (`ConcurrentHashMap`), per-identifier (email or phone) failure counter; locks out for `app.login-lockout.lockout-minutes` after `max-failures` consecutive failures. Single-instance only (no Redis yet).
- **`RateLimiterServiceImpl`** / **`RateLimitFilter`** (`com.ektrepha.config`) — separate, coarser guard: per-client-IP token bucket (Caffeine-backed, self-evicting, capped at 100k entries) applied to *every* request before auth/business logic runs. Configurable capacity/window/on-off via `app.rate-limit.*`; returns `429` with `Retry-After`. Client IP resolved from `X-Forwarded-For` (trusted only because it's expected to be overwritten by a fronting proxy) falling back to socket address.
- **`JwtServiceImpl`** — HS256 JWT, subject = user id, claims = `userType`, optionally `email`/`phone`. TTL from `app.jwt.access-token-ttl-minutes`.
- **`GoogleIdTokenVerifierServiceImpl`** / **`FirebaseTokenVerifierServiceImpl`** — verify third-party identity tokens (Google Sign-In / Firebase phone auth) before trusting the identity they assert.
- Refresh tokens are opaque (64 random bytes, base64url), stored server-side in `refresh_token` table — not JWTs themselves.

### Recent work on this branch (`email_otp_imp`)
Per git log: phone-number validation/normalization, a phone forgot-password stub, login flow fixes, a `RateLimiterServiceImplTest` constructor fix, and `GET /booking-requests/me`. Earlier on this branch: the Brevo/branded-HTML email rework, the shared `AbstractEmailSender` extraction, and the per-IP rate limiter.

---

## 2. Users (`com.ektrepha.controller.UserController`)

Base path `/api/v1/users`.

- `GET /me` (auth required) — current user's `{id, name, email, phone}`, used to prefill forms.
- `POST /identify` (public) — find-or-create a **guest** user by email or phone (normalizes email to lowercase, phone to `+91XXXXXXXXXX`; requires 10 digits). No password is set, so this account can't log in until a real signup/reset happens later. Used to attach an anonymous visitor (e.g. someone who just got a price quote) to a real user row before they complete signup.

---

## 3. Booking Requests (`com.ektrepha.bookingrequest`)

Base path `/api/v1/booking-requests`. This is the "lead capture" booking flow (distinct from the older `com.ektrepha.controller.BookingController` at `/api/v1/bookings`, which is a simpler, parent-only, authenticated booking creation endpoint).

- `POST /` (public) — submits a booking request. Deliberately unauthenticated: a guest who just got a price quote via `/users/identify` hasn't logged in yet, but is already tied to a real `User` row by that point. Fires a booking-confirmation email (via the shared `AbstractEmailSender`).
- `GET /me` (auth required, newest endpoint — commit `445d632`) — lists the caller's own booking requests. User id is taken from the JWT (`Authentication#getName()`), never from a request param, so a caller can only ever see their own bookings.

Schema: `014-booking-requests-schema.sql`, extended by `017-booking-requests-additional-fields.sql` (adds `frequency`: `1=ONE_TIME, 2=REPEAT_WEEKLY`, coded via `BookingFrequencyConverter`).

---

## 4. Nanny Search & Marketplace (`com.ektrepha.nannysearch`)

Base path varies by controller:

- `POST /api/v1/nanny-search` (role `PARENT`) — filtered, geo-bounded, ranked nanny search. POST because the payload carries lists (languages/skills) and a time-window object. Search bounds (`allowed-radii-km`, `default-page-size`, `candidate-fetch-limit`) come from `app.search.*`.
- `GET /api/v1/nanny-search/languages`, `GET /api/v1/nanny-search/skills` (authenticated) — filter-form option lists.
- `PUT /api/v1/nannies/me/service-area` (role `NANNY`) — idempotent set/edit of the authenticated nanny's own service area (one row per nanny).
- `POST /api/v1/reviews` (role `PARENT`) — submits a rating/comment for a completed booking.

Supporting entities: `Nanny`, `NannyServiceArea`, `Review`, `Language`, `Skill`, `CaregiverZoneMapping`, ranking config (`RankingConfig`, `RankingFactor`). Nanny verification lives in a sibling top-level package, `com.ektrepha.verification` (`NannyVerificationService`, a rollup calculator, and a `VerificationDriftAuditJob`), gated to `/api/v1/nanny-verification/**` for roles `NANNY`/`ADMIN`.

Seed data: `007-nanny-catalog-seed-data.sql`.

---

## 5. Serviceability (zones & pincodes) (`com.ektrepha.serviceability`)

Base path `/api/v1/serviceability`, all public/pre-signup discovery endpoints:

- `GET /search` — "is this available near me". Exactly one of `pincode`, `query`, `(lat, lng)`, or `(city, state)` is expected.
- `GET /live-zones` — marketing list of live areas for the homepage.
- `GET /localities` — typeahead for picking an area, modeled on quick-commerce apps (Zepto/Blinkit/Instamart) — a lighter sibling to `/search` for the same discovery moment.
- `POST /waitlist` (also `com.ektrepha.serviceability.controller.WaitlistController`) — join the waitlist for a not-yet-serviceable area.

Admin (`/api/v1/admin/**`, role `ADMIN`):
- `AdminPincodeController` — `POST /admin/pincodes`, `PUT /admin/pincodes/{id}/status`, `POST /admin/pincodes/bulk-import`.
- `AdminZoneController` — `GET/POST /admin/zones`, `PUT /admin/zones/{id}`.
- `AdminServiceTypeRolloutController` — `/admin/zones/{zoneId}/service-types/{serviceTypeId}` rollout control.
- Geocoding: `com.ektrepha.config.properties.AppProperties.Geocoding` wires OpenStreetMap's free Nominatim API (rate-limited by `min-interval-millis`, restricted to India via `country-codes` so short ambiguous queries don't match places on the other side of the world), with a `GeocodeCache` entity to avoid re-geocoding.
- Event-driven piece: `serviceability.event` package (service-type rollout events).

Seed/schema history: `008` (schema), `009` (Bangalore pincode seed), `011` (104 named-locality zones for Bangalore, replacing broad zones from `009`), `012` (backfills zone centroids for those 104 zones), `015` (Gurgaon/Patna/Noida **placeholder** zones — pincode-level only, not locality-level like Bangalore).

---

## 6. Pricing (`com.ektrepha.pricing`)

- `POST /api/v1/pricing/calculate` (public) — a visitor needs a quote before signing up or picking a caregiver.

Admin (role `ADMIN`):
- `AdminPricingController` — `PUT /admin/pricing/{id}`, and CRUD for pricing rules under a pricing config (`GET/POST /admin/pricing/{pricingId}/rules`, `PUT /admin/pricing/rules/{id}`).
- `AdminZonePricingController` — `GET/POST /admin/zones/{zoneId}/pricing`.
- `AdminDynamicPricingController` — `GET/PUT /admin/zones/{zoneId}/service-types/{serviceTypeId}/dynamic-pricing`, `GET .../demand-snapshot` (demand-based dynamic pricing, backed by `ZoneDemandSnapshot(History)`).

Pricing has a safety cap: `app.pricing.combined-multiplier-cap` bounds how far combined dynamic multipliers can push a price. Reference data: `010-bangalore-childcare-reference-pricing.sql`, `016-housekeeping-service-type.sql` (adds a service type beyond childcare).

---

## 7. Waitlist signup (`com.ektrepha.waitlistsignup`)

- `POST /api/waitlist` and `POST /api/v1/waitlist` (same controller, both paths mapped) — general product waitlist join, distinct from the serviceability-area waitlist above. Schema: `013-waitlist-signups-schema.sql` (also recreates `password_reset_tokens`, dropped in changeset 4's identity redesign).

---

## 8. Security configuration (`com.ektrepha.config.SecurityConfig`)

Stateless JWT security (`SessionCreationPolicy.STATELESS`, CSRF disabled, CORS configured from `app.cors.allowed-origins`).

**Public (`permitAll`):** `/api/health`, `/api/version`, `/actuator/**`, `/error`, all of `/api/v1/auth/**`; `GET` on serviceability search/live-zones/localities; `POST` on serviceability/general waitlist, `/pricing/calculate`, `/users/identify`, `/booking-requests`.

**Role-gated:** `/api/v1/admin/**` → `ADMIN`; `/api/v1/nanny-verification/**` → `NANNY` or `ADMIN`; `POST /api/v1/bookings/**` → `PARENT`; `POST /api/v1/nanny-search` and `POST /api/v1/reviews` → `PARENT`; `PUT /api/v1/nannies/me/service-area` → `NANNY`.

**Everything else** (including `GET /booking-requests/me`, `GET /users/me`, nanny-search languages/skills) → `anyRequest().authenticated()`.

Filter order: `RateLimitFilter` → `TraceIdFilter` → `JwtAuthenticationFilter` → `UsernamePasswordAuthenticationFilter` — rate limiting runs first so a throttled request is rejected before any tracing/auth work happens on it. A custom `AuthenticationEntryPoint` returns a JSON `401` body instead of the default redirect/blank response.

---

## 9. Infrastructure & tooling

- **Database migrations** — Liquibase, `src/main/resources/db/changelog/changes/`, 17 changesets (001 → 017): initial schema → users recreation → auth schema → identity redesign (dropped single-channel email+password model) → bigint IDs → nanny marketplace schema + catalog seed → serviceability/pricing schema → Bangalore pincode seed → Bangalore reference pricing → Bangalore locality zones (104 zones) → zone centroids backfill → waitlist signups schema → booking requests schema → Gurgaon/Patna/Noida placeholder zones → housekeeping service type → booking-request additional fields (frequency). (There's also a legacy, unused `db/migration/V1__init.sql` left over from before the Flyway→Liquibase switch.)
- **CI** (`.github/workflows/ci.yml`) — on push/PR to `master`: spins up Postgres 16 as a service container, runs `./mvnw -B test` on JDK 17 (Temurin).
- **Deploy** (`.github/workflows/deploy.yml`) — manual dispatch or auto-triggered after CI succeeds on `master`; builds the jar (`-DskipTests`), locates it, and deploys (with a version-verification step per an earlier commit).
- **Local dev** (`docker-compose.yml`) — Postgres 16 + Redis 7, both with healthchecks. (Redis is provisioned but not yet used by the app — see gaps below.)
- **CODEOWNERS** — requires `bachpanmitra` approval on all paths.
- **Docs** (`docs/`) — this file, plus `adr/0001-api-gateway-and-versioning.md`, Postman collection, runbooks for email/SMS auth setup, Nginx setup, SSL setup, and `test-cases/`.

## Tests

`src/test/java/com/ektrepha/`: `EktrephaApplicationTests` (context load), `config/RateLimiterServiceImplTest`, `verification/NannyVerificationRecomputeTest`, `pricing/{PricingControllerApiTest, DemandPricingServiceTest, PricingServiceTest}`, `serviceability/{ServiceabilitySearchServiceTest, ServiceTypeRolloutEventTest, ServiceabilityControllerApiTest}`. Notably **no test coverage yet** for the `auth` package (OTP, login, signup, rate limiting integration) or `bookingrequest`/`nannysearch` packages.

## Known gaps / stubs (as of this snapshot)

- `POST /password/forgot/phone` always throws — no SMS OTP provider wired up yet.
- Email-verification links (`sendVerificationEmail`) and password-setup links (`sendPasswordSetupEmail`) are generated but there's no visible endpoint yet that consumes the UUID token in the link.
- Rate limiting and login-lockout are both **in-memory, single-instance only** — Redis is provisioned in `docker-compose.yml` but not yet used; either would need to move to Redis (or another shared store) to work correctly behind more than one app instance.
- No auth-package test coverage.
