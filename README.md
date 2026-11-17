# Product Catalogue PoC

A product catalogue: browse featured products and categories, search, view a product and send
an enquiry to the sales inbox. Android (Kotlin, Jetpack Compose) client plus a Node/TypeScript
backend. Product data comes from the Fake Store API **only through the backend**; the app
never calls it.

- Decisions, assumptions, cuts and next steps: [DECISIONS.md](DECISIONS.md)
- How AI was used, and where it was wrong: [AI_USAGE.md](AI_USAGE.md)
- The plan agreed before coding: [docs/PLAN.md](docs/PLAN.md)

> **If the Fake Store API is slow or unreachable, run with `PRODUCT_SOURCE=snapshot` to serve a dated local snapshot.**

## Stack, and why

| Layer | Choice | Why |
|---|---|---|
| Client | **Android: Kotlin, Jetpack Compose**, ViewModel + StateFlow, Hilt, Retrofit/OkHttp, Coil | `TODO (author): your reason, e.g. it is your strongest platform.` Compose keeps the whole look and feel in one theme (`Tokens.kt`), which the brief says will change. |
| Backend | **Node 20 + TypeScript**: Express 5, Zod, Vitest | The brief prefers TypeScript. Zod validates both incoming requests and Fake Store's responses at runtime. It runs with npm alone, with no database or Docker needed. `TODO (author): add your own reason, or confirm.` |

## Run it locally

You need **Node 20+** and **Android Studio**. No accounts, no paid services, no Docker.

### 1. Backend (start this first)
```sh
cp .env.example .env
cd backend && npm install && npm run dev     # http://localhost:3000/api/v1
curl localhost:3000/api/v1/health            # {"status":"ok"}
```
- Enquiry emails are written to `backend/data/enquiries/*.eml` (open them in any mail client,
  or read them as text), and each one is logged. That is the local "sales inbox".
- Every setting is explained in `.env.example`. The useful ones:

| Setting | Default | What it does |
|---|---|---|
| `PRODUCT_SOURCE` | `fakestore` | `snapshot` serves a dated local copy instead of the live API |
| `FEATURED_PRODUCT_IDS` | empty | up to 5 product ids for the home page; empty = top-rated rule |
| `EMAIL_TRANSPORT` | `file` | `smtp` sends through any SMTP server (`SMTP_HOST`, `SMTP_PORT`) |
| `SALES_INBOX_EMAIL` | `sales@example.com` | where enquiries go |
| `PRODUCT_CACHE_TTL_MS` | `60000` | how long product data is cached |

### 2. Android app
Open the **`android/`** folder (not the repo root) in Android Studio and run the app.
Android Studio finds the SDK and JDK by itself.

- **Emulator:** works as-is. The app calls `http://10.0.2.2:3000/api/v1/`, which is how the
  emulator reaches your machine's `localhost`.
- **Physical phone over USB:**
  ```sh
  adb reverse tcp:3000 tcp:3000
  cd android && ./gradlew installDebug -PapiBaseUrl=http://127.0.0.1:3000/api/v1/
  ```
  Re-run `adb reverse` after reconnecting the phone.

From the command line, Gradle needs to know where the Android SDK and a JDK (17+) are.
On macOS with a default Android Studio install:
```sh
export ANDROID_HOME=~/Library/Android/sdk
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # Studio's bundled JDK
```
(Opening `android/` in Android Studio once also works: it writes `local.properties`.)

## Tests
```sh
cd backend && npm test                       # 130 tests: domain, adapters, routes, email, architecture
cd android && ./gradlew testDebugUnitTest    # 73 tests: ViewModels, data layer, contract, theme, architecture
```
They cover the critical path: featured, category, search, product detail and enquiry,
including the failure cases (upstream down, timeouts, not found, invalid input, email failure).

## Where things are
```
backend/src/domain/      catalogue rules and enquiries (no framework code)
backend/src/ports/       ProductSource, EmailSender, Logger: the seams
backend/src/adapters/    Fake Store, snapshot, cache, file + SMTP email
backend/src/http/        routes, request validation, the one error handler
backend/src/app.ts       the only place adapters are chosen
backend/snapshot/        dated copy of the Fake Store API (PRODUCT_SOURCE=snapshot, and tests)
android/app/src/main/java/com/example/myproducts/
  domain/  data/  di/    models, repository, Retrofit, dependency injection
  ui/theme/Tokens.kt     every colour, size and text style: the look-and-feel seam
  ui/<screen>/           one package per screen: Screen + ViewModel
```

## Time spent

`TODO (author): fill in your actual time. The brief asks for an honest total, roughly split.`

| Activity | Time |
|---|---|
| Planning | TODO |
| Building | TODO |
| Testing | TODO |
| Reviewing | TODO |
| **Total** | **TODO** (the brief's time-box was 2–3 hours) |
