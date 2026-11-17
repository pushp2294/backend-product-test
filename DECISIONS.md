# Decisions and assumptions

> Work in progress: entries are added as decisions are made during the build and
> organised into the brief's four sections (assumptions, cuts, growth, next day)
> at the end. The agreed spec is in [docs/PLAN.md](docs/PLAN.md).

## npm is the supported run path; no Docker
The supported path is `npm install && npm run dev`. There is no Dockerfile or compose
file in the repo, deliberately:
- The brief only suggests `docker compose up` for web clients. This is a mobile client,
  and two npm commands already meet the one-or-two-commands bar faster than an image pull.
- Docker was not available on the machine this was built on, so a compose file could not
  be run. I would not ship run instructions I had not executed: an unverified compose
  file is a claim, not a feature.
- AWS compatibility is unaffected: the backend is a plain Node process configured by
  environment variables, which containerises without code changes when needed.

## Two live email adapters, not one plus a stub
`EmailSender` has two working implementations, chosen in the composition root by
`EMAIL_TRANSPORT`:
- `FileEmailSender` (default) writes each enquiry email to `backend/data/enquiries/*.eml`
  and logs a line. No dependencies, works everywhere.
- `SmtpEmailSender` uses nodemailer and works with any SMTP server (Mailpit, for example,
  if you run one yourself). SES speaks SMTP, so production is a config change.

Two live adapters behind one port is real evidence the seam works; a commented-out
class would only be a claim.

## Express 5: no try/catch in routes
Express 5 forwards errors thrown in async route handlers to the error middleware, so
the routes carry no try/catch. All error-to-response mapping lives in one place,
`http/errorHandler.ts`.

## Node's `--env-file` instead of dotenv
Node's built-in `--env-file` flag loads `.env`, replacing the `dotenv` package: one
dependency fewer.

## Exact dependency versions
Every version in `backend/package.json` is pinned exactly (no `^`/`~`), and `.npmrc`
sets `save-exact=true`, so the reviewer installs what was tested.

## Adapters import domain errors; the domain never wraps them
Adapters importing `domain/errors` is correct because dependencies point inward:
adapters know the domain, the domain never knows the adapters. The alternative was
for the domain to wrap every rejection from a port. We rejected it because a plain
bug in mapping code would then be reported to the client as an upstream failure,
which is a lie.

## "Not found" is `null` at the port, `NotFoundError` in the domain
This keeps the Fake Store quirk (a missing product returns HTTP 200 with an empty
body) sealed inside the adapter, where it belongs. A Postgres adapter returns `null`
for a missing row and nothing above it changes.

## Logger: info / warn / error, final
I set a one-method minimalism rule on Logger and the design argued with it twice.
First, `FileEmailSender` needed to log a normal event (`info()`). Then the error
handler needed to log real crashes (`error()`). Logging a 500 at warn means an
operator filtering for errors misses real crashes. The right response was to fix the
rule, not to keep working around it with console calls. `Logger` is now `info()`,
`warn()`, `error()`, and that is final: no debug or trace. Every log goes through
the port.

## Fake Store adapter
- **5s timeout** on every upstream call (`AbortSignal.timeout`). A hung upstream
  becomes `UpstreamError` → 502; it cannot hang the server.
- **Strict validation of required fields, whole response; tolerant of additions.**
  Fake Store responses are checked with Zod. A missing or malformed required field
  anywhere in a list fails the whole call as `UpstreamError` (502). Fake Store
  *adding* a field is ignored (Zod objects drop unknown keys), so an upstream
  addition cannot take the catalogue down; a test pins this.
  Rejected alternative: skip the bad item and log a warning. A half-catalogue served
  silently is worse than a loud failure at this stage. In production I'd revisit it
  with per-item skipping plus alerting.
- **Impossible ids never reach the network: a security decision, not just an
  optimisation.** Fake Store ids are positive integers, so `getProduct('abc')` or
  `getProduct('../users')` returns `null` without a call. This keeps untrusted path
  input out of the upstream URL. Saving a round trip is a side benefit.
- **Currency is assumed USD.** Fake Store does not state one.
- **"jewelery" is misspelt at the source and shown as "Jewelery".** The brief says
  *"Product metadata is not important yet, so using the Fake Store API is
  acceptable."* The source is a placeholder, so a correction map would be code
  written around data we are about to replace. If the source became permanent, the
  fix belongs in the data, or at worst in the adapter's mapping step; never in the
  domain or the client.

## Layering enforced by a failing build: the architecture test
`test/architecture.test.ts` is the first thing I'd point a reviewer at. It fails the
build if anything outside `adapters/fakestore/` imports the Fake Store schemas, or if
the domain imports adapter or HTTP code. Layering enforced by a convention in a README
decays; layering enforced by a failing build does not.

