# Test Cases — Nanny App: Order Assignment, Order Details, Contact Masking, Live Location/ETA, Child Check-in/Check-out

Source: ad-hoc requirement (dictated, reconstructed in conversation) for the Nanny App booking
flow — Ops manually assigns a nanny to a booking, the order reaches the Nanny App with full
detail (time/day/location), the parent's phone number is shown masked (last 4 digits only), the
app shows the nanny's current distance/ETA to the parent's home, and child entry/exit at the
parent's home is tracked via geofence.

This file covers both **what already exists** (regression coverage, §1–§3 below) and **what is
missing** (§4–§6 below, written as target test cases for the feature once built — each such
section opens with a "Current state" note; none of these should be read as passing today).

Priority: P0 = blocks the screen if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Ops manually assigns a nanny to a booking

`POST /api/v1/admin/hourly-care/bookings/{id}/assign` (`AssignCaregiverRequest{nannyId}`, role: ADMIN)
`POST /api/v1/admin/hourly-care/bookings/{id}/reassign` (`ReassignCaregiverRequest{nannyId, reason}`, role: ADMIN)

Current state: **built**, but only for the hourly-care module (`HourlyCareAdminController`). There
is no equivalent assign endpoint on the generic `AdminBookingController` (`/api/v1/admin/bookings/**`
exposes list/detail/candidates/cancel only, no assign).

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-01 | Ops assigns a caregiver pre-confirmation | Booking in `ASSIGNING_CAREGIVER` status | POST `/admin/hourly-care/bookings/{id}/assign` `{"nannyId": 42}` as ADMIN | 200; `CaregiverAssignmentResponse{bookingId, nannyId: 42, status}` | P0 |
| NAO-02 | Assign with a non-existent `nannyId` | — | POST `{"nannyId": 999999}` | 4xx — no such caregiver | P0 |
| NAO-03 | Assign called by a non-ADMIN | Valid NANNY or PARENT token | POST as above | 403 — `/api/v1/admin/**` is ADMIN-only at `SecurityConfig` | P0 |
| NAO-04 | Assign a booking not in `ASSIGNING_CAREGIVER` | Booking already `CONFIRMED` | POST assign | Should be rejected — assign is documented as pre-CONFIRMED only; verify the actual status-guard error rather than assuming | P1 |
| NAO-05 | Ops reassigns after a no-show, with reason | Booking `CONFIRMED`, not yet checked in | POST `/reassign` `{"nannyId": 7, "reason": "No-show, unreachable 30 min past start time"}` | 200; new `nannyId` on the booking | P0 |
| NAO-06 | Reassign with blank `reason` | — | POST `/reassign` `{"nannyId": 7, "reason": ""}` | 400 — `@NotBlank` | P0 |
| NAO-07 | Reassign a booking already checked in | Booking has `checkedInAt` set | POST `/reassign` | Should be rejected — reassign is documented as "not-yet-checked-in" only; verify actual guard | P1 |
| NAO-08 | Assign/reassign missing `nannyId` | — | POST with no `nannyId` | 400 — `@NotNull` | P0 |
| NAO-09 | **Gap**: no assign/reassign on non-hourly-care bookings | A booking from a module other than hourly-care, if one exists, needs caregiver assignment | Look for an equivalent endpoint under `/api/v1/admin/bookings/**` | None exists today — confirm with product whether this is intentionally hourly-care-only or a gap | P1 — flag, don't file as a bug until scope is confirmed |

## 2. Nanny App fetches order/booking details

`GET /api/v1/nanny-bookings/today`, `GET /api/v1/nanny-bookings/{id}` (role: NANNY, `NannyTodayBookingResponse`)

