# rest-sample-cache-reader

[English](https://github.com/esasmer-dou/rest-sample-cache-reader/blob/master/README.md) | [Turkish](https://github.com/esasmer-dou/rest-sample-cache-reader/blob/master/README.tr.md)

Minimal Rust-Java REST sample that serves precomputed Redis JSON through `java-rust-cache`.

This process reads Redis and returns HTTP JSON.

It has no database. It has no scheduler. It has no Java Redis client. It has no Dubbo. Java owns the REST handler. Rust owns HTTP I/O and Redis I/O.

This sample uses `com.reactor:java-rust-cache:0.2.4` and `com.reactor:rust-java-rest:3.2.7`.

## Property Layers

The default `src/main/resources/rust-spring.properties` is the minimum local file. It keeps only the
server port, runtime profile, cache namespace and local Redis address.

Use production settings as an overlay:

```powershell
java "-Dreactor.config.file=src/main/resources/config/production.properties" ...
```

Use advanced tuning only after measuring p99, 503 ratio and RSS:

```powershell
java "-Dreactor.config.file=src/main/resources/config/production.properties;src/main/resources/config/advanced-tuning.properties" ...
```

- `config/production.properties`: required indexes, low-RSS gate, conservative Redis timeouts.
- `config/advanced-tuning.properties`: per-route admission, native trim and projection namespace overrides.
- Environment alternative: `REACTOR_CONFIG_FILE=/app/config/production.properties`.

## Contents

1. [Copy-Paste: Serve The Redis Snapshot Through REST](#copy-paste-serve-the-redis-snapshot-through-rest)
2. [Maven Package Access](#maven-package-access)
3. [Real Scenario](#real-scenario)
4. [Declarative Projection Config](#declarative-projection-config)
5. [Reader TTL And Namespace Recipes](#reader-ttl-and-namespace-recipes)
6. [Endpoints](#endpoints)
7. [Production Redis Topology](#production-redis-topology)
8. [Why This Shape](#why-this-shape)
9. [Main Properties](#main-properties)
10. [Glossary](#glossary)
11. [Production Config Copy](#production-config-copy)

## Copy-Paste: Serve The Redis Snapshot Through REST

In this scenario the customer snapshot already exists in Redis. Run the writer sample once first.

Then run these commands from the `rest-sample-cache-reader` directory:

```powershell
docker start rs-cache-redis-test

$env:GITHUB_PACKAGES_TOKEN="YOUR_TOKEN_WITH_READ_PACKAGES"
mvn -q clean package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"

$cp = Get-Content target\cp.txt
java "-Dserver.port=18080" `
  "-Dreactor.cache.redis.host=127.0.0.1" `
  "-Dreactor.cache.redis.port=16379" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.reader.app.RestSampleCacheReaderApplication
```

Open another terminal and call the endpoints:

```powershell
curl.exe http://127.0.0.1:18080/app/health
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/1
curl.exe "http://127.0.0.1:18080/api/v1/cache/customers/by-customer-no?customerNo=CUST-1002"
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/segments/pilot
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/statuses/active
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/campaigns/retention/candidates
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/meta
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/cache-metrics
```

The handler does not call the database. It calls the cache abstraction. Redis I/O runs in Rust
native code. The HTTP response is returned with `RawResponse.json(bytes)`.

## Maven Package Access

This sample pulls `rust-java-rest` and `java-rust-cache` from GitHub Packages. Maven must authenticate before it can download those packages; this is GitHub Packages' normal access model.

Add a GitHub token with `read:packages` access to your Maven `settings.xml`. Keep the `<id>` values exactly as shown, because they must match the repository ids in this project's `pom.xml`:

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

Then set `GITHUB_PACKAGES_TOKEN` before running Maven:

```powershell
$env:GITHUB_PACKAGES_TOKEN="YOUR_TOKEN_WITH_READ_PACKAGES"
mvn -q dependency:resolve
```

If Maven returns `401 Unauthorized`, check the token scope, environment variable, and server id matching first.

## Container Runtime Note

For minimal containers, set a writable native extract directory:

```bash
-Dreactor.cache.native.extract-dir=/tmp/java-rust-cache/native
```

The packaged Linux native binary is built on a manylinux2014/glibc 2.17 baseline. It is intended to run on common glibc-based images including CentOS 8, UBI 8/9, Ubuntu/Jammy, and Semeru/OpenJ9. If you use a custom native build, build it on the oldest Linux base your platform supports.

## Real Scenario

Use this sample for read-heavy API pods where the response can be served from a precomputed Redis read model.

Typical flow:

1. `rest-sample-cache-writer` writes a new versioned snapshot.
2. This reader receives HTTP requests.
3. It calls one high-level cache method such as `customer(id)` or `campaignCandidates("retention")`.
4. The cache library resolves the current version in the matching projection namespace.
5. The handler returns `RawResponse.json(bytes)` without rebuilding a Java DTO graph.

The reader intentionally uses the same projection split as the writer:

| Endpoint family | Reader namespace | Writer property that must match |
|---|---|---|
| Customer detail and customer number lookup | `sample.cache.customer.detail.namespace` | `sample.writer.detail.namespace` |
| Segment list | `sample.cache.customer.segment.namespace` | `sample.writer.segment.namespace` |
| Status list | `sample.cache.customer.status.namespace` | `sample.writer.status.namespace` |
| Campaign candidates | `sample.cache.customer.campaign.namespace` | `sample.writer.campaign.namespace` |
| Metadata | `sample.cache.customer.meta.namespace` | `sample.writer.meta.namespace` |

Keep one namespace per projection when TTLs differ. Do not point all endpoints to the same namespace if the writer publishes separate projection snapshots.

`sample.cache.customer.projections` controls which projection readers are created. If the writer publishes only `detail,campaign`, keep the reader aligned:

```properties
sample.cache.customer.projections=detail,campaign
```

This reduces reader setup without code changes. If an endpoint is still called for a projection that is not configured, the reader returns a controlled cache-not-ready/miss response. In production, keep the endpoint set and projection set aligned with the same use case.

## Declarative Projection Config

Projection reader config is resolved by `java-rust-cache`:

```java
List<CacheReaderProjectionSettings> projections =
        CacheReaderProjectionSettings.resolveAll(properties, "sample.cache.customer");

VersionedJsonProjectionReaders readers =
        VersionedJsonProjectionReaders.create(
                cache,
                projections,
                properties.getLong("sample.cache.customer.version-cache-ms"));
```

The library resolves:

- active projection names from `sample.cache.customer.projections`
- projection-specific namespaces such as `sample.cache.customer.detail.namespace`
- base namespace expansion such as `sample.cache.customer.namespace=crm.customer`

Your service still decides which endpoint reads which projection:

```java
public CacheReadResult campaignCandidates(String campaign) {
    return readers.getIndex("campaign", "campaign", campaign);
}
```

The process bootstrap is also explicit but short:

```java
RestApplication.builder()
    .module(context -> {
        CacheProperties properties = CacheProperties.from(context.properties());
        RustCache cache = context.manage(RustCaches.create(properties.asProperties()));
        CustomerCacheService service = new CustomerCacheService(cache, properties);
        context.handlers(new HealthHandler(), new CustomerCacheHandler(service));
    })
    .start();
```

`context.manage(...)` makes the REST lifecycle own the Redis client. It closes the client during
normal shutdown and if startup fails after the client has been created.

BEST: use the library to keep config parsing identical to the writer. Keep endpoint behavior and
miss handling explicit in the REST service.

## Reader TTL And Namespace Recipes

The reader does not set Redis data TTL. TTL is decided by the writer. The reader must use the same namespace names and tune only how long it caches the current-version pointer with `sample.cache.customer.version-cache-ms`.

### Scenario: Use The Writer Base Namespace Override

If the writer runs with this override:

```powershell
java "-Dsample.writer.namespace=crm.customer.prod" `
  "-Dsample.writer.cache-ttl-ms=900000" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.writer.app.RestSampleCacheWriterApplication
```

Start the reader with the same base namespace:

```powershell
java "-Dsample.cache.customer.namespace=crm.customer.prod" `
  "-Dsample.cache.customer.version-cache-ms=1000" `
  "-Dreactor.cache.redis.port=16379" `
  "-Dserver.port=18080" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.reader.app.RestSampleCacheReaderApplication
```

The reader derives `crm.customer.prod.detail`, `crm.customer.prod.segment`, `crm.customer.prod.status`, `crm.customer.prod.campaign`, and `crm.customer.prod.meta`. This is the simplest deployment shape when all projection names share one base.

### Scenario: Explicit Production Namespaces

Use this when teams own projections separately or when you want names that do not follow `<base>.<projection>`.

```yaml
env:
  - name: SAMPLE_CACHE_CUSTOMER_DETAIL_NAMESPACE
    value: "crm.customer.detail"
  - name: SAMPLE_CACHE_CUSTOMER_SEGMENT_NAMESPACE
    value: "crm.customer.segment"
  - name: SAMPLE_CACHE_CUSTOMER_STATUS_NAMESPACE
    value: "crm.customer.status"
  - name: SAMPLE_CACHE_CUSTOMER_CAMPAIGN_NAMESPACE
    value: "crm.customer.campaign"
  - name: SAMPLE_CACHE_CUSTOMER_META_NAMESPACE
    value: "crm.customer.meta"
  - name: SAMPLE_CACHE_CUSTOMER_VERSION_CACHE_MS
    value: "1000"
```

Operational effect: endpoint routing is explicit. The trade-off is more config surface. Use this for mature production deployments where cache ownership is clear.

### Scenario: Campaign Must Show New Snapshot Quickly

If the writer publishes campaign data every `30000 ms` with short campaign TTL, keep the reader version pointer cache low enough to observe the new `current` key quickly.

```properties
sample.cache.customer.version-cache-ms=250
```

Operational effect: fresh publishes become visible faster. The trade-off is more Redis `current` pointer reads. Use this only for endpoints where freshness matters more than a tiny Redis-read reduction.

### Scenario: Very Hot Read Path

If the endpoint receives high traffic and a few seconds of publish visibility delay is acceptable, raise the pointer cache.

```properties
sample.cache.customer.version-cache-ms=5000
```

Operational effect: fewer Redis `current` pointer reads on hot endpoints. The trade-off is that a newly published version can be observed up to about `5000 ms` later by that JVM.

### Cache Miss Checklist

If an endpoint returns `customer_cache_not_ready` or another cache miss code:

```bash
redis-cli keys 'crm.customer.detail:*'
redis-cli get crm.customer.detail:current
redis-cli pttl crm.customer.detail:current
```

Check these first: writer ran successfully, writer and reader namespace values match, TTL has not expired, and the reader points to the same Redis topology.

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

## Production Redis Topology

Local development uses standalone Redis because it is easy to run. Production read pods should normally use Sentinel or Cluster.

Use Sentinel when Redis has one writable primary and failover is handled by Sentinel:

```yaml
env:
  - name: REACTOR_CACHE_REDIS_TOPOLOGY
    value: "sentinel"
  - name: REACTOR_CACHE_REDIS_NODES
    value: "redis-sentinel-0:26379,redis-sentinel-1:26379,redis-sentinel-2:26379"
  - name: REACTOR_CACHE_REDIS_SENTINEL_MASTER_NAME
    value: "mymaster"
  - name: REACTOR_CACHE_REDIS_READ_CONNECTIONS
    value: "2"
  - name: REACTOR_CACHE_REDIS_MAX_READ_INFLIGHT
    value: "128"
```

Use Cluster when Redis data is sharded across nodes:

```yaml
env:
  - name: REACTOR_CACHE_REDIS_TOPOLOGY
    value: "cluster"
  - name: REACTOR_CACHE_REDIS_NODES
    value: "redis-cluster-0:6379,redis-cluster-1:6379,redis-cluster-2:6379"
  - name: REACTOR_CACHE_REDIS_CLUSTER_MAX_REDIRECTS
    value: "5"
  - name: REACTOR_CACHE_REDIS_TOPOLOGY_REFRESH_MS
    value: "30000"
```

For Cluster, keep `reactor.cache.redis.database=0`. If related keys must stay on the same Redis slot, design keys with hash tags such as `customer:{1001}:profile` and `customer:{1001}:orders`.

## Why This Shape

| Choice | Benefit | Trade-off |
|---|---|---|
| `RawResponse.json(bytes)` | Avoids DTO rebuild and JSON serialization on every request | Writer must publish valid JSON |
| Versioned snapshot | Readers never see half-written data | Requires writer publish flow |
| Route admission | Protects low-memory pod under bursts | Saturated routes can return controlled `503` |
| No Java Redis client | Lower classpath and native I/O in Rust | Redis feature set is intentionally minimal |

## Main Properties

| Property | Default | What it does | When to change |
|---|---:|---|---|
| `reactor.runtime.profile` | `micro-rest` | Starts the low-memory REST profile. | Keep for Redis-backed read APIs. |
| `sample.cache.customer.namespace` | `crm.customer` | Base namespace for all projections. | Set when writer uses a different base namespace. |
| `sample.cache.customer.projections` | `detail,segment,status,campaign,meta` | Selects projection readers created at startup. | Narrow it when this reader only serves selected read models. |
| `sample.cache.customer.detail.namespace` | `crm.customer.detail` | Reads customer detail data. | Must match `sample.writer.detail.namespace`. |
| `sample.cache.customer.segment.namespace` | `crm.customer.segment` | Reads segment list data. | Must match `sample.writer.segment.namespace`. |
| `sample.cache.customer.status.namespace` | `crm.customer.status` | Reads status list data. | Must match `sample.writer.status.namespace`. |
| `sample.cache.customer.campaign.namespace` | `crm.customer.campaign` | Reads campaign candidate data. | Must match `sample.writer.campaign.namespace`. |
| `sample.cache.customer.meta.namespace` | `crm.customer.meta` | Reads snapshot metadata. | Must match `sample.writer.meta.namespace`. |
| `sample.cache.customer.version-cache-ms` | `1000` | Caches the Redis current-version pointer in Java memory. | Lower for faster publish visibility. Raise for very hot reads. |
| `reactor.cache.redis.read-connections` | `2` | Opens native Redis read connections. | Increase only if Redis read latency is proven. |
| `reactor.cache.redis.max-read-inflight` | `128` | Bounds concurrent Redis reads. | Lower for memory-first pods. |
| `reactor.cache.redis.topology` | `standalone` | Selects Redis mode. | Use `sentinel` or `cluster` in production. |
| `reactor.cache.redis.nodes` | empty | Lists Sentinel or Cluster nodes. | Set when topology is `sentinel` or `cluster`. |
| `reactor.cache.redis.sentinel.master-name` | empty | Names the Sentinel master. | Required for Sentinel. |
| `reactor.cache.redis.sentinel.master-check-ms` | `1000` | Checks Sentinel master changes. | Lower only if measured recovery is too slow. |
| `reactor.rust.jni.workers` | `1` | Runs Java REST handlers. | Keep low for precomputed JSON reads. |
| `reactor.rust.route-admission.*` | route-specific | Limits hot endpoints. | Tune a route before raising global queues. |

## Glossary

| Term | Meaning |
|---|---|
| TTL | Time to live. The writer sets it on Redis keys. |
| Version cache | A short Java-side cache of the Redis `current` pointer. It is not Redis TTL. |
| Projection | A ready read model for one endpoint family. |
| Namespace | Redis key prefix for one projection. |
| RawResponse | A response that sends prepared JSON bytes without rebuilding DTOs. |
| Cache miss | Redis does not have the requested data. |
| p99 | The slowest 1 percent latency line. If p99 rises, tail latency is bad. |
| 503 | Controlled overload response. It protects the pod instead of growing queues. |
| Sentinel | Redis high-availability mode with primary failover. |
| Cluster | Redis sharding mode. |

## Production Config Copy

The default `src/main/resources/rust-spring.properties` stays easy to run on a developer machine. For Kubernetes or container production, use `src/main/resources/config/production.properties` as an overlay with `-Dreactor.config.file=...`. That file requires generated component/route indexes, disables runtime classpath scan fallback, enables the footprint gate in `enforce` mode and keeps native pools small by default.
