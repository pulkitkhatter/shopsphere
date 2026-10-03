# Pair programming

Pairing is a working practice, so there is no code for it; this file is the team agreement and the repo is set up to support it.

## Styles we use
| Style | When | How |
|---|---|---|
| **Driver / navigator** | default | One types (driver), the other thinks ahead (navigator): next test, edge cases, naming, design smells. Swap roles every 15-25 minutes or at every green test. |
| **Ping-pong TDD** | new logic with clear rules (pricing, validation, state machines) | A writes a failing test → B makes it pass and writes the next failing test → A makes it pass ... Naturally produces small commits and keeps both people engaged. |
| **Mob** (3+) | onboarding, tricky design decisions, production incidents | One driver, everybody else navigates; rotate every 10 minutes. |
| **Strong-style** | mixed experience levels | "For an idea to go from your head into the computer it must go through someone else's hands" - the navigator dictates intent, the driver types. Best for teaching. |

## Rules of thumb
* Pair on **risky and unfamiliar** work (security, concurrency, data model, outbox); do routine edits solo and review.
* Time-box a session (60-90 min) with a 10 minute break; keep the *why* in the commit message.
* The navigator keeps a scratch list (tests to write, refactors, questions) so the driver can stay in flow; nothing is lost.
* Disagree with a **test or a spike**, not with opinions: "let's write the failing test for that case".
* Remote: shared terminal/IDE session (VS Code Live Share, JetBrains Code With Me, tmux), voice on, camera optional. Agree on a driver *before* typing.
* Rotate pairs across the week so knowledge of auth/gateway/Kafka spreads instead of belonging to one person.

## How this repository supports it
* **Small test-first slices**: each behaviour is one test + a few lines, which is also the natural hand-over point in ping-pong.
* **`.github/pull_request_template.md`** asks whether the change was paired and lists the done-checklist.
* **Co-author trailers**: a pair commits with both names so credit and ownership are shared:
  ```
  git commit -m "Reject duplicate idempotency keys under concurrency" \
             -m "Co-authored-by: Dana Navigator <dana@example.com>"
  ```
* **`.github/CODEOWNERS`** routes reviews, but a paired change can satisfy "two sets of eyes" without waiting for async review (trunk-based teams often merge paired work directly).
* **Fast feedback loop** makes pairing pleasant: `./mvnw -pl order-service test` runs in seconds, `scripts/smoke-test.sh` verifies the whole platform in about a minute.
* **Everything is documented where the pair needs it**: architecture decisions in `docs/ARCHITECTURE.md`, test conventions in `docs/TESTING.md` - so a new pair partner can navigate without a lecture.

## A suggested 90-minute pairing session on this codebase
1. (5 min) Agree the goal as a failing test: e.g. "an order must be rejected when the user has more than 20 open orders".
2. (30 min) Ping-pong: write the test in `OrderServiceTest`, make it pass in `OrderService`, add the `OrderApiTest` for the HTTP status (`422`), swap at every green.
3. (15 min) Refactor together; navigator reads the diff aloud like a reviewer.
4. (20 min) Add the API-level assertion to `api-tests/generate_postman.py`, run `scripts/smoke-test.sh`.
5. (10 min) Update `docs/API.md` if the contract changed, commit with both co-author trailers, retro: what did we each learn?
