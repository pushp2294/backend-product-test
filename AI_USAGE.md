# AI usage

How AI was used to plan and build this, and, more usefully, where it was wrong and how that
was caught. I reviewed every change and can explain every line.

## 1. Tools and models

- **Claude Code** (Anthropic's command-line coding agent) with **Claude Opus 5.5**
  (`claude-opus-5-5`): planning, code, tests, documentation, git, and driving the Android
  build, the phone (adb) and the backend from the terminal.

## 2. Agent instruction files

- [`CLAUDE.md`](CLAUDE.md), committed in the very first commit on `main`, before any code.
  It sets the non-negotiables (the app never calls Fake Store; three seams; no secrets; no
  paid services) and how the agent must work:
  - state a 5-bullet plan and wait for approval before each new area;
  - prefer the boring, readable option, because I have to defend every line;
  - comment *why* above any non-obvious Node construct (I have little Node experience);
  - no new dependency without saying what it does and what it replaces;
  - when something is ambiguous, list the options with a recommendation instead of
    silently picking one;
  - flag uncertainty instead of producing confident-looking filler.

## 3. Plan / spec

[`docs/PLAN.md`](docs/PLAN.md) was agreed before any code and committed alongside `CLAUDE.md`
as the first commit on `main`, so the history shows the rules and the plan came first. It
holds the repo layout, the API contract, every ambiguity with options and a recommendation,
the test list and a build order. It was revised as decisions changed (e.g. dropping Docker),
and each revision is dated.

**How the work ran:** plan, then my approval, then one step built, then a pause for my
review, then the next step. Claims were checked against reality rather than trusted: live
API calls, the running server, a real phone, a fresh clone, and mutation runs proving each
test can fail.

**A note on git history:** at the end I had the history rewritten into one clean commit per
step (setup, each backend part, each Android part, docs), each verified to build and pass its
own tests. The individual fix commits were folded in, so the iterations are recorded here and
in `DECISIONS.md` rather than in `git log`.

## 4. Key prompts and moments that shaped the solution

1. **"If moving to Postgres means more than swapping one adapter, the design is wrong; fix it
   before showing me."** Part of the first planning prompt, alongside asking for the file
   tree, the API contract, every ambiguity and a build order. It forced string product ids
   and our own category slugs before any code existed, so the contract never exposes Fake
   Store's formats.

2. **"Verify the per-category product counts with a real call, not from memory."** The counts
   happened to be right, but the same live call revealed that Fake Store returns HTTP 200
   with an empty body for a missing product. Memory didn't know that. The adapter handles it,
   and a test pins it.

3. **"For each test, tell me the one-line change to production code that would make it fail.
   Any test where you can't name one is vacuous: delete it or fix it. Show me the column."**
   This turned "the tests pass" into "the tests can fail". The changes were actually applied
   and run. It found tests that could never fail and a hole in an architecture test, and it
   became the standard for every later step.

4. **"The client timeout is a user-experience constraint, not an arithmetic one."** Overruling
   the AI's 30-second timeout. When the server is too slow for a user, make the server faster,
   not the app more patient: that led to parallel upstream calls, bounded SMTP and a 15s app
   timeout.

5. **Running the real thing, not just the tests.** Putting the app on a real Pixel 9 exposed
   the lavender-card bug that every test had passed, and showed images failing when the phone
   had no internet. Fake Store going down mid-session led to the snapshot data source. A
   fresh-clone check found a README that only worked on the author's machine.

## 5. Where the AI got something wrong, and how it was caught

(My own mistakes are listed too: the point is how errors were caught, not who made them.)

- **Expected values typed from memory.** In the adapter's mapping test, the AI wrote
  the expected image URL for product 1 from memory (`…_SL1500_.jpg`). The test ran
  against a fixture captured from the live API and failed: the real value is
  `…_SL1500_t.png`. Fixed the test, not the code. This is the reason fixtures are
  dated snapshots of real responses, not hand-written data.
- **Product counts from memory.** During planning the AI stated per-category counts
  from memory. I required a real API call before accepting N = 2. The counts
  happened to be right, but the same call revealed the empty-body 200 quirk on
  missing products, which memory did not know about.
- **A test that claimed more than it checked.** The AI drafted a "uses a 5 second
  timeout by default" test that only checked a signal was attached; it could not
  see the 5s value. Deleted rather than kept as false confidence. The timeout
  behaviour itself is tested with a 20ms timeout against a fetch that never answers.
- **Tests that could not fail, found by a mutation audit I asked for.** I asked
  for every test to be paired with a one-line production change that breaks it, and
  for the changes to actually be run. Of 28 tests, 27 failed as expected. The
  survivor was the architecture test: while "tightening" its regex the AI had
  narrowed it so a side-effect import escaped. Separately, the audit showed the
  "integer minor units" test could never exercise `Math.round` with live data,
  and the AI replaced it with a crafted input.
- **My test spec was wrong, and the AI caught it.** I asked for a search test that
  "(" is treated as a literal and "returns no results". The data contains
  `POLYURETHANE(shell)` (product 16), so "(" correctly matches product 16. The AI
  checked the data before writing the test, reported the conflict, and substituted
  a test that actually proves the escaping: `.*` returns nothing, where unescaped it
  would match all 20. It kept a "(" test asserting no throw and exactly `['16']`.
- **"Listening" on a port it never got: found by a manual smoke test, not by tests.**
  `server.ts` logged "Backend listening on :3000" even when the port was already taken.
  Express 5's `app.listen` callback also fires on error, with the error as its argument,
  and the AI-written callback ignored it. A stale server from an earlier copy of the
  project held port 3000, so every request reached the wrong process while our log said
  all was well. Caught because the 404 message didn't match ours. Fixed: log the error
  and exit 1. Lesson: the entrypoint had no test; the smoke test against the real
  process was the only thing that could catch this.
- **My pasted API contract was wrong, and the AI caught it.** Briefing the Android
  work, I pasted a contract saying `POST /enquiries` returns 201. The AI checked it
  against the backend code and tests before writing any client code: it returns 200,
  deliberately (nothing is stored, so there is no created resource). We kept 200 and
  made the client accept any 2xx. This is the second spec error of mine the AI caught,
  after the "(" search case.
- **The lavender bug: a test that proved the wrong thing.** The client architecture
  test enforced that no screen contains a colour literal, and it passed. But `Theme.kt`
  defined only 12 of Material's 48 colour roles, so Material filled the remaining 36 with
  its own purple. The test proved screens did not hardcode colours; it never proved the
  theme supplied them. Found by looking at the running app, not by any test. Fixed by
  using the full `ColorScheme` constructor, which has no defaults, so an undefined role
  now fails to compile rather than silently falling back.
- **A timeout chosen by arithmetic instead of by the user.** The backend's worst case was
  about 25s, so the AI set the app's network timeout to 30s to stay above it. I rejected
  it: nobody waits 30 seconds on a phone. The AI had even named the right fix (make the
  server faster) and then done the other thing. We bounded the server instead (parallel
  category calls, 5-second SMTP stages) and set the app to 15s.
- **Docs that promised something nobody had run.** The plan, `.env.example` and
  `DECISIONS.md` all described an "optional docker compose path". No compose file was ever
  written, and Docker was never available to run one. Caught when I asked for the compose
  path to be verified or deleted; every mention was removed.
- **A README that only worked on the author's machine.** Following the README on a fresh
  clone, `./gradlew` failed with "SDK location not found": Android Studio had written the
  SDK path into a local file that a reviewer's clone doesn't have. Found by a fresh-clone
  check, not by tests. The README now says how to set it.
- **A toolchain mismatch.** The AI first picked the KSP version that matches the template's
  Kotlin version; it fails under AGP 9, which compiles Kotlin itself. Caught by building
  straight away; the current KSP version works.
- **"0 vulnerabilities", checked too narrowly.** The AI repeatedly reported a clean
  `npm audit`, but it was running `npm audit --omit=dev`, which checks only production
  dependencies. When I ran a plain `npm install`, npm reported two critical advisories in
  Vitest, the test runner. The AI's first fix attempt was also wrong: it ran the upgrade
  with `--silent`, which hid that the install had failed, and the tests "passed" on the old
  version. It caught that because `package.json` still showed the old version. The real
  fix (Vitest 4.1.12) needed a newer npm, because npm 10 crashes resolving Vitest 4's
  dependencies. It was then verified that a reviewer's npm 10 installs the result cleanly.

