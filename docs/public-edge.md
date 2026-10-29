# Public Catalog Edge Example (Source Preview)

[English](public-edge.md) | [Türkçe](public-edge.tr.md)

Use this example for the same anonymous product JSON shared by every visitor.
Keep customer, tenant and authorization-dependent data in the normal sample.
Build the `public-edge` sample application from this checkout; it is not in the
default published `0.8.0` reader JAR. The matching published REST `4.8.0` and cache
`0.9.0` native packages already include `edge-cache` support. Sample application
availability and native feature availability are separate.

## Choose the Path

| Situation | Choose |
| --- | --- |
| A handler must validate permissions or perform business work | Normal Java route |
| Everyone may read the exact same published catalog snapshot | Optional public edge route |
| Limit calls to an expensive route | `@RustEdgeRateLimit` |
| Limit a user's quota across all replicas | A separate authenticated, distributed quota policy; this limiter is not that policy |

On a cache hit, Rust returns JSON without invoking the Java handler or its guards.
Missing data, a query string or credentials keep the ordinary Java path.
Rate limiting runs before either path. It is shared per route in each replica.
It does not silently retry or queue rejected requests.

## Build and Run

The `public-edge` **Maven profile** compiles only `src/public-edge/java`.
The default build continues to compile `src/main/java`, including the unchanged
customer routes. Do not package both application entry points together.
Outputs are isolated: `target/` for the ordinary application and
`target-public-edge/` for this profile. Switching profiles cannot package stale
customer application classes into the public example.

```sh
mvn -B -ntp clean package dependency:copy-dependencies -Ppublic-edge \
  -DincludeScope=runtime -DoutputDirectory=target-public-edge/dependency
java -Drust.lib.path=/app/native/librust_hyper.so \
  -Dreactor.cache.redis.host=redis \
  -Dsample.public.edge.enabled=true \
  -cp 'target-public-edge/rest-sample-cache-reader-0.8.0.jar:target-public-edge/dependency/*' \
  com.reactor.sample.cache.publiccatalog.PublicCatalogApplication
```

On Windows, use `;` instead of `:` in the classpath and supply the matching DLL.
Use matching published dependencies and a native artifact containing `edge-cache`,
as included in the REST `4.8.0` / cache `0.9.0` packages. A property cannot add
support to a different native file that lacks it. Missing native support fails
startup instead of silently installing a different policy.

`sample.public.edge.enabled=false` is the default. It installs neither native
cache policies nor rate limits. It reads the same catalog through Java and Redis.
`micro-rest` remains the **runtime profile**; it is independent of the Maven profile.

For Kubernetes, build this same profile into your application image. Include its
coordinated edge-enabled native artifact. Then set:

```yaml
env:
  - name: SAMPLE_PUBLIC_EDGE_ENABLED
    value: "true"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-rest"
  - name: REACTOR_CACHE_REDIS_HOST
    value: "redis.cache.svc.cluster.local"
```

Replace the Redis service DNS with your own. No node setting or extra permission
is needed. Set the first value to `false` and restart the pod to keep Java reads.
This is not a live profile switch.

## The Application Code

```java
@EnableRustCache(
    publicEdgePolicies = PublicCatalogHandler.class,
    publicEdgeEnabledProperty = "sample.public.edge.enabled")
@ReactorApplication(scanBasePackages = "com.reactor.sample.cache.publiccatalog")
public final class PublicCatalogApplication {
    public static void main(String[] args) {
        RestApplication.run(PublicCatalogApplication.class, args);
    }
}
```

The cache processor writes direct startup calls to the generated policy class.
It does not scan annotations on requests. Invalid or duplicate policy classes
fail compilation. Cache clients close if policy installation fails.
Without `publicEdgePolicies`, existing generated startup code has no new edge call
or switch. The new annotation itself has source retention.

## Try a Published Product

For a temporary development fixture only, publish the body first, then its pointer:

```sh
version="$(($(date +%s) * 1000))-demo"
redis-cli -h redis SET "public.catalog.product:v:$version:idx:sku:TR-01" \
  '{"sku":"TR-01","name":"İstanbul Çay","details":{"stock":7}}' PX 60000
redis-cli -h redis SET public.catalog.product:current "1|$version" PX 60000
curl -i http://localhost:8080/api/v1/public/catalog/products/TR-01
curl -i http://localhost:8080/api/v1/public/catalog/limited/products/TR-01
curl -i http://localhost:8080/api/v1/public/catalog/rate-check
```

The first URL combines public caching and a generous route limit.
The second has no edge cache and deliberately permits two calls per second,
with a burst of two. A fast burst returns HTTP `429` with `application/problem+json`.
Back off before retrying; do not automatically replay write operations.

Use the versioned, fenced writer for a real publisher. These shell commands are
not a substitute for its ownership, atomic publication and cleanup lifecycle.

## Configuration and Limits

| Setting or rule | What it controls |
| --- | --- |
| `sample.public.edge.enabled` | Startup-only opt-in; default `false` |
| `reactor.cache.redis.access-mode=read-only` | No write pool in the reader |
| `reactor.cache.redis.read-connections=2` | Bounded Redis read connections |
| `reactor.cache.redis.max-read-inflight=8` | Bounded concurrent Redis reads |
| `reactor.cache.redis.max-response-bytes=16384` | Rejects oversized Redis replies before unbounded allocation |
| `sample.public.catalog.max-age-ms=60000` | Java fallback freshness; keep it equal to the annotation |
| `sample.public.catalog.serve-stale=false` | Do not serve expired snapshots |
| Compiled namespace `public.catalog.product` | Redis keys must match the edge annotation and generated projection reader |
| Native edge slab | 32 entries per cached route, at most 8192 payload bytes each |
| Native process edge budget | At most 2 MiB including conservative key/control accounting |
| Policy count | At most 64 policies, including at most six cached routes; space is reserved for registry controls and bounded fetches |
| Concurrent refresh of a version or primary slot | Share one native fetch, wait at most 50 ms, then retain the Java fallback on failure or timeout |
| Cached payload ownership | Prepared on refill and shared by hits. A slow reader prevents overwrite; the slot never detaches into an extra payload allocation |
| In-flight response budget | Held until the last response-byte reference is released, including slow socket writes |
| Pointer and cached-entry visibility | Up to one second; no background polling thread |

Namespace, age and stale policy are part of the compiled edge contract. Do not
override only the Java properties while leaving different annotation values.
Regenerate the application when changing that contract. Larger responses fall
back to Java; the 8 KiB entry limit is not an HTTP response-size limit.

`/api/v1/public/catalog/rate-check` returns a fixed public availability response
directly from Rust. It has a generous native rate limit but no cache or Redis
read, so limiter cost can be measured separately. It does not replace the product API.

This example does not make a private endpoint public automatically. There is no
native JWT implementation in this preview. Authentication-dependent routes must
remain on the Java security path. Performance and memory results depend on the
enabled recipe; an edge cache is not a guarantee of lower whole-process RSS.
