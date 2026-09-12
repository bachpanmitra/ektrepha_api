# Ektrepha — Email (Brevo) + Phone Auth (Firebase) Setup (Reference Doc)

What's wired up for transactional email and phone-based signup/reset, how it got there, and the
gotchas hit along the way — for future reference.

Summary of what changed: email now sends for real via Brevo (was a stub). Phone-based OTP no
longer uses MSG91/SMS at all — it was replaced with Firebase Phone Auth, where the **client app**
sends/verifies the OTP directly with Firebase, and this backend only verifies the resulting ID
token. No SMS is sent or paid for by this backend.


## Email — Brevo

- Provider: [Brevo](https://app.brevo.com) (formerly Sendinblue), free plan (300 emails/day, no
  card required).
- Code: `EmailServiceImpl` (`src/main/java/com/ektrepha/auth/impl/`) posts to
  `https://api.brevo.com/v3/smtp/email` using a plain `RestClient` — no SDK dependency.
- Config: `app.email.brevo.*` in `AppProperties.java` / `application-*.yml`, sourced from
  `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME`.

### Domain authentication

`ektrepha.com` (hosted on GoDaddy DNS) is authenticated with Brevo via DKIM + DMARC, so mail sends
as the real domain instead of showing "via 12125811.brevosend.com" in recipients' inboxes.

Records added in GoDaddy (`Domains → DNS Records`):

| Type | Name | Value |
|---|---|---|
| TXT | `@` | `brevo-code:c0eeda1c9295d91adfd7d7c04666337e` |
| CNAME | `brevo1._domainkey` | `b1.ektrepha-com.dkim.brevo.com` |
| CNAME | `brevo2._domainkey` | `b2.ektrepha-com.dkim.brevo.com` |

DMARC was **deliberately left as GoDaddy's existing default** (`_dmarc` TXT,
`rua=mailto:dmarc_rua@onsecureserver.net`) — DMARC is domain-wide, not per-provider, so the
existing record already covers Brevo once DKIM aligns; overwriting it risked breaking whatever
else uses GoDaddy-hosted email on this domain.

Verified sender: `no-reply@ektrepha.com` (added under Brevo → Senders, Domains & Dedicated IPs →
Senders). This is what `BREVO_SENDER_EMAIL` must point to — using an unverified/unauthenticated
sender is exactly what caused the "via ..." issue in the first place.

### Gotchas hit

- **SMTP key vs REST API key are different credentials.** Brevo's "SMTP" tab gives a key starting
  `xsmtpsib-...` (for SMTP relay, port 587) — the REST API this code calls needs a **separate** key
  from the "API Keys & MCP" tab, starting `xkeysib-...`. Using the wrong one gives a
  `401 {"code":"unauthorized"}`, not a helpful "wrong key type" message.
- **IP allowlisting.** Brevo's account has "block unauthorized IPs" on for both API and SMTP keys.
  Every new IP calling the API (including a dev machine's dynamic IP, which changes across
  sessions/days) needs one-time approval at `Settings → Security → Authorized IPs`. If email
  sends suddenly start 401ing with `"unrecognised IP address"`, this is why — go authorize the new
  IP shown in the error.
- **A stray leading space in a DNS TXT value silently fails exact-match verification.** GoDaddy's
  UI doesn't visually show a leading space in a value field, but `dig` will. If Brevo's domain
  verification says a record "doesn't match" and the value *looks* identical, check for this with
  `dig +short TXT <domain>` and compare byte-for-byte.
- **Brevo SMS is not price-competitive for India** — checked and rejected. ~₹6/SMS to India vs
  MSG91's ~₹0.13–0.20/SMS (~30–40x more expensive). This is why phone OTP uses Firebase instead of
  Brevo's own SMS product.

### Config reference (`.env`, gitignored)

```bash
export BREVO_API_KEY=xkeysib-...          # Settings -> SMTP & API -> API Keys tab
export BREVO_SENDER_EMAIL=no-reply@ektrepha.com
export BREVO_SENDER_NAME=ektrepha
```


## Phone auth — Firebase (replaced MSG91)

