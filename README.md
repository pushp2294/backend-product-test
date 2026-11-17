# Product Catalogue PoC

Android (Kotlin, Jetpack Compose) client + Node/TypeScript backend. Product data comes
from the Fake Store API **only via the backend**. Decisions: [DECISIONS.md](DECISIONS.md).
AI usage: [AI_USAGE.md](AI_USAGE.md). Agreed plan: [docs/PLAN.md](docs/PLAN.md).

> **If the Fake Store API is slow or unreachable, run with `PRODUCT_SOURCE=snapshot` to serve a dated local snapshot.**

## Backend
Requires Node 20+.
```sh
cp .env.example .env
cd backend && npm install && npm run dev     # http://localhost:3000/api/v1
curl localhost:3000/api/v1/health            # {"status":"ok"}
npm test                                     # backend tests
```
Enquiry emails are written to `backend/data/enquiries/*.eml` by default
(`EMAIL_TRANSPORT=file`). Every setting is documented in `.env.example`.

## Android
Open the `android/` folder (not the repo root) in Android Studio, start the backend first,
then run the app. Android Studio finds the SDK and JDK by itself.

From the command line, Gradle needs to know where the Android SDK and a JDK (17+) are.
On macOS with a default Android Studio install:
```sh
export ANDROID_HOME=~/Library/Android/sdk
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # Studio's bundled JDK
```
(Opening `android/` in Android Studio once also works: it writes `local.properties`.)

- **Emulator:** works as-is (the app calls `http://10.0.2.2:3000/api/v1/`).
- **Physical phone over USB:**
  ```sh
  adb reverse tcp:3000 tcp:3000
  cd android && ./gradlew installDebug -PapiBaseUrl=http://127.0.0.1:3000/api/v1/
  ```
- Tests: `cd android && ./gradlew testDebugUnitTest`

_Work in progress: time spent is still to be added._
