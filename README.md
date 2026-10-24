# Rate Limiter

Framework-agnostic Java rate-limiting library with a Redis backend and a Spring Boot starter.

> Work in progress. See `CLAUDE.md` for design decisions and milestones.

## Build

```sh
./mvnw verify
```

Requires Java 21+.

## Modules

| Module | Purpose |
|---|---|
| `ratelimiter-core` | API, store SPI, algorithms, in-memory store. Zero runtime dependencies. |

## Usage

TODO (plain-Java example first, then Spring).

## Architecture

TODO

## Algorithms

- Token bucket: [low-level design](docs/lld/token-bucket.md)

## Benchmarks

TODO

## Failure scenarios

TODO

## Known limitations

TODO
