# Architecture

## Services

| Service | Port | Responsibility | Owns (MongoDB db) | Talks to |
|---|---|---|---|---|
| discovery-server | 8761 | Eureka registry | - | - |
| api-gateway | 8080 | Single entry point, edge security, rate limiting | - (Redis for limiter state) | all services via `lb://` |
| auth-service | 8081 | Accounts, tokens, JWKS, OIDC discovery | `shopsphere_auth` (users, refresh_tokens) | - |
| product-service | 8082 | Catalogue (v1, v2), stock | `shopsphere_products` (products, processed_events) | Kafka (publishes product-events, consumes order-events), Redis cache |
| order-service | 8083 | Order placement/cancellation | `shopsphere_orders` (orders incl. outbox) | product-service (REST, circuit breaker), Kafka (publishes order-events) |
| notification-service | 8084 | Turns order events into per-user notifications | `shopsphere_notifications` | Kafka (consumes order-events) |
| common-security (library) | - | JWT decoder defaults, RFC 7807 handler, correlation-id filter | - | used by auth, product, order |

Ports 8081-8084 are reachable on localhost for development (Swagger UI, metrics); in a real deployment only the gateway is exposed.

## Why these boundaries
* **Auth** changes for security reasons, not business reasons, and every other service depends on it only through a public key (JWKS). No service ever calls auth at request time, so auth being down does not stop already-authenticated traffic.
* **Product** and **order** have different load shapes (read-heavy catalogue vs. write-heavy orders) and scale independently. Orders store a *snapshot* (name, SKU, unit price) of every product so later catalogue edits never rewrite history.
* **Notification** is a pure consumer: adding "send an e-mail" or "update analytics" later means adding another consumer group, not touching the order flow.

## Request flow: placing an order

```
Browser ── POST /api/v1/orders (Bearer JWT, Idempotency-Key) ──► Gateway
   Gateway: verify JWT signature (JWKS) + issuer + expiry ► rate-limit per user (Redis) ► add X-Correlation-Id ► route lb://order-service
   order-service:
     1. verify JWT again (defence in depth)                      ← never trust "the gateway already checked"
     2. Idempotency-Key seen for this user? → return the stored order (200)
     3. for each line: GET http://product-service/api/v2/products/{id}   (circuit breaker, 1s connect / 2s read timeout)
        → exists? enough stock? price comes from the catalogue, never from the client
     4. total = Σ unitPrice × qty in BigDecimal (OrderPricingCalculator)
     5. ONE MongoDB write: the order document INCLUDING an "ORDER_PLACED" event in its outbox array
     6. 201 Created + Location
   OutboxRelay (every second):
     for orders with pending events: send to Kafka (key = orderId, acks=all, idempotent producer),
     then atomically $pull the event from the order's outbox
   Kafka topic order-events (3 partitions):
     ├─ group product-service      → StockService: marker insert (idempotency) → atomic $inc stock → evict Redis entry
     └─ group notification-service → insert notification (unique eventId)
```

### Why an outbox instead of "save, then send to Kafka"
Saving to MongoDB and publishing to Kafka are two systems; any code that does one after the other can crash in between and either lose the event (order exists, stock never reduced) or announce an order that was never stored. Mongo transactions need a replica set and still don't span Kafka. Because MongoDB writes to **one document are atomic**, the event is stored *inside* the order document. A relay then delivers it at-least-once. The resilience demo proves it: with Kafka stopped an order is still accepted (201), and the stock is reduced after Kafka comes back.

### Why consumers must be idempotent
At-least-once means duplicates happen (relay crash after send, consumer crash before commit, rebalances). `StockService` writes an `eventId` marker (`_id` = unique) *before* applying the change and removes it again if applying fails; a duplicate delivery hits `DuplicateKeyException` and is skipped. The notification service uses a unique index on `eventId`. A broker-level "exactly once" would not remove the need for this, because the side effects (Mongo writes) live outside Kafka.

