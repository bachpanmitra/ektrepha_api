# Test Cases — Admin Home Page Banner Management

Source: Claude Doc PRD "PRD: Admin Home Page Banner Management" (authored 2026-11-16). This is a
**pre-implementation PRD** — a repo-wide search confirms no `banner`/`home_banners`-related
controller, service, repository, migration or table exists yet anywhere under `src/main/java`
(the only string matches on "banner" are unrelated: `NannySosController` and a couple of
`hourlycare` DTOs). Every test case below describes expected behavior **per the PRD's own spec**,
to be validated once the feature is actually built — there is no Postman collection, MockMvc test
class, or real class/endpoint to cite yet. Endpoint paths, field names and table/column names below
are quoted directly from the PRD's own "Data model and API" section, not from any existing code, and
should be treated as the PRD's proposal rather than a confirmed contract.

Priority: P0 = blocks the screen if broken, P1 = important edge case, P2 = nice-to-have / low-frequency.

## 1. Create Banner

`POST /admin/banners` (role: Super admin or Content manager — PRD's Permissions table gives
"Other admin roles" View only, not Create/edit)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-01 | Create with only required fields | Logged in as Super admin or Content manager | POST with `title`, `mobileImage`, `linkType: "none"`, `startAt`, `platforms` (all 3), `careTypes` (both) | 200/201; banner created with `isActive: false` (Draft) — PRD: "Create a banner, saved as Draft until published" | P0 |
| BN-02 | Create with every optional field populated | — | POST adding `ctaText`, `desktopImage`, `languages`, `endAt` | 200/201; all fields stored as given | P0 |
| BN-03 | `title` missing or blank | — | POST without `title` | 400 — required field | P0 |
| BN-04 | `title` over 60 characters | — | POST 61-char `title` | 400 — "Max 60 characters" | P1 |
| BN-05 | `ctaText` over 30 characters | — | POST 31-char `ctaText` | 400 — "Max 30 characters" | P1 |
| BN-06 | `mobileImage` missing | — | POST without a mobile image | 400 — required field | P0 |
| BN-07 | `mobileImage` wrong format (e.g. GIF, BMP) | — | POST a non-JPG/PNG/WebP file | Blocked with a clear error naming the required formats — PRD edge case: "Wrong image size or format: block upload with a clear error naming the required size" | P0 |
| BN-08 | `mobileImage` over 1 MB | — | POST an oversized mobile image | Blocked with a clear size error | P0 |
| BN-09 | `mobileImage` not 1080×540 (2:1) | — | POST a correctly-formatted image at the wrong dimensions | Blocked with a clear error naming the required size | P1 |
| BN-10 | `desktopImage` omitted | — | POST without a desktop image | 200/201; display falls back to the mobile image — verify at **display** time (home page), not just accepted at creation | P1 |
| BN-11 | `desktopImage` over 2 MB or not 1920×480 (4:1) | — | POST an oversized or wrong-ratio desktop image | Blocked, same as BN-08/BN-09 for the desktop slot | P2 |
| BN-12 | `linkType` is a screen/URL type but `linkTarget` is missing | — | POST `linkType: "screen"` (or `internal_url`/`external_url`) with no `linkTarget` | 400 — "Link target — Required if link type is not None" | P0 |
| BN-13 | `linkTarget` is not a valid URL for an Internal/External URL link type | — | POST `linkType: "external_url"`, `linkTarget: "not a url"` | 400 | P1 |
| BN-14 | `linkType: "screen"` with a `linkTarget` key not in the fixed screen-key list | — | POST `linkTarget: "some_unknown_key"` | **PRD doesn't specify the fixed screen-key list yet** ("Which in-app screens can a banner link to? Need the final list of screen keys" — Open question) — flag with PM before this can be asserted either way | P2 — open item |
| BN-15 | `linkType: "none"` with a `linkTarget` value supplied anyway | — | POST `linkType: "none"`, `linkTarget: "https://example.com"` | PRD doesn't say whether this is rejected or just ignored — reasonable default is "ignored since not required", but flag with PM to confirm | P2 — open item |
| BN-16 | `startAt` missing | — | POST without `startAt` | 400 — required field | P0 |
| BN-17 | `endAt` before `startAt` | — | POST `endAt` earlier than `startAt` | 400 — "Must be after start" | P0 |
| BN-18 | `endAt` equal to `startAt` | — | POST `endAt == startAt` | 400 — PRD says end "must be **after** start", so equal should fail; confirm this isn't accidentally treated as valid | P2 |
| BN-19 | `endAt` omitted | — | POST without `endAt` | 200/201; banner has no end date and stays live indefinitely once active — "empty = no end" | P0 |
| BN-20 | `endAt` in the past relative to now | — | POST (or edit) with `endAt` before the current time | Blocked with an error — PRD edge case: "End date in the past on save: block save with an error" | P0 |
| BN-21 | `platforms` with none selected | — | POST `platforms: []` | 400 — field is marked Required; UI defaults to all 3 pre-checked, but an explicit empty selection should be rejected | P1 |
| BN-22 | `languages` omitted | — | POST without `languages` | 200/201; empty/omitted means "all languages" | P1 |
| BN-23 | `careTypes` with neither Child care nor Elder care selected | — | POST `careTypes: []` | 400 — Required, default both; same reasoning as BN-21 | P1 |
| BN-24 | Create as Content manager | Logged in as Content manager | POST valid banner | 200/201 — Content manager has full Create/edit per the Permissions table, same as Super admin | P0 |
| BN-25 | Create as an "Other admin role" (view-only) | Logged in as a non-Super-admin, non-Content-manager admin | POST valid banner | 403 — Permissions table: Other admin roles get View only | P0 |
| BN-26 | Create without admin auth | — | POST with no/invalid token | 401 | P0 |