Current state: **built**. Returns `id`, `status`, `child` (`ChildSummary`), `startTime`, `endTime`,
`address` (`AddressResponse` — includes `lat`/`lng`), `careNotes`. Deliberately excludes
price/review/availableActions (nanny app has no concept of these) and — see §4 — does **not**
include parent contact info at all.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-10 | Today's shift, one assigned | Nanny has a booking with `startTime`/`endTime` today | GET `/nanny-bookings/today` as that NANNY | 200; full `NannyTodayBookingResponse` for that booking | P0 |
| NAO-11 | Today's shift, nothing assigned | Nanny has no booking today | GET `/nanny-bookings/today` | 200 with empty body (per `@Operation` doc — confirm exact shape: `null` body vs `{}`) | P0 |
| NAO-12 | Booking detail by id, own booking | Nanny assigned to booking X | GET `/nanny-bookings/{X}` | 200; same shape as `/today` | P0 |
| NAO-13 | Booking detail by id, another nanny's booking | Booking Y assigned to a different nanny | GET `/nanny-bookings/{Y}` as this nanny | 404 — `findByIdAndNannyId` style scoping, not a 403 | P0 |
| NAO-14 | Booking detail, non-existent id | — | GET `/nanny-bookings/999999` | 404 | P0 |
| NAO-15 | Called by non-NANNY role | Valid PARENT/ADMIN token | GET `/nanny-bookings/today` | 403 — `SecurityConfig` GET matcher on `/api/v1/nanny-bookings/**` is NANNY-only | P0 |
| NAO-16 | Called with no auth | — | GET `/nanny-bookings/today`, no bearer token | 401 | P0 |
| NAO-17 | Address includes coordinates for navigation | Booking has a resolved address | GET detail | `address.lat`/`address.lng` non-null — needed by the client to drive turn-by-turn navigation, since no in-app ETA exists yet (§4) | P1 |

`GET /api/v1/nanny-bookings/{id}/care-details` (role: NANNY, `NannyCareDetailsResponse`) — **built this
session** (API Map rows 12-13, "Care guide" screen). Returns only the subset of the spec's
"Parent Details" field list that's actually backed by real data today: child summary, allergies,
`medicalNotes`/`routine`/`comfort`/`doNot` (the child's existing care-notes, same fields the
parent app writes via `PUT /children/{id}/care-notes`), the booking address, and the parent's
name + masked phone. Fields with no backing data model (medicine schedule, doctor/clinic, diet
specifics, screen-time rules, pickup authorization, languages, pets, detailed house rules, a
second contact) are omitted rather than sent as always-null placeholders.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-77 | Care guide returns child notes, address, and masked contact | Booking assigned, child has allergies + care-notes set | GET `/{id}/care-details` as that NANNY | 200; `child`, `allergies`, `medicalNotes`/`routine`/`comfort`/`doNot`, `address` (incl. `accessNotes`), `parentName`, `parentPhoneMasked` — **automated**: `NannyBookingControllerApiTest#careDetails_confirmedBooking_returnsChildCareNotesAddressAndMaskedContact` | P0 |
| NAO-77b | Care guide scoped to the assigned nanny only | Booking belongs to a different nanny | GET `/{id}/care-details` | 404 — **automated**: `careDetails_anotherNannysBooking_isNotFound` | P0 |

## 3. Shift check-in / check-out (time-based, not geofenced, not child-specific)

`POST /api/v1/nanny-bookings/{id}/check-in`, `POST /api/v1/nanny-bookings/{id}/check-out`
(no explicit role annotation on the controller methods — enforced by `SecurityConfig`'s
`POST /api/v1/nanny-bookings/** → hasRole('NANNY')` matcher)

