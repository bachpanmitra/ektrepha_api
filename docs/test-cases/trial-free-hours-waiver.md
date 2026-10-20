# Test Cases — Free-Hours Nanny Trial (rework of FIRST5_FEE_WAIVER)

**Status: CONFIRMED — spec assembled from a verbal description, not a written PRD, then clarified
and signed off point-by-point. Remaining assumptions (not explicitly re-confirmed, but consistent
with existing patterns) are marked ⚠️ below.**

## 0. What's changing

Today's `FIRST5_FEE_WAIVER` (see [`trial-first5-fee-waiver.md`](trial-first5-fee-waiver.md), already
implemented) waives only the platform fee + its GST, capped by a *number of bookings* (3) and a
total ₹ amount (₹1,500). This rework replaces that cap model:

- While the trial is `ACTIVE`, **every booking is 100% free** — the nanny's own hourly cost (the
  `subtotal` in `PricingServiceImpl`, not just the platform fee) plus the platform fee and its GST
  are all waived, in full, no matter how many hours the booking is. **The parent is never charged
  any amount during the trial** — this is a business rule, not a cap: there is no partial charge,
  no "excess hours billed", no blocked/rejected booking.
- `app.trial.free-hours-cap` (new config, replaces `booking-cap`) is a **cumulative usage counter,
  not a per-booking limit**: every confirmed booking's hours add to a running total on
  `parent_trials`. Once that running total reaches or passes the cap, the trial ends
  (`ENDED`/`CAP_HOURS`) right after that booking — the booking that crossed the threshold was
  itself still fully free. Every booking made *after* the trial has ended goes back to full price,
  same as today.
- Default **16** hours, overridable (e.g. down to **1** hour for a dev/QA run, the same way
  `window-minutes` is overridden today).
