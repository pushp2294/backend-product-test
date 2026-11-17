# Plan — Product Catalogue PoC

Agreed plan, written before any code. Decisions recorded here are carried into
`DECISIONS.md` at the end of the build; this file is the spec the build follows.

- Client: Android (Kotlin, Jetpack Compose)
- Backend: Node 20 + TypeScript (Express, Zod, Vitest)
- Product data: Fake Store API, reached **only** by the backend
- Time-box: 3 hours (180 min), actual time reported honestly

---

## 1. Design in one paragraph

The backend has a small domain core (catalogue rules, enquiry handling) that depends
only on two ports: `ProductSource` and `EmailSender`. Concrete adapters (Fake Store,
SMTP) implement those ports and are chosen in exactly one place, the composition
root `app.ts`. A caching decorator also implements `ProductSource` and wraps the Fake
Store adapter, which is the clearest demonstration of why the port exists. HTTP
routes are thin: validate with Zod, call the domain, map domain types to our response
types. Fake Store's response shapes are parsed and mapped inside the adapter folder
and never leave it. The Android client talks only to our API, maps our DTOs to its
own models in a repository, and keeps all visual decisions in a design-token file
behind a Compose theme.

The three things expected to change, and the single seam each sits behind:

| Expected to change | Seam | Current implementation |
|---|---|---|
| Product data source | `ProductSource` port (backend) | `FakeStoreProductSource`, wrapped by `CachingProductSource` |
| Email provider | `EmailSender` port (backend) | `FileEmailSender` (default, writes `.eml`) and `SmtpEmailSender` (Mailpit locally; SES speaks SMTP), chosen by `EMAIL_TRANSPORT` |
| Look and feel | Compose theme + `Tokens.kt` (client) | Placeholder tokens until creative work lands |

---

## 2. Repo layout

```
/
├── README.md  DECISIONS.md  AI_USAGE.md  CLAUDE.md
├── docs/PLAN.md                  # this file
├── .env.example
├── backend/
│   ├── package.json  tsconfig.json  vitest.config.ts
│   ├── src/
│   │   ├── server.ts             # entrypoint: load config, build app, listen
│   │   ├── app.ts                # composition root: the ONLY place adapters are chosen and wired
│   │   ├── config.ts             # env parsed + validated with Zod; fails fast on startup
│   │   ├── domain/
│   │   │   ├── types.ts          # Product, Category, Money, Rating, Enquiry (our shapes)
│   │   │   ├── catalogue.ts      # featured, byCategory, search, getProduct (pure rules)
│   │   │   ├── enquiries.ts      # submitEnquiry: look up product, build email, send
│   │   │   └── errors.ts         # NotFoundError, UpstreamError, EmailError
│   │   ├── ports/
│   │   │   ├── ProductSource.ts  # listProducts(), getProduct(id), listCategories()
│   │   │   ├── EmailSender.ts    # send({ to, from, replyTo, subject, text })
│   │   │   └── Logger.ts         # info() + warn() only; every log goes through it. Injected so tests can assert
│   │   ├── adapters/
│   │   │   ├── fakestore/
│   │   │   │   ├── FakeStoreProductSource.ts   # implements ProductSource
│   │   │   │   └── fakeStoreSchemas.ts         # Zod for THEIR shapes; never imported outside this folder
│   │   │   ├── caching/
│   │   │   │   └── CachingProductSource.ts     # decorator: implements ProductSource, wraps another
│   │   │   └── email/
│   │   │       ├── FileEmailSender.ts          # default: writes backend/data/enquiries/*.eml + logs a line
│   │   │       └── SmtpEmailSender.ts          # EMAIL_TRANSPORT=smtp: nodemailer → Mailpit locally
│   │   └── http/
│   │       ├── routes.ts         # thin: validate → call domain → map to response
│   │       ├── schemas.ts        # Zod request schemas + domain→response mappers
│   │       └── errorHandler.ts   # the single place errors become the error shape
│   └── test/
│       ├── fixtures/fakestore-products.json   # dated snapshot of the live API (2026-11-16)
│       ├── fixtures/README.md                 # capture date, source URL, command used
│       ├── fakes/                # InMemoryProductSource, RecordingEmailSender, RecordingLogger
│       ├── catalogue.test.ts     # domain unit tests
│       ├── fakeStoreAdapter.test.ts
│       ├── cachingProductSource.test.ts
│       └── routes.test.ts        # supertest against the app with fakes injected
└── android/
    └── app/src/
        ├── main/java/<pkg>/
        │   ├── CatalogueApp.kt  MainActivity.kt
        │   ├── data/
        │   │   ├── remote/CatalogueApi.kt  remote/dto/   # Retrofit + DTOs matching OUR API
        │   │   └── ProductRepository.kt                  # interface + impl; DTO → model, errors → Result
        │   ├── model/            # Product, Money, Category (client-side)
        │   ├── di/NetworkModule.kt
        │   ├── ui/
        │   │   ├── theme/        # Tokens.kt (colours, spacing, radii, type) + Theme.kt
        │   │   ├── components/   # ProductTile, ErrorState, LoadingState
        │   │   ├── home/ category/ search/ product/ enquiry/   # Screen + ViewModel per feature
        │   │   └── navigation/AppNavHost.kt
        └── test/java/<pkg>/      # ViewModel tests + FakeProductRepository
```

