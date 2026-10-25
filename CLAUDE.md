# Distributed Rate Limiter: Project Context

Context and decisions from the planning session (2026-10-24). Read this before working on the repo.

## Working style

- Raghav writes the implementation himself. Claude's role is to review, point out bugs and design problems, and push back with targeted questions. Do not write full implementations unless explicitly asked.
- Be blunt and direct. No padding or encouragement.
- Prefer structured, actionable guidance over theory.
- When a design decision is unclear, ask one focused question rather than deciding silently.

## Goal

A resume project: a **framework-agnostic Java rate-limiting library** with a Redis backend (for the distributed part) and a Spring Boot starter on top. It must be finished, tested, and measured. Depth and defensible design decisions matter more than feature count.

## Core decisions (already made)

1. **It is a library, not an application.** Plain Java (Maven multi-module). Spring Boot is only a thin add-on layer.
2. **Single system first, distributed later.** Phase 1 is the in-memory, single-JVM limiter in `ratelimiter-core`. Phase 2 adds Redis as a new store implementation. Phase 2 must not require changes to `core` beyond adding the new store.
3. **Framework-agnostic core.** `ratelimiter-core` has zero compile dependencies. It knows nothing about HTTP, Spring, Redis, or metrics libraries.
4. **The store executes the algorithm natively** (in-memory: Java CAS; Redis: Lua script, one round trip). The math is deliberately written twice, and a shared **contract test suite** keeps the implementations in sync. Rejected alternative: algorithm in Java with Redis as dumb state + CAS retries (2+ round trips, retry storms on hot keys).
5. **Sync public API, async-capable store SPI.** `RateLimiter.tryAcquire` is synchronous for v1. The store SPI should return `CompletionStage<Decision>` so the Redis (Lettuce) store can be async; a sync facade over async is trivial, the reverse is a rewrite.
6. **Token bucket first.** Other algorithms come later.
7. **Timeline: done properly over 3–4 weekends**, not rushed into one. "Done" means every design decision is tested, measured, or written down as a tradeoff in the README.

## Module layout

Create modules only when needed. Start with `ratelimiter-core` alone.

```
rate-limiter/                        parent POM, Java 21, dependencyManagement
├── ratelimiter-core/                API + SPIs + algorithms + in-memory store. ZERO deps.
├── ratelimiter-redis/               RateLimitStore impl: Lettuce + Lua          (Phase 2)
├── ratelimiter-micrometer/          RateLimitListener impl                      (later)
├── ratelimiter-servlet/             plain Servlet Filter, no Spring             (later)
├── ratelimiter-spring-boot-starter/ auto-config, @RateLimit, 429 handling       (later)
└── demo-app/                        Spring Boot app for load tests; not part of the library
```

Dependency direction: everything points toward `core`. `core` depends on nothing. If `core` ever imports Spring, Redis, or Micrometer, the design is broken.

What each module does:
- **core**: decides allow/reject for a key. `RateLimiter`, `Rule`, `Decision`, `RateLimitStore`, `TimeSource`, `RateLimitListener`, `Algorithm`, `InMemoryTokenBucketStore`. On its own it is a working single-JVM limiter.
- **redis**: `RedisTokenBucketStore` + `token_bucket.lua`. Shared state across app instances; this is what makes it distributed.
- **spring-boot-starter**: `@RateLimit`, aspect/filter that calls `RateLimiter` and returns 429 with headers, auto-configuration, `@ConfigurationProperties`. No algorithm logic.
- **demo-app**: endpoints using `@RateLimit`; run 2–3 instances + Redis in Docker Compose for load tests.

## Core API shapes (implemented in M1–M2; LLD in `docs/lld/token-bucket.md`)

```java
interface RateLimiter       { Decision tryAcquire(String key, long permits); }
interface RateLimitStore    { CompletionStage<Decision> evaluate(String key, Rule rule, long permits, long currentTimeNanos);
                              Set<Algorithm> supportedAlgorithms(); }
interface TimeSource        { long currentTimeNanos(); }   // monotonic, not wall clock; fake in tests, Redis TIME later
interface RateLimitListener { void onAllowed(String key, Rule r); void onRejected(String key, Rule r); void onFallback(String key, Throwable t); }   // not built yet
final class Rule            // capacity, tokensPerRefill, refillIntervalNanos; validated in private ctor
                            // behind Rule.tokenBucket(capacity, tokensPerRefill, Duration refillInterval)
final class Decision        // allowed, remaining, limit, retryAfterNanos, timeUntilFullNanos; Decision.NEVER = Long.MAX_VALUE
enum Algorithm { TOKEN_BUCKET, ... }
```

