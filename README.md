# rest-sample-cache-reader

[English](README.md) | [Turkish](README.tr.md)

Minimal Rust-Java REST sample that serves precomputed Redis JSON through `java-rust-cache`.

There is no database connection, no scheduler, no Java Redis client, and no Dubbo in this process. Java owns the REST business handler shape; Rust owns HTTP I/O and Redis I/O.

This sample is wired to `com.reactor:java-rust-cache:0.2.1` and `com.reactor:rust-java-rest:3.2.4`. Keep these versions aligned because Cluster uses Redis native ABI version `2` and Sentinel master failover refresh uses ABI version `3`.

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

| Property | Default | Use when |
|---|---:|---|
| `reactor.runtime.profile` | `micro-rest` | Low-RSS REST profile for cache-backed reads. |
| `sample.cache.customer.namespace` | `crm.customer` | Must match writer namespace. |
| `sample.cache.customer.version-cache-ms` | `1000` | How long the reader keeps the current snapshot pointer in Java memory. Lower for faster publish visibility; raise for very hot read paths. |
| `reactor.cache.redis.read-connections` | `2` | Increase only if Redis read latency is proven bottleneck. |
| `reactor.cache.redis.max-read-inflight` | `128` | Bounds concurrent Redis reads. Lower for memory-first pods. |
| `reactor.cache.redis.topology` | `standalone` | Use `sentinel` or `cluster` for production HA/sharding. |
| `reactor.cache.redis.nodes` | empty | Sentinel node list or Cluster startup node list. |
| `reactor.cache.redis.sentinel.master-name` | empty | Required only for Sentinel. |
| `reactor.cache.redis.sentinel.master-check-ms` | `1000` | How often Sentinel mode checks whether the master changed after failover. Lower for faster recovery; keep measured. |
| `reactor.rust.jni.workers` | `1` | Good starting point for precomputed JSON reads. |
| `reactor.rust.route-admission.*` | route-specific | Tune hot endpoints without increasing global queues. |

## Production Config Copy

The default `src/main/resources/rust-spring.properties` stays easy to run on a developer machine. For Kubernetes or container production, start from `src/main/resources/rust-spring.production.properties` instead. That file requires generated component/route indexes, disables runtime classpath scan fallback, enables the footprint gate in `enforce` mode and keeps native pools small by default.
