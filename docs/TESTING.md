# Testing and TDD

## The pyramid in this repo

| Layer | Tools | What it proves | Count |
|---|---|---|---|
| Unit (pure logic) | JUnit 5, AssertJ | `OrderPricingCalculator`, token issuing, filters, converters | many |
| Service tests with mocked collaborators | Mockito (strict stubs) | business rules: idempotency, ownership, optimistic paths, event emission | |
| Web slice tests | `@WebMvcTest`, MockMvc, `spring-security-test` `jwt()` | HTTP contract, validation, status codes, **security rules (401/403/404)**, headers | |
| Proxy/behaviour tests | Spring test context | `@Cacheable`/`@CacheEvict` really cache and evict (a plain Mockito test cannot prove annotations) | |
| Contract tests | Embedded Kafka, `MockRestServiceServer` | exact Kafka wire format; tolerant reader against the v2 product contract | |
| Integration against real infrastructure | Testcontainers MongoDB (`*IT`, failsafe) | decimal sorting, text search, unique index, **atomic stock under 8 concurrent threads** | 7 |
| Gateway tests | `WebTestClient` on the real filter chain | edge auth, CORS pre-flight, correlation id, error mapping | 13 |
| Documentation tests | Spring REST Docs | docs generated from real responses; drift fails the build | 3 |
| Front end | `node:test` (no dependencies) | cart maths, JWT decoding, API client + token refresh, static server hardening | 30 |
| System tests | `smoke-test.sh`, Newman | the whole platform through the gateway incl. Kafka/Redis/Mongo | 61 checks, 39 requests / 69 assertions |
| Failure injection | `resilience-demo.sh` | circuit breaker, isolation, outbox, Redis outage | 15 checks |

Totals are in the "Verified results" block at the end of this file.

## TDD workflow used
1. **Red** - write the smallest failing test that states the behaviour.
2. **Green** - write the simplest code that passes.
3. **Refactor** with the tests as a safety net.
4. Commit after each green; one behaviour per test, named as a sentence (`place_withKnownIdempotencyKey_returnsOriginalOrder_withoutTouchingCatalogueOrDb`).

### Captured red -> green: `OrderPricingCalculator`
The test class `OrderPricingCalculatorTest` was written first. Running it before the class existed:
```
[ERROR] OrderPricingCalculatorTest.java:[22,28] cannot find symbol     <- RED (does not even compile)
[ERROR] OrderPricingCalculatorTest.java:[30,20] cannot find symbol
```
After adding the minimal `OrderPricingCalculator`:
```
Tests run: 6, Failures: 0, Errors: 0   <- GREEN
```
The tests pinned down: exact decimal arithmetic (`0.10 x 3 = 0.30`), rounding to 2 decimals, rejection of empty orders, merging duplicate product lines in first-seen order, rejecting non-positive quantity.

**Honesty note:** the calculator is the only module where the red run was captured live. For the rest of the code base tests and implementation were written together in small slices (test first where the behaviour was clear, test right after where it was exploratory). The tests are written to be useful regardless of order: behaviour-named, one assertion theme each, and every security rule has a test that *tries the forbidden thing*.

## What tests caught (and what they could not)
Real defects found while building this, and by which layer. This is the argument for having all layers:

