# Performance tuning and bottleneck analysis

## Method (the order matters)
1. **State the goal** (e.g. "catalogue read p99 < 20 ms at 30 concurrent users").
2. **Measure first** with a repeatable load: `perf/load-test.mjs` (closed model, N workers, warm-up excluded, reports rps and p50/p95/p99) plus the server's own metrics (`/actuator/prometheus`: `http_server_requests_seconds` histograms, `cache_gets_total`, Hikari-like pool and JVM metrics).
3. **Find the bottleneck, don't guess**:
   * database → `explain("executionStats")` (index used? docs examined vs returned?),
   * CPU/GC → `jcmd <pid> JFR.start duration=60s filename=rec.jfr` then open in JDK Mission Control; or async-profiler flame graph,
   * threads blocked → `jcmd <pid> Thread.dump_to_file -format=json t.json` (virtual threads show up there),
   * downstream → circuit breaker / client timers, p99 of the call,
   * network/gateway → compare direct-to-service vs through-gateway latency.
4. **Change one thing**, re-measure under identical conditions, keep the change only if the number moves.
5. **Write down the result** (this file) and add a guard where cheap (a test, a bound, an index annotation).

## Test conditions (so the numbers can be judged)
MacBook, macOS, JDK 21 (default JIT, `-Xmx256m` per service), MongoDB 7 / Redis 7 / Kafka in Docker **on the same machine**, load generator on the same machine, 32 concurrent connections with keep-alive, 8 s per run after a 2 s warm-up, 12 products in the catalogue. These are **indicative laptop numbers, not capacity figures**: everything shares CPUs, the data set is tiny, and the network is loopback. Use them for *relative* comparisons.

## Results

### 1. Redis read-through cache (GET `/api/v2/products/{id}`, direct to product-service)
| Run | req/s | p50 | p95 | p99 |
|---|---|---|---|---|
| Cache **on**, cold JVM (first run) | 10,438 | 2.70 ms | 5.99 ms | 8.72 ms |
| Cache **on**, warmed up (2 runs) | 25,093 / 22,642 | 1.16 / 1.25 ms | 2.08 / 2.39 ms | **2.81 / 3.32 ms** |
| Cache **off** (second instance with `SPRING_CACHE_TYPE=none`), cold | 7,496 | 3.69 ms | 7.87 ms | 12.90 ms |
| Cache **off**, warmed up | 15,553 | 1.90 ms | 3.37 ms | **4.40 ms** |

Reading the numbers:
* **JIT warm-up dominates** the first run of *any* configuration (cache off: 7.5k → 15.5k req/s once warm). Comparing a cold run with a warm run would have "proved" a 3.3x speed-up; the honest warm-vs-warm gain here is **≈ 1.45x throughput and ≈ 25% lower p99**.
* The gain is modest because MongoDB runs next to the service and fetching one document by `_id` is already a primary-key lookup. With a database on another host (0.5-2 ms RTT), a larger working set or heavier queries the cache saves proportionally more, and it takes load off MongoDB, which is the shared, hard-to-scale resource.
* Correctness cost of the cache: stale reads are prevented by eviction on update/delete **and** on every stock change (`StockService`), tested in `ProductCachingTest` and the smoke test ("cache evicted: next read shows the new price"). TTL 10 min is the safety net.

### 2. Query shapes (direct, warm)
| Endpoint | Index used (verified with `explain`) | req/s | p50 | p99 |
|---|---|---|---|---|
| `GET /api/v2/products?q=laptop&sort=price,asc` | text index `Product_TextIndex` → `TEXT_MATCH`, then in-memory sort | 7,320 | 4.25 ms | 8.03 ms |
| `GET /api/v2/products?category=electronics&sort=price,asc` | compound `category_price`, 5 docs examined / 5 returned | 9,434 | 3.25 ms | 5.65 ms |
| `GET /api/v1/products` (legacy, `Slice`, no count query) | `_id`/name sort, capped at 100 | 9,692 | 3.12 ms | 6.16 ms |

The search is the heaviest (text scoring + sort + a separate count query for the page envelope). It is the first candidate for caching the *result* of popular queries for a few seconds, or for moving to a search engine, once real traffic shows it matters. That decision needs production data, so it was not done.

