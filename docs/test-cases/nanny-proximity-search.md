# Test Cases — Nanny Proximity Search

Source: Ektrepha PRD "Nanny Proximity Search" (Draft v1). Related to and largely already implemented as part of the "Nanny Search & Discovery" PRD (see `docs/test-cases/nanny-search.md`) — this PRD formalizes the geo mechanism specifically. Cases below either confirm existing behavior against this PRD's more precise requirements, or cover the one functional gap this PRD calls out that wasn't in scope before: a nanny actually setting her own service area.

Schema: `nanny_service_area` (id, nanny_id, lat, lng, radius_km DEFAULT 10) — see `db/changelog/changes/006-nanny-marketplace-schema.sql`. Extensions `cube`/`earthdistance` and the GiST index `idx_nanny_service_area_geo` on `ll_to_earth(lat, lng)` already exist from that migration.

Priority: P0 = blocks the PRD's core guarantee if broken, P1 = important edge case, P2 = nice-to-have.

## 1. Nanny Sets Service Area (FR §4 — new functional gap)

No endpoint existed for this before this PRD; the prior search work only ever *read* `nanny_service_area`, seeded via direct DB inserts for testing.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| SA-01 | Nanny sets service area for the first time | Authenticated NANNY, no existing service area row | `PUT` service area with valid `lat`, `lng`, `radiusKm` | 200/201; a `nanny_service_area` row is created for this nanny | P0 |
| SA-02 | Radius defaults to 10km when omitted | Authenticated NANNY | `PUT` with `lat`/`lng` only, no `radiusKm` | Row created with `radius_km = 10`, matching the PRD's stated default | P0 |
| SA-03 | Nanny edits her existing service area | Nanny already has a service area row (from SA-01) | `PUT` again with a different `lat`/`lng`/`radiusKm` | The existing row is updated in place — no second row created for the same nanny (PRD explicitly scopes "one service center" as in-scope, multiple as future) | P0 |
| SA-04 | Non-nanny (PARENT/ADMIN) cannot set a service area | Authenticated as PARENT | `PUT` service area | 403 | P0 |
| SA-05 | Unauthenticated request | No token | `PUT` service area | 401 | P0 |
| SA-06 | Invalid lat/lng (out of range) | Authenticated NANNY | `PUT` with `lat: 999` | 400 | P1 |
| SA-07 | Missing lat or lng | Authenticated NANNY | `PUT` with `lat` only, no `lng` | 400 | P1 |
| SA-08 | Unrealistic radius (open question §8 — no cap decided yet) | Authenticated NANNY | `PUT` with `radiusKm: 500` | Currently accepted (no admin cap implemented) — flag as a known gap, not a bug, until §8 is decided | P2 — **blocked on PRD open question** |

## 2. Distance-Filtered, Distance-Sorted Search (FR §4, already implemented)

Cross-references `docs/test-cases/nanny-search.md` FL-01/FL-02/RK-01 (same underlying query) — cases here focus on what this PRD adds precision to: the bounding-box + exact-distance query pattern and the `distance_m` field specifically.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| PX-01 | Nanny inside her declared radius of the parent is returned | Nanny's service area covers the parent's search point | Search at that location | Nanny appears in results | P0 — already covered by nanny-search.md FL-01 |
| PX-02 | Nanny just outside her declared radius is excluded | Parent's point is radius_km + a small margin from the nanny's center | Search at that location | Nanny excluded — hard boundary, not a ranking penalty | P0 — already covered by nanny-search.md FL-02 |
| PX-03 | Results are sorted by distance by default | Multiple nannies at different distances, no ranking_config weights configured | Search | Results ordered nearest-first (matches `ORDER BY distance_m ASC` in `NannySearchRepository`, independent of the ranking layer) | P0 |
| PX-04 | Response exposes distance per result | Any successful search with results | Inspect response body | Each result item carries a distance field the client can format as "X km away" | P0 — confirm field name/unit matches this PRD's `distance_m` (meters), not the prior implementation's kilometers |
| PX-05 | Distance value is accurate within earthdistance's stated error bound | Two points at a known great-circle distance apart (e.g. ~5km) | Search and compare returned distance to the known value | Within the PRD's stated "negligible under ~50km" error tolerance | P1 |

## 3. Index-Assisted Query, Not a Full Scan (NFR §6)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| NF-01 | Query plan uses the GiST index via bounding-box pruning | Realistic nanny volume in `nanny_service_area` (hundreds+ rows) | Run `EXPLAIN ANALYZE` on the search query | Plan shows `idx_nanny_service_area_geo` used via the `earth_box(...) @>` clause, not a sequential scan | P0 — this is the PRD's central technical requirement |
| NF-02 | `earth_distance` alone (no `earth_box`) would force a full scan | — | Compare `EXPLAIN ANALYZE` with and without the `earth_box` bounding-box clause | Confirms the PRD's own stated rationale for requiring both clauses together — documentation/regression case, not a runtime check | P1 |
| NF-03 | Query stays index-assisted as nanny volume grows | Seed a large synthetic `nanny_service_area` dataset (thousands of rows) | Re-run `EXPLAIN ANALYZE` | Plan remains index-assisted; latency stays well under whatever p95 target is set for launch scale | P1 — success metric from PRD §2, needs a concrete latency target before this can be a pass/fail gate |

## 4. Implementation Constraints (Technical Approach §5, already followed)

| ID | Title | Expected | Priority |
|----|-------|----------|----------|
| IC-01 | Uses `cube`/`earthdistance` extensions, not PostGIS | `006-nanny-marketplace-schema.sql` creates exactly these two extensions (plus `btree_gist`, for the unrelated booking-overlap constraint) — confirmed, no PostGIS anywhere in the schema | P0 |
| IC-02 | Geo query implemented as native SQL / JdbcTemplate, not JPA Criteria | `NannySearchRepository` uses `NamedParameterJdbcTemplate` with hand-built SQL — confirmed, matches PRD §5 exactly | P0 |

## Open items blocking full coverage (from PRD §8)

- **Radius capping**: SA-08 above is explicitly unresolved — no admin-configured maximum radius exists. A nanny can currently set an arbitrarily large radius, which the PRD itself flags as a pollution risk for every search in that area. Needs a product decision before a real test case (with an expected rejection status) can replace SA-08's placeholder.
- **Per-city default radius**: out of scope until multi-city launch; the current default (10km) is a single global constant (`nanny_service_area.radius_km DEFAULT 10` at the DB level, not yet exposed as a per-city `AppProperties` value).