Current state: **built**, but this is **shift-level attendance only** — it stamps
`Booking.checkedInAt`/`checkedOutAt` with no location/geofence check and no distinction of the
child's own entry/exit. Do not confuse this with the child geofence flow requested in §6, which
does not exist.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-18 | Check in on own booking, first time | Booking assigned to this nanny, `checkedInAt` null | POST `/{id}/check-in` | 200; `AttendanceResponse{bookingId, checkedInAt: <now>, checkedOutAt: null}`; parent receives a push ("Your nanny has arrived") | P0 |
| NAO-19 | Check in twice | `checkedInAt` already set | POST `/{id}/check-in` again | 4xx — `InvalidBookingTransitionException` "already been checked into" | P0 |
| NAO-20 | Check out after checking in | `checkedInAt` set, `checkedOutAt` null | POST `/{id}/check-out` | 200; `checkedOutAt` set; parent push ("Your nanny has left") | P0 |
| NAO-21 | Check out before checking in | `checkedInAt` null | POST `/{id}/check-out` | 4xx — "hasn't been checked into yet" | P0 |
| NAO-22 | Check out twice | `checkedOutAt` already set | POST `/{id}/check-out` again | 4xx — "already been checked out of" | P0 |
| NAO-23 | Check in/out on a booking not assigned to caller | Booking belongs to a different nanny | POST `/{id}/check-in` | 404 — `resolveOwnBooking` scopes by `nannyId` | P0 |
| NAO-24 | Push failure doesn't roll back check-in | Simulate `pushSenderService.sendToUser` throwing/failing | POST `/{id}/check-in` | `checkedInAt` still persisted — push is fire-and-forget per the implementation comment | P1 |
| NAO-25 | No distance/location check at all | Nanny's device location is nowhere near the booking address | POST `/{id}/check-in` | Currently succeeds regardless of actual location — there is no geofence guard; this passes today only because the feature doesn't exist (contrast with §6's target behavior) | P1 — document as current (lenient) behavior, not a defect |

## 4. Parent phone number masking on the Nanny App

Current state: **built this session** (API Map row 11). Rather than a dedicated "parent-contact"
endpoint, `parentPhoneMasked` was added directly to the existing `NannyTodayBookingResponse`
(`/nanny-bookings/today` and `/nanny-bookings/{id}`) and to the new care-details response (§2) —
masked via `com.ektrepha.util.PhoneMasker` (last 4 digits visible, everything before that `*`).
Gated on the same `CONFIRMED`/`IN_PROGRESS` statuses as the parent-side equivalent
(`GET /api/v1/bookings/{id}/contact`), which itself still returns the nanny's real, unmasked
number — that asymmetry (nanny→parent masked, parent→nanny unmasked) is as-designed for this
session's scope, not a bug. NAO-31 (tap-to-call still functional) remains a genuine product
question below — masking here is purely display-side; there's no telephony proxy, so the raw
masked string is exactly what reaches the client, nothing more to build without that product
decision.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-26 | Nanny app exposes a masked parent contact once confirmed | Booking assigned, `CONFIRMED` | GET `/nanny-bookings/{id}` as that NANNY | 200; `parentPhoneMasked` is the number with all but the last 4 digits starred — **automated**: `NannyBookingControllerApiTest#detail_confirmedBooking_includesMaskedParentPhone` | P0 |
| NAO-27 | Full number never returned to the nanny | Same as above | Inspect full response payload | `parentPhoneMasked` is the only phone-shaped field; no field anywhere contains the unmasked number | P0 |
| NAO-28 | Masking format consistent regardless of number length | Parent numbers with/without country code | GET detail | `PhoneMasker.mask` always keeps exactly the last 4 characters, stars the rest — length-independent by construction, not a special-cased format | P1 |
| NAO-29 | Contact not available before confirmation | Booking still `PENDING` | GET `/nanny-bookings/{id}` as the assigned nanny | 200; `parentPhoneMasked` is `null` (not a 4xx — the rest of the booking detail is still shown) — **automated**: `detail_pendingBooking_parentPhoneMaskedIsNull` | P0 |
| NAO-30 | Contact scoped to the assigned nanny only | Booking assigned to nanny A | GET `/nanny-bookings/{id}` as nanny B | 404 — existing `resolveOwnBooking` scoping, unrelated to the new field | P0 |
| NAO-31 | Calling/masked-number tap-to-call still functional | Masked number displayed | Nanny taps "call" in-app | Still an open product question: is this purely a display mask (the device dialer would need the real number from somewhere, which this session deliberately never sends to the nanny app), or does a telephony proxy number belong here instead? Not built — needs a product decision first | P2 — needs product clarification |

## 5. Nanny's live location / distance / ETA to parent's home

Current state: **built this session** (API Map rows 30, 31, 51, 52, 62), following a separate,
more detailed build-spec doc the product/eng team put together. "On the way" is never stored as a
status on `booking_attendance` itself — it's derived each time from a new append-only
`booking_event` table (TRIP_STARTED/ARRIVED rows), the same way weekly-off is derived from a
missing `Booking` row rather than stored. A new `nanny_location` table holds one row per nanny
(not per booking) — the latest ping always wins. ETA is **haversine-only** (straight-line distance
× 1.3 road factor, at an assumed 20km/h) — the spec calls for a real Ola Maps routing call with
this as the fallback, but Ola Maps' directions/distance-matrix contract was never confirmed
against a live call the way the existing reverse-geocode/autocomplete/place-details integrations
were (see `OlaMapsClientImpl`'s own javadoc), so wiring it blind risked shipping a silently-broken
path — `EtaService.eta()`'s `source` is always `"STRAIGHT_LINE"` until that's done as its own pass.
See `NannyTripServiceImpl`, `NannyTripController`, `EtaService`, `BookingEventService`.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-32 | Nanny app reports current location | Nanny ON_THE_WAY, LOCATION consent granted | `POST /nanny-bookings/{id}/location {lat, lng, accuracyM, recordedAt}` | 204; `nanny_location` upserted — **automated**: `NannyTripControllerApiTest#location_afterConsentAndOnTheWay_returns204` | P0 |
| NAO-32b | Location rejected without consent | LOCATION consent not granted | `POST .../location` | 428 — **automated**: `location_withoutConsent_isPreconditionRequired` | P0 |
| NAO-32c | Location rejected when not on the way | No TRIP_STARTED event today | `POST .../location` | 409 — **automated**: `location_notOnTheWay_isConflict` | P1 |
| NAO-33 | Distance/ETA from nanny to parent's home | Nanny has started a trip (location known) | `GET /nanny-bookings/{id}/eta` | 200; `{distanceM, durationSec, etaAt, source:"STRAIGHT_LINE", locationUpdatedAt, stale:false}` — **automated**: `eta_afterStartTrip_returnsDistanceAndEta` | P0 |
| NAO-35 | No location reported yet | New booking, nanny hasn't started a trip | `GET .../eta` | 204, not a 500 — **automated**: `eta_noLocationYet_returns204` | P1 |
| NAO-36 | Location/ETA visible to parent too | Nanny ON_THE_WAY | Parent `GET /api/v1/bookings/{id}/nanny-eta` | 200 with the same distance/ETA plus the nanny's lat/lng for a map pin — **automated**: `parentEta_whileOnTheWay_returnsEta` | P0 |
| NAO-36b | Parent sees nothing before the trip starts | Booking not yet ON_THE_WAY | `GET .../nanny-eta` | 204 — **automated**: `parentEta_beforeTripStarted_returns204` | P1 |
| NAO-37 | Location stops being shared to the parent after arrival (privacy) | Nanny has tapped "Arrived" | Parent `GET .../nanny-eta` | 204 — the nanny's location is never sent to the parent again once ARRIVED/checked-in — **automated**: `parentEta_afterArrived_returns204_privacy` | P0 |
| NAO-39 | Third-party routing provider failure | — | `GET .../eta` | No live Ola Maps call exists yet to fail — every call already takes the documented straight-line path; revisit once a real routing integration is wired in | P2 — not applicable to the current (fallback-only) implementation |
| NAO-40 | Nanny taps "Start trip" | Booking CONFIRMED/IN_PROGRESS, within 3h of start and before end | `POST /nanny-bookings/{id}/start-trip {lat, lng}` | 200; `{status:"ON_THE_WAY", tripStartedAt, eta}`; family gets a push with an ETA estimate — **automated**: `startTrip_withinWindow_returnsOnTheWay` | P0 |
| NAO-40b | Too early/late to start | Outside the 3h-before/before-end window | `POST .../start-trip` | 409 — **automated**: `startTrip_tooEarly_isConflict` | P1 |
| NAO-41 | Nanny taps "I've reached" | Within ~200m (+ GPS accuracy buffer) of the address | `POST /nanny-bookings/{id}/arrived {lat, lng, accuracyM}` | 200; `{status:"ARRIVED", arrivedAt, distanceM}`; family pushed to open the app for today's code — **automated**: `arrived_nearAddress_marksArrived` | P0 |
| NAO-41b | Too far to mark arrived | More than the allowed radius away | `POST .../arrived` | 422 `TOO_FAR` — **automated**: `arrived_tooFar_isRejected` | P1 |
| NAO-42 | Roster shows upcoming bookings | Nanny has bookings in range | `GET /nanny-bookings?from=&to=` (max 62 days) | 200; one entry per working day with `dayStatus`/`attendanceStatus` — **automated**: `NannyBookingControllerApiTest#roster_returnsBookingsInRange` | P1 |
| NAO-42b | Range too wide is rejected | `to - from` > 62 days | `GET /nanny-bookings?from=&to=` | 400 — **automated**: `roster_rangeTooWide_isBadRequest` | P2 |
| NAO-43 | Push reminder ~15 min before a shift | Today's attendance PENDING, start 10-20 min away | `TripReminderJob` runs | Both nanny and parent pushed, no plain code in the text; deduped via `reminder_sent_at` — **automated**: `TripReminderJobTest#shiftStarts15MinFromNow_getsReminded`, `alreadyReminded_isNotRemindedAgain` | P1 |
| NAO-43b | Evening-before reminder for tomorrow's bookings | — | — | **Not built**: tomorrow's `booking_attendance` row doesn't exist until the 00:05 daily code-generation job runs, so this sweep's own dedup flag needs a column on `booking` instead — left for a later pass | P2 |

## 6. Child entry/exit timing via geofence at parent's home — MISSING

Current state: **not built**. The only existing "entry/exit" concept is the nanny's own shift
check-in/check-out (§3), which is explicitly **not** geofenced
(`NannyTodayBookingResponse`'s own class comment: "a real geofenced check-in/out flow doesn't
exist yet") and is **not child-specific** — it has no link to a child entering/leaving the
parent's home. The test cases below target the feature as described (auto-detect child
entry/exit at the home location via geofence) once built.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-40 | Child entry detected on arrival at home | Nanny+child device enters the geofence radius around the booking address | Trigger geofence-enter event (new mechanism) | A child "entered home" event is recorded with a timestamp, distinct from the nanny's own `checkedInAt` | P0 |
| NAO-41 | Child exit detected on leaving home | Device exits the geofence | Trigger geofence-exit event | "exited home" event recorded with timestamp | P0 |
| NAO-42 | Geofence radius is configurable | — | Inspect config (new) | Radius (meters) is a tunable value, not hardcoded per booking — confirm default and whether it's global or per-address | P1 |
| NAO-43 | False-positive guard: brief dip outside radius | Device exits and re-enters within a short window (e.g. GPS jitter near the boundary) | Trigger exit then immediate re-entry | Should not log a spurious exit/entry pair — confirm the intended debounce window when built | P1 |
| NAO-44 | Child entry/exit timeline visible on the order | Events recorded for a booking | GET booking detail (nanny app and/or parent/admin view) | Entry/exit timestamps surfaced alongside the existing order detail (time/day/location) from §2 | P0 |
| NAO-45 | No geofence event without an active/assigned booking | Device roams near an address with no active booking there | — | No event recorded — geofence must be scoped to an active booking's address/time window, not a bare location match | P1 |
| NAO-46 | Parent notified on child entry/exit | Geofence event fires | — | Push notification to parent, mirroring the existing `notifyParent` pattern used for nanny check-in/check-out (§3) — confirm whether this reuses that notification category or is a new one | P1 |
| NAO-47 | Permission/consent for background location | Nanny device | App requests background/always-location permission for geofencing | Confirm product/legal requirement for consent flow and what happens if permission is denied (feature degrades to manual check-in only?) | P2 — product/legal scope question, not purely backend |

## 7. Daily OTP-gated check-in/check-out (Phase 1 of the nanny-app spec)

Source: `Ektrepha_Nanny_API_Map.xlsx`, "OTP Rules" + "API Spec" + "Test Cases" sheets.

Current state: **built this session**, replacing the old no-OTP, no-location §3 check-in/check-out
entirely. New `booking_attendance` table (migration 047), one hashed check-in/check-out code per
booking per day, 200m geofence, 5-attempt lock, `/api/v1/nanny-bookings/{id}/check-in`|`check-out`
now take `{otp, lat, lng}`. New endpoints: parent `GET/POST /api/v1/bookings/{id}/attendance/**`,
admin `GET /api/v1/admin/bookings/{id}/attendance` + manual check-in/out. See
`BookingAttendance`/`NannyAttendanceServiceImpl`/`AttendanceCodeGenerator`/
`DailyAttendanceCodeGenerationJob`.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-48 | Check-in with today's correct code | Booking confirmed, today's `booking_attendance` row generated | Nanny `POST /check-in {otp, lat, lng}` with the right code, near the address | 200; today's row CHECKED_IN, method OTP; parent gets a push — **automated**: `BookingAttendanceControllerApiTest#checkIn_withCorrectCode_stampsTimestamp` | P0 |
| NAO-49 | Check-in with a wrong code | Today's row exists | `POST /check-in` with a wrong `otp` | 4xx `WRONG_OTP`; attempts +1; not checked in — **automated**: `checkIn_withWrongCode_isRejected` | P0 |
| NAO-50 | Yesterday's code used today | A prior day's row/code exists for the same booking | `POST /check-in` with yesterday's check-in code | 4xx `WRONG_OTP` — codes are per date — **automated**: `checkIn_withYesterdaysCode_isRejected` | P0 |
| NAO-51 | Check-in code used for check-out | Nanny already checked in | `POST /check-out` with the check-in code instead of the check-out code | 4xx `WRONG_OTP` — check-out needs its own code — **automated**: `checkOut_withCheckInCode_isRejected` | P0 |
| NAO-52 | Each day in a recurring series is independent | Two `Booking` rows in one `recurrenceGroupId` series, each with its own attendance row | GET the month view for the series | Day 1's CHECKED_OUT status never carries over to day 2's PENDING row — **automated**: `monthView_eachDayInSeriesIsIndependent` (via the month endpoint rather than a literal day-2 OTP call, since the OTP endpoints always resolve "today") | P0 |
| NAO-53 | 5 wrong tries | Today's row exists | Send 5 wrong codes in a row | Locked 15 min (423 `OTP_LOCKED`); correct code also rejected while locked — **automated**: `checkIn_fiveWrongAttempts_locksEvenCorrectCode`. No dedicated ops-alert channel exists yet — only logged at ERROR, see `NannyAttendanceServiceImpl`'s comment | P0 |
| NAO-54 | Check-in on a weekly off / no shift that day | No `Booking` row exists for that date (weekly off is encoded by `occurrenceDates` simply not creating one) | Nanny app calls `GET /today` | Returns nothing to check in against — there's no booking id to call `/check-in` on in the first place; not separately automated as a negative check-in call | P1 |
| NAO-55 | Parent regenerates the code | Today's row exists | Parent `POST regenerate-otp {type: CHECK_IN}`, nanny tries the old code then the new one | Old code 4xx `WRONG_OTP`; new code 200 — **automated**: `parentRegeneratesCode_oldCodeFails_newCodeSucceeds` | P1 |
| NAO-56 | Correct code but far away | Today's row exists | `POST /check-in` with the right otp, location ~2km from the address | 4xx `TOO_FAR`; attempt not counted (geofence checked before the code) — **automated**: `checkIn_tooFarFromAddress_isRejected` | P1 |
| NAO-57 | Check-out code hidden before check-in | Today's row exists, not yet checked in | Parent `GET /attendance/today` | `checkOutOtp` is null — **automated**: `parentToday_beforeCheckIn_checkOutOtpIsNull` | P1 |
| NAO-58 | Ops manual check-in | Booking exists | Ops `POST manual-check-in {time, reason}`; also try a blank reason | 200, method MANUAL with ops user id and reason; blank reason → 400 — **automated**: `adminManualCheckIn_succeeds_andBlankReasonIsRejected` | P1 |
| NAO-59 | Code never in API logs or nanny responses | Today's row exists | Inspect nanny-side responses (`/today`, `/{id}`) and logs | No plain code anywhere except the parent's `/attendance/today` response — **automated**: `nannyResponses_neverContainThePlainCode` (response-shape + substring check; log output isn't asserted) | P0 |
| NAO-60 | Night shift crosses midnight | Live-in booking, shift starts before midnight | Check in 8 PM, check out 7 AM the next day | Check-out resolves against the start date's own row/code (`resolveOpenCheckInRow` checks today then yesterday), not a new day's row | P2 — not automated: needs an injectable clock to simulate the day rolling over mid-test, which the service doesn't have yet |

## 8. Nanny verification gates assignment + documents (Phase 2 of the nanny-app spec)

Source: `Ektrepha_Nanny_API_Map.xlsx`, "Nanny Documents" rows (spec's own `nanny_document` table
design was superseded — see below).

Current state: **built this session**, extending the pre-existing `com.ektrepha.verification`
package (KYC, ban-evasion, references, interviews, training, code-of-conduct, rolled into
`nanny.overallVerificationStatus`) rather than the spec's separate parallel document table, since
the existing system already covers the same ground more rigorously. Closed four real gaps: (1)
assignment never checked verification status, (2) a nanny had no way to see her own document
status, (3) no audit trail on who viewed a document and when, (4) no parent-facing signal for
which checks a nanny passed. New `document_access_log` table (migration 048). New endpoints:
nanny-self `GET /api/v1/nanny-verification/documents` + `GET
/api/v1/nanny-verification/documents/{id}/view-url`. See `VerificationBadgeMapper`,
`NannyVerificationServiceImpl#listOwn`/`#ownViewUrl`/`#verifiedDocumentTypes`,
`HourlyCareServiceImpl#assignCaregiver`/`#reassignCaregiver`.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-71 | Unverified caregiver can't be assigned | Nanny's `overallVerificationStatus` is not APPROVED | Ops `POST assign`/`reassign` targeting that nanny | 422 `NANNY_DOCS_NOT_VERIFIED`; booking's nanny unchanged — **automated**: `HourlyCareControllerApiTest#assign_unverifiedNanny_isRejected` | P0 |
| NAO-72 | Document view-url is 404 for another nanny's document | Two nannies each with a document; one's id used by the other | Nanny B `GET /documents/{A's doc id}/view-url` | 404, same shape as "not found" — never reveals the document exists or whose it is — **automated**: `NannyVerificationSelfServiceApiTest#viewUrl_anotherNannysDocument_isNotFound` | P0 |
| NAO-73 | Unverified caregiver excluded from the candidate list | A mapped nanny with `overallVerificationStatus` not APPROVED | Ops `GET` assignment candidates for a booking | That nanny never appears in the list, even though she's zone/service-mapped — **automated**: `AdminBookingControllerApiTest#candidates_excludesUnverifiedNanny` | P0 |
| NAO-74 | Nanny sees only her own document records | Nanny A has two documents; nanny B exists with none | Nanny A `GET /api/v1/nanny-verification/documents` | Returns exactly A's records (one per type, latest wins), never B's — **automated**: `NannyVerificationSelfServiceApiTest#myDocuments_listsOwnRecordsOnly` | P1 |
| NAO-75 | Every document view is logged | Nanny has a document | Nanny `GET /documents/{id}/view-url` | A `document_access_log` row is written for that document id and viewer — **automated**: `NannyVerificationSelfServiceApiTest#viewUrl_ownDocument_returnsUrl_andLogsAccess` (asserts the log row directly; the signed URL itself is null in dev/test since `app.gcs.enabled=false` there) | P1 |
| NAO-76 | Verified checks show as parent-facing badges | Nanny has a VERIFIED `ID_PROOF` record | Parent `GET` nanny profile, or booking detail showing nanny summary | `verificationBadges` includes "ID Verified" in both `NannyPublicProfileResponse` and the booking's `NannySummary` — **automated**: assertion in `NannyProfileControllerApiTest#getProfile_verifiedNanny_reportsVerifiedTrueAndRatingAggregate` | P1 |

Two more Phase 2 gaps closed this session: the ops cross-nanny document review queue (API Map row
20) and the 30-days-before-expiry reminder (API Map row 26) — the latter is a new third sweep on
the pre-existing `VerificationExpiryAuditJob`, alongside its existing PCC-auto-suspend and
12-month-re-verification sweeps.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-78 | Ops document review queue defaults to PENDING | One nanny with a PENDING document, another with a VERIFIED one | Ops `GET /api/v1/nanny-verification/documents/queue` (no `status` param) | Only the PENDING nanny's document appears, with `nannyName` — **automated**: `VerificationReviewQueueApiTest#queue_defaultsToPending_andExcludesOtherStatuses` | P1 |
| NAO-79 | Review queue status filter | A VERIFIED document exists | Ops `GET .../documents/queue?status=VERIFIED` | Returns that document — **automated**: `queue_statusParam_filtersToThatStatus` | P2 |
| NAO-80 | PCC expiring within 30 days gets a reminder | A VERIFIED BACKGROUND_CHECK with `expiryDate` 10 days out | Run `VerificationExpiryAuditJob` | Push sent to the nanny; `expiry_reminder_sent_at` set so it isn't sent again tomorrow — **automated**: `VerificationExpiryReminderJobTest#pccExpiringWithin30Days_getsReminderMarkedSent` | P1 |
| NAO-81 | PCC expiring beyond 30 days is not reminded yet | `expiryDate` 45 days out | Run the job | `expiry_reminder_sent_at` stays null — **automated**: `pccExpiringBeyond30Days_isNotReminded` | P2 |
| NAO-82 | Already-reminded PCC isn't reminded twice | `expiry_reminder_sent_at` already set | Run the job again | Timestamp unchanged, no duplicate push — **automated**: `alreadyReminded_isNotRemindedAgain` | P2 |

## 9. Nanny consent + attendance alert jobs (Release 1 "safe basics" of the Build Spec doc)

Source: a separate, more detailed build-spec Google Sheet (Build Spec + Build Order tabs) the
product/eng team put together from the "Not Built Yet" tab, with full request/response/step specs
per endpoint. That doc's own Release 1 ("safe basics") list is 10 rows; of those, two were found
already built under different paths during this pass — `POST /api/v1/nanny-sos` (SOS) and
`POST /api/v1/nanny-leave` (leave requests) both already exist in `com.ektrepha.workforce` — so
this session's slice covers the rest that had no prior overlap: consent, and the two attendance
scheduler alerts. The remaining Release 1 rows (roster, start-trip, arrived, location ping, parent
ETA, push reminder) need new tables (`nanny_location`, `booking_event`) plus a rate limiter and ETA
cache, and are deliberately deferred to a later pass.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NAO-83 | Nanny grants a consent | — | Nanny `POST /api/v1/nanny/consents {type: LOCATION}` | 200; `{type, grantedAt}`; `nannyConsentService.isGranted` now true for that type only — **automated**: `NannyConsentControllerApiTest#grant_locationConsent_returnsGrantedAtAndPersists` | P1 |
| NAO-83b | Only a NANNY can grant consent | — | `POST /api/v1/nanny/consents` as PARENT | 403 — **automated**: `grant_asNonNanny_isForbidden` | P2 |
| NAO-84 | Shift started 10+ min ago, not checked in | Booking CONFIRMED, today's `booking_attendance` row PENDING, `startTime` > 10 min in the past | Run `AttendanceAlertJob#lateCheckInSweep` | Push to nanny; `late_check_in_alert_sent_at` set so the next 5-min run doesn't re-alert — **automated**: `AttendanceAlertJobTest#shiftStarted20MinAgo_notCheckedIn_getsLateAlert` | P1 |
| NAO-84b | Within the 10-min grace period | `startTime` 5 min in the past | Run the sweep | Not alerted yet — **automated**: `shiftStarted5MinAgo_withinGrace_notAlertedYet` | P2 |
| NAO-84c | Already checked in | — | Run the sweep | Never flagged late, regardless of how long ago the shift started — **automated**: `alreadyCheckedIn_isNeverFlaggedLate` | P1 |
| NAO-85 | Shift ended 60+ min ago, still checked in | `booking_attendance` CHECKED_IN, `endTime` > 60 min in the past | Run `AttendanceAlertJob#forgotCheckOutSweep` | Push to both nanny and parent; `forgot_check_out_alert_sent_at` set — **automated**: `shiftEnded70MinAgo_stillCheckedIn_getsForgotCheckOutAlert` | P1 |
| NAO-85b | Already checked out | — | Run the sweep | Never flagged — **automated**: `alreadyCheckedOut_isNeverFlagged` | P2 |

## Open items / questions for product before building §6

- §4 (NAO-31): masking itself is built and display-only — is that sufficient, or does "tap to call" need a telephony proxy number so the real one is never sent to the client at all? This changes both the response contract and whether a server-side call-bridging step is needed.
- §5 (NAO-39): a real Ola Maps directions/distance-matrix integration (source `"OLA_MAPS"` instead of always `"STRAIGHT_LINE"`) needs its own pass once that API's actual contract is confirmed against a live call.
- §5/§6: §5's location-reporting endpoint (`POST .../location`) is nanny-app-only today — does §6's child-entry/exit geofence detection reuse the same ping stream, or is it a separate one?
- §6: geofence radius default, debounce window for boundary jitter, and whether this is purely a backend concern or needs an SDK/library choice (e.g. native OS geofencing vs. periodic polling).
- §1: is hourly-care the only booking type Ops assigns nannies to, or is `AdminBookingController`'s missing assign endpoint (NAO-09) an actual gap to fill?