## 2. Edit Banner

`PUT /admin/banners/:id`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-27 | Edit a Draft banner's fields | Banner exists, `isActive: false` | PUT updated `title` | 200; saved immediately, no confirmation needed | P0 |
| BN-28 | Edit a field on a currently Live banner | Banner is Live (active, within schedule) | PUT a changed field | PRD: "Edit any field; edits to a live banner apply on save, with a confirm prompt" — the confirm prompt is admin-panel UI; confirm whether the API itself applies the change unconditionally on PUT, or whether confirmation is purely client-side with no server-side distinction between editing a draft vs. a live banner | P1 — worth confirming the API has no separate "are you sure" step |
| BN-29 | Edit a non-existent banner id | — | PUT `/admin/banners/does-not-exist` | 404 | P0 |
| BN-30 | Edit as an "Other admin role" | — | PUT as a view-only admin | 403 | P0 |
| BN-31 | Edit `isActive` directly via `PUT /admin/banners/:id` instead of `PATCH .../status` | — | PUT including `isActive` in the body | **Open item**: the PRD lists a separate `PATCH /admin/banners/:id/status` purely for toggling active — unclear whether `PUT` also accepts `isActive` (and if so, whether it's redundant with PATCH) or whether `PUT` should reject/ignore that field; flag with eng during implementation | P2 — open item |
| BN-32 | Edit without admin auth | — | PUT with no token | 401 | P0 |

## 3. Publish / Unpublish (Active Toggle)

`PATCH /admin/banners/:id/status`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-33 | Toggle a Draft banner (future `startAt`) active | `startAt` in the future | PATCH `{"isActive": true}` | Banner becomes **Scheduled**, not Live, until `startAt` is reached; home page does not show it yet | P0 |
| BN-34 | Toggle a Draft banner whose `startAt` has already passed | `startAt` in the past, `endAt` empty or in the future | PATCH `{"isActive": true}` | Banner goes straight to **Live** — PRD: "A banner whose start time has already passed goes straight from Draft to Live on publish" | P0 |
| BN-35 | Toggle a Live banner inactive | Banner currently Live | PATCH `{"isActive": false}` | Banner removed from the home page within 60 seconds — PRD: "takes effect within 60 seconds" | P0 |
| BN-36 | Toggle called by an "Other admin role" | — | PATCH as a view-only admin | 403 — Permissions table: Publish/delete requires Super admin or Content manager | P0 |
| BN-37 | Toggle on a soft-deleted banner | Banner previously deleted (BN-43) | PATCH its status | 404 or 409 — deleted banners should not be toggleable; exact status code not specified by the PRD, flag with eng | P2 — open item |
| BN-38 | Toggle without admin auth | — | PATCH with no token | 401 | P0 |

## 4. Reorder

`PUT /admin/banners/reorder` (body: ordered list of ids)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-39 | Reorder with a complete, valid ordered list of existing ids | ≥2 banners exist | PUT the full id list in a new order | 200; `sortOrder` updated on each banner to match; new order reflected in the next home-page load | P0 |
| BN-40 | Reorder list omits some existing banner ids | ≥3 banners exist | PUT a list containing only some of them | **PRD doesn't specify** whether omitted banners keep their old order, get appended at the end, or the call is rejected outright — flag with PM | P2 — open item |
| BN-41 | Reorder list includes an id that doesn't exist | — | PUT a list with one bogus id | 400/404 | P1 |
| BN-42 | Reorder called by an "Other admin role" | — | PUT as a view-only admin | 403 — reorder is an edit-class action | P1 |
| BN-43 | Reorder without admin auth | — | PUT with no token | 401 | P0 |

## 5. Duplicate

No endpoint for this appears in the PRD's "Data model and API" table at all, even though "Duplicate
a banner to reuse it for a new campaign" is listed as an Admin action — **this looks like a spec gap
in the PRD itself** (an admin action with no corresponding API row). Flag with PM/eng before
implementation; the rows below describe the expected behavior from the action's one-line
description only.

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-44 | Duplicate an existing banner | Banner exists | Trigger duplicate (endpoint TBD) | A new banner is created as **Draft**, `isActive: false`, with the same content fields (title, images, link, platforms, languages, care types) | P1 |
| BN-45 | Duplicated banner's dates, order and stats | Same as BN-44 | Inspect the new banner | **Open items, not specified by the PRD**: does the duplicate copy `startAt`/`endAt` verbatim (likely needs resetting since the original's dates may already be in the past) or clear them; does it get appended at the end of `sortOrder` or inherit the original's position; views/clicks must start at 0 regardless | P2 — open item |

