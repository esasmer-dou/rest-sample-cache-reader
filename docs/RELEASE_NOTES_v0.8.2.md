# rest-sample-cache-reader 0.8.2

[English](RELEASE_NOTES_v0.8.2.md) | [Türkçe](RELEASE_NOTES_v0.8.2.tr.md)

Coordinated update: November 1, 2026.

## Dependencies

Use platform parent `com.reactor:rust-java-platform-parent:4.8.2`, which aligns
cache `0.9.2`. Dependency entries for the existing application POM:

```xml
<dependency>
  <groupId>com.reactor</groupId>
  <artifactId>rust-java-starter-cache-reader</artifactId>
  <version>4.8.2</version>
</dependency>
<dependency>
  <groupId>com.reactor.sample</groupId>
  <artifactId>rust-sample-model</artifactId>
  <version>0.5.2</version>
</dependency>
```

Keep the existing compiler/profile setup. See [Maven access](../README.md#maven-package-access)
for authenticated dependency resolution; rebuild with `mvn -B -ntp -DskipTests clean package`.
Skipping tests is packaging only, not qualification.

## Existing Examples

[CustomerCacheHandler](../src/main/java/com/reactor/sample/cache/reader/handler/CustomerCacheHandler.java)
uses `CacheResponses.reply(..., COMMON_ERRORS)` for both `CacheReadResult` and
`CacheReadValue`. Detail/segment use the latter: Redis-only reads forward bytes;
opt-in L1 hits transfer owned native responses, released after writing or cancellation.
The explicit flag preserves legacy JSON versus problem details. L1 was first
introduced in `0.8.0`; it remains disabled by default and is not a Rust edge bypass.
See the [L1 recipe](../README.md#optional-l1-reads) and [Compose quickstart](../README.md#compose).

## Upgrade And Evidence

No JNI ABI, HTTP route or configuration-key migration is required. Use the packaged
native library from the aligned dependencies, not an older copied SO/DLL.
The six-service Compose smoke is not a performance/soak guarantee; its native
overlay is catalog-only. These notes do not establish publication or full
qualification: use the exact candidate's receipt. Hosted platform compatibility
is separate from EmaTech performance/soak evidence.
