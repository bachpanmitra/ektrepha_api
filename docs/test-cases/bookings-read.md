# Test Cases — Bookings (Read Side: My Bookings / History)

Source: PRD v2 — "Parent-Side Profile & Booking Surfaces" (screens B1, B2, H1, H2) + companion "Parent App — API Design Spec" §3.4.

Implemented in `com.ektrepha.booking` (`BookingReadController`). Read-only — booking creation, cancellation, contact-nanny, and rebook are all out of scope for this build pass, gated on the payments decision (PRD §16.4/§17); `POST /api/v1/bookings` remains the pre-existing placeholder stub in `com.ektrepha.controller.BookingController`, untouched. Automated coverage: `src/test/java/com/ektrepha/booking/BookingReadControllerApiTest.java` (MockMvc, `@Transactional` rollback) exercises the P0 rows below plus every regression noted inline; no Postman collection exists yet.

Priority: P0 = blocks the screen if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. List — Active (My Bookings, B1)

`GET /api/v1/bookings?scope=active` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BK-A-01 | Active list returns only PENDING/CONFIRMED/IN_PROGRESS | Parent has bookings in every status | GET `?scope=active` | Only those three statuses appear; COMPLETED/CANCELLED excluded | P0 — verified |
| BK-A-02 | Active list sorted ascending by start time | Multiple active bookings at different times | GET `?scope=active` | Items ordered soonest-first (an IN_PROGRESS booking that started 40 min ago sorts before a PENDING one starting tomorrow, since its `startTime` is earlier) | P0 — verified |
| BK-A-03 | Empty active list for a fresh account | No parent row at all, or parent row with no bookings | GET `?scope=active` | 200; `{items: [], totalElements: 0, totalPages: 0}` — must NOT 404/500 | P0 — verified regression; originally 500'd for a user with no parent row (`ParentRepository` lookup threw), fixed to short-circuit to an empty page |
| BK-A-04 | `elapsedSeconds` present only for IN_PROGRESS | One booking IN_PROGRESS, others PENDING/CONFIRMED | GET `?scope=active` | IN_PROGRESS card has a non-null `elapsedSeconds` (server-clock-derived); PENDING/CONFIRMED cards have `elapsedSeconds: null` | P0 — verified |
| BK-A-05 | `elapsedSeconds` is server-clock derived, not client-suppliable | IN_PROGRESS booking | Inspect response, compare to `now() - startTime` computed independently | Value matches server time, not anything the client could spoof | P1 — verified by construction (`Duration.between(startTime, Instant.now())`, no client input involved) — PRD explicitly calls out a device clock must never drive this |
| BK-A-06 | Nanny rating/verification badge on each card | Nanny has reviews and `overallVerificationStatus = VERIFIED` | GET `?scope=active` | `nanny.verified: true`, `nanny.ratingAvg`/`reviewCount` populated, batched across all cards on the page (not N+1) | P0 — verified |
| BK-A-07 | `reviewPending` always `false` on active bookings | Any active-scope booking | GET `?scope=active` | `reviewPending: false` for every item — only COMPLETED bookings without a review set this `true` (see H-list) | P1 — verified |
| BK-A-08 | Pagination — page/pageSize honored | ≥2 active bookings | GET `?scope=active&page=0&pageSize=1`, then `page=1&pageSize=1` | Each page returns exactly one distinct item; `totalElements`/`totalPages` consistent | P1 — verified |
| BK-A-09 | `scope` parameter missing entirely | — | GET `/bookings` (no `scope` query param) | 400; message: `Required request parameter 'scope' for method parameter type String is not present` | P0 — verified regression; originally fell through to a generic 500 (`MissingServletRequestParameterException` wasn't handled) — now explicitly mapped to 400 in `GlobalExceptionHandler`, also benefits the pre-existing `ServiceabilitySearchController` autocomplete endpoint which had the same gap |
| BK-A-10 | Invalid `scope` value | — | GET `?scope=bogus` | 400; "scope must be 'active' or 'history'" | P0 — verified |
| BK-A-11 | A booking spanning midnight renders as one card | Booking `startTime`/`endTime` cross midnight | GET | Single card with both timestamps present; client is responsible for the "10pm – 2am (next day)" display — API must not split it into two rows | P2 — not directly exercised with a midnight-spanning row this session, but nothing in the query/response shape would split it |

## 2. List — History (Booking History, H1)

`GET /api/v1/bookings?scope=history` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BK-H-01 | History list returns only COMPLETED/CANCELLED | Parent has bookings in every status | GET `?scope=history` | Only those two statuses appear; PENDING/CONFIRMED/IN_PROGRESS excluded | P0 — verified |
| BK-H-02 | History list sorted descending by start time | Multiple history bookings at different times | GET `?scope=history` | Most-recent-first ordering | P0 — verified |
| BK-H-03 | `reviewPending: true` on a COMPLETED booking with no review yet | COMPLETED booking, no `review` row for it | GET `?scope=history` | That item has `reviewPending: true` | P0 — verified |
| BK-H-04 | `reviewPending: false` on a COMPLETED booking that's already been reviewed | COMPLETED booking with an existing `review` row | GET `?scope=history` | `reviewPending: false` | P0 — verified |
| BK-H-05 | `reviewPending: false` on a CANCELLED booking | CANCELLED booking (nothing to review) | GET `?scope=history` | `reviewPending: false` regardless of review existence — cancelled bookings are never reviewable | P1 — verified |
| BK-H-06 | Active and history scopes never share a booking | Any booking set | Compare `?scope=active` and `?scope=history` results for overlapping ids | No booking id appears in both — same underlying query/repository method, two disjoint status sets, so they structurally cannot drift or double-count | P0 — verified by construction (`BookingRepository.findByParentIdAndStatusIn`, one method, two callers) |

## 3. Booking Detail (B2 / H2)

`GET /api/v1/bookings/{id}` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BK-D-01 | Get own COMPLETED booking with a review | Booking belongs to caller, has a `review` row | GET | 200; `review` object populated with `rating`/`comment`/`createdAt` | P0 — verified regression: this originally 500'd (`ClassCastException` — `ReviewRepository.findRatingAggregate` returning a bare `Object[]` for a no-GROUP-BY aggregate query got double-wrapped by Spring Data). Fixed by changing the return type to `List<Object[]>`. |
| BK-D-02 | Get own COMPLETED booking with no review yet | Booking belongs to caller, no `review` row | GET | 200; `review: null` | P0 — verified |
| BK-D-03 | Get own IN_PROGRESS booking | Booking belongs to caller, IN_PROGRESS | GET | 200; `elapsedSeconds` non-null, server-derived | P0 — verified |
| BK-D-04 | Address embedded with serviceability flag | Booking has a non-null `address_id` | GET | `address` object present, includes `accessNotes`, `serviceable` computed live from the current `serviceability_pincode` state | P0 — verified |
| BK-D-05 | Get a booking belonging to another parent | Booking exists, owned by a different parent | GET that booking's `id` as caller | 404; "No booking found with id X" — does not leak that it exists under someone else's account | P0 — verified |
| BK-D-06 | Get a nonexistent booking id | — | GET `/bookings/999999` | 404 | P0 — verified |
| BK-D-07 | `durationHours` computed correctly | Booking spans a known duration (e.g. 2h) | GET | `durationHours: 2` | P1 — verified |
| BK-D-08 | No itemized price breakdown (`totalAmount` only) | Any booking | GET | Response has a single `totalAmount`, no `baseAmount`/`surgeAmount`/`platformFee` breakdown fields | P1 — intentional scope limit (PRD API design doc conflict #4): no `payment_transaction` model exists yet; a fabricated breakdown would be worse than none. Revisit once §16.4 is decided. |
| BK-D-09 | Nanny summary includes live rating aggregate | Nanny has multiple reviews from multiple parents | GET | `nanny.ratingAvg`/`reviewCount` reflect the current aggregate across all reviews for that nanny, not just this booking's own review | P1 — verified |

## 4. Cross-cutting

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BK-C-01 | Both list and detail reject a NANNY-role token | Valid NANNY token | Call list and detail with it | 403 on both | P0 — verified |
| BK-C-02 | Both reject no-auth requests | — | Call with no token | 401 on both | P0 — verified |
| BK-C-03 | `POST /api/v1/bookings` (pre-existing stub) is unaffected by this build | — | POST with a PARENT token | 201; `{"message": "Booking created", "requestedBy": "<userId>"}` — placeholder behavior unchanged, confirms no route collision was introduced | P1 — verified |

## Out of scope for this build pass (do not write test cases against these yet)

- `POST /api/v1/bookings/{id}/cancel` (B4) — blocked on the cancellation-fee-tier decision (PRD §16.4).
- `GET /api/v1/bookings/{id}/contact` (B5) — number masking/telephony integration not built.
- `GET /api/v1/bookings/{id}/rebook-context` (H4) — depends on nanny-still-serves-zone checks not yet wired to this read path.
- Any itemized charge breakdown (`ChargeSummary` in the API design doc) — no payment model exists (§17 hard blocker).

## Open items / not yet covered

- No automated Postman/curl collection exists for this module yet.
- Timezone/DST edge cases (PRD §10: "render in device timezone... use absolute timestamps for duration, never wall-clock arithmetic") are satisfied by construction (`Instant`/`TIMESTAMPTZ` throughout, `Duration.between` for elapsed time) but not exercised against a real cross-timezone booking in this session.