## 6. Delete (Soft Delete)

`DELETE /admin/banners/:id`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-46 | Delete a banner with confirmation | Banner exists | DELETE (admin-panel confirm prompt is UI-level) | Soft-deleted (`deletedAt` set); no longer appears in the admin list or on the home page | P0 |
| BN-47 | Deleted banner remains in the audit log | Same as BN-46 | Inspect audit log | Entry recorded with admin id, action = delete, and timestamp — PRD: "deleted banners stay in the audit log" | P1 |
| BN-48 | Delete a currently Live banner | Banner is Live | DELETE it | Removed from the home page — PRD doesn't state a separate latency for delete vs. the 60-second figure given for the active toggle; reasonable to expect at least as fast, confirm with eng | P1 — open item |
| BN-49 | Delete called by an "Other admin role" | — | DELETE as a view-only admin | 403 | P0 |
| BN-50 | Delete a non-existent or already-deleted banner id | — | DELETE it again | 404 — idempotency on double-delete not specified by the PRD | P2 |
| BN-51 | Delete without admin auth | — | DELETE with no token | 401 | P0 |

## 7. List & Filter

`GET /admin/banners?status=` (list with filter and pagination)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-52 | List with no filter | ≥1 non-deleted banner exists | GET `/admin/banners` | 200; paginated list, soft-deleted banners excluded by default | P0 |
| BN-53 | Filter by each status value | Banners exist in each of Draft/Scheduled/Live/Expired/Inactive | GET `?status=Live` (etc.) | Only matching banners returned — since status is **computed from dates + `isActive`, not stored**, filtering must derive status server-side at query time for every row | P0 |
| BN-54 | List response includes performance figures | Banner has logged events | GET list | Response includes views/clicks per banner, per PRD's admin-list requirement | P1 |
| BN-55 | List/filter as a view-only "Other admin role" | — | GET as that role | 200 — View is permitted for every admin role per the Permissions table | P1 |
| BN-56 | List without admin auth | — | GET with no token | 401 | P0 |

## 8. Image Upload

`POST /admin/banners/upload`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-57 | Upload a valid image | Logged in as Super admin/Content manager | POST valid JPG/PNG/WebP within size limits | 200; returns a CDN URL | P0 |
| BN-58 | Upload an invalid format | — | POST a GIF/BMP/etc. | Blocked, clear error naming the required formats | P0 |
| BN-59 | Upload an oversized file | — | POST >1 MB (mobile slot) or >2 MB (desktop slot) | Blocked, clear error naming the max size | P0 |
| BN-60 | Upload as an "Other admin role" | — | POST as a view-only admin | 403 | P1 |
| BN-61 | Upload without admin auth | — | POST with no token | 401 | P0 |

## 9. Stats

`GET /admin/banners/:id/stats`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-62 | Get stats for a banner with logged events | Banner has view/click events over several days | GET `.../stats` | 200; views, clicks and CTR broken down by day, with 7-day and 30-day rollups per the admin list requirement | P0 |
| BN-63 | Get stats for a banner with zero events | Fresh banner, never displayed | GET `.../stats` | 200; zeros, not an error | P1 |
| BN-64 | Get stats for a non-existent banner id | — | GET bogus id | 404 | P1 |
| BN-65 | Stats accessible to a view-only "Other admin role" | — | GET as that role | 200 — View permission covers this, unlike create/edit/publish | P2 |