### Ordering
Events are keyed by order id, so `ORDER_PLACED` and `ORDER_CANCELLED` of one order land in the same partition and are consumed in order. The relay stops at the first failure of an order's list so a later event never overtakes an earlier one.

## Concurrency control
| Problem | Mechanism |
|---|---|
| Two orders for the last item | Single atomic update `findAndModify`-style: `update({_id, stock >= qty}, {$inc: {stock: -qty}})`; proven by `ProductRepositoryIT` (20 parallel decrements of 5 units → exactly 5 succeed) |
| Two admins editing the same product | `@Version` optimistic locking → HTTP 409 |
| Double-click / client retry on "place order" | `Idempotency-Key` + partial unique index `(userId, idempotencyKey)`; the race of two simultaneous retries is settled by the index |
| Double cancel | `@Version` on the order + status check (second one → 409) |
| Duplicate registration | Existence check **and** unique indexes (the index wins the race) |

## Failure modes and what the system does
| Failure | Behaviour | Verified by |
|---|---|---|
| product-service down | order-service circuit breaker opens; orders fail fast with 503 problem+json; reads of own orders and notifications keep working; recovers automatically | `scripts/resilience-demo.sh` |
| Kafka down | orders still accepted (outbox); product producer failures are logged, never fail an HTTP request; delivery resumes on recovery | `scripts/resilience-demo.sh`, `OutboxRelayTest` |
| Redis down | cache errors are logged and skipped (`LoggingCacheErrorHandler`), reads fall back to MongoDB; the rate limiter fails open (logins keep working) | `scripts/resilience-demo.sh` step 5 |
| Poison message on Kafka | 3 retries 1 s apart, then parked on `order-events.DLT`; deserialisation errors skip retries | `KafkaConfig` |
| Slow dependency | connect 1 s / read 2 s timeouts + 3 s time limiter, so threads are not held hostage | `ClientConfig` |
| Service starts before its dependency | JWKS is fetched lazily and cached; Eureka/LB caches refresh every 5 s (dev) | observed during smoke runs |
| Eureka registration lag after a restart | gateway answers 503 problem+json until the instance is visible | `GatewayErrorHandler` |

## Event contracts
Events are plain JSON without Java type headers. Each consumer owns its own record class and ignores unknown fields (tolerant reader), so a producer can **add** fields freely; renaming or removing a field would be a breaking change and needs a new event version. `OrderEventWireFormatTest` (embedded Kafka) pins the exact wire format, `OrderEventListenerTest` in both consumers deserialises the same JSON.

## Decisions worth knowing (short ADRs)
1. **URI versioning** over header/media-type versioning: visible in logs and caches, trivial to route at the gateway, easy to explain to clients. Cost: URLs change on a major version.
2. **Database per service, no shared model classes**: duplication of a few record classes is cheaper than coupling release cycles.
3. **Outbox in the aggregate** instead of Mongo transactions or CDC: works on a standalone MongoDB; at-least-once is acceptable because consumers are idempotent. A CDC tool (Debezium) would replace the polling relay at higher volume.
4. **Gateway *and* services validate JWTs**: a misconfigured route, a debugging tunnel or an internal caller cannot bypass authentication.
5. **Redis (not Caffeine) for the cache**: with several product-service instances a per-JVM cache would serve stale data after an update on another instance.
6. **Virtual threads** (`spring.threads.virtual.enabled`): blocking Mongo/HTTP calls scale to many concurrent requests without a tuned thread pool.
7. **Eureka over Kubernetes DNS** here only because the project must run on a laptop; the `lb://` abstraction would map to Kubernetes Services unchanged.

## Scaling notes
Every service is stateless. Run N instances of any of them; Eureka + client-side load balancing spreads calls. Kafka consumers scale up to the partition count (3). The relay may deliver an event twice when two order-service instances poll the same order; that is harmless by design (add ShedLock to avoid the duplicate work). Mongo indexes are created by Spring at start-up for convenience; in production create them through migrations.
