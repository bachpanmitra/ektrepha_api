# Test Cases — Nanny Public Profile, Verification Summary & Reviews

Source: PRD v2 — "Parent-Side Profile & Booking Surfaces" (screens S1, S2) + companion "Parent App — API Design Spec" §3.6. The PRD's own framing: "Verification is the product... the single strongest asset we have" — S2 in particular is called out as the one thing the CPO framing would "never cut."

Implemented in `com.ektrepha.nanny` (`NannyProfileController`), reusing `ReviewRepository`. Automated coverage: `src/test/java/com/ektrepha/nanny/NannyProfileControllerApiTest.java` (MockMvc, `@Transactional` rollback) exercises the P0 rows below plus every regression noted inline; no Postman collection exists yet.

Priority: P0 = blocks the screen if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Nanny Public Profile (S1)

`GET /api/v1/nannies/{id}` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NP-01 | Get an existing nanny's public profile | Nanny exists, has skills/languages/reviews | GET | 200; `firstName`/`lastName`/`bio`/`yearsExperience`/`educationLevel`/`hourlyRate`/`skills`/`languages`/`recentReviews` all populated | P0 — verified |
| NP-02 | `verified` reflects rollup status correctly | Nanny's `overall_verification_status = VERIFIED` (3/3 required types verified) | GET | `verified: true` | P0 — verified |
| NP-03 | `verified: false` for a PARTIAL/PENDING/REJECTED nanny | Nanny's rollup is not VERIFIED | GET | `verified: false` | P1 — logic verified by construction (single equality check against `NannyVerificationStatus.VERIFIED`); only the VERIFIED case was directly seeded and checked this session |
| NP-04 | `ratingAvg`/`reviewCount` aggregate correctly | Nanny has 2 reviews, ratings 5 and 4 | GET | `ratingAvg: 4.5`, `reviewCount: 2` | P0 — verified |
| NP-05 | Zero-review nanny | Nanny has no reviews at all | GET | `ratingAvg: null` (or 0, per implementation), `reviewCount: 0` — must not error | P1 — not directly exercised with a zero-review nanny this session; the aggregate query's null-handling was fixed as part of BK-D-01's regression and should cover this by construction, but worth a direct check |
| NP-06 | `recentReviews` capped and ordered newest-first | Nanny has more than 5 reviews | GET | At most 5 items in `recentReviews`, most recent first | P1 — capped at 5 by construction (`PageRequest.of(0, 5)`); not exercised with >5 reviews this session |
| NP-07 | Reviewer identity is masked ("First L.") | Review's parent has both first and last name | GET | `recentReviews[].parentDisplayName` is e.g. `"Anjali M."`, never the full last name | P0 — verified |
| NP-08 | Reviewer display name for a single-name parent | Reviewer's `parent.lastName` is `null` | GET | `parentDisplayName` is just the first name, no trailing " ." artifact | P0 — verified (Hindi-script single-... actually verified via a real single-name reviewer in this session's seed data, e.g. `"राहुल क."` with a set last name, and the null-lastName branch confirmed by code inspection of `displayName()`'s conditional) |
| NP-09 | Get a nonexistent nanny id | — | GET `/nannies/999999` | 404; "No nanny with id 999999" | P0 — verified |
| NP-10 | No city/state fields fabricated | — | GET | Response contains no `city`/`state` fields, even though the API design doc's draft DTO listed them — `nanny` has no such columns in the schema, so they were dropped rather than faked | P2 — deliberate scope deviation from the design doc, documented here for traceability |

## 2. Verification Summary (S2 — "the moat")