## Cache decorator limits
`CachingProductSource` (60s TTL by default, `PRODUCT_CACHE_TTL_MS`) is deliberately simple:
- Errors are never cached; the next request retries.
- `null` (not found) is not cached, so requests for made-up ids cannot grow memory.
- Concurrent requests on a cold cache each reach the source (no in-flight
  de-duplication). Acceptable at PoC traffic.
- Cached arrays are shared, so the domain copies before sorting.

## Every test can fail (mutation audit)
Each backend test was checked by applying a one-line change to production code and
confirming that test goes red. The audit found and fixed three problems:
- A test that asserted nothing useful (deleted).
- A test whose live data could not exercise the code it named. No live price has
  float noise, so `Math.round` was untested; replaced with a crafted 1.15 price.
- A gap in the architecture test: a side-effect `import '.../fakeStoreSchemas'`
  slipped past the regex. Fixed and re-verified.

## Catalogue rules
- **Top rated** = rating desc, then rating count desc, then id asc. The id comparison
  is numeric-aware ("9" before "10"), because ids are strings in our domain.
- **Featured:** curated ids (`FEATURED_PRODUCT_IDS`) come first in config order. Unknown
  ids are logged as a warning naming each one, and every unfilled slot is filled from
  the rule, skipping curated products. The AC says "THEN I see those 5 featured
  products", so a mistyped id must not leave the home page with 4.
  The warning is logged **once per unknown id per process** (a `Set` of ids already
  warned). Config cannot change at runtime, so repeating it on every request is log
  spam that would bury real signals in production. The production answer is
  neither extreme: structured logging with alerting on this event.
- **Category page:** top 2 by the rule, then the rest of the category by the same
  rule, with no overlap. Category pages ignore the curated list.
- **Search: word-start prefix matching**, case-insensitive. Every whitespace-separated
  term must match the start of some word in the title or description (`\b` + term).
  "back" finds "backpack"; "men" finds "Men's" but not "Women's".
  Rejected: plain substring ("men" matched "women") and whole-word ("back" no longer
  found "backpack").
  **User input never reaches the regex engine unescaped.** Each term is escaped first,
  so `.*` matches nothing (unescaped it would match every product) and `(` matches only
  the literal character, and never throws an invalid-regex error.
  Search covers title and description only, not category: "men" does not return the
  backpack in men's clothing, because its text never mentions men.
- **Ranking never sorts in place.** Product arrays may be the cache's shared copy.

## Config validation
- Parsed once at startup with Zod; any problem stops the server with every issue listed.
- `KEY=` (empty) in `.env` means "not set", so defaults apply. Otherwise `""` would
  coerce to `0` and silently turn the cache off.
- `FEATURED_PRODUCT_IDS` format check: up to 5 comma-separated ids, no duplicates,
  only letters/digits/`-`/`_`. Deliberately **not** "must be numeric". A numeric check
  would encode Fake Store's id format in our config, and a Postgres UUID would then
  fail validation at startup. The config must not know what the data source is.
  Whether an id *exists* is checked per request, because checking at startup would
  mean calling the source before the server is listening.

## HTTP layer
- **One error shape, one place.** `http/errorHandler.ts` maps every error to
  `{ error: { code, message, details? } }`. Client-facing messages never include
  internals: no upstream name, no stack, no `cause`. Those go to the log only. A test
  asserts a 502 body never mentions Fake Store or the timeout.
- **`PAYLOAD_TOO_LARGE` (413) added to the error codes.** JSON bodies are capped at
  10kb (an enquiry is tiny). Returning 400 for an oversized body would misstate the
  problem.
- **`/health` never touches the product source**, so it stays instant and still
  answers during an upstream outage. It reports liveness, not upstream health. A
  health check that depends on a third party means the container gets restarted when
  Fake Store blips.
- **Unexpected errors (500) are logged with `error()`**; upstream and email failures with `warn()`.
- Security basics: `X-Powered-By` disabled; repeated query params (`?q=a&q=b`) are
  rejected rather than coerced; path ids capped at 100 characters.

## Enquiries
- **Server-side product lookup.** The enquiry body carries only `productId` and contact
  details. Title and price come from our product source. Any product fields the client
  sends are dropped by the schema (Zod objects strip unknown keys) and the domain never
  reads them. Unknown `productId` → 404 and no email is sent.
- **200, not 201, and only after the provider accepts.** `POST /enquiries` returns
  `200 { "status": "sent" }` only once the `EmailSender` resolves. 201 promises a
  created, addressable resource and we store nothing, so there is no Location header
  to give. If enquiries ever move to a queue, 202 becomes correct. The Android client
  treats any 2xx as success, so that change would not break it. Any failure is 502 `EMAIL_FAILED`, so the client
  cannot show "Thank you" for an email that was never sent.
