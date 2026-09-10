# Test Cases — Nanny Search & Discovery

Source: Ektrepha PRD "Nanny Search & Discovery" (Draft v1).

Schema: `parent`, `nanny`, `nanny_verification`, `language`/`nanny_language`, `skill`/`nanny_skill`, `nanny_service_area`, `booking`, `review` — see `db/changelog/changes/006-nanny-marketplace-schema.sql`. Status/type columns there are SMALLINT codes (see `COMMENT ON COLUMN` in that migration for the mapping), not the string values written below — string values here are for readability against the PRD's own wording.

Priority: P0 = blocks the M1 MVP if broken, P1 = important edge case, P2 = nice-to-have / low-frequency. Milestone column marks which PRD milestone (M1/M2/M3) the case belongs to.

## 1. Search Form

| ID | Title | Preconditions | Steps | Expected Result | Priority | Milestone |
|----|-------|----------------|-------|------------------|----------|-----------|
| SF-01 | Search defaults to parent's saved primary address | Parent has a `parent_address` with `is_primary=true` | Open search with no location override | Search center = primary address lat/lng | P0 | M1 |
| SF-02 | Search with explicit location override | Parent has multiple addresses | Search specifying a non-primary address or raw lat/lng | Search center uses the overridden location, not the primary address | P1 | M1 |
| SF-03 | Radius selector accepts only allowed values | — | Search with radius = 2, 5, 10, 20 km | All accepted; results scoped to that radius | P0 | M1 |
| SF-04 | Radius selector rejects out-of-set value | — | Search with radius = 7 km (not in {2,5,10,20}) | 400 — invalid radius | P1 | M1 |
| SF-05 | Search requires a date & time window | — | Submit search with no `start_time`/`end_time` | 400 — required field missing | P0 | M1 |
| SF-06 | Search with `end_time` before/equal `start_time` | — | Submit search with inverted or equal times | 400 — invalid time window | P0 | M1 |
| SF-07 | Child selector filters by age-appropriate care | Parent has children of different ages saved | Search selecting one child | Only nannies whose profile/skills fit that child's age band returned (exact age-matching rule TBD — flag if no such rule exists yet in nanny profile data) | P1 | M1 — **needs clarification, PRD doesn't define the age-matching rule itself** |
| SF-08 | Search with a child not belonging to the requesting parent | Child ID belongs to a different parent | Submit search with that `child_id` | 403/404 — not the parent's child | P0 | M1 |
| SF-09 | Recurring pattern requested (post-MVP) | — | Submit search with a recurring pattern parameter | 400 or ignored with a clear message — recurring is explicitly out of scope for M1 (§8 open question) | P2 | M3 |

## 2. Filters

| ID | Title | Preconditions | Steps | Expected Result | Priority | Milestone |
|----|-------|----------------|-------|------------------|----------|-----------|
| FL-01 | Distance filter excludes nannies outside radius | Nanny A within radius, Nanny B outside | Search at given location/radius | Nanny B absent from results | P0 | M1 |
| FL-02 | Distance filter is a hard boundary, not a ranking-only signal | Nanny just outside radius (radius + 1m) | Search at radius R | Nanny excluded, not just ranked lower | P0 | M1 |
| FL-03 | Verification filter always applied, even with no other filters | Mix of VERIFIED/PENDING/PARTIAL/REJECTED nannies, all otherwise matching | Search with no filters set | Only VERIFIED nannies appear | P0 | M1 — **NFR: "no unverified nanny may ever appear," test this can't be bypassed by any filter combination** |
| FL-04 | Verification filter cannot be disabled via query params | — | Attempt to pass a param that would include non-VERIFIED nannies (e.g. `includeUnverified=true`) | Ignored or rejected; only VERIFIED ever returned | P0 |
| FL-05 | Price range filter | Nannies at various `hourly_rate` | Search with min/max price | Only nannies within range returned | P0 | M1 |
| FL-06 | Price filter with min > max | — | Search with `minPrice=1000, maxPrice=500` | 400 — invalid range | P1 | M1 |
| FL-07 | Years of experience filter | Nannies with varying `years_experience` | Search with min experience threshold | Only nannies meeting threshold returned | P0 | M1 |
| FL-08 | Education level filter (nice-to-have) | Nannies with different `education_level` | Search with education filter set | Only matching nannies returned; absence of filter returns all levels | P1 | M1 |
| FL-09 | Languages filter — single language | Nannies with different `nanny_language` rows | Search requiring language X | Only nannies with a `nanny_language` row for X returned | P0 | M1 |
| FL-10 | Languages filter — multiple languages (AND vs OR semantics) | Nanny speaks X only; Nanny speaks X and Y | Search requiring [X, Y] | Confirm and document whether this is AND (must speak all) or OR (must speak any) — **not specified in PRD, needs a decision** | P0 | M1 — **open question, blocks correctness** |
| FL-11 | Skills filter — single/multiple skills | Nannies with different `nanny_skill` rows | Search requiring skill(s) | Same AND/OR ambiguity as FL-10 — needs the same decision | P0 | M1 — **open question, blocks correctness** |
| FL-12 | Rating filter absent for M1 | No reviews exist yet | Search with any filters | No rating filter option surfaced/usable (deferred per PRD §5.2, needs review volume) | P1 | M1 |
| FL-13 | Rating filter present post-M2 | Reviews exist | Search with min-rating filter | Only nannies meeting/exceeding average rating returned | P1 | M2 |
| FL-14 | Gender preference filter absent for M1/M2 | — | Attempt to search with a gender filter param | 400 or ignored — explicitly deferred pending product/legal review (§8) | P2 | M3 |
| FL-15 | Combining all MVP filters simultaneously | Diverse nanny pool | Search with distance + price + experience + languages + skills all set | Result set satisfies every filter as an AND — no filter combination leaks a nanny that fails any one of them | P0 | M1 |

