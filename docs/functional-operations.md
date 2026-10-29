# Freshness And Failure Policy

[English](functional-operations.md) | [Turkish](functional-operations.tr.md)

These examples belong to released sample `0.8.0`, cache `0.9.0` and REST/platform
`4.8.0` (optional agent `0.7.1`). Use the aligned published packages and native files.

## Feature And Run Map

| Feature / executable example | Select and run | Boundary / errors |
| --- | --- | --- |
| Generated projection reader and Java HTTP handlers | No Maven profile; [Quick Start](../README.md#quick-start) | APT generates wiring, not business logic; Redis-only by default |
| Versioned detail/segment L1 and `CacheReadValue` | No Maven profile; [L1 overlay command](../README.md#optional-l1-reads) | Per-replica bounded memory; no slab or snapshot wrapper on the default value path |
| Freshness and common errors | External properties recipe below; normal reader launch | Opt-in; see the HTTP table below |
| Public catalog policies | `-Ppublic-edge`; [run guide](public-edge.md) | Separate catalog application; use its admission/error policy |
| Binary native view | `-Pnative-view`; [run guide](../NATIVE_VIEW.md) | Separate source set, not a Redis projection replacement |
| Outbound HTTP / native HS256 | `-Poutbound`; [run guide](../OUTBOUND.md) | Separate application; bounded retry/circuit policy, not a writer retry policy |
| Public-key JWT / trace propagation | `-Pjwt-security` or `-Ptrace-propagation`; [JWT](../JWT_SECURITY.md), [tracing](../TRACE_PROPAGATION.md) run guides | Isolated examples; authentication/tracing is not enabled on default cache routes |
| Shared slab / optional FFM | `-Pshared-slab`; [run guide](../SHARED_SLAB.md) | Separate raw Redis example; JNI on OpenJ9, optional qualified HotSpot FFM; storage costs are opt-in |

Select one isolated Maven profile at a time and follow its guide's configuration/JVM
arguments. These are source-backed examples, not features switched on by a normal launch.
The [service contract](../src/main/java/com/reactor/sample/cache/reader/service/CustomerCacheService.java)
declares value/result/snapshot APIs. The [current handler](../src/main/java/com/reactor/sample/cache/reader/handler/CustomerCacheHandler.java)
uses value APIs for ID/segment; explicit snapshot ownership is an alternative, not its default implementation.

## HTTP Error Map

| Condition | Default `sample.errors.common-contract=false` | Opt-in `sample.errors.common-contract=true` |
| --- | --- | --- |
| No published snapshot | Legacy `404 customer_cache_not_ready` | `503` common cache-not-ready error |
| Missing item in a fresh snapshot | Endpoint-specific `404` | `404` common cache-miss error |
| Redis dependency failure | Existing runtime exception handling; no sample common mapper | `CacheFailureHandler` maps `RedisCacheException` to `503` |
| Snapshot exceeds configured age | No age rejection by default (`max-age-ms=0`) | With the recipe below: `503`; allowed stale success adds `X-Cache-Stale: true` |

The flag selects adapters/error mapping; `reactor.rust.errors.format=problem-details`
selects the common error representation. Neither setting enables L1 or changes writer TTL.
Age enforcement is a separate setting, not implicitly enabled by the common error flag.

## Customer Details Must Be Fresh

Add to your external properties file:

```properties
sample.errors.common-contract=true
reactor.rust.errors.format=problem-details
sample.cache.customer.detail.max-age-ms=120000
sample.cache.customer.detail.serve-stale=false
```

The default age limit is zero and the common error contract is off: existing behavior is unchanged.
These settings reject detail snapshots older than two minutes with HTTP 503.
A missing customer in a fresh snapshot returns 404. Redis failure returns 503.
The JSON body is not parsed again to decide its age.
Age starts when the writer begins building the snapshot.
Keep its refresh interval plus build duration comfortably below this bound.

For a catalogue that may temporarily show old data, change
`sample.cache.customer.detail.serve-stale=true` in this recipe.
Successful stale responses have `X-Cache-Stale: true` with the common error adapter.
Expired Redis data still cannot be served.

Each projection may have a separate limit. A root-level
`sample.cache.customer.max-age-ms` supplies the shared limit.
Specific runtime overrides win, then global runtime overrides, then specific file values.
Do not change writer TTL and reader age independently without checking their relationship.
Snapshot age, L1 entry age and the cached version pointer (`version-cache-ms`) are
different limits. L1 does not make Redis expiry/deletion immediately visible on every hit.
