# ShopSphere

A small but complete **microservices e-commerce platform** that exercises, end to end, the stack in the brief:
Java 21 + Spring Boot 3, REST APIs with versioning and documentation, MongoDB, Redis caching, Kafka messaging,
OAuth2/JWT security, an API gateway, service discovery, Maven + Gradle + npm builds, test-driven development and a
JavaScript web front end.

Everything described here was built **and run**: 176 automated tests (138 Maven, 8 Gradle, 30 JavaScript), a 61-check end-to-end smoke test, a 39-request
Postman/Newman suite, a failure-injection demo and a load test were executed against the running system.

```
                          ┌─────────────────────────────┐
   Browser (JS SPA :3000) │      API Gateway :8080      │  JWT check · rate limit (Redis) · CORS
   Postman / curl ───────►│  Spring Cloud Gateway       │  circuit breakers · correlation id
                          └──────┬───────┬───────┬──────┘
                    discovery    │       │       │        (lb://service-name via Eureka :8761)
              ┌──────────────────┘       │       └───────────────────┐
              ▼                          ▼                           ▼
     ┌────────────────┐        ┌──────────────────┐         ┌───────────────────┐
     │ auth-service   │        │ product-service  │         │ order-service     │
     │ :8081          │        │ :8082            │◄────────│ :8083             │
     │ users, tokens  │        │ catalogue v1/v2  │  REST   │ orders, outbox    │
     │ JWKS, OIDC     │        │ Redis cache      │ +circuit│ idempotency       │
     └──────┬─────────┘        └────┬────────▲────┘ breaker └────────┬──────────┘
            │                       │        │ stock updates         │ OrderPlaced / OrderCancelled
            ▼                       ▼        │ (idempotent)          ▼
        MongoDB                 MongoDB   ┌──┴───────────── Kafka  topic: order-events (3 partitions, DLT) ─────┐
     (shopsphere_auth)    (shopsphere_products)                                   │
                                          └──────────────────────────────────────┬┘
                                                                                 ▼
                                                                  ┌───────────────────────────┐
                                                                  │ notification-service :8084│  (Gradle build)
                                                                  │ consumes events, stores   │
                                                                  │ notifications per user    │
                                                                  └───────────────────────────┘
```
One database per service (`shopsphere_auth`, `_products`, `_orders`, `_notifications`); services never read each other's data,
they talk through REST (synchronous, only where an answer is needed *now*) and Kafka events (asynchronous, everything else).

## Run it

Prerequisites: JDK 21, Node 20+, Docker (for MongoDB, Kafka, Redis). Maven and Gradle are provided through the wrappers.

```bash
scripts/start-all.sh       # docker compose (Mongo, Kafka, Redis) + build + start 6 services + web app (~2 min first time)
scripts/smoke-test.sh      # 61 end-to-end checks through the gateway
scripts/resilience-demo.sh # kills product-service and Kafka on purpose and shows the system degrading and recovering
scripts/stop-all.sh        # add --infra to also stop the containers
```

| What | Where |
|---|---|
| Web app | http://localhost:3000 |
| API gateway (the only door for clients) | http://localhost:8080 |
| Swagger UI per service | :8082/swagger-ui.html (products, groups v2 and v1-deprecated), :8083 orders, :8081 auth, :8084 notifications |
| Spring REST Docs (generated from tests) | http://localhost:8082/docs/index.html (also `docs/rest-docs/index.html`) |
| OpenAPI specs (exported) | [docs/openapi/](docs/openapi) |
| Eureka dashboard | http://localhost:8761 |
| Metrics | `:808x/actuator/prometheus` |

Demo logins exist **only** through the dev seed (`SEED_DEMO_USERS=false` disables them): `admin` (roles ADMIN, USER) and `alice` (USER);
the passwords are in `auth-service/src/main/resources/application.yml` and can be overridden by environment variables.