## 10. Public API — Home Page Banners (Display)

`GET /home/banners?platform=&lang=&care=`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-66 | Request returns only currently-qualifying banners | Mix of Draft/Scheduled/Live/Expired/Inactive banners exist | GET with a given `platform`/`lang`/`care` | Only banners where now is between `startAt`/`endAt`, `isActive: true`, and platform/language/care-type match are returned | P0 |
| BN-67 | More than 5 banners qualify | 6+ qualifying banners exist | GET | Only the top 5 by `sortOrder` returned — PRD edge case: "More than 5 banners live: show the top 5 by order; warn the admin in the panel" (the admin-facing warning is a separate admin-panel concern, not part of this response) | P0 |
| BN-68 | Zero banners qualify | No qualifying banners | GET | Empty list — client is expected to hide the banner area entirely with no gap; verify the API itself returns an empty array rather than an error | P0 |
| BN-69 | `platform` filter correctness | Banners targeted at different platform subsets | GET `platform=ios` (and `android`, `web`) | Only banners whose `platforms` includes the requested platform are returned | P0 |
| BN-70 | `lang` filter — banner with no languages set | Banner created with `languages` empty ("all languages") | GET any `lang` value | That banner is included regardless of the requested language | P1 |
| BN-71 | `care` filter (Child care vs. Elder care tab) | Banners targeted at one or both care types | GET `care=child` / `care=elder` | Only banners whose `careTypes` includes the requested type are returned; a banner targeting both appears under either tab | P0 |
| BN-72 | Overlapping schedules | Two banners both qualify for the same window | GET | Both returned; PRD: "Overlapping schedules are allowed; order decides display" — this is not an error, `sortOrder` alone governs display sequence | P1 |
| BN-73 | Response caching | — | Two GETs within 60 seconds after a banner's state changes | Response may be up to 60 seconds stale (CDN-cached) — consistent with the "takes effect within 60 seconds" figure used elsewhere in the PRD | P1 |
| BN-74 | Request with no auth required | — | GET with no auth header | 200 — this is a public, unauthenticated endpoint | P1 |

## 11. Public API — Events (View/Click Tracking)

`POST /home/banners/:id/events`

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-75 | Log a view event | Banner is live and visible | POST `{"eventType": "view", "platform": "..."}` | 200/201; `banner_events` row created (`event_type: view`) | P0 |
| BN-76 | Log a click event | — | POST `{"eventType": "click", ...}` | 200/201; row created (`event_type: click`) — per PRD, logged "on every tap", unlike the view's dedup rule below | P0 |
| BN-77 | View dedup — once per session per banner | A view for this banner was already logged this session | POST another `view` event for the same banner in the same session | **Open item**: PRD states the client fires a view "once per session per banner" (50% visible for 1 second), but doesn't say whether the server also enforces this or purely trusts the client; confirm whether a duplicate view call is accepted, rejected, or deduped server-side | P2 — open item |
| BN-78 | Log an event for a non-existent or deleted banner id | — | POST to a bogus/deleted id | **Not specified by the PRD** — flag whether this 404s or is silently accepted/ignored | P2 — open item |
| BN-79 | Log an event without authentication | Anonymous (not logged in) end user | POST with no auth token | 200/201 — `user_id` is explicitly nullable on `banner_events`, so unauthenticated events must be accepted | P1 |

## 12. Non-Functional: Performance & Reliability

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-80 | Public banner API latency | — | Measure `GET /home/banners` under load | p95 under 200 ms | P1 |
| BN-81 | Banner API failure or slow response | Simulate the banner service erroring or taking >2s | Load the home page | Home page loads normally without the banner area; failure logged server-side, nothing shown to the end user | P0 — PRD: "Banners must never slow down or break the home page" is called out as a hard requirement |
| BN-82 | Images served compressed and lazy-loaded | — | Inspect network requests on home page load | Images are WebP via CDN; only the first banner loads eagerly, the rest lazy-load | P2 |
| BN-83 | Banner area reserves height before load | — | Load home page on a slow connection | Banner area occupies its final 2:1 (mobile) / 4:1 (desktop) height immediately via a placeholder, avoiding layout shift | P1 |