### 3. Gateway protection
Hammering a cached read through the gateway at 32 connections: 83,441 requests in 8 s → **400 served (200), 83,041 rejected (429)** in ≈ 2.5 ms p50. The per-user limit (50 req/s, burst 100) is working as designed and the rejection path is very cheap, so a flood costs the platform almost nothing. Login is deliberately expensive (BCrypt cost 12 ≈ 250 ms per attempt), which is exactly why the login route has the strictest per-IP limit (burst 10, 2/s): without it, a handful of clients could pin all CPUs.

## Bottlenecks found by measuring, and what was done
| Finding | Evidence | Fix |
|---|---|---|
| Price-range filter and price sort did a **collection scan** | `explain`: `COLLSCAN` for `{price: {$gte, $lte}}` | `@Indexed` on `price` (harmless at 12 docs, essential at 12 million) |
| Prices would have been stored as strings (range queries and sorting wrong *and* unindexable) | `ProductRepositoryIT.priceSortIsNumeric_notLexicographic` | `Decimal128` converters |
| Unbounded list endpoint | review + test | v1 list capped at 100; `size` ≤ 100; sort fields whitelisted (an arbitrary sort key is an unindexed sort) |
| Needless `COUNT` on the legacy list | code review | `Slice` instead of `Page` |
| Cache metrics invisible, so hit rate could not be measured | missing `cache_gets_total` | `spring.cache.cache-names` + `enable-statistics` |
| Fresh gateway after (re)start returned errors until Eureka caches caught up (35 s default load-balancer cache) | smoke test | 5 s registry fetch and LB cache TTL for dev |

## Design choices that are performance work, not just style
* **Virtual threads** (`spring.threads.virtual.enabled=true`): thread-per-request code scales over blocking Mongo/HTTP calls without sizing pools.
* **Timeouts and circuit breakers** everywhere a call leaves the process: one slow dependency cannot exhaust all threads. Failing fast (`503` in ≈ 0 ms for 8 attempts in the resilience demo) is a performance feature.
* **Asynchronous work off the request path**: stock updates and notifications happen through Kafka, so placing an order does not wait for them.
* **Atomic single-document updates** instead of read-modify-write: no lock contention, no retries.
* **Projection-free but bounded reads**; **keep-alive** everywhere (RestClient, gateway Netty pool).

## What to try next (and how to know if it helped)
1. Run the load test with the **gateway limits raised** to measure the gateway's own overhead (compare direct vs gateway p99).
2. Add **JFR** during a 5-minute soak at 2x the expected peak; look for allocation hot spots (`ProductV2.from`, JSON serialisation) and GC pauses.
3. Tune the **Mongo connection pool** (`maxPoolSize`, `maxWaitTime`) only if `connections.wait` metrics show queueing.
4. Cache search *results* (key = normalised query, TTL 5-30 s) if the text-search p99 becomes the SLO bottleneck.
5. Kafka: raise partitions and consumer concurrency (`spring.kafka.listener.concurrency`) if consumer lag (`kafka-consumer-groups --describe`) grows.
6. Move product search to OpenSearch/Atlas Search when relevance ranking, fuzzy matching or faceting is needed (the MongoDB text index only matches whole words).

## Reproduce
```bash
scripts/start-all.sh
PID=$(curl -s "localhost:8080/api/v2/products?size=1" | jq -r '.content[0].id')
node perf/load-test.mjs "http://localhost:8082/api/v2/products/$PID" --c 32 --d 8          # run it twice: the first run warms the JIT
# cache off for comparison:
SERVER_PORT=8092 EUREKA_CLIENT_ENABLED=false SPRING_CACHE_TYPE=none SEED_DEMO_DATA=false \
  java -Xmx256m -jar product-service/target/product-service-1.0.0.jar &
node perf/load-test.mjs "http://localhost:8092/api/v2/products/$PID" --c 32 --d 8
docker exec shopsphere-mongo mongosh shopsphere_products --eval 'db.products.find({category:"electronics"}).sort({price:1}).explain("executionStats")'
```