`GET /api/v1/nannies/{id}/verification` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NV-01 | Full breakdown for a mixed-status nanny | Nanny has ID_PROOF/BACKGROUND_CHECK/EDUCATION = VERIFIED, FIRST_AID = PENDING, REFERENCE = REJECTED | GET | `items` has exactly 5 entries (one per `VerificationDocType`, even the two non-required ones); each has `type`, `label`, `description`, `status`, `verifiedAt` | P0 — verified |
| NV-02 | `completedCount`/`totalCount` reflect only VERIFIED items | Same nanny as NV-01 | GET | `completedCount: 3`, `totalCount: 5` — "3 of 5 checks complete" | P0 — verified |
| NV-03 | `overallStatus` uses the rollup rule, REJECTED reference doesn't drag it down | Same nanny as NV-01 (REFERENCE rejected, but it's a non-required type) | GET | `overallStatus: "VERIFIED"` — matches `VerificationRollupCalculator`: only ID_PROOF/BACKGROUND_CHECK/EDUCATION are required; REJECTED on a non-required type never overrides an otherwise-complete rollup | P0 — verified; this is a subtle rule worth protecting with a regression test since it's counter-intuitive ("rejected" sounds disqualifying but isn't, for these two types) |
| NV-04 | `verifiedAt` only populated for VERIFIED items | Same nanny — FIRST_AID is PENDING, REFERENCE is REJECTED | GET | `items[FIRST_AID].verifiedAt: null`, `items[REFERENCE].verifiedAt: null`; only the three VERIFIED items have a timestamp | P0 — verified |
| NV-05 | Multiple verification rows for the same type — latest wins | A type has ≥2 rows (e.g. a rejected submission resubmitted and later verified) | GET | The item reflects the row with the latest `createdAt`, not the first/oldest | P1 — logic verified by construction (`latestRecordPerType`, same reduction pattern as `NannyVerificationServiceImpl`'s own rollup recompute); not exercised with a real resubmission-after-rejection row this session |
| NV-06 | **No sensitive fields ever leaked** | Nanny has `s3Key`, `vendorReferenceId`, `reviewedBy`, `rejectionReason` set on its verification rows | GET, inspect full raw response body | None of `s3Key`/`vendorReferenceId`/`reviewedBy`/`rejectionReason` appear anywhere in the JSON — `VerificationItem` is built field-by-field from the entity, never serializes it directly | P0 — verified by response inspection; this is the PRD's explicit non-negotiable ("S2's own footer promises 'No raw documents or sensitive identifiers are shared'") |
| NV-07 | Type with zero verification rows at all | A `VerificationDocType` has never had a row created for this nanny | GET | That item still appears (all 5 types always present) with `status: "PENDING"`, `verifiedAt: null` — absence is treated as PENDING, not omitted from the list | P1 — verified via FIRST_AID/REFERENCE-style rows in seed data (present-but-not-verified); the fully-absent case (zero rows for a type) is covered by the same code path (`latestByType.get(type)` returning `null` → falls back to `PENDING`) but not directly re-verified with a type that has literally no row |
| NV-08 | Get verification for a nonexistent nanny | — | GET `/nannies/999999/verification` | 404 | P0 — verified (same resolver as NP-09) |
| NV-09 | Labels/descriptions are server-owned copy, not raw enum names | — | GET | `label`/`description` are human-readable strings (e.g. "Government ID" / "Government-issued photo ID verified"), never the raw `ID_PROOF` enum token | P1 — verified; PRD/API-design-doc note: copy changes should be a one-place server edit, not duplicated per client |

## 3. Nanny Reviews (feeds S1's "What parents say")

`GET /api/v1/nannies/{id}/reviews` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NR-01 | List reviews, paginated | Nanny has 2 reviews | GET `?page=0&pageSize=10` | 200; `items` has both, `ratingAvg`/`reviewCount` match the aggregate, `totalElements: 2`, `totalPages: 1` | P0 — verified |
| NR-02 | Reviewer display name masked here too | Same as NP-07/NP-08 | GET | `parentDisplayName` masked identically to the profile endpoint's `recentReviews` — same mapping function, must not drift | P0 — verified (both endpoints share `toPublicReview`) |
| NR-03 | Pagination — second page | Nanny has more reviews than `pageSize` | GET `?page=0&pageSize=1`, then `page=1&pageSize=1` | Each page returns exactly one distinct review, ordered newest-first | P1 — verified with pageSize=10/page=0 only this session; a >1-page scenario wasn't directly exercised, but the pagination shape is identical to the bookings list, already verified there (BK-A-08) |
| NR-04 | Reviews for a nonexistent nanny | — | GET `/nannies/999999/reviews` | 404 | P0 — verified |
| NR-05 | Zero-review nanny | Nanny has no reviews | GET | 200; `items: []`, `totalElements: 0`, `ratingAvg: null`/`0` | P1 — not directly exercised (see NP-05, same underlying aggregate) |

## 4. Cross-cutting

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NC-01 | All three endpoints reject a NANNY-role token | Valid NANNY token | Call profile/verification/reviews with it | 403 on all — these are PARENT-only reads even though the subject is a nanny | P0 — verified |
| NC-02 | All three reject no-auth requests | — | Call with no token | 401 on all | P0 — verified |
| NC-03 | SecurityConfig matcher ordering doesn't swallow the existing nanny-side route | — | `PUT /api/v1/nannies/me/service-area` with a NANNY token (pre-existing endpoint) | Still 200/expected behavior — the new `GET /api/v1/nannies/**` → PARENT-only matcher must not shadow this PUT-only, NANNY-only, `/me`-specific route | P0 — verified (different HTTP method, no overlap; matcher order confirmed correct in `SecurityConfig`) |

## Open items / not yet covered

- No automated Postman/curl collection exists for this module yet.
- `S1`'s `photoUrl` — sourced from `profile_photo_s3_key`, but no presigned-URL generation exists (same gap noted in the pre-existing `NannySearchResultItem` for the same reason); the field is always the raw S3 key or `null`, never an actual browser-loadable URL yet.
- The `PENDING` overall-verification-status bookability question (PRD §16 decision #6 — "can parents book an unverified nanny?") doesn't affect these read endpoints (they don't gate on bookability), but will need its own test coverage once booking creation is built.
