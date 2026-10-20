# Test Cases — FIRST5_FEE_WAIVER (5-Day New-Parent Fee Waiver Trial)

Source: "5-day free trial for new parents" spec (platform-fee + GST-on-fee waiver, hourly-care only, capped bookings/amount, 120h window from first OTP verification).

Feature-flagged behind `app.trial.enabled` (default `false`). Scope is hourly-care only
(`com.ektrepha.hourlycare`) — the parent-picks-a-nanny booking module (`com.ektrepha.booking`) and
monthly-care are untouched. Where a case is already exercised by an automated test, that class is
noted in the Notes column instead of re-describing the assertion.

Priority: P0 = blocks the trial's core money-handling correctness, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Eligibility at first OTP verification

`POST /api/v1/auth/mobile-otp/verify` (first verification only — `isNewUser`)

| ID | Title | Preconditions | Steps | Expected Result | Priority | Notes |
|----|-------|----------------|-------|------------------|----------|-------|
| ELG-01 | Brand-new phone + device, no prior trial anywhere | `app.trial.enabled=true`; phone/device never seen | Verify OTP for a new number with `deviceId` | A `parent_trials` row is created: `status=ACTIVE`, `endsAt = startedAt + windowMinutes`, `phoneHash`/`deviceIdHash` set; `trial_started` analytics fired | P0 | `TrialServiceImplTest.onFirstOtpVerification_eligible_createsActiveTrial` |
| ELG-02 | Phone hash already used by another trial (incl. a deleted/soft-deleted account's phone) | A `parent_trials` row already exists with this phone's hash | Verify OTP for that phone (new `users` row or not) | `status=INELIGIBLE`; `trial_ineligible{reason=PHONE_REUSED}`; no discount ever applies | P0 | `TrialServiceImplTest.onFirstOtpVerification_phoneAlreadySeen_createsIneligibleTrial` |
| ELG-03 | Device id already linked to an earlier trial, different phone | A `parent_trials` row exists with this `deviceIdHash` | Verify OTP for a brand-new phone, same `deviceId` | `status=INELIGIBLE`, reason `DEVICE_REUSED` | P0 |
| ELG-04 | Parent already has a past booking (edge case — e.g. admin-created) | `bookingRepository.countByParentId > 0` | First-ever OTP verify for that parent | `status=INELIGIBLE`, reason `HAS_PAST_BOOKINGS` | P1 | `TrialServiceImplTest.onFirstOtpVerification_hasPastBookings_createsIneligibleTrial` |
| ELG-05 | Feature flag off | `app.trial.enabled=false` | Any first-ever OTP verify | No `parent_trials` row created at all; existing OTP flow unaffected | P0 | `TrialServiceImplTest.onFirstOtpVerification_disabled_doesNothing`; also covered incidentally by `AuthControllerApiTest` (flag is off by default there) |
| ELG-06 | Concurrent first-verify race for the same brand-new phone | Two verify calls land near-simultaneously for the same new number | Both complete | Exactly one `parent_trials` row exists (unique `parent_id`); the losing insert is swallowed, not surfaced as an error | P1 | Mirrors `AuthServiceImpl#createMobileUser`'s own documented race guard — same `REQUIRES_NEW` + constraint-violation-swallow technique |
| ELG-07 | Older app build omits `deviceId` | `deviceId` absent from the verify request | First-ever OTP verify | Eligibility still runs on phone+bookings alone; `deviceIdHash` stays null on the row | P2 |

## 2. `GET /parents/me` — trial status block

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| ME-01 | Parent has an ACTIVE trial | ACTIVE row exists | GET `/parents/me` | `trial.status="ACTIVE"`, `endsAt` matches, `bookingsLeft`/`amountLeftPaise` reflect remaining caps, `serverNow` present | P0 |
| ME-02 | Parent has no trial row at all | No `parent_trials` row for this parent (pre-feature signup, or flag was off at their signup) | GET `/parents/me` | `trial` is `null` | P0 |
| ME-03 | Parent has no parent row yet (never completed P1) | — | GET `/parents/me` | Whole response is the existing "complete your profile" shape; `trial` is also `null` | P1 |
| ME-04 | Trial ENDED/REVOKED/INELIGIBLE | — | GET `/parents/me` | `trial.status` reflects it; `bookingsLeft`/`amountLeftPaise` are `0`, never negative | P1 |

## 3. `POST /hourly-care/bookings/quote`

| ID | Title | Preconditions | Steps | Expected Result | Priority | Notes |
|----|-------|----------------|-------|------------------|----------|-------|
| QUO-01 | Quote with no trial | Parent has no ACTIVE trial | POST quote (RANGE/FIXED) | `lineItems` has `PLATFORM_FEE`+`PLATFORM_FEE_GST` only, no `TRIAL_DISCOUNT`; `payablePaise == fullPayablePaise` | P0 | `HourlyCareTrialQuoteApiTest.quote_noTrial_hasNoDiscountLine` |
| QUO-02 | Quote with an ACTIVE trial, under both caps | ACTIVE trial, room in both caps | POST quote | `TRIAL_DISCOUNT` line present (negative, = fee+GST, capped by remaining ₹), `payablePaise = fullPayablePaise - discount`; a `hourly_care_quote` row is persisted with `expiresAt = now + quoteLockMinutes` | P0 | `HourlyCareTrialQuoteApiTest.quote_withActiveTrial_waivesFeeAndShowsStruckThroughLines` |
| QUO-03 | Quote always returns the normal fee line, struck-through-able | — | POST quote (with or without a trial) | `PLATFORM_FEE`/`PLATFORM_FEE_GST` lines are always present so the app can render them struck through when a discount also applies | P1 |
| QUO-04 | Care date beyond the 14-day limit | ACTIVE trial; `startTime` > `careDateLimitDays` out | POST quote | No `TRIAL_DISCOUNT` line — booking proceeds at full price, trial itself untouched | P1 | `TrialServiceImplTest.evaluateForQuote_careDateBeyondLimit_returnsEmpty` |
| QUO-05 | Booking cap already reached | `bookingsUsed >= bookingCap` | POST quote | No discount line | P0 | `TrialServiceImplTest.evaluateForQuote_bookingCapAlreadyHit_returnsEmpty` |
| QUO-06 | ₹ cap already reached | `amountWaivedPaise >= amountCapPaise` | POST quote | No discount line | P0 | `TrialServiceImplTest.evaluateForQuote_amountCapAlreadyHit_returnsEmpty` |
| QUO-07 | ₹ cap would be exceeded by this fee alone | `remaining < fee+gst` | POST quote | Discount is capped at exactly the remaining ₹, not the full fee | P1 | `TrialServiceImplTest.evaluateForQuote_remainingAmountCapsTheDiscount` |
| QUO-08 | Monthly pricing mode | `pricingMode=MONTHLY` | POST quote | Rejected (400) — monthly is out of scope for the trial and for this endpoint entirely | P0 |

## 4. `POST /hourly-care/bookings` (confirm, with `quoteId`)

| ID | Title | Preconditions | Steps | Expected Result | Priority | Notes |
|----|-------|----------------|-------|------------------|----------|-------|
| CNF-01 | Valid, unexpired quote with a discount | Quote from QUO-02 | POST bookings with that `quoteId` | Booking created at the quote's locked, discounted `payablePaise`; `trialId`/`trialDiscountPaise` stamped on the booking; quote marked used | P0 | `HourlyCareTrialQuoteApiTest.createBooking_withValidQuoteId_honorsLockedDiscountedPrice` |
| CNF-02 | Expired quote | `expiresAt` in the past | POST bookings with that `quoteId` | 409 `{code:"QUOTE_EXPIRED"}` | P0 | `HourlyCareTrialQuoteApiTest.createBooking_withExpiredQuote_returns409QuoteExpired` |
| CNF-03 | Already-used quote (double-submit) | Quote's `usedAt` already set | POST bookings again with the same `quoteId` | 409 `QUOTE_EXPIRED` (quotes are single-use) | P1 |
| CNF-04 | Quote params don't match the booking request | `quoteId` from a different address/time/pricingMode | POST bookings with mismatched params + that `quoteId` | 409 `QUOTE_EXPIRED` — no swapping a cheaper/discounted quote onto different params | P1 |
| CNF-05 | No `quoteId` supplied (older app build) | — | POST bookings with no `quoteId` | Falls back to today's live-recompute price, no trial discount — unchanged pre-feature behavior | P0 |
| CNF-06 | Trial ends during checkout (locked quote honored), then a fresh quote reflects no trial | Quote taken while ACTIVE; trial's `endsAt` passes before confirm | POST bookings with that still-unexpired `quoteId` | Booking still gets the locked discount (quote lock, not trial state, governs honoring). A *second*, fresh quote afterward shows no discount | P0 | Matches spec's explicit "trial ends during checkout" test requirement |
| CNF-07 | Quote carries a trial discount and the care address duplicates another trial's claimed address | — | POST bookings with that `quoteId` | 409 `{code:"TRIAL_REVOKED"}`; this trial flips to `REVOKED`/`ABUSE`; no booking is created (pre-charge, no payment order exists yet) | P0 | `TrialServiceImplTest.guardAddressOrRevoke_addressAlreadyClaimedByAnotherTrial_revokesAndThrows` |

## 5. Payment confirm — trial settlement ("checkout completed")

`POST /hourly-care/payments/{paymentId}/confirm` and the stuck-payment reconcile path (`GET /hourly-care/bookings/{id}/status`)

| ID | Title | Preconditions | Steps | Expected Result | Priority | Notes |
|----|-------|----------------|-------|------------------|----------|-------|
| PAY-01 | Confirm a trial-discounted booking's payment | Booking from CNF-01, payment initiated | Confirm payment | `parent_trials.bookingsUsed`/`amountWaivedPaise` incremented by exactly this booking's discount, inside one locked (`FOR UPDATE`) update; `trial_booking_confirmed` analytics fired | P0 | `HourlyCareTrialQuoteApiTest.fullFlow_confirmPayment_settlesTrialUsageAndEndsOnCap` |
| PAY-02 | Confirm hits the booking cap | `bookingCap=1` (as PAY-01's test config), first confirmed booking | Confirm payment | Trial flips to `ENDED`/`CAP_BOOKINGS` in the same call; `trial_ended` analytics fired; email rule evaluated (see NTF-03) | P0 | Same test as PAY-01 |
| PAY-03 | Confirm hits the ₹ cap (not the booking cap) | Remaining ₹ cap exhausted by this booking's discount, booking cap not yet hit | Confirm payment | Trial flips to `ENDED`/`CAP_AMOUNT` | P0 | `TrialServiceImplTest.settleBookingCheckout_bookingCapHit_endsTrial` (booking-cap variant; amount-cap path is symmetric in `TrialServiceImpl`) |
| PAY-04 | Concurrent confirms against the same trial, both under the cap individually but not together | Two bookings confirmed near-simultaneously, trial has room for exactly one more | Confirm both | Exactly one of the caps' worth is consumed correctly — no double-spend; the `FOR UPDATE` lock on `parent_trials` serializes the two confirms | P0 | `ParentTrialRepository#findByIdForUpdate` — recommend a dedicated two-thread `@SpringBootTest` against the real DB if deeper assurance is wanted; unit tests above verify the single-call math, not thread interleaving |
| PAY-05 | Duplicate payment-instrument fingerprint across two different parents' trials | Trial B's booking confirms with a fingerprint already claimed by Trial A | Confirm Trial B's payment | Trial B flips to `REVOKED`/`ABUSE`; **this already-captured booking's discount is NOT clawed back** (best-effort, post-hoc — Razorpay auto-captures before the fingerprint is knowable); only future bookings on Trial B are blocked | P0 | `TrialServiceImplTest.settleBookingCheckout_duplicatePaymentFingerprint_revokesTrial` |
| PAY-06 | Reconciled (stuck-payment) success path also settles the trial | Payment confirm never arrives client-side; `GET .../status` reconciles via Razorpay lookup | Poll status | Trial settlement runs exactly the same as the direct confirm path | P1 | `HourlyCareServiceImplReconciliationTest` (pre-existing test, extended for the new constructor args — doesn't itself exercise a trial booking, since its fixture's `trialId` is null by default) |

## 6. Scheduled jobs

| ID | Title | Preconditions | Steps | Expected Result | Priority | Notes |
|----|-------|----------------|-------|------------------|----------|-------|
| JOB-01 | Expiry sweep ends a past-due ACTIVE trial | `ACTIVE`, `endsAt` in the past | `TrialExpiryJob` tick | `ENDED`/`EXPIRED`; `trial_ended` fired; ENDED email sent iff `bookingsUsed >= 1` | P0 | `TrialExpiryJobTest.expireDueTrials_callsExpireForEveryDueRow`, `TrialServiceImplTest.expireIfStillActive_pastEndsAt_endsTrial` |
| JOB-02 | Expiry sweep leaves a not-yet-due trial alone | `ACTIVE`, `endsAt` in the future | Tick | No change | P1 | `TrialServiceImplTest.expireIfStillActive_notYetDue_doesNothing` |
| JOB-03 | One trial's expiry throws, the sweep still processes the rest | — | Tick with 2 due trials, one throwing | The other trial is still expired; error logged, sweep doesn't abort | P1 | `TrialExpiryJobTest.expireDueTrials_oneRowThrows_otherStillProcessed` |
| JOB-04 | Day-2 push sent once, 10:00 IST | `ACTIVE`, `bookingsUsed=0`, now past day-2 10:00 IST, not yet sent | `TrialNotificationJob` tick | Push sent, `day2PushSentAt` stamped; a later tick doesn't resend | P1 |
| JOB-05 | Day-2 push skipped if parent already booked | `bookingsUsed >= 1` | Tick | Row excluded from the query entirely (`findByStatusAndBookingsUsedAndDay2PushSentAtIsNull(ACTIVE, 0)`) | P1 |
| JOB-06 | 24h-before-expiry push, quiet hours respected | `now` within 24h of `endsAt` but it's 23:00 IST | Tick | No push sent this tick; sent on a later tick once past 08:00 IST | P1 |
| JOB-07 | ENDED email only if at least one trial booking was made | Trial ends with `bookingsUsed=0` | Expiry sweep | No Brevo email sent | P0 |

## 7. Config / money-representation sanity

| ID | Title | Expected Result | Priority |
|----|-------|------------------|----------|
| CFG-01 | `app.trial.enabled=false` (default) | Zero behavior change anywhere — no trial rows created, no discount lines, existing hourly-care flow byte-for-byte as before | P0 |
| CFG-02 | `app.pricing.platform-fee-gst-pct=0` (default) | `platformFeeGst` is always `0`; `total`/quote totals unchanged from before this feature for every non-trial parent | P0 |
| CFG-03 | `app.trial.window-minutes` set to 30 for QA | Trial expires 30 minutes after `startedAt`, not 120h | P1 |
| CFG-04 | Every `parent_trials`/`booking`/`hourly_care_quote` money column is a `BIGINT` (paise), never a float/double | Schema review of migration 043 | P0 |
