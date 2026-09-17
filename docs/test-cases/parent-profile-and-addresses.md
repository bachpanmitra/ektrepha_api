# Test Cases — Parent Profile & Address Book

Source: PRD v2 — "Parent-Side Profile & Booking Surfaces" (screens P1–P4) + companion "Parent App — API Design Spec" §3.2.

Implemented in `com.ektrepha.parent` (`ParentProfileController`, `ParentAddressController`). Automated coverage: `src/test/java/com/ektrepha/parent/ParentControllerApiTest.java` (MockMvc, `@Transactional` rollback) exercises the P0 rows below plus every regression noted inline; no Postman collection exists yet.

Priority: P0 = blocks the screen if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Parent Profile — Get

`GET /api/v1/parents/me` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| PP-01 | Get profile for a user with no parent row yet | Fresh PARENT account, `PUT /parents/me` never called, no child/address ever created either | GET `/parents/me` | 200; `{id: null, firstName: null, lastName: null, photoUrl: null, primaryCity: null, primaryState: null}` — P1 renders this as "Complete your profile", not an error | P0 — verified |
| PP-02 | Get profile after a stub parent row was auto-created (child/address added first) | Fresh account, `POST /children` or `POST /addresses` called before ever calling `PUT /parents/me` | GET `/parents/me` | 200; `id` is non-null, `firstName`/`lastName` still `null` | P0 — verified; see CH-04/AB-04 for the auto-vivify trigger |
| PP-03 | Get profile with a saved primary address | Parent has ≥1 address, one marked primary | GET `/parents/me` | `primaryCity`/`primaryState` reflect the primary address's `city`/`state`, not any other saved address | P1 — verified |
| PP-04 | Get profile without auth token | — | GET `/parents/me`, no `Authorization` header | 401 | P0 — verified |
| PP-05 | Get profile with a NANNY-role token | Valid NANNY account token | GET `/parents/me` with that token | 403 | P0 — verified |

## 2. Parent Profile — Upsert

`PUT /api/v1/parents/me` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| PP-06 | First-time upsert creates the parent row | No parent row yet | PUT `{firstName: "Rahul"}` | 200; parent row created, `firstName: "Rahul"`, `lastName: null` | P0 — verified |
| PP-07 | Single-name user — `lastName` omitted | — | PUT `{firstName: "Rahul"}` (no `lastName` key) | 200 — `lastName` stays/becomes `null`; must NOT 400 (migration 023 dropped the NOT NULL constraint) | P0 — verified, this is the PRD's explicit "single-name users are common in India" requirement |
| PP-08 | Blank `firstName` rejected | — | PUT `{firstName: ""}` | 400; message references `firstName` | P0 — verified, inline validation error not a toast (per PRD) |
| PP-09 | Unicode / non-Latin script name | — | PUT `{firstName: "राहुल", lastName: "कुमार"}` | 200; both fields round-trip byte-for-byte on the next GET | P0 — verified, PRD requires full Unicode support, no transliteration |
| PP-10 | Re-PUT overwrites previous values (full replace, not merge) | Parent already has `firstName`/`lastName` set | PUT `{firstName: "New"}` only (omit `lastName`) | 200; `lastName` becomes `null` — this is correct PUT semantics, distinct from the deliberately-preserving PATCH-like behavior on care notes (see CH-13) | P1 — verified; confirm client always resends both fields together to avoid surprising a user |
| PP-11 | `firstName` over 100 chars | — | PUT `{firstName: "<101 chars>"}` | 400 | P2 |
| PP-12 | Concurrent edits from two devices | Two requests race | Fire two PUTs with different names near-simultaneously | Last-write-wins, no error, no corrupted row (per PRD, acceptable for this data) | P2 |

## 3. Address Book — List