- **Header injection is handled explicitly, in layers:**
  1. HTTP: `name` must be a single line (CR/LF → 400). `email` must be a valid address,
     and the email format allows no whitespace at all.
  2. Domain: `toHeaderValue` strips CR/LF from everything that becomes a header
     (subject, reply-to), so the domain is safe whoever calls it.
  3. Adapters: `FileEmailSender` writes headers by hand and strips CR/LF itself;
     `SmtpEmailSender` goes through nodemailer's header encoding, which does the same.
     A test fails if the adapter ever bypasses it.
  The message field allows line breaks because it only goes in the body.
- **No path traversal in .eml files.** Filenames are generated: an ISO timestamp plus a
  random UUID. The `EmailSender.send` signature takes no filename, so user input
  cannot influence the path. Files are created with the `wx` flag (never overwrite).
- **Plain text only.** No HTML part, so customer input cannot inject markup.
- **SMTP timeouts.** Nodemailer waits up to 2 minutes by default; we set 5s connect /
  10s socket so an unreachable mail server fails the request quickly (verified live:
  502 in ~0.6s against a closed port).
- **Both adapters are tested for real.** `FileEmailSender` writes to a temp directory.
  `SmtpEmailSender` is driven through its production factory over TCP to a ~50-line fake
  SMTP server built on Node's `net` module (no new dependency). That server records the
  envelope recipients, so the injection test proves no extra recipient was added, not
  just no extra header line. Not tested: delivery through a real third-party SMTP server.
- **Defence in depth vs. testability.** The mutation audit found that layered checks
  can make a test impossible to fail with one change. A redundant CR/LF regex on
  `email` (on top of `z.email()`) was removed for that reason. Where both layers are
  natural (schema stripping, plus a domain that never reads client product fields),
  the test asserts each layer separately.

## Android client
- **Material You dynamic colour is off.** Dynamic colour recolours the app from the
  user's wallpaper, which would bypass `Tokens.kt` entirely. The brief says the look and
  feel is expected to change, and that change has to go through one file. Dynamic
  colour would make that seam real on paper and false in practice.
- **`RepoResult` over Kotlin's `Result`.** `Result` carries only a `Throwable`, and the
  UI needs to distinguish a validation failure from an outage from a missing product.
- **Error strings in a Kotlin mapper, not `strings.xml`.** Keeps ViewModel tests free of
  an Android `Context`. Cost: no translation. The production answer is string
  resources, with the mapper returning resource IDs instead of strings.
- **Toolchain (found by building, not assumed):** AGP 9.4 compiles Kotlin itself
  ("built-in Kotlin"). The KSP release matching the template's Kotlin 2.2.10 predates
  that and fails; current KSP (2.3.14, versioned independently of Kotlin) works with
  Hilt 2.60.1. The current libraries (kotlinx-serialization 1.13, etc.) are compiled
  with Kotlin 2.4, which a 2.2 compiler cannot read, so the project moved to Kotlin
  2.4.10 (the stdlib version those libraries already resolve to) rather than pinning
  every library to an older release. Hilt was working within ~2 minutes.
- **The client timeout is a user-experience constraint, not an arithmetic one.** When
  the server's worst case exceeded what a user will tolerate, the right move was to
  make the server faster, not the client more patient. A first pass set the client to
  30s to sit above a backend worst case of ~25s; that was rejected (nobody waits 30s on
  a phone; they kill the app). Instead the server was bounded:
  - The category page's two upstream calls now run in parallel (`Promise.all`): ~10s
    worst case down to ~5s. A test fails if they are made sequential again. No other
    endpoint had two independent calls (the enquiry's lookup must finish before the
    email can be built).
  - SMTP connect, greeting and socket timeouts are 5s each. `EMAIL_TRANSPORT` defaults
    to `file`, which is instant, so an optional local-only SMTP path should never have
    been setting the timeout for every screen in the app.
  - The client read timeout is 15s, a margin over the bounded worst case (~5s for
    every catalogue call, ~10s for an enquiry over SMTP).
  Residual risk, stated honestly: nodemailer's timeouts apply per stage, so an SMTP
  server that is slow but never quite times out at every stage could still exceed 15s.
  The client would then show "Something went wrong." although the email may still be
  sent. That only affects the optional SMTP path. The fix, if needed, is one overall
  deadline on the send.
- **Error mapping is forgiving about bodies, strict about bugs.** Any unknown error code
  (PAYLOAD_TOO_LARGE today, new ones later) and any missing, empty, non-JSON or
  wrongly shaped error body becomes the generic "Something went wrong." with retry.
  An exception that is none of HTTP/IO/serialization is a bug in the app and is
  rethrown, not hidden. Coroutine cancellation is rethrown explicitly, and also by
  that rule: two layers, verified by mutation (the test fails only when both go).