## 3. Availability Check

| ID | Title | Preconditions | Steps | Expected Result | Priority | Milestone |
|----|-------|----------------|-------|------------------|----------|-----------|
| AV-01 | Nanny with a fully overlapping existing booking is excluded | Nanny has a CONFIRMED booking 10:00–14:00 | Search for window 11:00–12:00 | Nanny excluded | P0 | M1 |
| AV-02 | Nanny with a partially overlapping booking is excluded | Existing booking 09:00–11:00 | Search for window 10:00–13:00 | Nanny excluded (overlap exists per `start < requested_end AND end > requested_start`) | P0 | M1 |
| AV-03 | Nanny with a back-to-back (non-overlapping) booking is included | Existing booking ends exactly at 10:00 | Search for window 10:00–12:00 | Nanny included (no overlap — boundary is exclusive per the PRD's comparison operators) | P0 | M1 |
| AV-04 | CANCELLED bookings do not block availability | Existing booking 10:00–12:00 with status CANCELLED | Search for window 10:00–12:00 | Nanny included | P0 | M1 |
| AV-05 | COMPLETED bookings do not block availability | Existing booking with status COMPLETED overlapping requested window (data anomaly / past booking) | Search for a future window that happens to overlap a COMPLETED record's timestamps | Nanny included — COMPLETED never blocks per schema note | P1 | M1 |
| AV-06 | PENDING bookings do block availability | Existing PENDING (not yet confirmed) booking overlapping window | Search that window | Nanny excluded — PENDING counts per PRD/schema | P0 | M1 |
| AV-07 | Availability check under concurrent booking attempts | Two parents search/book the same nanny for overlapping windows simultaneously | Both attempt to confirm a booking at once | Exactly one booking succeeds; the other gets a clear "just got booked" error — DB-level `no_overlapping_bookings` EXCLUDE constraint is the actual guarantee, search-time check is UX-only | P0 | M1 — cross-ref booking creation, not pure search |

## 4. Ranking

| ID | Title | Preconditions | Steps | Expected Result | Priority | Milestone |
|----|-------|----------------|-------|------------------|----------|-----------|
| RK-01 | Closer nanny ranks higher, all else equal | Two nannies, same price/experience, different distance | Search | Closer nanny ranked first | P0 | M1 |
| RK-02 | Better price-fit ranks higher relative to stated budget | Two nannies, same distance/experience, different price | Search with a budget hint | Nanny closer to stated budget ranks higher (exact price-fit formula TBD — PRD defers weight tuning) | P1 | M1 |
| RK-03 | More experience ranks higher, all else equal | Two nannies, same distance/price, different `years_experience` | Search | More experienced nanny ranks higher | P1 | M1 |
| RK-04 | Rating factor excluded from ranking pre-M2 | No reviews exist | Search | Ranking formula uses only distance/price/experience — no rating term applied yet | P0 | M1 |
| RK-05 | Rating factor included post-M2 | Reviews exist for at least some nannies | Search | Ranking incorporates rating average as an additional weighted factor | P1 | M2 |
| RK-06 | Ranking weights are configurable without a deploy | `ranking_config` has adjustable weights (per schema, changeset 006) | Change a weight row, repeat the same search | Result ordering reflects the new weights without an app redeploy | P1 | M1 |
| RK-07 | Ranking is stable/deterministic for identical inputs | Same search repeated with no data change | Run the same search twice | Identical ordering both times (no non-deterministic tie-breaking) | P1 | M1 |

## 5. Reviews (post-booking)

| ID | Title | Preconditions | Steps | Expected Result | Priority | Milestone |
|----|-------|----------------|-------|------------------|----------|-----------|
| RV-01 | Parent can review after a COMPLETED booking | Booking status = COMPLETED, no existing review for it | Submit rating (+ optional comment) for that booking | Review created, linked to booking/parent/nanny | P0 | M2 |
| RV-02 | Cannot review a booking that isn't COMPLETED | Booking status = PENDING/CONFIRMED/IN_PROGRESS/CANCELLED | Attempt to submit a review | Rejected (400/403) | P0 | M2 |
| RV-03 | Cannot review a booking twice | Booking already has a review | Attempt a second review submission for the same booking | Rejected — one review per completed booking (`review.booking_id` UNIQUE) | P0 | M2 |
| RV-04 | Cannot review someone else's booking | Booking belongs to a different parent | Attempt to submit a review for it | Rejected (403) | P0 | M2 |
| RV-05 | Repeat bookings between the same parent-nanny pair can each be reviewed | Parent has 2 separate COMPLETED bookings with the same nanny | Submit a review for each | Both succeed independently — one review per booking, not per parent-nanny pair | P1 | M2 |
| RV-06 | Rating must be within 1–5 | Valid COMPLETED booking | Submit rating = 0 or 6 | Rejected (400) | P0 | M2 |
| RV-07 | Nanny's aggregate rating updates after a new review | Nanny has N existing reviews | Add a new review | Rating average used in ranking/results reflects the new review | P1 | M2 |

## 6. Non-Functional

| ID | Title | Preconditions | Steps | Expected Result | Priority | Milestone |
|----|-------|----------------|-------|------------------|----------|-----------|
| NF-01 | Search latency at expected launch scale | A few hundred nanny rows in a city | Run a representative filtered+ranked search | Response well under 1s | P1 | M1 |
| NF-02 | Verification filter cannot be bypassed under any filter combination | Full fuzz/combinatorial pass over filter params | Run search with every reasonable filter combination | No combination ever returns a non-VERIFIED nanny | P0 | M1 — most important NFR in this PRD, deserves dedicated test coverage, not just spot checks |
| NF-03 | Proximity queries use the geo index, not a full scan | `nanny_service_area`/`parent_address` populated at realistic volume | Run `EXPLAIN ANALYZE` on the search query | Query plan shows index usage (`idx_nanny_service_area_geo` / `idx_parent_address_geo`) via `earth_box()` bounding-box pruning, not a sequential scan | P1 | M1 |
| NF-04 | Search query correctly combines bounding-box + exact-distance filtering | Nannies just inside/outside the radius, near the bounding-box edge | Search near a radius boundary | Bounding box over-selects, then exact `earth_distance()` filter trims to the true radius — no false positives beyond the radius | P0 | M1 |

## Open items blocking full coverage (from PRD §8, plus gaps found while writing these cases)

- **Languages/skills filter semantics (FL-10, FL-11)**: PRD doesn't say whether multi-value filters are AND or OR. This changes expected results for a large share of test cases and needs a decision before those cases can be finalized.
- **Child age-matching rule (SF-07)**: PRD says the child selector filters "by age-appropriate care" but no rule for what makes a nanny age-appropriate exists in the schema or PRD text.
- Recurring bookings (§8) — deferred to a separate PRD, cases here (SF-09) only cover that M1 correctly rejects/ignores it.
- Gender preference filter (§8) — pending product/legal review; FL-14 only covers that it's correctly absent pre-M3.
- Service area model — radius vs explicit pincode list is still open per §8; schema (changeset 006) implements the radius model, so these cases assume that, but confirm before treating them as final if the pincode model is chosen instead.