## 13. Accessibility

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-84 | Image alt text | Banner has a `title` | Inspect rendered markup | Banner image's alt text equals the banner's `title` | P2 |
| BN-85 | Carousel controls are keyboard/screen-reader accessible | Multiple live banners | Navigate the carousel via keyboard / screen reader only | Arrows/dots (desktop) and swipe equivalent (mobile) are all reachable and operable without a mouse/touch | P2 |

## 14. Carousel / Home Page Display Behavior

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-86 | Exactly one live banner | 1 qualifying banner | Load home page | Static display — no dots, no auto-advance | P1 |
| BN-87 | 2–5 live banners | 2–5 qualifying banners | Load home page | Carousel auto-advances every 5 seconds; swipe + dots on mobile, arrows + dots on desktop; pauses on touch (mobile) / hover (desktop) | P0 |
| BN-88 | Switching Child care / Elder care tab | Different banners target each care type | Switch tabs on the home page | Carousel swaps to that tab's qualifying banner set | P0 |
| BN-89 | Tapping a banner | Banner has a non-None link type | Tap/click the banner | External URL opens in a browser/new tab; Internal URL / In-app screen navigates within the app to the target | P0 |
| BN-90 | Tapping a banner linking to an in-app screen unavailable on an older app version | Banner's `linkType: "screen"` targets a screen that doesn't exist on the caller's app build | Tap the banner on an outdated app version | Opens the home page instead of crashing or no-op'ing — PRD edge case | P1 |
| BN-91 | A banner's image fails to load on-device | Image URL unreachable/broken for one banner in a multi-banner carousel | Load home page | That banner is skipped in the carousel; the rest still display | P1 |
| BN-92 | Carousel position on the page | — | Load home page, both care-type tabs | Banner carousel sits between the Child care/Elder care switch and "Who are you booking for?" on both mobile app and web desktop | P2 |

## Permissions Matrix (Cross-Cutting)

| ID | Title | Preconditions | Steps | Expected Result | Priority |
|----|-------|----------------|-------|------------------|----------|
| BN-93 | Super admin has full access | — | Exercise every admin endpoint above as Super admin | All allowed (View, Create/edit, Publish/delete) | P0 |
| BN-94 | Content manager has full access | — | Exercise every admin endpoint above as Content manager | All allowed — identical to Super admin per the Permissions table | P0 |
| BN-95 | Other admin roles are view-only | — | Exercise every admin endpoint above as another admin role | View-type endpoints (list, get, stats) succeed; every Create/edit/Publish/delete endpoint 403s | P0 |
| BN-96 | Every mutating action is audit-logged | — | Create, edit, publish, unpublish and delete a banner as an authorized admin | Each action produces an audit log entry recording admin id and timestamp | P1 |

## Open Questions (from the PRD itself)

The PRD's own "Release plan, acceptance criteria and open questions" section lists these as
unresolved — they block or soften several rows above and should go back to PM before/during build:

- Exact image sizes: do 1080×540 (mobile) and 1920×480 (desktop) match the current home page design? (affects BN-09, BN-11)
- Is 5 the right maximum carousel size, and 5 seconds the right rotation time? (affects BN-67, BN-87)
- Which in-app screens can a banner link to? Need the final list of screen keys. (affects BN-14, BN-90)
- Is targeting by user group (e.g. child age band, city, paid vs. free) needed in v1, beyond the basic platform/language/care-type targeting already specified?
- Does any banner need approval by a second admin before going live? (would add a new state/workflow not covered anywhere above)
- Which languages must the app support for banner text?

## Open Items / Spec Gaps Noticed While Writing These Cases

- **No duplicate endpoint in the API table** (§5 above) despite being a listed admin action — a
  gap in the PRD itself, not just an open question.
- **PATCH status vs. PUT edit overlap** (BN-31) — unclear whether `isActive` is settable through
  both endpoints or only the dedicated status one.
- **Reorder with a partial id list** (BN-40) and **duplicate's copied fields** (BN-45) — the PRD
  doesn't say what happens to banners/fields it doesn't explicitly mention.
- **Server-side view dedup** (BN-77) and **events against a missing banner id** (BN-78) — the PRD
  describes client-side behavior (visibility threshold, once per session) without saying whether
  the server enforces or merely trusts it.
- **Delete vs. unpublish latency** (BN-48) — only the active-toggle path has an explicit "60
  seconds" figure; delete's isn't stated.
- This entire feature is unbuilt: there is no migration, controller, service, or admin-panel screen
  to point these cases at yet. Every row above is "to be validated once built," not "currently
  passing" or "currently failing."