| Defect | Found by |
|---|---|
| `Bob@X.io` and `bob@x.io` could register as two accounts (email uniqueness checked before normalising) | unit test `AuthServiceTest` (strict Mockito stubbing flagged the mismatch) |
| Spring Data Mongo in this Boot version has no `BigDecimalRepresentation` option; prices would be stored as *strings* and sort wrongly | compile error, then `ProductRepositoryIT.priceSortIsNumeric_notLexicographic` pins the fix |
| REST Docs `getProduct` failed: path parameters need `RestDocumentationRequestBuilders` | documentation test |
| Bearer placeholder `<access_token>` is rejected by Spring's token parser (401) | documentation test |
| Spring Cloud Gateway's "forward to fallback" path threw `UnsupportedOperationException` (read-only headers) → clients got 500 instead of a clean 503 | **live failure injection** (kill a service). Fixed with a dedicated `GatewayErrorHandler`, unit-tested |
| Kafka producer failed to start: `delivery.timeout.ms` (10 s) < `request.timeout.ms` (30 s default) | **end-to-end run only** (unit tests mock Kafka). The outbox kept the orders safe and delivered them after the fix, which also proved the outbox |
| `docker-compose.yml` had a misspelt variable → broker could not create `__consumer_offsets`, so *no consumer group ever started* | **end-to-end run only** |
| Notification consumer started before the topic existed → broker auto-created it with 1 partition → the service saw only partition 0 | **end-to-end run only**; fixed by declaring the topic in every service + broker default of 3 partitions |
| Unknown URL (`/api/v2/products/`) returned 500 instead of 404 (`NoResourceFoundException` fell into the catch-all handler) | smoke test; handler now extends `ResponseEntityExceptionHandler`, three new tests |
| `/v3/api-docs.yaml` was blocked by the security rules (only `/v3/api-docs/**` was allowed) | OpenAPI export step |
| Price range query did a full collection scan | `explain()` (see PERFORMANCE.md), index added |
| Cache hit metrics were missing (dynamic Redis caches are registered after metrics binding) | smoke test assertion on `cache_gets_total`; fixed with `spring.cache.cache-names` |
| Rebuilding jars while JVMs run from them crashes those JVMs (`NoClassDefFoundError`) | tooling; `start-all.sh` now refuses to run while services are up |
| Parallel login attempts needed to exercise the rate limiter (bcrypt makes sequential attempts slow) | smoke test design |

**Lesson:** 130+ green unit/slice tests did *not* mean the system worked: five of the problems above only appeared when real Kafka, Redis, Eureka and the gateway ran together. Conversely the end-to-end suite is too slow and coarse to pin down business rules. Keep both.

## Running
```bash
./mvnw verify                                 # all Maven modules; *IT classes need Docker (skipped automatically if absent)
./mvnw -pl product-service test               # one module
(cd notification-service && ./gradlew test)
(cd frontend && npm test)                     # add: node --test --experimental-test-coverage test/
```
Coverage: JaCoCo HTML per module in `target/site/jacoco/index.html` (Gradle: `build/reports/jacoco/test/html`).

## Conventions
* Slice tests import only what they test (`@Import(SecurityConfig.class, ApiExceptionHandler.class)`), mock the JWT decoder, and use `jwt().authorities(...)` for roles.
* Strict Mockito stubs: an unnecessary or mismatching stub fails the test (this is what found the email bug).
* Time is injected (`Clock`) wherever a test needs to assert timestamps.
* Anything asynchronous in system tests is **polled with a timeout**, never `sleep`-and-hope.
* A feature is not done until: a test fails without it, a test tries the forbidden/failure path, and the docs/OpenAPI mention it.

## Verified results (last full run on the final code)
| Suite | Result |
|---|---|
| Maven (`./mvnw clean verify`) | **138 tests, 0 failures** (131 unit/slice/contract + 7 Testcontainers integration) |
| Gradle (`notification-service`) | **8 tests, 0 failures** |
| npm (`frontend`) | **30 tests, 0 failures** |
| `scripts/smoke-test.sh` (live platform) | **61 checks, 0 failed** |
| Newman (`api-tests`) | 39 requests, 69 assertions, 0 failed |
| `scripts/resilience-demo.sh` | **15 checks, all as designed** (product-service, Kafka and Redis killed in turn) |

JaCoCo line coverage (unit + integration where present): common-security 64% · discovery-server 33% (one `main` method) · auth-service 71% · product-service 82% · order-service 78% · api-gateway 91% · notification-service 84%. Coverage is a smoke alarm, not a goal: the lowest numbers are application bootstrap classes, OpenAPI configuration and the dev seeder, whereas rules that matter (ownership, idempotency, outbox, stock, token handling) each have a test that fails when the rule is removed.
