# Test Cases — Child Profiles

Source: PRD v2 — "Parent-Side Profile & Booking Surfaces" (screens C1–C6) + companion "Parent App — API Design Spec" §3.3.

Implemented in `com.ektrepha.child` (`ChildController`). Automated coverage: `src/test/java/com/ektrepha/child/ChildControllerApiTest.java` (MockMvc, `@Transactional` rollback) exercises the P0 rows below plus every regression noted inline; no Postman collection exists yet. `AgeDisplay` (the months-vs-years rule) and the `allergies` real-column promotion (migration 026) are both PRD-mandated design decisions, not incidental implementation choices — see inline notes.

Priority: P0 = blocks the screen if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Children List

`GET /api/v1/parents/me/children` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CH-01 | List with no children | No children linked | GET | 200; `[]` — C1 empty state routes straight to "Add child", with "you can book without this" escape (per PRD) | P0 — verified |
| CH-02 | List with no parent row at all | Fresh account, never created a parent row | GET | 200; `[]` — must NOT 404 | P0 — verified regression; originally 404'd, fixed via `ParentResolver` short-circuit in `ChildServiceImpl.list` |
| CH-03 | Age display under 24 months shows months | Child DOB makes them 14 months old | GET list | `ageDisplay: "14 months"` (singular "1 month" at exactly 1) | P0 — verified; PRD: an infant vs toddler is priced/prepared for differently, "1 year old" hides that |
| CH-04 | Age display at/over 24 months shows years | Child DOB makes them 5 years old | GET list | `ageDisplay: "5 years"` (singular "1 year" at exactly 1) | P0 — verified |
| CH-05 | Age recomputes on every read, never cached/stored | Child ages past the 24-month boundary between two GETs (or simulate via DOB near the boundary) | GET on two different days spanning the boundary | `ageDisplay` output changes automatically — no migration, no stale value | P1 — logic verified by construction (`AgeDisplay.of` computes from `LocalDate.now()` on every call, never persisted) |
| CH-06 | Allergies chip surfaces on the list, not just detail | Child has allergies set via care-notes | GET list | `allergies: ["Peanuts", "Dairy"]` present on the summary row, matching detail | P0 — verified; PRD: "shown to nanny at booking", high visibility required at the list level too |
| CH-07 | `lastCareAt` populated only from COMPLETED bookings | Child has a COMPLETED booking and a separate CANCELLED one, ended later | GET list | `lastCareAt` reflects the COMPLETED booking's `endTime`, ignoring the CANCELLED one | P1 |
| CH-08 | `lastCareAt` batched, not N+1 | Parent has multiple children with completed bookings | GET list | Single batched query backs this (`BookingRepository.findLastCompletedByChildIds`) — verify via query count/log inspection if performance-testing | P2 — implementation detail, not directly observable via HTTP |

## 2. Child Detail

