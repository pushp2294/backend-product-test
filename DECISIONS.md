# Decisions and assumptions

The brief asks for four things, answered in sections 1–4. Section 5 is a reference: the
reasoning behind individual technical choices, for anyone who wants the detail. The spec
agreed before any code was written is [docs/PLAN.md](docs/PLAN.md).

---

## 1. Assumptions where the acceptance criteria were unclear

**Home page: "I have chosen 5 products to be top rated"**
- "Chosen" reads as a merchant curating them. There is no admin tool, so the choice is a
  config value: `FEATURED_PRODUCT_IDS` (up to 5 ids, display order).
- Without it, "top rated" is computed: rating desc, then number of ratings desc, then id.
  Live data gives 12, 11, 3, 18, 5; the tie-break matters (12 and 11 both rate 4.8).
- A curated id that doesn't exist is logged once as a warning and its slot is filled from
  the rule, skipping products already shown. The criterion says "I see those 5", so a typo
  in config must not leave 4.

**Category page: "top rated products … AND additional products"**
- Top rated = the top 2 in the category by the same rule; additional = the rest, same order,
  no overlap. Why 2: two live categories have only 4 products, so 3 would leave one
  "additional" product.
- Category pages always use the rule; the curated list is for the home page only.
- "Click a category link" needs a list of categories, so there is a `GET /categories`
  endpoint. Hardcoding categories in the app would make it know the data source.

**Search: "products that have text matching that search term"**
- Searches title and description (the criterion says "text", not "title").
- Case-insensitive, word-start prefix matching: every word in the query must match the start
  of a word. "back" finds "backpack"; "men" finds "Men's" but not "Women's".
- Search does not filter by category (not asked for; can be added without breaking anything).
- An empty search is not allowed: the button is disabled, and the server returns 400.
- Results are ordered by the top-rated rule.

**Product page:** image, title, price, category, rating, description and an Enquire button.

**Enquiry: "complete the form with my contact details"**
- Fields: name and email (required), phone and message (optional). The product is attached
  automatically; the customer never types it.
- The server looks up the product's title and price itself; anything product-related the
  app sends is ignored.

**"The sales inbox will receive an email" when the reviewer has no mail account**
- By default the email is written to `backend/data/enquiries/*.eml`, a real email file any
  mail client can open, and the path is logged. No account, no server, no Docker.
- `EMAIL_TRANSPORT=smtp` sends it through any SMTP server instead.

**"The customer will see a Thank you message"**
- Only once the email has actually been accepted. If sending fails, the customer stays on
  the form with what they typed and sees an error, never "Thank you".
- The endpoint returns 200, not 201: nothing is stored, so there is no new resource to point
  to. If enquiries moved to a queue, 202 would be right; the app accepts any 2xx.