- `FIXED` mode is excluded (per Decision #2). `MONTHLY` keeps today's existing behavior — the
  series' one real charge is still fully waived while the trial is `ACTIVE` — now tracked against
  the same cumulative-hours counter using that charged occurrence's daily duration (not the whole
  series' duration). This wasn't explicitly revisited in this round; flagging it here rather than
  silently changing already-tested Monthly behavior.

This is structurally a small diff from today's `TrialServiceImpl`: swap "discount = fee + GST,
capped by bookingCap/amountCapPaise" for "discount = full total (subtotal + fee + GST), capped by
cumulative hours" — the quote-lock, confirm, settlement, race-safety (`FOR UPDATE`), and
expiry-job machinery are otherwise unchanged.

## Decisions

| # | Question | Decision |
|---|----------|----------|
| 1 | Does the ₹ cap (`amountCapPaise`, today ₹1,500) still apply as a secondary safety net? | **Dropped.** The cumulative-hours counter is the only limit. |
| 2 | `FIXED`-mode bookings have no per-hour rate (a flat price for the whole slot) — does the trial discount apply to them at all? | **Excluded for now.** `FIXED` mode gets no trial discount (quoted at full price, same as `MONTHLY` already is), until a future spec defines an equivalent. |
| 3 | Is the free-hours counter debited at quote time or only at payment confirmation? | ⚠️ Assumed, not explicitly confirmed: same as today — quote reflects the *current* trial status but doesn't reserve anything; the running total is only incremented at `settleBookingCheckout` (payment confirm) — same race-safety model as `bookingsUsed`/`amountWaivedPaise` today. |
| 4 | Should `16` become `1` as the shipped default, or only as a dev/QA override? | ⚠️ Assumed, not explicitly confirmed: `16` stays the product default; `1` is a dev-profile/env override for testing, mirroring how `TRIAL_WINDOW_MINUTES` is overridden today. |

## 1. Eligibility at first OTP verification

No change from the existing feature (see `trial-first5-fee-waiver.md` §1) — eligibility rules
(phone/device reuse, past bookings, feature flag) are unaffected by this rework. Only the row's
shape changes: `bookings_used`/`amount_waived_paise` are replaced by a `free_hours_used`
(`BigDecimal`, 2-decimal scale, matching `PricingServiceImpl`'s `hours`) column seeded at `0`.

## 2. `GET /parents/me` — trial status block

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| ME-01 | Parent has an ACTIVE trial, no usage yet | ACTIVE row, `freeHoursUsed=0` | GET `/parents/me` | `trial.status="ACTIVE"`; `hoursUsed=0`, `hoursCap=16` (or configured value) | P0 |
| ME-02 | Parent has partially used the trial | `freeHoursUsed=10` of a 16h cap, still `ACTIVE` | GET `/parents/me` | `hoursUsed=10` — every booking the parent made in this window was still fully free | P1 |
| ME-03 | Trial ENDED (cap reached) / EXPIRED / REVOKED / INELIGIBLE | — | GET `/parents/me` | `trial.status` reflects it; no further discount implied | P1 |
| ME-04 | No trial row at all | — | GET `/parents/me` | `trial` is `null` | P0 |

## 3. `POST /hourly-care/bookings/quote` (RANGE mode)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| QUO-01 | Quote with no trial | Parent has no ACTIVE trial | POST quote, any-length RANGE booking | No `TRIAL_DISCOUNT` line; `payablePaise == fullPayablePaise` | P0 |
| QUO-02 | Quote while trial is ACTIVE, short booking | ACTIVE trial, `hoursUsed=0` of 16, booking = 3h | POST quote | `TRIAL_DISCOUNT` line = full `subtotal + fee + gst`; `payablePaise = 0` | P0 |
| QUO-03 | Quote for a booking longer than the remaining cap | ACTIVE trial, `hoursUsed=15` of 16 (1h "left"), booking = 5h | POST quote | Still fully free — `TRIAL_DISCOUNT` = full `subtotal + fee + gst` for all 5h, `payablePaise = 0`. The cap is not consulted at quote time to shorten/split the booking; it only governs whether the trial *ends after* this booking settles (see PAY-02) | P0 |
| QUO-04 | Quote right after the trial has already ended from a prior booking | `status=ENDED`/`CAP_HOURS` (set by an earlier confirm) | POST quote | No `TRIAL_DISCOUNT` line — behaves exactly like QUO-01, full price | P0 |
| QUO-05 | `FIXED` pricing mode | ACTIVE trial, `pricingMode=FIXED` | POST quote | No `TRIAL_DISCOUNT` line — out of scope per Decision #2 | P1 |
| QUO-06 | `MONTHLY` pricing mode | — | POST quote | Rejected (400), unchanged from today | P0 |
| QUO-07 | Care date beyond the existing 14-day window limit | ACTIVE trial; `careDateLimitDays` exceeded | POST quote | No discount — booking proceeds at full price, same as today's QUO-04 | P1 |

## 4. `POST /hourly-care/bookings` (confirm, with `quoteId`)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CNF-01 | Valid unexpired quote, trial-free booking | Quote from QUO-02 or QUO-03 | POST bookings with `quoteId` | Booking created at `payablePaise=0` regardless of hours; `trialId`/`trialDiscountPaise` (= full total) stamped on the booking | P0 |
| CNF-02 | Expired quote | `expiresAt` in the past | POST bookings with `quoteId` | 409 `QUOTE_EXPIRED`, unchanged from today | P0 |
| CNF-03 | No `quoteId` supplied | — | POST bookings with no `quoteId` | Falls back to live recompute; discount applies only if the trial is still `ACTIVE` at that moment, unchanged from today's fallback behavior | P0 |
| CNF-04 | Quote taken while ACTIVE; trial ends (from a *different* booking settling) before this quote is confirmed | Quote locked a full-free price; trial flips to `ENDED` in between | POST bookings with the still-unexpired `quoteId` | Booking still honors the locked quote's full-free price — quote lock, not live trial state, governs honoring (same rule as today's CNF-06) | P0 |

## 5. Payment confirm — trial settlement

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| PAY-01 | Confirm a trial-free booking's payment, cap not yet reached | Booking from CNF-01, 3h, `hoursUsed=0` of 16 before this | Confirm payment | `parent_trials.free_hours_used` incremented by exactly this booking's hours (`3.00`), inside the same `FOR UPDATE` locked update as today; trial stays `ACTIVE` | P0 |
| PAY-02 | Confirm a booking whose hours push the running total to/past the cap | `hoursUsed=15` of 16 before this booking; booking = 5h (pushing total to 20) | Confirm payment | This booking is still fully honored at `payablePaise=0`; `free_hours_used` becomes `20.00`; trial flips to `ENDED`/`CAP_HOURS` in the same call | P0 |
| PAY-03 | Concurrent confirms racing near the cap boundary | Two bookings confirm near-simultaneously against the same trial, both still ACTIVE at quote time | Confirm both | Both are honored fully-free (quote lock governs, per CNF-04) regardless of ordering; `free_hours_used` ends up as the sum of both bookings' hours with no double-counting — the `FOR UPDATE` lock on `parent_trials` serializes the two increments | P0 |
| PAY-04 | Reconciled (stuck-payment) success path | Payment confirm never arrives client-side; status reconcile fires | Poll status | Settlement runs identically to the direct confirm path | P1 |
| PAY-05 | Duplicate payment-instrument fingerprint across two parents' trials | Trial B's booking confirms with a fingerprint already claimed by Trial A | Confirm Trial B's payment | Trial B flips to `REVOKED`/`ABUSE`, unchanged from today — this already-honored (free) booking isn't retroactively charged (there is nothing to claw back); only future bookings on Trial B lose the discount | P1 |

## 6. Scheduled jobs

No structural change from today's `trial-first5-fee-waiver.md` §6 — expiry is still driven by
`endsAt` (the trial's time window), independent of the free-hours counter. The `ENDED` email
condition (today: `bookingsUsed >= 1`) becomes `free_hours_used > 0` (at least one booking actually
used the trial) if `bookings_used` is dropped from the schema entirely.

## 7. Config / money-representation sanity

| ID | Title | Expected Result | Priority |
|----|-------|------------------|----------|
| CFG-01 | `app.trial.enabled=false` (default) | Zero behavior change anywhere, same as today | P0 |
| CFG-02 | `app.trial.free-hours-cap` default | `16` hours in every environment unless overridden | P0 |
| CFG-03 | `app.trial.free-hours-cap` overridden to `1` for a dev/QA run | First booking of any length is still fully free; trial ends immediately after that one confirm (since even a short booking's hours meet/exceed a 1h cap) | P1 |
| CFG-04 | `free_hours_used` precision | Stored/compared with the same `BigDecimal`, 2-decimal-scale precision `PricingServiceImpl` already uses for `hours` — never a float/double | P0 |
| CFG-05 | A parent can never be charged during an ACTIVE trial | For every `pricingMode`/`hours` combination where a trial discount applies at all (RANGE only, per Decision #2), `payablePaise` is always exactly `0` — this is the one invariant that must hold everywhere, since "never charge during trial" is a hard business rule, not best-effort | P0 |