MSG91 (SMS OTP) was fully removed. Reasoning: no free tier exists for real SMS anywhere (carrier
termination fees are real costs passed through by every provider), and Firebase Phone Auth is
free up to 10,000 verifications/month — but only because the **client app** does the actual
SMS send/verify via Firebase's client SDK; this backend never sends an SMS itself, it just
verifies the resulting token.

- Firebase project: **`ektrepha`** (free Spark plan, $0/month), under the `bachpanmitra.com`
  Google Cloud org.
- Phone sign-in provider: enabled at Firebase Console → Authentication → Sign-in method → Phone.
- Code: `FirebaseTokenVerifierService` / `FirebaseTokenVerifierServiceImpl`
  (`src/main/java/com/ektrepha/auth/security/`, `auth/impl/`) — mirrors the existing
  `GoogleIdTokenVerifierService` pattern. Verifies the ID token via `firebase-admin` SDK, checks
  `firebase.sign_in_provider == "phone"`, extracts the verified `phone_number` claim.
- Config: `app.firebase.service-account-path` → `FIREBASE_SERVICE_ACCOUNT_PATH` env var, pointing
  to a downloaded service account JSON key. **Unlike other integrations, initialization failure
  here is caught and logged as a warning, not a startup crash** — the app boots fine without real
  Firebase credentials; only phone signup/reset actually fail until configured.

### New/changed API endpoints (breaking change from the old MSG91 flow)

| Before (MSG91, removed) | After (Firebase) |
|---|---|
| `POST /signup/phone/initiate` + `POST /signup/phone/verify` (two-step, OTP generated server-side) | `POST /signup/phone` — single call, takes `{firebaseIdToken, password, role}` |
| `POST /password/forgot` accepted `{email}` or `{phoneNumber}` | `POST /password/forgot` — **email only** now, `{email}` |
| `POST /password/reset` accepted `{phoneOrEmail, otp, newPassword}` | `POST /password/reset` — **email only**, `{email, otp, newPassword}`; new `POST /password/reset/phone` — `{firebaseIdToken, newPassword}` for the phone path |
| `POST /login/phone` | unchanged — always was password-only, no OTP involved |

The client app is responsible for the actual phone verification UX (send OTP, let the user enter
it, get a Firebase ID token back) using Firebase's client SDK — this backend was never involved
in that half of the flow and still isn't.

### Getting the service account key (one-time setup)

1. Firebase Console → Project Settings → Service Accounts → **Generate new private key**.
2. If this fails with *"Key creation is not allowed on this service account... organization
   policies"* — the Google Cloud org (`bachpanmitra.com`) has
   `iam.managed.disableServiceAccountKeyCreation` enforced by default (a common security default,
   not specific to this project). Fix, as the Workspace super admin:
   - Grant yourself **Organization Policy Administrator** at the org level (IAM & Admin → IAM →
     switch resource picker to the org → Grant access).
   - Then go to IAM & Admin → Organization Policies → find the constraint scoped to **this
     project** (not the org-wide default) → Manage policy → Override parent's policy → add a rule
     with Enforcement **Off**, scoped to just this project. This avoids weakening the security
     default for every future project in the org.
3. Retry "Generate new private key" — downloads a JSON file.
4. Move it to `secrets/firebase-service-account.json` in this repo (already gitignored via
   `secrets/` in `.gitignore`) and `chmod 600` it.
5. Set `FIREBASE_SERVICE_ACCOUNT_PATH=secrets/firebase-service-account.json` in `.env`.

### Gotchas hit

- **Org policy blocks key downloads by default** — see above. This is a Google Cloud security
  baseline applied automatically to Workspace-backed orgs, not something misconfigured.
- **SMS region policy blocks all regions by default on new projects.** Firebase Console →
  Authentication → Settings → SMS region policy defaults to "Allow" mode with an **empty**
  region list, which means *no* region actually works until you explicitly add one. Real or
  test-number sends fail with `OPERATION_NOT_ALLOWED: SMS unable to be sent until this region
  enabled by the app developer` until India (or whichever regions are needed) is added to the
  allow-list.
- **Spark (free) plan caps new projects at 10 real SMS/day.** Raising this requires adding a
  billing account (can still stay on pay-as-you-go without necessarily costing anything if usage
  stays low, but it does require a card on file). Test phone numbers (below) are unaffected by
  this cap since no real SMS is sent for them.