Value objects are plain `final` classes with `private final` fields, not records (Raghav's choice).
Names carry their unit as a suffix (`...Nanos`) wherever the type is a bare `long`.

Extracting a key from an HTTP request is the adapter's job, not core's.

## Phase 1 rules (so Phase 2 doesn't need a rewrite)

1. All bucket state lives in the store. `RateLimiter` keeps no maps or counters of its own.
2. The store interface takes key + rule + permits + current time and returns a `Decision`. No `AtomicLong`, `ConcurrentHashMap`, or `synchronized` in the interface; those stay inside `InMemoryTokenBucketStore`.
3. Time comes from `TimeSource`, never `System.nanoTime()` directly.
4. `Rule` uses plain numeric time units internally (Lua needs plain numbers).
5. Token math is a small pure function `TokenBucket.compute(state, rule, permits, currentTimeNanos) → (newState, decision)`, easy to port to Lua line by line.
6. Token bucket state per key is two fields: `BucketState(tokens, lastUpdatedNanos)`. In-memory: `AtomicReference<BucketState>` holding one immutable object (packing into one `long` is a later optimization). Two separate atomics race.

Self-test: when Redis is added, if any class in `core` other than a new store needs editing, a rule was broken.

## Milestones

| # | Milestone | Done when |
|---|---|---|
| 1 | Maven skeleton + core API | `mvn verify` passes; core's dependency tree has only test-scoped deps (JUnit 5, AssertJ); a test proves invalid `Rule`s are rejected |
| 2 | Lock-free in-memory token bucket | 100 threads on one key, admitted ≤ capacity; deterministic refill tests with a fake `TimeSource` (no `Thread.sleep`) |
| 3 | Contract test suite | Abstract `RateLimitStoreContractTest`; in-memory store extends it and passes |
| 4 | Redis store (Lua) | Same contract tests pass against real Redis via Testcontainers |
| 5 | Resilience | Timeouts on every Redis call, fail-open/fail-closed per rule, circuit breaker, fallback to local; test that kills Redis mid-run |
| 6 | Second algorithm | Sliding window counter in both stores |
| 7 | Servlet adapter | Works in embedded Jetty without Spring |
| 8 | Spring Boot starter + demo | `@RateLimit` returns 429 with `X-RateLimit-Limit/Remaining/Reset` and `Retry-After`; Docker Compose with multiple instances |
| 9 | Hybrid leasing | Nodes lease token batches from Redis; benchmarked vs Redis-only (latency gain vs over-admission) |
| 10 | Benchmarks + README | p50/p99 overhead, throughput, over-admission %; resume bullet writable |

Don't start a milestone until the previous one meets its "done when". Commit after each milestone.

Suggested pacing: weekend 1 = M1–M2; weekend 2 = M3–M4; weekend 3 = M5 + starter + demo; weekend 4 = multi-instance load test + README. **Minimum resume-ready cut: M1–M5 + Spring starter + one multi-instance benchmark.**

## M1 decisions (made)

- `Rule` representation: capacity + `tokensPerRefill` per `refillInterval` (an exact integer ratio, not a `double` rate).
- Units: `Duration` at the API boundary, `long` nanos internally.
- Validation location: private constructor behind the `Rule.tokenBucket(...)` factory.
- `Decision` contents: `allowed`, `remaining`, `limit`, `retryAfterNanos`, `timeUntilFullNanos`.
- Tokens are `double` (fractional refill; matches Lua numbers). A rejection returns the same `BucketState` instance, so the store skips the write. `permits > capacity` is rejected with `retryAfterNanos = Decision.NEVER`.
- `groupId`: `io.github.raghumhulkoti`.

Decided (2026-10-25): two limiters sharing one store and one key used to share a bucket, because the store keyed only by `key`. Fix: `Rule` gets a validated `name` (non-blank, no `:`), and `DefaultRateLimiter` builds the store key as `rule.name() + ":" + key` before calling the store. Stores stay unaware of rule names, so Redis needs no extra work. Not yet implemented.

## Production features (for later milestones)

**Must-have:** Lua atomicity; single time source (Redis `TIME`); TTL on every key; concurrency tests; fail-open/closed; timeouts; circuit breaker + local fallback; Lettuce connection pooling; 429 + standard headers; flexible keys (user/IP/API key/endpoint/composite); rules in config; multiple rules per request (10/s AND 1000/h); Micrometer metrics (allowed/rejected per rule, Redis latency, fallbacks, breaker state); structured logs; Testcontainers (no mocked Redis); Docker Compose (instances + Redis + Prometheus + Grafana); GitHub Actions CI.

**Differentiators (pick 3–4):** hybrid local/global leasing; lock-free bucket benchmarked with JMH vs `synchronized`; dynamic rule updates via pub/sub; Redis Cluster support with hash tags (`{user:123}`); tiered limits (free/pro/enterprise); shadow mode; hot key handling.

**Skip:** admin UI, Kubernetes/Helm, 5+ algorithms, gRPC, adapters for Quarkus/Micronaut/Vert.x.

Avoid high-cardinality Micrometer tags (e.g. user ID).

## What "distributed" means here

Multiple app instances share one limit because state lives in Redis. It is a **centralized-store** distributed limiter (instances distributed, state centralized), not peer-to-peer or consensus-based. The in-memory store alone is not distributed (3 instances × 100 = 300). Proof requires the load test to run against 2–3 instances and show the combined admitted total stays at the limit. Answer to "Redis is a SPOF": fail-open/closed + local fallback now; replication/Cluster as future work.

## Algorithm animations

`docs/algorithms/` will hold five GIFs (token bucket, leaky bucket, fixed window, sliding window log, sliding window counter), generated by `make_gifs.py` (Python + Pillow). All use the same request stream: arrivals at 1.0, 3.2, 3.4, 3.6, 3.8, 4.1, 4.3, 4.5, 4.7, 4.9, 6.5, 7.5, 9.0 s; token bucket capacity 4, refill 1 token/s, starting full.

**Free test case for M2:** the Java in-memory token bucket must produce the same pattern on that stream: 9 allowed, 4 rejected (rejected at 4.1, 4.5, 4.7, 4.9 s).

## README must include

Architecture diagram; algorithm choices and why; benchmark table (Redis-only vs hybrid: p50/p99 overhead, throughput, over-admission % under contention); failure scenarios tested and results; known limitations; a 10-line plain-Java usage example before any Spring example.

Target resume bullet shape:
> Built a distributed rate limiter (Java, Redis/Lua, Spring Boot starter) with a hybrid local-lease mode that cut per-request overhead from X ms to Y µs at Z% over-admission; supports fail-open/closed fallback and zero-downtime rule updates.

## Interview questions to be ready for

- Why not just use Bucket4j or Resilience4j?
- Who is the user: API gateway, library, or sidecar?
- What accuracy guarantee: "never exceeds N" or "roughly N"?
- Why is `GET` → compute in Java → `SET` racy even though Redis is single-threaded?
- Why must every key a Lua script touches be passed in `KEYS`?
- Fail-closed vs fail-open for a login endpoint vs a search endpoint?
- With 3 nodes each leasing 10 tokens from a 100/s bucket, what is worst-case over-admission?

## Open items from the session

- Raghav to answer the four algorithm self-checks: token-count formula at time `now` without a background thread; why fixed window can admit ~2× at a boundary; how sliding window counter estimates; why token bucket over sliding log for this project.
- Raghav to state the dependency direction between core/redis/starter and what breaks if core depended on redis.
- M1 and M2 are implemented (token bucket, in-memory store, 21 tests). Next action: resolve the key-namespace open item, then M3 (contract test suite).

## Repo state

- Maven skeleton exists: parent POM + `ratelimiter-core`. Build with `./mvnw verify`.
- `ratelimiter-core` enforces zero non-test dependencies via `maven-enforcer-plugin` (`bannedDependencies`). Adding any compile/runtime dependency to core fails the build.
- Base package: `io.github.raghumhulkoti.ratelimiter`.
