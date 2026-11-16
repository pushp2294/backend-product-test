# Project rules

## Context
Take-home: a product catalogue PoC. Android (Kotlin, Compose) client + Node/TypeScript
backend. Product data comes from the Fake Store API, but ONLY via the backend.
This is a foundation meant to grow, not a throwaway prototype.

## Non-negotiables
1. The Android client NEVER calls fakestoreapi.com. All product data goes through our backend.
2. Three things are expected to change and must each sit behind one interface:
   - product data source  -> ProductSource port (backend)
   - email provider       -> EmailSender port (backend)
   - look and feel        -> Compose theme + design tokens (client)
3. Fake Store response shapes never escape the adapter. Map to our own domain types.
4. No secrets in the repo. Config via .env, with a committed .env.example.
5. No paid services. A reviewer must run everything locally with no account signups.

## How I want you to work
- Before writing code for a new area, state your plan in 5 bullets and WAIT for my go-ahead.
- Prefer the boring, readable option over the clever one. I have to defend every line
  in an interview, so optimise for my understanding, not for brevity.
- I have little Node experience. In backend code, add a one-line comment above any
  non-obvious construct explaining WHY, not what.
- No new dependency without telling me what it does and what it replaces.
- Do not add features that are not in the acceptance criteria. Ask instead.
- When a requirement is ambiguous, stop and list the options with a recommendation.
  Do not silently pick one.
- Flag anything you are unsure about rather than producing confident-looking filler.

## Stack
- Backend: Node 20, TypeScript, Express, Zod for validation, Vitest for tests.
- Client: Kotlin, Jetpack Compose, ViewModel + StateFlow, Retrofit + OkHttp,
  Hilt, Coil. MVVM with a repository layer.
- Run: docker compose up for the backend. Android Studio for the client.

## Testing
- Backend: unit tests on the domain core, and route tests with the ProductSource faked.
- Client: ViewModel tests with a fake repository. No UI tests unless time allows.
- Tests cover the critical path: featured, category, search, product detail, enquiry.