`GET /api/v1/parents/me/addresses` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AB-01 | List with no addresses | No addresses saved | GET | 200; `[]` — P3 renders empty state + "Add address" CTA, never a dead end | P0 — verified |
| AB-02 | List with no parent row at all | Fresh account, never created a parent row via any path | GET | 200; `[]` — must NOT 404 | P0 — verified (regression case; originally 404'd, fixed via `ParentResolver`) |
| AB-03 | List ordering — primary first | ≥2 addresses, one primary | GET | Primary address is `items[0]`; remaining ordered oldest-first | P1 — verified |

## 4. Address Book — Create

`POST /api/v1/parents/me/addresses` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AB-04 | First address for a parent auto-becomes primary | No addresses yet | POST with `makePrimary` omitted or `false` | 201; `primary: true` regardless of what was sent — PRD rule: first address is always primary | P0 — verified |
| AB-05 | Creating a child/address before completing P1/P2 auto-vivifies the parent row | Fresh account, `PUT /parents/me` never called | POST an address | 201 — succeeds; a stub `parent` row is created with `firstName: null` | P0 — verified regression; this needed migration 029 (`parent.first_name` made nullable) after the first fix attempt 500'd |
| AB-06 | Second address does not become primary unless requested | One address already primary | POST second address, `makePrimary: false`/omitted | 201; `primary: false`; original stays primary | P0 — verified |
| AB-07 | `makePrimary` key entirely omitted from the JSON body | — | POST body with no `makePrimary` field at all | 201 — must NOT 500 | P0 — verified regression; `makePrimary` was originally a primitive `boolean`, which this app's Jackson setup fails to default when absent, returning `400 Malformed request body` (worse, it was actually observed as a hard failure, not silently defaulting) — fixed by boxing to `Boolean` |
| AB-08 | Non-serviceable pincode still saves | Pincode has no `serviceability_pincode` row, or `is_serviceable = false` | POST with that pincode | 201; address saved, `serviceable: false` — client shows "We're not in this area yet" + waitlist CTA, save is not blocked | P0 — verified |
| AB-09 | Serviceable pincode | Pincode has `is_serviceable = true` | POST with that pincode | 201; `serviceable: true` | P0 — verified |
| AB-10 | Invalid pincode format | — | POST `pincode: "12A"` (not 6 digits) | 400; message references pincode pattern | P0 — verified |
| AB-11 | `country` always defaults server-side | — | POST without a `country` field (request DTO has none) | 201; `country: "India"` | P1 — verified |
| AB-12 | `accessNotes` round-trips | — | POST with `accessNotes: "Gate code 4432, ask for the watchman"` | 201; field present verbatim on read | P1 — verified; PRD calls this an "underrated field" for on-time arrivals |
| AB-13 | Missing required field (`addressLine1`) | — | POST without `addressLine1` | 400 | P1 |
| AB-14 | Invalid `label` value | — | POST `label: "INVALID"` (not HOME/WORK/OTHER) | 400 (JSON deserialization failure on the enum) | P2 — verified |

## 5. Address Book — Update

`PUT /api/v1/parents/me/addresses/{id}` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AB-15 | Update own address fields | Address exists, owned by caller | PUT with changed `addressLine1`/`city`/etc | 200; fields updated | P0 |
| AB-16 | Update sets `makePrimary: true` on a non-primary address | Address exists, not currently primary; another address is primary | PUT `{..., makePrimary: true}` | 200; this address becomes primary, the previous primary flips to `false` — exactly one primary at all times | P0 — verified |
| AB-17 | Update another parent's address | Address belongs to a different parent | PUT to that `addressId` with caller's own token | 400; "No such address for this parent" — deliberately not 403/404, matches existing ownership-check convention (doesn't reveal whether the id exists) | P0 — verified |
| AB-18 | Update non-existent address id | — | PUT to `/addresses/999999` | 400; same "No such address" message | P0 — verified |

## 6. Address Book — Delete

`DELETE /api/v1/parents/me/addresses/{id}` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AB-19 | Delete a non-primary address | ≥2 addresses, target not primary | DELETE | 204; address removed; primary unaffected | P0 — verified |
| AB-20 | Delete the primary address promotes the next one | ≥2 addresses, target is primary | DELETE | 204; the next remaining address (oldest by id) is promoted to primary | P0 — verified |
| AB-21 | Delete the only remaining address | Exactly one address, it's primary | DELETE | 204; list is now empty, no primary — no error | P1 |
| AB-22 | Delete an address referenced by a non-terminal booking | Address is `address_id` on a booking with status PENDING/CONFIRMED/IN_PROGRESS | DELETE | 409; "This address is used by an active booking — edit it instead of deleting it" | P0 — verified; this is the PRD's explicit silent-data-corruption guard (`booking.address_id` is nullable, an unguarded delete would blank a live booking) |
| AB-23 | Delete an address only referenced by COMPLETED/CANCELLED bookings | Address used only by terminal-status bookings | DELETE | 204 — terminal bookings don't block deletion | P1 |

## 7. Address Book — Make Primary

`PUT /api/v1/parents/me/addresses/{id}/primary` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| AB-24 | Make a non-primary address primary | ≥2 addresses | PUT `/primary` on the non-primary one | 200; target becomes primary, previous primary flips off, atomic (no window where two are primary or zero are) | P0 — verified |
| AB-25 | Make the already-primary address primary again | Target already primary | PUT `/primary` | 200; idempotent no-op, still primary | P2 — verified |

## 8. Cross-cutting

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| PA-01 | Every mutating endpoint above rejects a NANNY-role token | Valid NANNY token | Call each POST/PUT/DELETE above with it | 403 on all | P0 |
| PA-02 | Every endpoint above rejects no-auth requests | — | Call each with no token | 401 on all | P0 |
| PA-03 | `Address` responses never expose another parent's data | Two different parent accounts, each with addresses | List/get as parent A | Only parent A's own addresses ever appear | P0 |

## Open items / not yet covered

- No automated Postman/curl collection exists for this module yet — these cases were verified by hand this session; converting them to `docs/postman/` is a follow-up.
- `parentProfileComplete`-style derived flag (mentioned in the API design doc's `UserProfileResponse`) is not implemented — A1's "Complete your profile" nudge would need to derive this client-side from `firstName == null` for now.