**If product data moved to Postgres:** add `adapters/postgres/PostgresProductSource.ts`
and change the one line in `app.ts` that constructs the inner adapter (the cache
decorator keeps wrapping it); domain, routes, tests and Android are untouched.
Compose gains a `postgres` service, which is infrastructure, not design.

Two things were fixed during planning specifically to pass that test:
- Product ids are **strings** in our domain (Fake Store's are numbers; Postgres may use UUIDs).
- Category ids are **our own slugs**, not Fake Store's raw strings such as `"men's clothing"`.

Known, deliberate limit: search matching runs in the domain over `listProducts()`.
Fine at 20 products. At real scale `search()` would move into the port so Postgres can
use an index. That is a planned port change, not something the swap requires.

---

## 3. API contract

Base path `/api/v1` (mobile clients cannot be force-updated, so the contract is
versioned from day one). Every response body is an object, never a bare array, so
fields such as pagination can be added without breaking clients. Values below are
illustrative.

### Shared types

```json
// ProductSummary
{
  "id": "12",
  "title": "WD 4TB Gaming Drive Works with Playstation 4",
  "price": { "amountMinor": 11400, "currency": "USD" },
  "imageUrl": "https://fakestoreapi.com/img/61mtL65D4cL._AC_SX679_.jpg",
  "rating": { "average": 4.8, "count": 400 },
  "categoryId": "electronics"
}

// ProductDetail = ProductSummary + { "description": string, "category": Category }
// Category
{ "id": "mens-clothing", "name": "Men's clothing" }
```

Money is integer minor units plus currency (no float rounding). Verified: no Fake
Store price has more than 2 decimal places, so `Math.round(price * 100)` is exact.

### Endpoints

| # | Method + path | Params | 200 body |
|---|---|---|---|
| 1 | `GET /products/featured` | — | `{ "products": [ProductSummary × 5] }` |
| 2a | `GET /categories` | — | `{ "categories": [Category] }` |
| 2b | `GET /categories/:categoryId/products` | path `categoryId` | `{ "category": Category, "topRated": [ProductSummary × 2], "others": [ProductSummary] }` |
| 3 | `GET /search?q=...` | `q` required, 1–100 chars after trim | `{ "query": string, "products": [ProductSummary] }` |
| 4 | `GET /products/:id` | path `id` | `{ "product": ProductDetail }` |
| 5 | `POST /enquiries` | JSON body below | **200** (not 201) `{ "status": "sent" }`. The client treats any 2xx as success |
| — | `GET /health` | — | `{ "status": "ok" }` (compose healthcheck only) |

Enquiry request:
```json
{
  "productId": "12",
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "phone": "+44 7700 900123",
  "message": "Is this in stock?"
}
```

The server looks up the product's title and price itself; it never trusts product
details sent by the client. The "Thank you" copy is client-owned.

### Error shape (one shape, everywhere)

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request is invalid",
    "details": [{ "field": "email", "message": "Must be a valid email address" }]
  }
}
```

| HTTP | code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bad query/body/malformed JSON. `details` present only here |
| 404 | `NOT_FOUND` | Unknown product, category, or route |
| 413 | `PAYLOAD_TOO_LARGE` | JSON body over 10kb (added in step 2) |
| 502 | `UPSTREAM_UNAVAILABLE` | Product source failed or timed out. Message never names Fake Store |
| 502 | `EMAIL_FAILED` | Email provider rejected or unreachable. Client shows an error, not "Thank you" |
| 500 | `INTERNAL_ERROR` | Anything else. Generic message; detail logged server-side only |

**If product data moved to Postgres:** nothing in the contract changes. It is built
from our domain types (string ids, our slugs, opaque image URLs), so the client
cannot tell the source changed.

---

## 4. Agreed decisions

### Top rated / featured
- **Rule:** sort by `rating.rate` desc, tie-break `rating.count` desc, then `id` asc.
  One pure function, used by home and category pages.
- **Curated override (home page only):** optional `FEATURED_PRODUCT_IDS` in config.
  The AC says "I have chosen 5 products"; "chosen" means a merchant curated them.
  The computed rule stands in for a curation feature that does not exist yet; the
  config hook keeps that reading visible.
  - Startup check: comma-separated, 1–5 ids, no duplicates. Bad format → refuse to start.
  - At request time, curated ids come first in config order.
  - Unknown ids (not in the source) → log a warning naming each one, then **fill the
    remaining slots from the rating rule**, skipping ids already curated. The AC
    says "THEN I see those 5 featured products", so a mistyped id must not leave 4.
  - Existence is not checked at startup because that would mean calling the data
    source before the server is listening.
- **Category pages always use the rule**, never the curated list.
- Live data, verified 2026-11-16: computed home page = **12, 11, 3, 18, 5**. The
  tie-break matters with real data (12 and 11 both 4.8; 3 and 18 both 4.7).

### Category page
- `topRated` = top **N = 2** by the rule; `others` = the rest of the category, no
  duplicates, same ordering.
- Verified counts: men's clothing 4, jewelery 4, electronics 6, women's clothing 6.
  With N = 3 two categories would show 1 "additional" product, so N = 2.

### Categories list
- `GET /categories` added. The AC says "I click on a category link"; links cannot
  render without a list. Hardcoding categories in the client would put knowledge of
  the data source in the client and break the seam.
- Display name: the source string, capitalised (`"jewelery"` → `"Jewelery"`).
  Reasoning is the brief's own: *"Product metadata is not important yet, so using the
  Fake Store API is acceptable."* The source is a placeholder, so a correction map
  would be code written around data we are about to replace. DECISIONS.md notes the
  misspelling and that, if the source became permanent, the fix belongs in the
  data (or at worst in the adapter's mapping step), never in the domain or client.

### Search
- Matches **title + description** (the AC says "text matching", not "title matching").
- Case-insensitive; query is trimmed and split on whitespace; **every term** must
  appear in title or description.
- Empty or whitespace-only `q` → 400 `VALIDATION_ERROR`; the client disables the button.
- Results ordered by the top-rated rule (deterministic, one rule everywhere).
- **Not** combined with category filter. Not in the AC; an optional `category` query
  param can be added later without breaking the contract.

### Enquiry
- Fields: `name` (required, 1–100), `email` (required, Zod email, ≤254),
  `phone` (optional, ≤30, digits/spaces/`+`/`-`), `message` (optional, ≤1000),
  `productId` (required; unknown → 404).
- Server validation is authoritative. Client mirrors the rules only for UX.
- Email is **plain text** (no HTML injection). Customer address goes in `replyTo`,
  never `from`. Recipient is `SALES_INBOX_EMAIL`, sender is `EMAIL_FROM`, both from env.
- Sent synchronously; `200 { "status": "sent" }` only after the provider accepts.
  (Would become `202` if we moved to a queue.)
- Sales inbox locally, two live adapters behind `EmailSender`, chosen in `app.ts` by
  `EMAIL_TRANSPORT`:
  - `file` (default): `FileEmailSender` writes each email to `backend/data/enquiries/*.eml`
    and logs a line. No dependencies, no Docker.
  - `smtp`: `SmtpEmailSender` (nodemailer) to Mailpit; reviewer views mail at `localhost:8025`.
  Two working adapters behind one port is evidence the seam works; a commented-out
  class would only be a claim. (Revised 2026-11-17: Docker is not on the happy path.)
  No signup, no internet. Production path: SES via SMTP = config change.
- Abuse: validation + JSON body size limit now. Rate limiting is next-day work.

### Image URLs (recognised tension)
- The client loads `imageUrl` directly, and today that host is fakestoreapi.com.
- Strict reading of the brief/CLAUDE.md: the client must never contact Fake Store.
  Our reading: the brief bans the client fetching product **data** from Fake Store;
  an image asset referenced by a URL that **our** API returns is not that.
- The fix, if the strict reading is required: a `/images/:id` streaming proxy on the
  backend. Cost ~20 minutes, which we chose to spend elsewhere.
- When the source changes, that URL becomes S3/CloudFront and nothing else moves.
- Recorded in DECISIONS.md as a tension, not a silent choice.

### Running locally (revised 2026-11-17)
- Documented happy path: `cp .env.example .env`, then `cd backend && npm install && npm run dev`.
  No Docker required. The brief suggests compose for web clients; we are mobile, and
  npm already meets the one-or-two-commands bar faster than an image pull.
- No Dockerfile or compose file (revised 2026-11-17): Docker was never available to run
  one, and unverified run instructions are not shipped. See DECISIONS.md.
- Dependency versions are pinned exactly (`.npmrc` `save-exact=true`), so the reviewer
  installs what was tested.

### Resilience: caching decorator
- `CachingProductSource implements ProductSource` and wraps any other `ProductSource`.
  Wired in `app.ts`: `new CachingProductSource(new FakeStoreProductSource(...), ttlMs)`.
- TTL **60 seconds** by default, set by one env var (`PRODUCT_CACHE_TTL_MS`, commented in
  `.env.example`). Read at startup, so changing it needs a restart, not a rebuild.
- Caches successful results per method + arguments. **Never caches errors.**
- Clock is injected (`now: () => number`) so tests control time without sleeping.
- Upstream timeout 5s inside the Fake Store adapter → `UpstreamError` → 502.
- Why a decorator rather than logic inside the adapter: Fake Store is slow and
  rate-limited, and the decorator proves the port's value. Caching works for any
  future source with zero changes to that source or the domain.

### Fake Store quirks found by live calls (2026-11-16)
- `id`, `price`, `rating.rate` are JSON numbers. The adapter turns ids into strings
  with `String(raw.id)` at the mapping step.
- `GET /products/999` returns **HTTP 200 with an empty body**, not 404. The adapter
  treats an empty body as "not found" (`null`), which the domain turns into `NOT_FOUND`.
- Category strings include `"jewelery"` (misspelt) and apostrophes (`"men's clothing"`).

### Client
- Emulator only: base URL `http://10.0.2.2:3000` via BuildConfig; cleartext allowed
  for that host only through a network security config.
- Thank-you: the enquiry screen swaps the form for a thank-you state; Back returns
  to the product.
- Currency displayed from the payload (USD today).

---

## 5. Test list (critical path)

### Backend — domain (`catalogue.test.ts`)
- Featured, computed: against the live-shaped fixture, returns exactly
  **`["12", "11", "3", "18", "5"]` in that order**. Pins both the rate sort and the
  count tie-break.
- Featured, curated: `FEATURED_PRODUCT_IDS = 1,2,999` → returns **5** products,
  includes `"1"` and `"2"`, excludes `"999"`, no duplicates, and the logger records a
  warning naming `999`.
- Featured, curated valid ×5 → returns exactly those, in config order.
- Category: `electronics` → `topRated` = `["12","11"]`, `others` = the remaining 4,
  no overlap. A 4-product category → 2 + 2.
- Search: matches title; matches description; case-insensitive; multi-term requires
  all terms; no match → empty list.
- Get product: unknown id → `NotFoundError`.

### Backend — config
- `FEATURED_PRODUCT_IDS` bad format (non-numeric junk, >5 ids, duplicates) → config
  parse fails. Absent → fine.

### Backend — adapter (`fakeStoreAdapter.test.ts`, fetch stubbed)
- Maps the fixture to domain types: `id` is a **string**, price in minor units,
  category slugged, no Fake Store field names leak.
- Upstream returns **200 with an empty body** for a product → adapter returns `null`.
- Upstream 5xx / timeout / schema mismatch → `UpstreamError`.

### Backend — cache decorator (`cachingProductSource.test.ts`)
- Second call within TTL does not hit the inner source.
- Call after TTL expires hits the inner source again (clock advanced, no sleeping).
- Inner source error is not cached; the next call retries.
- Different arguments (e.g. `getProduct("1")` vs `getProduct("2")`) are cached separately.

### Backend — email adapters
- `FileEmailSender` writes one `.eml` into a temp directory with To, From, Reply-To,
  Subject and the body text.
- Config: `EMAIL_TRANSPORT` accepts only `file` | `smtp`, defaults to `file`; `smtp`
  without `SMTP_HOST` fails at startup.
- `SmtpEmailSender` is not unit-tested against a real server; it is verified manually
  against Mailpit on the optional compose path (recorded honestly in DECISIONS.md).

### Backend — routes (`routes.test.ts`, supertest, fakes injected)
- `GET /products/featured` → 200, 5 products, response shape.
- `GET /categories` → 200, list.
- `GET /categories/:id/products` → 200; unknown category → 404 `NOT_FOUND`.
- `GET /search?q=` empty → 400 `VALIDATION_ERROR`; valid → 200 filtered.
- `GET /products/:id` → 200; unknown → 404.
- **`GET /products/:id` where the source returns 200 with an empty body → our API
  returns 404 `NOT_FOUND`** (not a crash, not a 200). Built with the real Fake Store
  adapter over a stubbed fetch, so it exercises the actual quirk end to end.
- `POST /enquiries` valid → 200 and `RecordingEmailSender` received one email to the
  sales inbox, `replyTo` = customer, body contains the product title.
- `POST /enquiries` invalid email → 400 with `details[].field = "email"`; nothing sent.
- `POST /enquiries` unknown product → 404; nothing sent.
- `POST /enquiries` sender throws → 502 `EMAIL_FAILED`.
- Source throws → 502 `UPSTREAM_UNAVAILABLE`, same error shape.
- Unknown route → 404 in the same error shape.

### Android — ViewModels (fake repository)
- Home: loading → content with 5 products; repository error → error state.
- Search: blank query does not call the repository; results → content; empty → empty state.
- Enquiry: success → thank-you state; validation error → field errors shown;
  failure → error state, form retained.

---

## 6. Build order (180 min)

| Step | Work | Min |
|---|---|---|
| 0 | `git init`; move Android template into `/android`; root `.gitignore`; initial commit on `main`; create feature branch; `.env.example`; empty `backend/` scaffold | 10 |
| 1 | Backend: domain types, ports, Fake Store adapter + Zod + fixture, catalogue rules incl. curated featured, **caching decorator**, domain/adapter/cache tests | 40 |
| 2 | HTTP: routes 1–4 + categories, error handler, route tests | 20 |
| 3 | Enquiry: EmailSender, File + SMTP adapters, transport switch in `app.ts`, route, tests | 20 |
| 4 | `npm install && npm run dev` end to end with curl (happy path); compose file as optional path, verified once Docker is available | 10 |
| 5 | Android: Hilt, Retrofit, repository, theme tokens (minimal), Home + Product detail | 25 |
| 6 | Android: Category + Search | 15 |
| 7 | Android: Enquiry form + thank-you state | 15 |
| 8 | ViewModel tests | 10 |
| 9 | README, DECISIONS, AI_USAGE, PR description | 15 |
| | **Total** | **180** |

The cache's extra 10 minutes come out of Android visual polish in steps 5–6, **not**
step 9. Docs are a large part of the assessment and are protected.

Honest risk: Android setup (Hilt, Retrofit, Navigation) often overruns. A realistic
total is ~3.5h; any overrun will be reported, not hidden.

### Cut first if running late (in order)
1. ViewModel tests beyond Home and Enquiry.
2. Multi-term search → simple case-insensitive phrase substring.
3. Client-side form validation (rely on server field errors, which the UI shows anyway).
4. Visual polish beyond the token file (skeletons, empty-state art).
5. The optional compose path (Dockerfile + compose). The npm happy path does not depend on it.

**Never cut:** the ports, the cache decorator, the error shape, the route tests, the docs.

---

## 7. Dependencies (each announced again with what/why when added)

Backend:
- `express`: HTTP server/router.
- `zod`: runtime validation of env, requests, and Fake Store responses.
- `nodemailer`: SMTP client for `SmtpEmailSender`.
- `vitest`: test runner. `supertest`: drives Express routes in tests without a real port.
- `tsx`: runs TypeScript directly in dev (replaces a separate build-then-run step).
- `typescript`, `@types/*`: compile-time only.
- Not added: `axios` (Node 20 has `fetch`), `helmet`/`cors` (not needed for a native client; revisit for web).

Android (beyond the stack in CLAUDE.md):
- `navigation-compose`, `hilt-navigation-compose`: screen navigation + ViewModel injection per destination.
- `kotlinx-serialization` + Retrofit converter: JSON (chosen over Moshi; Kotlin-native, no reflection).
- `kotlinx-coroutines-test`: deterministic ViewModel tests.

Infra:
- `axllent/mailpit` Docker image: local SMTP server + web inbox. Not a code dependency.

---

## 8. Next day (out of scope, recorded for DECISIONS.md)
- `/images/:id` proxy, to meet the strict reading of the image rule.
- Rate limiting on `POST /enquiries`.
- Merchant curation as a real feature (an `isFeatured` flag on the source) replacing the config hook.
- `search()` on the `ProductSource` port for indexed search at scale.
- Pagination on category/search responses (non-breaking thanks to object envelopes).
- Persist enquiries + send via a queue (`202 Accepted`), with retries.
- UI tests; contract tests that check the Android DTOs against the backend schemas.