Note: the smoke test finishes by *deliberately* exhausting the per-IP login rate limiter (to prove the 429). Wait ~10 s before running another login-heavy suite such as Newman.

Run the tests individually:

```bash
./mvnw verify                                  # 138 Maven tests (unit, slice, embedded Kafka, Testcontainers Mongo) + REST Docs
(cd notification-service && ./gradlew test)    # Gradle module
(cd frontend && npm test)                      # 30 JavaScript tests, no dependencies to install
npx newman run api-tests/shopsphere.postman_collection.json   # 39 requests / 69 assertions against the running platform
node perf/load-test.mjs http://localhost:8082/api/v2/products/<id> --c 32 --d 10
```

## What was built against each topic of the brief

| Topic from the brief | What is implemented | Where to look |
|---|---|---|
| **Java, Spring Boot** | Java 21 (records, switch expressions, virtual threads enabled), Spring Boot 3.3, Spring Cloud 2023.0, 6 services + shared library | every module |
| **REST API development** | Resource-oriented endpoints, proper status codes (201+Location, 204, 409, 422...), pagination with bounds, filtering, whitelisted sorting, RFC 7807 errors, optimistic locking (`@Version`, 409), `Idempotency-Key` on order placement | `product-service/web`, `order-service/web`, `common-security/web/ApiExceptionHandler` |
| **API documentation: Swagger, Spring REST Docs, OpenAPI** | springdoc generates OpenAPI 3 + Swagger UI (grouped per API version); Spring REST Docs snippets are produced *by tests* (docs fail the build if they drift) and rendered with Asciidoctor; specs exported as YAML | `docs/openapi/`, `docs/rest-docs/`, `product-service/src/docs/asciidoc`, `ProductApiDocumentationTest` |
| **API versioning** | URI versioning: `/api/v1` (frozen, deprecated, emits `Deprecation` / `Sunset` / `Link: rel=successor-version`) and `/api/v2` (price became an object, tags, filters, paging envelope) served side by side from one service | `ProductControllerV1/V2`, [docs/API.md](docs/API.md) |
| **Service discovery / registration** | Netflix Eureka server; every service registers; gateway and order-service resolve `lb://name` / `http://name` with client-side load balancing | `discovery-server`, `ClientConfig`, gateway routes |
| **API testing and management** | Postman collection (generated, with assertions) run by Newman, `.http` file, 61-check shell smoke test, MockMvc slice tests, REST Docs tests; management through gateway routing, rate limits, actuator, Prometheus metrics, correlation ids | `api-tests/`, `scripts/`, [docs/API.md](docs/API.md) |
| **API gateways (plus)** | Spring Cloud Gateway: JWT validation, per-route Redis rate limiting (login limited per IP, others per user), CORS allow-list, circuit breakers, correlation-id propagation, uniform 503/504 problem responses, only whitelisted routes exposed | `api-gateway` |
| **Microservices architecture** | Bounded contexts with own data stores, consumer-owned event contracts (no shared model classes), database per service, independent builds | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| **Large-scale distributed systems, cloud applications** | Transactional **outbox** inside the order document (atomic with the order), at-least-once Kafka delivery + idempotent consumers, dead-letter topic, circuit breaker + timeouts, partition-keyed ordering, stateless services (horizontal scaling), graceful shutdown, health probes, 12-factor config via env vars | `OutboxRelay`, `StockService`, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), `scripts/resilience-demo.sh` |
| **Web & API security: authentication, authorization, OAuth, OpenID, SAML, OWASP** | OAuth2-style token endpoint (password + refresh_token grants), RS256 JWTs verified through a published **JWKS**, **OIDC discovery** and `/userinfo`, rotating single-use refresh tokens (stored hashed), BCrypt, role + ownership authorization, defence in depth (gateway *and* each service validate), OWASP Top-10 mapping. **SAML is documented, not implemented** (see below) | `auth-service`, [docs/SECURITY.md](docs/SECURITY.md) |
| **Backend development and design** | Layered design (web → service → repository), pure domain logic (`OrderPricingCalculator`), DTOs per API version, validation, centralised error handling | all services |
| **MongoDB** | Spring Data MongoDB: unique, text, compound, partial-unique and TTL indexes; `Decimal128` money; atomic `$inc` stock updates (no overselling under concurrency); `$pull` for the outbox; `explain()`-verified index usage | `Product`, `ProductRepositoryCustomImpl`, `Order`, [docs/PERFORMANCE.md](docs/PERFORMANCE.md) |
| **Caching** | Redis read-through cache (`@Cacheable`) with TTL, eviction on update/delete and on every stock change, resilient to Redis outage (falls back to MongoDB), cache hit metrics, measured effect | `ProductService`, `CacheConfig`, `ProductCachingTest` |
| **Kafka messaging** | KRaft broker; JSON events keyed by order id; idempotent producer (`acks=all`); two independent consumer groups (stock, notifications); manual retry + dead-letter topic; embedded-Kafka wire-format test | `OutboxRelay`, `OrderEventListener`, `OrderEventWireFormatTest` |
| **Build and packaging: Maven, Gradle, npm** | Maven multi-module reactor with wrapper, JaCoCo, failsafe, Asciidoctor; `notification-service` built with Gradle (wrapper, JaCoCo); npm package for the web app (`npm test`, `npm start`); executable jars; GitHub Actions workflow running all three | `pom.xml`, `notification-service/build.gradle`, `frontend/package.json`, `.github/workflows/ci.yml` |
| **TDD and unit testing (front to back)** | Back end: 138 Maven + 8 Gradle tests. Front end: 30 tests. `OrderPricingCalculator` was written test-first with the red run captured. Tests also found real bugs (list in [docs/TESTING.md](docs/TESTING.md)) | [docs/TESTING.md](docs/TESTING.md) |
| **Paired programming** | A practice rather than a feature: [docs/PAIR-PROGRAMMING.md](docs/PAIR-PROGRAMMING.md) describes the workflow, ping-pong TDD and how the repo supports it (PR template, CODEOWNERS, co-author trailers, small test-first slices) | docs, `.github/` |
| **Performance tuning and bottleneck analysis** | Load tester, measured cache effect, `explain()` index checks, found and fixed a collection scan, bounded queries, virtual threads, a documented method (metrics → profile → fix → re-measure) | [docs/PERFORMANCE.md](docs/PERFORMANCE.md) |
| **JavaScript, web application development** | Dependency-free ES-module SPA: catalogue search/filter/paging, cart, checkout with idempotency key, orders, notifications, admin form; XSS-safe DOM building, strict CSP, in-memory access token, single-flight token refresh | `frontend/` |

## Honest limits

* **SAML** is not implemented. [docs/SECURITY.md](docs/SECURITY.md) explains how it would be added (Spring Security `saml2Login`, or federating through an IdP) and why it is not needed for a first-party API.
* The auth-service is a **purpose-built token service** that speaks the OAuth2 token endpoint / JWKS / OIDC discovery protocols for the `password` and `refresh_token` grants. It is *not* a full OpenID Provider (no authorization-code + PKCE flow, no ID tokens, no consent screens). For production use a hardened IdP (Keycloak, Cognito, Spring Authorization Server).
* The RSA signing key is generated at start-up, so restarting the auth-service invalidates issued access tokens (they live 15 min). Production: load it from a KMS/secret store and rotate with several `kid`s.
* Kafka delivery is **at-least-once**; "exactly once" effects come from idempotent consumers (event-id markers), not from the broker.
* Single-node infrastructure, no TLS, Eureka without authentication, demo seed users: this is a development setup. [docs/SECURITY.md](docs/SECURITY.md) lists what to change for production.
* Paired programming cannot be delivered as code; only the supporting workflow and documents are included.
