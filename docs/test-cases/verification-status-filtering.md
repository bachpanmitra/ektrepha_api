# Test Cases — Verification Status Filtering in Search

Source: Ektrepha PRD "Verification Status Filtering in Search" (Draft v1). Related to "Nanny Search & Discovery" and "Nanny Proximity Search" (already implemented — see `docs/test-cases/nanny-search.md` and `nanny-proximity-search.md`). This PRD formalizes the verification-filter guarantee specifically and adds the recompute rule that was previously just a schema comment, not real code.

Schema: `nanny.overall_verification_status` (SMALLINT, `NannyVerificationStatus` enum), `nanny_verification` (per-document rows, `VerificationDocType`/`VerificationRecordStatus` enums), partial index `idx_nanny_verification_status ON nanny(overall_verification_status) WHERE overall_verification_status = 3` — all from `006-nanny-marketplace-schema.sql`. Required types for the rollup: `ID_PROOF`, `BACKGROUND_CHECK`, `EDUCATION` (per the Nanny Search PRD's data model — `FIRST_AID`/`REFERENCE` are not required).

Priority: P0 = the PRD's core trust guarantee if broken, P1 = important edge case, P2 = nice-to-have.

## 1. Hard Filter Cannot Be Bypassed (FR §4.1, already implemented)

Already covered by `docs/test-cases/nanny-search.md` FL-03/FL-04 and NF-02 — re-verified here against this PRD's stronger wording ("evaluated before any OR-based filter logic").

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| VF-01 | Verification filter present with zero other filters | Mix of VERIFIED/PENDING/PARTIAL/REJECTED nannies | Search with no other filters | Only VERIFIED nannies returned | P0 |
| VF-02 | Verification filter survives every other filter combination | Same mix, plus varying price/experience/language/skill filters | Run search with every reasonable combination of other filters | No combination ever returns a non-VERIFIED nanny — the filter is a literal unconditional `WHERE` line in `NannySearchRepository`, never inside an `if`, so this is true by construction, not just by testing | P0 |
| VF-03 | No query parameter can disable or weaken the filter | — | Attempt any parameter that might plausibly relax it (e.g. an `includeUnverified` flag, a `minVerificationStatus` filter) | No such parameter exists in `NannySearchRequest` — request is rejected by Jackson's default unknown-field handling (ignored) or bean validation, never honored | P0 |

## 2. Verification Status Is Derived, Not Directly Editable (FR §4.2 — new)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| DV-01 | No API surface accepts `overallVerificationStatus` as direct input | — | Inspect every request DTO in the codebase | None exists — confirmed, no endpoint lets a nanny or admin set this field directly | P0 |
| DV-02 | Entity-level setter is removed for this field | — | Inspect `Nanny.java` | `overallVerificationStatus` is excluded from the class-level Lombok `@Setter` (`@Setter(AccessLevel.NONE)` on the field) — the only way to change it is the dedicated recompute method, enforced at compile time, not just by convention | P0 |

## 3. Recompute Rule (FR §4.3 — new)

Required types = {ID_PROOF, BACKGROUND_CHECK, EDUCATION}.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| RC-01 | All three required types VERIFIED | Nanny has ID_PROOF=VERIFIED, BACKGROUND_CHECK=VERIFIED, EDUCATION=VERIFIED | Recompute | `overall_verification_status = VERIFIED` | P0 |
| RC-02 | Some but not all required types VERIFIED, none REJECTED | ID_PROOF=VERIFIED, BACKGROUND_CHECK=PENDING, EDUCATION=VERIFIED | Recompute | `PARTIAL` | P0 |
| RC-03 | No required type VERIFIED, none REJECTED, none submitted | No `nanny_verification` rows exist for this nanny at all | Recompute | `PENDING` (the schema default) | P0 |
| RC-04 | Any required type REJECTED wins over everything else | ID_PROOF=VERIFIED, BACKGROUND_CHECK=REJECTED, EDUCATION=VERIFIED | Recompute | `REJECTED` — rejection is not overridden by other verified types | P0 |
| RC-05 | Non-required type status never affects the rollup | All three required types VERIFIED, FIRST_AID=REJECTED | Recompute | Still `VERIFIED` — FIRST_AID/REFERENCE are informational only for the rollup | P1 |
| RC-06 | Resubmission after rejection (multiple rows, same type) | A nanny has two `nanny_verification` rows for ID_PROOF — an older REJECTED one and a newer VERIFIED one (after resubmission) | Recompute | Uses the most recent row per type, not the oldest — rollup reflects VERIFIED for that type | P1 — confirms "latest per type" tie-break, not documented explicitly in the PRD but necessary since the schema allows multiple rows per (nanny, type) |
| RC-07 | Recompute triggers automatically on a status change | Nanny currently PENDING (no verification rows) | A required type's `nanny_verification` row is inserted/updated to VERIFIED via the service-layer update path (three times, once per required type) | After the third update, `overall_verification_status` flips to VERIFIED without any separate manual recompute call — the update path and the recompute are the same transaction | P0 — this is the literal "trigger point" requirement from FR §4.3 |

## 4. Drift Detection / Audit (Scope §3, Success Metric §2 — new)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AU-01 | Audit finds no drift in the normal case | Every nanny's stored `overall_verification_status` matches what recompute would produce from their current `nanny_verification` rows | Run the drift audit | Empty result | P0 |
| AU-02 | Audit detects a nanny whose stored status is stale | Directly mutate a `nanny_verification` row's status via SQL (bypassing the service layer, simulating drift from e.g. a manual DB fix or a bug) without triggering recompute | Run the drift audit | That nanny is reported as drifted, with both the stored and expected status | P0 — this is the concrete mechanism behind the PRD's "not just code review" success metric |
| AU-03 | Audit runs on a schedule, not only on demand | — | Inspect the scheduled job | A `@Scheduled` job exists that runs the audit periodically and logs any drift found (warning level, one line per drifted nanny) | P1 — satisfies "tracked via a scheduled audit query" without building a full alerting pipeline, which this PRD doesn't ask for |
| AU-04 | Audit is a single query pass, not N+1 per nanny | Realistic nanny volume | Run the audit and inspect query count/plan | One query loads all `nanny_verification` rows; rollup computation happens in Java over the grouped result — no per-nanny query loop | P1 |

## Explicitly out of scope (per PRD §3) — not tested here

- Changing what counts as a "required" verification type — fixed at {ID_PROOF, BACKGROUND_CHECK, EDUCATION}, defined by the Nanny Search PRD, not reconsidered by this one.
- Any parent-facing display of partial verification tiers ("background check pending") — this PRD is the binary search-visibility gate only.
- The admin document-review workflow itself (approving/rejecting a `nanny_verification` row via an admin UI/endpoint) — that's checklist item "Admin approval workflow before nanny goes live," separate and not yet built. This PRD only requires that *if* a `nanny_verification` row's status changes, the recompute happens — the service-layer method that changes it exists and is tested (RC-07), but no REST endpoint exposes it yet.
