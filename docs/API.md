# API design, versioning, documentation and testing

## Conventions
* JSON over HTTPS, resource-oriented URLs, plural nouns: `/api/v2/products`, `/api/v1/orders/{id}/cancel`.
* Status codes: `200`, `201` + `Location`, `204`, `400` validation, `401` no/invalid token, `403` authenticated but not allowed, `404`, `409` conflict (duplicate SKU, concurrent edit, already cancelled), `422` well-formed but cannot be fulfilled (unknown product, not enough stock), `429` rate limited, `503`/`504` dependency problems.
* **Errors are RFC 7807 `application/problem+json`**: `{"title","status","detail","errors":{"field":"message"}}`. Unexpected errors return a generic message; details stay in the log under the correlation id.
* **Pagination is always bounded** (`size` 1-100, orders 1-50), the legacy unpaginated list is capped at 100 items, sort keys are whitelisted, quantities and list lengths are limited.
* **Idempotency**: `POST /api/v1/orders` accepts `Idempotency-Key`. First call → `201`, replay → `200` with the original order, never a second order.
* **Correlation id**: send `X-Correlation-Id` (or let the gateway create one); it is echoed on the response and appears in every service's log lines.

## Versioning strategy
URI major versions, **one service, two versions side by side**.

| | v1 | v2 |
|---|---|---|
| Status | **deprecated** | current |
| `price` | number | `{ "amount": 12.34, "currency": "USD" }` |
| List | JSON array, max 100, optional `category` | page envelope `{content,page,size,totalElements,totalPages}` with `q`, `category`, `minPrice`, `maxPrice`, `inStock`, `sort`, `page`, `size` |
| Extras | - | `tags`, `inStock`, `version`, `createdAt`, `updatedAt` |

Rules: a **breaking** change (rename/remove a field, change a type, change semantics) → new major version. Adding optional fields or endpoints is not breaking. A deprecated version keeps working and every response carries
`Deprecation: true`, `Sunset: <date>` and `Link: </api/v2/products>; rel="successor-version"` (RFC 8594); Swagger marks the operations `deprecated`. Both versions are separate OpenAPI groups (`v2`, `v1-deprecated`). Shared behaviour lives once in `ProductService`; each version only owns its DTOs and mapping.

## Documentation, three complementary ways
| Tool | Purpose | Output |
|---|---|---|
| **springdoc / Swagger UI** | Explore and try endpoints (Authorize button takes a JWT) | `/swagger-ui.html` on each service |
| **OpenAPI 3 spec** | Machine-readable contract for client generation, gateways, linters | [`docs/openapi/*.yaml`](openapi), live at `/v3/api-docs` |
| **Spring REST Docs** | Narrative documentation whose examples come from *tests*: the snippets (curl, request/response, field tables) are produced by MockMvc tests, so if a field is added/removed and the test's `responseFields` is not updated the **build fails** | `docs/rest-docs/index.html`, served by product-service at `/docs/index.html` |

Regenerate: `./mvnw verify` rebuilds the REST Docs; the OpenAPI YAML is exported with `curl localhost:8082/v3/api-docs.yaml/v2` (see the Swagger group names above).

## API testing layers
1. **Slice tests** (`@WebMvcTest` + MockMvc + `jwt()`): status codes, validation, security rules, headers, JSON shape - fast, no infrastructure.
2. **REST Docs tests**: documentation as a test.
3. **Contract tests**: wire format of Kafka events (embedded Kafka), consumer-side deserialisation of the same JSON, tolerant-reader client test with `MockRestServiceServer`.
4. **Postman / Newman collection** (`api-tests/`): 39 requests, 69 assertions through the gateway, with chained variables (tokens, ids), setup and cleanup. Regenerate with `python3 api-tests/generate_postman.py`; run `npx newman run api-tests/shopsphere.postman_collection.json`.
5. **Shell smoke test** (`scripts/smoke-test.sh`): 61 checks including the asynchronous Kafka effects (polls until stock/notifications change) and rate limiting.
6. **Failure injection** (`scripts/resilience-demo.sh`).
7. `api-tests/requests.http` for manual exploration in IntelliJ / VS Code.

## API management (what the gateway and the platform provide)
Single entry point and routing table (`api-gateway/application.yml`) · authentication at the edge · **rate limits** per route (login 2 req/s burst 10 per IP; catalogue 50/s per user; orders 10/s per user; excess → 429 with `X-RateLimit-*` headers) · **circuit breakers** per route · CORS allow-list · uniform error documents for routing failures · metrics (`/actuator/prometheus`: request latency histograms, cache hit/miss, circuit breaker state) · correlation ids · deprecation/sunset signalling to API consumers.