- **Downloading a browser API key requires registering an "app" in the project**, even though no
  real client app exists yet — Firebase ties the Web API key to a registered Web/iOS/Android app
  entry. A lightweight Web app (`ektrepha-test`) was registered purely to obtain the key for
  testing; the actual client app can reuse this entry or register its own.

### Testing without a real client app or real SMS cost

Firebase supports registering **test phone numbers** (Console → Authentication → Sign-in method →
Phone → "Phone numbers for testing") — e.g. `+91 99999 99999` → fixed code `123456`. These:
- Never send a real SMS (no cost, no daily-quota impact).
- Bypass reCAPTCHA when called via Firebase's REST API directly (real numbers require a genuine
  reCAPTCHA solve, which only a human in a real browser can complete — this is deliberate
  anti-abuse protection and was not and should not be bypassed programmatically).
- Still produce a **real, validly-signed Firebase ID token**, so they're good enough to fully
  verify this backend's token-verification logic end-to-end.

Full test flow via curl (Web API key from Firebase Console → Project Settings → General → your
registered web app):

```bash
API_KEY="<web api key>"

# 1. Request a code (test number bypasses the recaptcha check)
curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:sendVerificationCode?key=$API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+919999999999", "recaptchaToken": "test-bypass"}'
# -> {"sessionInfo": "..."}

# 2. Exchange the fixed test code for a real ID token
curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPhoneNumber?key=$API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"sessionInfo": "<from step 1>", "code": "123456"}'
# -> {"idToken": "...", "refreshToken": "...", "localId": "...", ...}

# 3. Drive the real backend endpoint with the real token
curl -s -X POST "http://localhost:8080/api/v1/auth/signup/phone" \
  -H "Content-Type: application/json" \
  -d '{"firebaseIdToken": "<idToken from step 2>", "password": "TestPass123!", "role": "PARENT"}'
```

This was run end-to-end and confirmed working for both `POST /signup/phone` (201, user created)
and `POST /password/reset/phone` (200, password changed, subsequent login worked).

A standalone HTML test page (Firebase Web SDK, no build step) was also built for testing with a
**real** phone number — this must be run by a human in a real browser (serve via
`python3 -m http.server` since Firebase's SDK needs a real HTTP origin, not `file://`) so any
reCAPTCHA challenge is completed by an actual person, not automation.

### Config reference (`.env`, gitignored)

```bash
export FIREBASE_SERVICE_ACCOUNT_PATH=secrets/firebase-service-account.json
```

`application-dev.yml` defaults this same path if the env var is unset, so a real key placed at
that path just works without any `.env` changes.


## Known limitations / what's still open

- **No real client app exists yet.** The actual mobile/web app needs the Firebase client SDK
  wired up to do the phone-number → OTP → ID token flow. This backend is ready for it but has
  never been exercised by a real client.
- **Real-number SMS delivery has not been confirmed**, only the test-number path (which
  deliberately never sends a real SMS). First real-number test should happen through the actual
  client app, or via the standalone HTML test page above run by a human.
- **10 SMS/day cap** on the Spark plan will need addressing (billing account) before any real
  production traffic.
- `OtpPurpose.SIGNUP` remains in the enum (used to be written for phone-signup and a dead
  `register()` side-effect) but nothing writes it anymore — left in place rather than removed, to
  avoid any migration/compatibility concern over old `otps` rows. Harmless either way.


## When to revisit this setup

- If MSG91/SMS-based OTP is ever wanted again (e.g. as a fallback if Firebase Phone Auth proves
  unreliable for a specific carrier) — `SmsService`/`SmsServiceImpl` were deleted, not deprecated;
  would need to be rebuilt from the git history of this change if resurrected.
- If SMS volume grows enough that the Spark plan's 10/day cap matters — add a billing account to
  the `ektrepha` Firebase project (Blaze plan, pay-as-you-go, does not require it to actually cost
  money if usage stays within free-tier equivalents on Blaze).
- If a second environment (staging/prod) needs its own Firebase project — repeat the org-policy
  and SMS-region-policy setup above for the new project; both are project-scoped, not inherited
  from `ektrepha`.
