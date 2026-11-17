# AI usage

> Work in progress: completed at the end of the build.

## Where the AI got something wrong, and how it was caught

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
