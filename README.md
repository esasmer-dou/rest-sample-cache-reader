# rest-sample-cache-reader

[English](README.md) | [Turkish](README.tr.md)

Minimal Rust-Java REST sample that serves precomputed Redis JSON through `java-rust-cache`.

There is no database connection, no scheduler, no Java Redis client, and no Dubbo in this process. Java owns the REST business handler shape; Rust owns HTTP I/O and Redis I/O.

This sample is wired to `com.reactor:java-rust-cache:0.1.0-rc3`. The cache dependency includes the matching Windows/Linux native Redis bridge; when `rust-java-rest` is on the classpath, the same native bridge is reused.

## Maven Package Access

This sample pulls `rust-java-rest` and `java-rust-cache` from GitHub Packages. Add a GitHub token with `read:packages` access to your Maven `settings.xml`:

```xml
<servers>
  <server>
    <id>github-rust-java-rest</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
</servers>
```

Then set `GITHUB_PACKAGES_TOKEN` before running Maven.

## Real Scenario

Use this sample for read-heavy API pods where the response can be served from a precomputed Redis read model.

Typical flow:

1. `rest-sample-cache-writer` writes a new versioned snapshot.
2. This reader receives HTTP requests.
3. It calls one high-level cache method such as `customer(id)` or `campaignCandidates("retention")`.
4. The cache library resolves current version, Redis key, and miss handling.
5. The handler returns `RawResponse.json(bytes)` without rebuilding a Java DTO graph.

## Endpoints

Start the reader on `18080` for local testing, then use:

```bash
curl http://127.0.0.1:18080/app/health
curl http://127.0.0.1:18080/api/v1/cache/customers/1
curl "http://127.0.0.1:18080/api/v1/cache/customers/by-customer-no?customerNo=CUST-1002"
curl http://127.0.0.1:18080/api/v1/cache/customers/segments/pilot
curl http://127.0.0.1:18080/api/v1/cache/customers/statuses/active
curl http://127.0.0.1:18080/api/v1/cache/customers/campaigns/retention/candidates
curl http://127.0.0.1:18080/api/v1/cache/customers/meta
curl http://127.0.0.1:18080/api/v1/cache/customers/cache-metrics
```

## Run Locally

First run `rest-sample-cache-writer` once so Redis contains a snapshot.

Then start this service:

```powershell
mvn -q clean package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"
$cp = Get-Content target\cp.txt
java "-Dreactor.cache.redis.port=16379" `
  "-Dserver.port=18080" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.reader.app.RestSampleCacheReaderApplication
```

## Why This Shape

| Choice | Benefit | Trade-off |
|---|---|---|
| `RawResponse.json(bytes)` | Avoids DTO rebuild and JSON serialization on every request | Writer must publish valid JSON |
| Versioned snapshot | Readers never see half-written data | Requires writer publish flow |
| Route admission | Protects low-memory pod under bursts | Saturated routes can return controlled `503` |
| No Java Redis client | Lower classpath and native I/O in Rust | Redis feature set is intentionally minimal |

## Main Properties

| Property | Default | Use when |
|---|---:|---|
| `reactor.runtime.profile` | `micro-rest` | Low-RSS REST profile for cache-backed reads. |
| `sample.cache.customer.namespace` | `crm.customer` | Must match writer namespace. |
| `sample.cache.customer.version-cache-ms` | `1000` | How long the reader keeps the current snapshot pointer in Java memory. Lower for faster publish visibility; raise for very hot read paths. |
| `reactor.cache.redis.read-connections` | `2` | Increase only if Redis read latency is proven bottleneck. |
| `reactor.cache.redis.max-read-inflight` | `128` | Bounds concurrent Redis reads. Lower for memory-first pods. |
| `reactor.rust.jni.workers` | `1` | Good starting point for precomputed JSON reads. |
| `reactor.rust.route-admission.*` | route-specific | Tune hot endpoints without increasing global queues. |
