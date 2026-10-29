# Raw Redis JSON With A Shared Slab

[English](SHARED_SLAB.md) | [Türkçe](SHARED_SLAB.tr.md)

Use this isolated source example when an existing Redis key already contains the
JSON you want to return. Java selects the key and response. Rust reads Redis and
holds the payload until the managed HTTP writer finishes. No payload `byte[]`
is created in Java on the FFM path. This is not the default versioned reader.
Build this checkout's profile; its unchanged 0.8.0 POM version is not evidence
that these additions were shipped in an earlier sample release.

Slab means fixed-size native storage. A lease is temporary ownership of one slot.
Closing the snapshot returns that slot for reuse. JNI is the existing Java/Rust
bridge; FFM is Java's optional foreign-function API. OpenJ9 uses the JNI slab path.

## Run The Example

Run from this sample directory, with JDK 21, Maven 3.9+,
curl, redis-cli, a disposable Redis at 127.0.0.1:6379 and port 8080 free.
Resolve the published 4.8.0 `rust-java-ffm-fast`/`rust-java-ffm-cache` modules
with authenticated Maven access. Their publication does not imply that an
arbitrary packaged native library has slab/FFM capabilities. Use a matching
qualified native file; if its required capability is absent, build that feature
from the matching source revision. See [selection/read costs](https://github.com/esasmer-dou/rust-java-rest/blob/master/docs/feature-examples.md).
The published `4.8.0` native package already includes `shared-slab` for OpenJ9 JNI,
retaining the normal panic-abort engine; this path does not require a native rebuild.
Only HotSpot FFM needs a qualified build with `--profile release-ffm --features ffm-fast`.
Abort-profile FFM capabilities are zero; it must never activate the foreign API.
No existing native ABI number changes.

For an explicit native source build, from the sibling `rust-spring`
directory run `cargo build --release --features shared-slab` (OpenJ9 JNI), or
`cargo build --profile release-ffm --features ffm-fast` (HotSpot FFM). Use the
resulting `target/release/librust_hyper.so` or `target/release-ffm/librust_hyper.so`
respectively in `rust.lib.path`; `/app/native/librust_hyper.so` below is the
deployment location of that selected file, not a path created by Maven.

```sh
mvn -B -ntp -Pshared-slab clean package dependency:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory=target-shared-slab/lib
redis-cli SET phase54:catalog:small '{"stock":42}'
redis-cli SET phase54:catalog:heavy '{"description":"Warehouse stock","stock":42}'

SAMPLE_REDIS_HOST=127.0.0.1 SAMPLE_REDIS_PORT=6379 \
SAMPLE_SLAB_ENABLED=true SAMPLE_SLAB_REQUIRED=true \
java -Drust.lib.path=/app/native/librust_hyper.so \
  -cp 'target-shared-slab/rest-sample-cache-reader-0.8.0.jar:target-shared-slab/lib/*' \
  com.reactor.sample.slab.SlabApplication
```

This is the Linux/macOS classpath separator. On Windows, use `;` instead of `:`.

```sh
curl --fail http://localhost:8080/api/v1/catalog/health
curl --fail http://localhost:8080/api/v1/catalog/small
curl --fail http://localhost:8080/api/v1/catalog/heavy
```

Use a disposable Redis instance for the SET examples. They deliberately use raw
keys, not the writer sample's versioned namespace. Do not point these examples at
another application's keys. The running reader has read-only access and cannot SET.

## Configure It

Values are read from this profile's `rust-spring.properties`. Environment or JVM
property overrides use the framework's normal configuration rules.

| Property | Default | Purpose |
| --- | --- | --- |
| `sample.redis.host` | `127.0.0.1` | Redis address; use its Service DNS in Kubernetes |
| `sample.redis.port` | `6379` | Redis TCP port |
| `sample.redis.max-response-bytes` | `65536` | RESP wire-byte budget, including framing; slab upper bound |
| `sample.slab.enabled` | `false` | Request native slab at startup; false keeps ordinary JNI reads |
| `sample.slab.ffm` | `false` | False uses JNI shared slab when enabled; true explicitly requests FFM |
| `sample.slab.required` | `false` | If true, refuse startup when the selected slab transport cannot activate |
| `sample.slab.slots` | `4` | Native payload slots; four reserve 256 KiB |
| `sample.slab.max-key-bytes` | `256` | Maximum UTF-8 bytes in a key |
| `sample.slab.key.small` | `phase54:catalog:small` | Key read by `/small` |
| `sample.slab.key.heavy` | `phase54:catalog:heavy` | Key read by `/heavy` |
| `sample.diagnostics.enabled` | `false` | Enable `/internal/slab/metrics` for a protected test network |
| `reactor.runtime.profile` | `micro-rest` | Existing bounded REST runtime profile |

Kubernetes environment example for a matching native file at `/app/native`:

```yaml
env:
  - name: SAMPLE_REDIS_HOST
    value: redis.cache.svc.cluster.local
  - name: SAMPLE_SLAB_ENABLED
    value: "true"
  - name: SAMPLE_SLAB_REQUIRED
    value: "true"
  - name: SAMPLE_SLAB_SLOTS
    value: "4"
  - name: JAVA_TOOL_OPTIONS
    value: "-Drust.lib.path=/app/native/librust_hyper.so"
```

Merge these JVM flags with your existing bounded JVM options; do not erase them.

At65536 wire bytes, a five-digit Redis bulk reply can carry65526 value bytes;
its header and trailer use10 bytes. The slab has64KiB storage, but the configured
wire limit still applies. Use ordinary JNI with a larger budget for larger values.
The YAML does not mount the library or define pod limits. Supply both through
your deployment. Tested runtime compatibility is Semeru/OpenJ9 Java 21 Linux x64.
Do not assume another JVM, OS or native build has the same performance result.

## Add Your Own Handler

`CatalogReader` creates one `SharedSlabCacheReader` at startup and closes it at
shutdown. `CatalogHandler` returns one line:

```java
return CacheResponses.ownedProblem(reader.read(key));
```

The managed writer closes the snapshot on success or failure. Growing its direct
output buffer does not repeat Redis GET. Avoid converting the payload to String,
record or `byte[]` just to return the same JSON. Never discard an unclosed snapshot;
there is no per-snapshot Cleaner. Such a bug can exhaust the fixed slot capacity.

Selection is startup-only. Missing preview/native access, incompatible native
symbols or a response limit above 64 KiB selects JNI when FFM is optional. Strict
mode stops startup instead. Active FFM errors never replay through JNI. The sample
maps cache failures to 503. Do not increase slots without measuring the memory
cost. Four slots are bounded storage, not a promise of unlimited concurrency.

The default enabled slab uses JNI, not FFM. OpenJ9 21's FFM downcall scope
retention was reproduced under load, so FFM selection is blocked before linker
initialization. JNI shared slab keeps the same Rust GET/lease/copy implementation
without that VM path. For qualified HotSpot Java 21 only, set `sample.slab.ffm=true`
and add `--enable-preview --enable-native-access=ALL-UNNAMED`. Never use internal-JDK
reflection, per-call thread replacement or a larger heap to hide the retention.

Slab memory has process lifetime. Closing the reader releases the client and
detaches its input wrappers but does not return the slab to the OS. Snapshot and
FFM segment objects still allocate. This is neither end-to-end zero allocation nor
zero copy. Defaults for the normal sample and versioned snapshot consistency are
unchanged. [Module details](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/ffm-cache/README.md).

## Native Storage

Requests keep the existing `reader.read(key)` and managed response. Every request
still performs GET. No response cache, DTO graph or Java payload array is added.
No new key-preparation API or Java-side pool is required.

The JNI path passes your existing String to Rust. Rust validates and encodes
the key in bounded stack storage; Java key buffers/wrappers are not needed.
Keep calling `reader.read(key)`, including for Unicode keys. Invalid UTF-16 or
an oversized UTF-8 key is rejected before GET, not truncated.

Standalone Redis replies go directly into the leased slot. Their intermediate
payload Vec and its copy disappear. Sentinel/Cluster retain their existing
recovery path and temporary Vec. HTTP output still copies from the owned slot.

The reader does not change your JIT, heap, allocator thresholds or thread settings.
Measure steady and peak RSS separately. Removing payload allocations does not
remove JVM compilation peaks or make the whole process zero-copy.