- **Contract fixtures.** Client tests decode real responses captured from the running
  backend with the app's real DTOs, so client/server field drift fails a test.
- **Architecture test on the client too.** Kotlin has no package-private visibility,
  so `internal` alone cannot stop a screen importing a DTO. A unit test fails the build
  if anything outside `data/` and `di/` imports the DTOs/Retrofit API, if `ui/` imports
  the data layer, if `domain/` imports Android or JSON, or if any screen code contains a
  literal `Color(0x…)` or raw `NN.dp`.
- **Physical phone over USB: `adb reverse` plus a configurable base URL.** `10.0.2.2` only
  exists inside the emulator. For a real device, `adb reverse tcp:3000 tcp:3000` makes the
  phone's `127.0.0.1:3000` reach the Mac, and the base URL is a Gradle property
  (`-PapiBaseUrl=http://127.0.0.1:3000/api/v1/`, default `10.0.2.2`). Cleartext is
  permitted for exactly those two addresses, both of which can only reach the developer's
  own machine; it stays off for everything else.
- **The image-URL tension, observed for real.** On the first on-device run the phone had
  no internet connection: product data still arrived (through our backend over USB) but
  every image showed the error drawable, because images load directly from the Fake Store
  host. That is the cost of the strict reading we chose not to implement. The
  `/images/:id` proxy would have made images work wherever the backend works.
- **Every Material colour role is set from the palette.** The first on-device run showed
  lavender product cards. `Card` uses `surfaceContainerHighest`, one of the 36 (of 48)
  roles the theme had not set, so Material filled it with its default purple, which
  bypassed `Tokens.kt`. Both schemes are now built with the full `ColorScheme`
  constructor (no defaults, so omitting a role does not compile), and `ThemeTokensTest`
  fails if any role is not a `Palette` colour. It uses reflection, so roles added by a
  future Material version are checked too.

## Three data sources behind one port
`PRODUCT_SOURCE` picks the data source: `fakestore` (default, the live API) or `snapshot`
(a dated local copy in `backend/snapshot/`, served by `SnapshotProductSource`). This is not
just a fallback. It is the clearest demonstration that the data source is swappable: three
implementations behind one port (live, snapshot, and the caching decorator that wraps
either), chosen by config, with nothing above the port changing. The snapshot reuses the
live adapter's validation and mapping (`fakeStoreMapping.ts`); a test proves both produce
identical domain data. The snapshot files are the same ones the tests use: one copy.

## Stale-on-error cache, bounded
When the source fails with `UpstreamError` and a cached value exists, `CachingProductSource`
serves the stale value and logs a warning, but only if it is under 10 minutes old. Beyond
that the error goes through: very old data is worse than an honest error. Only source
failures are masked; any other error is a bug and stays loud. Limit: it cannot help a
cold cache, and product detail (`getProduct`) is cached per id, so a product never opened
before has nothing stale to serve. `PRODUCT_SOURCE=snapshot` covers those cases.
- **Search screen state.** The rule is that every screen renders loading, error and
  success, not that there be one flow object per screen. A search screen holds two
  things with different lifecycles: the text being edited, and the result of the last
  submitted search. So its state is `SearchUiState(query, results)`, where `results` is
  the usual `UiState` (null before the first search). Whether the Search button is
  enabled (`canSearch`) is derived there, not in the Composable, so it is unit-tested:
  disabled for empty or whitespace input (the server would return 400, which the user
  should never see) and while a search is running (no double submission). Retry re-runs
  the query that failed, even if the field has since been edited.
- **Category page renders the server's split as-is.** "Top rated in X" on a tinted band,
  then "More in X" on the plain background. The ViewModel passes `topRated` and `others`
  through untouched; a test with deliberately unsorted data fails if they are re-sorted.
- **Enquiry form.** Same shape as search: the form being edited and the result of the
  last submission have different lifecycles, so the state is
  `EnquiryUiState(form, fieldErrors, submission)`.
  - `productId` comes from the navigation route; the customer never types it. The route
    also carries the product title, for display only ("Enquiry about …"). The server
    looks the product up itself.
  - Client validation mirrors the server's rules and wording, so obvious mistakes are
    shown instantly and never cost a request. The server stays authoritative: its
    `VALIDATION_ERROR` details are mapped back onto the matching fields; a detail for a
    field the customer cannot edit (e.g. `productId`) shows only the summary message.
  - Submit is disabled while sending, so a second tap cannot send a second email.
  - On any 2xx, the thank-you state replaces the form, and Back returns to the product.
  - On `EMAIL_FAILED`, or any other failure, the customer stays on the form with
    everything they typed, and the error shows above it. The thank-you state must never
    appear for an email that was not sent: that would be a lie to the customer. A test
    asserts this, and was checked by mutation (treating EMAIL_FAILED as success fails it).