`GET /api/v1/parents/me/children/{id}` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CH-09 | Get own child's detail | Child linked to caller | GET | 200; full detail incl. `careNotes`, `guardianCount`, `relationship: "PARENT"`, `primaryContact` | P0 — verified |
| CH-10 | Get a child not linked to caller | Child exists but belongs to a different parent | GET that `childId` | 403; "childId does not belong to the requesting parent" — deliberately 403 not 404, matches `ForbiddenChildAccessException` convention elsewhere in the codebase (doesn't reveal whether the id exists) | P0 — verified |
| CH-11 | Get a nonexistent child id | — | GET `/children/999999` | 403 (same as CH-10 — id existence is never distinguished from ownership failure) | P0 — verified |

## 3. Add Child

`POST /api/v1/parents/me/children` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CH-12 | First child for a parent becomes primary contact | No children linked yet | POST a child | 201; `primaryContact: true` | P0 — verified |
| CH-13 | Second child does not auto-become primary contact | One child already linked (primary) | POST a second child | 201; `primaryContact: false` | P1 — verified |
| CH-14 | Adding a child before completing P1/P2 auto-vivifies the parent row | Fresh account, `PUT /parents/me` never called | POST a child | 201 — succeeds; stub `parent` row created with `firstName: null` | P0 — verified regression; needed migration 029 (`parent.first_name` nullable) |
| CH-15 | Single-name child (no `lastName`) | — | POST `{firstName: "Aarav", dob: "..."}`, no `lastName` | 201; `lastName: null` | P0 — verified |
| CH-16 | Future DOB rejected | — | POST `dob: "2099-01-01"` | 400; message references `dob` (`@Past` constraint) | P0 — verified |
| CH-17 | DOB more than 18 years ago | — | POST `dob` 20 years in the past | Per PRD: warn client-side ("This app is for childcare — is this right?"), do NOT hard-block server-side — confirm API still returns 201 | P2 — not yet explicitly tested; server has no age-ceiling validation by design |
| CH-18 | Blank `firstName` rejected | — | POST `{firstName: ""}` | 400 | P0 — verified |
| CH-19 | Child created with no `gender` | — | POST without `gender` | 201; `gender: null` — field is optional | P2 |

## 4. Edit Child

`PUT /api/v1/parents/me/children/{id}` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CH-20 | Update own child's basic info | Child linked to caller | PUT changed `firstName`/`lastName`/`dob`/`gender` | 200; fields updated, `careNotes`/`guardianCount`/`relationship` unaffected | P0 — verified |
| CH-21 | Update a child not linked to caller | Child belongs to a different parent | PUT to that `childId` | 403 | P0 — same convention as CH-10 |
| CH-22 | Update does not touch care notes or allergies | Child has existing allergies/care notes | PUT basic-info-only payload | 200; `careNotes` in the response is unchanged | P1 |

## 5. Care Notes

`PUT /api/v1/parents/me/children/{id}/care-notes` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CH-23 | Set allergies + all four JSONB fields together | Child linked to caller | PUT `{allergies: ["Peanuts","Dairy"], medicalNotes, routine, comfort, doNot}` | 200; all five fields round-trip exactly on the next GET | P0 — verified |
| CH-24 | Allergies is a real column, not JSONB — survives independently | — | Set allergies, then separately update only `medicalNotes` in a later call | Allergies persists in the `children.allergies` `TEXT[]` column, unaffected by the JSONB write to `meta_data` | P0 — verified; this is the PRD's explicit CPO-mandated safety promotion (§16.7) out of `meta_data` |
| CH-25 | **Omitting `allergies` from a care-notes update must NOT wipe it** | Child has existing allergies set | PUT `{medicalNotes: "update only"}` — no `allergies` key at all | 200; `allergies` in the response is **unchanged**, not cleared to `[]` | P0 — verified regression: original implementation always overwrote `allergies` (`null` → `List.of()`), silently wiping it on any partial update — exactly the "silent failure with a physical-safety consequence" the PRD warns about. Fixed: `null` now means "leave untouched." |
| CH-26 | Explicitly clearing allergies with an empty array works | Child has existing allergies | PUT `{allergies: []}` | 200; `allergies: []` — explicit empty list DOES clear, distinguishing "omitted" from "intentionally emptied" | P0 — verified |
| CH-27 | Updating a child not linked to caller | Child belongs to a different parent | PUT care-notes to that `childId` | 403 | P0 |
| CH-28 | Unreadable/corrupt `meta_data` JSON on an existing row | Row has malformed JSON in `meta_data` (e.g. hand-edited) | GET child detail or PUT care-notes | Does not 500 — logs a warning and treats non-allergy fields as empty rather than crashing the request | P2 — defensive path (`ChildServiceImpl.parseMetaData` catch-and-log), not exercised via a real corrupt row in this session |

## 6. Guardians

`GET /api/v1/parents/me/children/{id}/guardians` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CH-29 | Single-guardian child returns one row (self) | Only the caller is linked | GET guardians | 200; one item, `relationship: "PARENT"`, `primaryContact: true`, `email`/`phone` populated from the linked user | P0 — verified |
| CH-30 | Guardians list for a child not linked to caller | Child belongs to a different parent | GET guardians for that `childId` | 403 | P0 |
| CH-31 | Multi-guardian child (co-parent scenario) | Two parent rows both linked via `parent_child` to the same child | GET guardians as either linked parent | 200; both guardians listed, each with their own `relationship`/`primaryContact`/contact info | P1 — schema supports this (many-to-many), but write path to *add* a second guardian is out of scope per PRD §16.1 (v1 ships read-only) — not exercised with real multi-guardian data this session |

## 7. Remove Child

`DELETE /api/v1/parents/me/children/{id}` (role: PARENT)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CH-32 | Unlink a child with no active bookings | Child linked, no PENDING/CONFIRMED/IN_PROGRESS bookings | DELETE | 204; `parent_child` row removed | P0 — verified |
| CH-33 | The `children` row survives — this is unlink, not delete | Same as CH-32 | DELETE, then query the DB directly for the `children` row | `children` row still exists (only `parent_child` was removed) — required so booking history for this child still renders after removal | P0 — verified directly against Postgres in this session |
| CH-34 | Removing a child with a non-terminal booking is blocked | Child has a PENDING/CONFIRMED/IN_PROGRESS booking | DELETE | 409; "This child has an active booking — cancel or complete it first" | P0 — verified |
| CH-35 | Removing a child whose only booking is COMPLETED/CANCELLED | No non-terminal bookings | DELETE | 204 — terminal bookings don't block unlink; history must still render post-unlink | P1 |
| CH-36 | Removing a child not linked to caller | Child belongs to a different parent | DELETE that `childId` | 403 | P0 |
| CH-37 | Removing a multi-guardian child only unlinks the caller | Child has ≥2 guardians | DELETE as one guardian | 204; the child row and the *other* guardian's `parent_child` link both survive | P1 — matches PRD note ("already implemented correctly" pattern referenced for the general case); not directly re-verified with real multi-guardian data this session |

## 8. Cross-cutting

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| CC-01 | Every endpoint above rejects a NANNY-role token | Valid NANNY token | Call each above with it | 403 on all | P0 |
| CC-02 | Every endpoint above rejects no-auth requests | — | Call each with no token | 401 on all | P0 |
| CC-03 | No child photo support yet | — | Attempt to read/set a photo field | No `profile_photo_s3_key`-backed field is exposed on children endpoints yet (column exists per migration 025, but no upload path is wired) — confirm this stays a documented gap, not a silent no-op | P2 — Phase 2 per PRD |

## Open items / not yet covered

- No automated Postman/curl collection exists for this module yet.
- Photo upload (C1/C2/C3 avatars) — column added (migration 025) but no S3 upload endpoint built; explicitly Phase 2 per the PRD's own build-order.
- Multi-guardian write path (adding a second guardian) is out of scope per PRD §16.1 — CH-31/CH-37 assume such links already exist in the DB, not that they can be created via the API.