**Smaller assumptions**
- Currency is US dollars (Fake Store doesn't say). Prices travel as whole cents plus a
  currency code, and the app formats them using the currency's own decimals.
- "Jewelery" is misspelt at the source and shown as-is: the brief says product metadata
  doesn't matter yet, and the source will be replaced, so a correction map would be code
  written around data we are about to throw away.
- The Android emulator is the target (the brief's requirement). A physical phone over USB
  also works (see README).

---

## 2. Requirements dropped or simplified, and why

| What | Why |
|---|---|
| **No Docker / docker compose** | The brief suggests compose for web clients; this is mobile, and two npm commands meet the "one or two commands" bar. Docker wasn't available on the build machine, and I won't ship run instructions I haven't executed. The backend is a plain Node process configured by environment variables, so containerising it later needs no code changes. |
| **Images load straight from Fake Store** | Strictly, "the client never calls Fake Store" could include images. Our API returns the image URL and the app loads it, the usual pattern for assets. A `/images/:id` proxy on the backend would satisfy the strict reading (about 20 minutes); the time went elsewhere. Seen for real: a phone with no internet showed products (through the backend over USB) but no images. |
| **Enquiries are not stored, and sending is synchronous** | Not in the criteria. One request = one email, answered once it's sent. No queue, no retries. |
| **No rate limiting or captcha on enquiries** | Not in the criteria. Validation and a 10 kB body limit only. |
| **No pagination** | 20 products. Every response is a JSON object, not a bare array, so pagination can be added without breaking clients. |
| **Search runs in memory over the full list, with no relevance ranking** | Fine at 20 products; see "next day". |
| **No UI (instrumentation) tests** | ViewModels are unit-tested with a fake repository, which covers the behaviour. Every flow was checked by hand on a Pixel 9 (Android 17) against the live backend: home, category, search (including no results), product detail, enquiry validation, a sent enquiry arriving as an `.eml`, and an email failure keeping the customer on the form. |
| **Error messages aren't translatable** | They live in a Kotlin mapper so ViewModel tests need no Android Context. The production answer is string resources, with the mapper returning resource ids. |
| **The stale cache can't help a cold start or a product nobody opened yet** | `PRODUCT_SOURCE=snapshot` covers that: it serves a dated local copy of the catalogue. |
| **SMTP sending was not tested against a real mail server** | It is tested end to end over real SMTP against a small fake server written for the tests. |
| **"Back" is a text button, not an arrow icon** | The icon set is a separate dependency; adding it is a one-line change. |

---

## 3. How the design supports growing beyond the PoC

The brief names three things that will change: the product data source, the email provider,
and the look and feel. Each sits behind exactly one seam, and each seam is enforced, not just
described.

**Product data source: the `ProductSource` port (backend)**
- The domain depends only on an interface: list products, get one product, list categories.
  "Not found" is `null`; a failing source throws `UpstreamError`.
- Three implementations already sit behind it, chosen by config: the live Fake Store API,
  a dated local snapshot (`PRODUCT_SOURCE=snapshot`), and a cache that wraps either. Nothing
  above the port changed when the snapshot and cache were added.
- Moving to our own Postgres database means writing one more adapter and adding one case to
  `createProductSource`. The API contract doesn't change, because it's built from our own
  types: product ids are strings (Fake Store's numbers become strings at the boundary;
  Postgres UUIDs would fit), and categories have our own slug ids.
- Fake Store's response shapes are validated and mapped in one file; a test fails the build
  if anything outside that folder imports them.

**Email provider: the `EmailSender` port (backend)**
- Two working implementations, chosen by `EMAIL_TRANSPORT`: write to file, or send over SMTP.
  Two live adapters behind one port prove the seam works; a commented-out class would only
  claim it.
- AWS SES accepts SMTP, so moving to it is a config change. A different provider is one
  adapter.

**Look and feel: design tokens and the Compose theme (client)**
- Every colour, spacing, size, corner radius and text style lives in `Tokens.kt`; `Theme.kt`
  builds the Material theme from it.
- All 48 Material colour roles are set from the palette, using the constructor that has no
  defaults, so leaving one out won't compile. A test fails if any role isn't a palette colour.
- Another test fails if any screen contains a literal colour or a raw size like `16.dp`.
- Material You dynamic colour is off: it repaints the app from the user's wallpaper, which
  would bypass the tokens entirely.

**Also built to grow**
- **API:** versioned under `/api/v1`; one error shape everywhere.
- **Client tolerance:** the app ignores unknown JSON fields and treats unknown error codes as
  a generic, retryable error, so the server can evolve without breaking installed apps.
- **Client layers:** screens depend on a repository interface, never on Retrofit, and a test
  enforces that.
- **Contract checks:** client tests decode real responses captured from the backend, so a
  field-name mismatch fails a test, not the app.
- **Config:** read from environment variables and validated at startup, failing with a clear
  message.

---

## 4. What I would do next with another day

1. **Compose UI tests** for the critical path, automating the flows that were checked by hand
   on a device. Also mark the enquiry fields as contact details for Android autofill: on a real
   phone, Google Password Manager offered to "save a password" after an enquiry, because
   autofill guessed a name + email form was a login.
2. **Image proxy** (`/images/:id`) or a CDN, so images work wherever the backend works.
3. **Enquiries:** store them, send through a queue with retries (and return 202), add rate
   limiting, and put one overall deadline on an SMTP send.
4. **Merchant curation as a real feature** (a "featured" flag on products) instead of a
   config value.
5. **Search inside the data source** (`search()` on the port), so a database can use an
   index and relevance ranking.
6. **Resilience:** serve a product page from the cached product list when the source is down;
   de-duplicate concurrent requests on a cold cache.
7. **Operations:** structured logging with alerts (for example on unknown featured ids or
   stale data being served), a container image, and CI running both test suites.
8. **Translation:** move error messages into string resources.

---

## 5. Reference: technical decisions in detail

### Backend structure
- **Ports and adapters.** The domain (catalogue rules, enquiries) depends on three ports:
  `ProductSource`, `EmailSender`, `Logger`. Adapters implement them and are chosen in one
  place, `app.ts`.
- **Adapters import domain errors; the domain never wraps them.** Dependencies point inward.
  If the domain wrapped every failure, a plain bug in mapping code would be reported to the
  client as an upstream outage, which is a lie.
- **"Not found" is `null` at the port and `NotFoundError` in the domain.** This keeps Fake
  Store's quirk (a missing product returns HTTP 200 with an empty body) sealed inside its
  adapter. A Postgres adapter would return `null` for a missing row, and nothing above it
  changes.
- **Logger has info, warn and error, and that's final.** It started with one method; the
  design needed `info()` (an email was written) and then `error()` (a real crash). The right
  response was to fix the rule, not to work around it with console calls.
- **Express 5:** errors thrown in async route handlers reach the error middleware
  automatically, so routes have no try/catch.
- **Node's built-in `--env-file`** loads `.env`, replacing the dotenv package.
- **Exact dependency versions** (no `^`), so a reviewer installs exactly what was tested.
- **Vitest 4.1.12, not 5.** Vitest 3 carried two critical advisories (in its mocking and
  worker-pool code; development-only, the running server was never affected). Vitest 5
  requires Node 22.12+, and this project supports Node 20, so the fix is the newest 4.x.
  `npm audit` is clean for all dependencies, development tools included.

### Fake Store adapter
- **5-second timeout** on every upstream call; a hung upstream becomes a 502, never a hung
  server.
- **Strict about required fields, tolerant of additions.** A missing or malformed field fails
  the whole call (502); Fake Store adding a field is ignored. Rejected alternative: skip bad
  items with a warning, because a half catalogue served silently is worse than a loud failure
  at this stage. In production I'd revisit with per-item skipping plus alerting.
- **Impossible ids never reach the network.** Fake Store ids are positive integers, so
  `getProduct('../users')` returns `null` without a call. This is a security decision:
  untrusted input never becomes part of the upstream URL.

### Cache
- 60 seconds fresh (`PRODUCT_CACHE_TTL_MS`). If the source fails, data up to 10 minutes old is
  served with a warning; older than that, the error goes through, because very old data is
  worse than an honest error.
- Only source failures are hidden; any other error is a bug and stays loud. Errors and
  "not found" are never cached (otherwise made-up ids could fill memory).

### Catalogue rules
- Ranking never sorts in place, because product lists may be the cache's shared copy.
- The category page fetches categories and products **in parallel**, so its worst case is one
  upstream timeout (about 5s), not two. A test fails if they become sequential again.
- Search input never reaches the regex engine unescaped: `.*` matches nothing (unescaped it
  would match everything), and `(` is a literal character, never an error.

### Configuration
- Validated once at startup; every problem is listed and the server refuses to start.
- `KEY=` with nothing after it means "not set", so defaults apply. Otherwise an empty value
  would become 0 and silently switch the cache off.
- Featured ids must look like ids, not like numbers: requiring numbers would build Fake
  Store's id format into our config, and a Postgres UUID would then fail at startup.

### HTTP and security basics
- One error shape: `{ "error": { "code", "message", "details"? } }`, mapped in one place.
  Messages never reveal internals (upstream name, stack traces); those go to the log.
- 400 for invalid input, 404 not found, 413 body over 10 kB, 502 upstream or email failure,
  500 for anything unexpected (logged as an error).
- `/health` never calls Fake Store, so it stays instant and still answers during an outage. A
  health check that depends on a third party gets the container restarted whenever Fake
  Store blips.
- No `X-Powered-By` header; repeated query parameters are rejected; path ids capped at 100
  characters. If the port is already in use, the server says so and exits instead of claiming
  to be listening.

### Enquiries and email security
- **Header injection** (a name like `"Ann\r\nBcc: x@evil.com"` adding hidden recipients) is
  handled explicitly, in layers: the API rejects line breaks in single-line fields; the domain
  strips them from anything that becomes an email header; the file adapter strips them again
  because it writes headers by hand; nodemailer encodes headers itself, and a test fails if
  our adapter ever bypasses that.
- **No path traversal:** email filenames are generated (timestamp plus random UUID). The send
  function doesn't take a filename at all, so user input cannot influence where files go.
- **Plain text only**, so customer input can't inject markup. SMTP waits at most 5 seconds per
  stage, not nodemailer's default of 2 minutes.

### Android client
- **One state stream per screen**, rendering loading, error and success. Search and Enquiry
  hold two things with different lifecycles (what is being typed, and the result of the last
  submission), so their state wraps the usual loading/error/success value. Whether a button
  is enabled is decided in the ViewModel, where it is tested.
- **Retry is offered only when it can help:** an outage shows "Try again"; a missing product
  doesn't.
- **`RepoResult` instead of Kotlin's `Result`:** `Result` only carries an exception, and the
  screens need to tell a validation error from an outage from a missing product.
- **Forgiving about error responses, strict about bugs:** unknown codes and garbled error
  bodies show the generic message; an unexpected exception is a bug and is not hidden.
  Coroutine cancellation is always rethrown.
- **The 15-second timeout is a user-experience limit, not arithmetic.** An early version set
  30s to sit above a backend worst case of about 25s; nobody waits 30s on a phone. So the
  server was made faster instead (parallel category calls, 5s SMTP stages), and 15s sits above
  its new worst case. Remaining risk: an SMTP server that is slow at every stage, without ever
  quite timing out, could still exceed 15s on the optional SMTP path.
- **Toolchain, found by building:** AGP 9 compiles Kotlin itself; the KSP release matching
  the template's Kotlin version didn't support that, the current one does. Current libraries
  need Kotlin 2.4, so the project moved to it rather than pinning old library versions.
- **Cleartext HTTP is allowed for exactly two addresses,** both of which can only reach the
  developer's own machine: `10.0.2.2` (emulator) and `127.0.0.1` (USB phone via `adb reverse`).

### Testing approach
- **Every test was checked to be able to fail:** for each test, a one-line change to the
  production code it guards was applied and the test had to go red. This found tests that
  could never fail and a gap in an architecture test; all were fixed. Where two independent
  layers protect one behaviour, each layer is asserted separately, or the double protection is
  recorded.
- **Architecture tests on both sides** turn layering rules into failing builds. A rule in a
  README decays; a failing build doesn't.
- **Real data, dated:** backend tests use a byte-for-byte snapshot of the live Fake Store API;
  client tests decode real responses captured from our backend. Nothing is hand-invented.
