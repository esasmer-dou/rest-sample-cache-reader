# Call A Stock Service And Protect Account APIs

[English](OUTBOUND.md) | [Turkish](OUTBOUND.tr.md)

Build this optional sample profile from the source checkout; its application is
not in the default published `0.8.0` reader JAR. It uses generated HTTP clients,
a circuit breaker and a native HS256 filter. The published `4.8.0` native package
already includes `outbound-http` and `edge-jwt`; no native rebuild is needed just
to include those features. The normal Redis reader is unchanged. Do not combine
the `outbound`, `native-view` and default application builds.

## What Runs Where?

| Operation | Owner |
| --- | --- |
| Stock business method and response choice | Java |
| Generated client and per-method circuit decision | Java, without a dynamic proxy |
| Outbound HTTP/1 sockets and connection reuse | Separate Rust Tokio runtime |
| Completion, decoding and application continuations | Bounded Java completion worker |
| Optional rejection of invalid HS256 tokens | Rust, before the JNI queue |
| Valid identity, roles and business authorization | Existing Java security starter |

There is no Redis or database call in this build. Native HTTP currently accepts
`http://` only. Choose `reactor.http-client.transport=jdk` for HTTPS, proxies or
HTTP/2. A native HTTPS URL fails at startup; it is never silently downgraded.

## Build With Matching Dependencies

Resolve the matching published `4.8.0` core, platform and starters with Maven,
then build this sample profile. Use their matching native library, which already
contains `outbound-http,edge-jwt`. A custom native build is only needed if you
deliberately select an artifact without the required features.
See [native HTTP build requirements](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-http-client/NATIVE-INTEGRATION.md).

```sh
mvn -Poutbound -DskipTests package
```

The result goes to `target-outbound`, not the default Redis reader's `target`.
The compiler generates the clients and startup index; no runtime scanning or
reflection is needed for this example.

## Run The Local Upstream

Open one terminal in this repository. The following Linux example uses an
absolute library path. Replace it with the matching DLL path on Windows.

```sh
SAMPLE_API_ENABLED=false SAMPLE_OUTBOUND_ENABLED=false \
SAMPLE_BACKEND_ENABLED=true REACTOR_SECURITY_ENABLED=false SERVER_PORT=8082 \
mvn -Poutbound -Drust.lib.path=/opt/native/librust_hyper.so exec:java
```

The same entry point starts only `StockBackend`. Unused client factories are not
created. The backend exposes small JSON, larger JSON and a deliberate HTTP 500.

## Run The REST Application

Use a separate terminal. This secret is **only for this local example**. Supply a
high-entropy secret from your deployment's Secret in real environments.

```sh
export REACTOR_SECURITY_JWT_HMAC_SECRET='test-only-012345678901234567890123456789'
export SAMPLE_STOCK_BASE_URL=http://127.0.0.1:8082
mvn -Poutbound -Drust.lib.path=/opt/native/librust_hyper.so exec:java
```

```sh
curl -i http://127.0.0.1:8080/api/v1/account/health
curl -i http://127.0.0.1:8080/api/v1/stock/small
curl -i http://127.0.0.1:8080/api/v1/stock/heavy
curl -i http://127.0.0.1:8080/api/v1/stock/error
curl -i http://127.0.0.1:8080/api/v1/stock/error
```

The first error call reaches the upstream and returns an error. An immediate
second call is rejected with 503 while that method's circuit is open. Small/heavy
methods have independent breakers. The example's short recovery interval makes
this visible; choose a recovery interval suitable for your real dependency.

The controller returns `CompletionStage<RawResponse>`. The JNI worker starts the
call but does not block on the socket. Responses still become Java `byte[]` and
`RawResponse`; this is not a zero-copy response-handle example.

## Enable Native Token Rejection

Set `SAMPLE_EDGE_JWT_ENABLED=true` before starting the REST application. Startup
explicitly installs the generated `AuthHandler_EdgeJwtPolicies` before routes.
Without this setting, authentication stays in Java.

```sh
export SAMPLE_EDGE_JWT_ENABLED=true
mvn -Poutbound -Drust.lib.path=/opt/native/librust_hyper.so exec:java
```

In a third terminal, use the same test secret to create a local test token:

```sh
export REACTOR_SECURITY_JWT_HMAC_SECRET='test-only-012345678901234567890123456789'
TOKEN=$(mvn -q -Poutbound exec:java \
  -Dexec.mainClass=com.reactor.sample.outbound.TokenTool -Dexec.args=valid)
curl -i -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/v1/account/me
curl -i -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/v1/account/admin
curl -i -H 'Authorization: Bearer invalid' http://127.0.0.1:8080/api/v1/account/me
```

The valid reader gets 200 from `/me` and 403 from `/admin`. The invalid token gets
401 before Java. `TokenTool expired` produces a signed expired test token;
`TokenTool admin` produces an admin test token. This tool is not an identity server.
Keep valid-request validation and roles in Java. Never turn a protected route into
a public edge-cache route to make a benchmark faster.

With the generated installer, the built-in HS256 guard uses Rust's scoped token
proof instead of verifying the same signature twice. Java still checks expiry
at guard execution, identity and roles. Configuration drift fails startup; forged
headers cannot supply a proof. `/api/v1/account/async-me` demonstrates the same
guard on a `CompletionStage<RawResponse>` route. The proof is not propagated to
later application callbacks. Custom Java verifiers retain their original checks.

The private metrics endpoint exposes
`reactor_native_edge_jwt_signature_checks_total`,
`reactor_native_edge_jwt_proof_accepted_total` and
`reactor_native_edge_jwt_proof_rejected_total`.
A generated guard that accepts the native proof increments that counter, even
when a later role check returns 403. It is not an authorization-success count. Signature
checks need not match request counts because the bounded digest cache is reused.
Unexpected proof rejection deserves investigation; these counters are not user IDs.

## Response Body Ownership

Native HTTP returns one owned body array per call. Completion metadata is separate;
Java does not unpack the body from another full response array. Existing `byte[]`,
String and DTO decoders work as before. Use the matching native binary from the
same source set; the startup probe checks the split-completion symbol.

The sample's `ProtectedStockClient.small()` and `heavy()` methods return only
the body. The native client does not create an extra `HttpClientResponse` or
body-mapping future on this path. It also avoids turning unused response headers
into Java objects. HTTP header validation and size limits still apply.

If you need status or response headers, declare
`CompletionStage<HttpClientResponse<byte[]>>` instead. That path preserves
headers. Both forms return an independently owned `byte[]`; business logic stays
in Java. This does not remove the JNI body copy entirely.

The completion worker starts parked. After a response, it uses the existing
native wait for a short activity gap; a new reply wakes it immediately. It parks
indefinitely again when genuinely idle. This adds no fixed request delay, new
timer or thread. No extra property is required.

The small and heavy methods have no parameters. Their generated native clients
prepare fixed URIs and headers once at startup, for both plain `StockClient` and
protected `ProtectedStockClient`. Responses are not retained; every call reaches
the stock service. Methods with parameters or contextual header providers use
dynamic preparation. A previous request's `traceparent` or user header is not
reused for another call. No new annotation or property is required.

## Check Path Parameters

The isolated sample also includes synchronous and asynchronous path examples:

```sh
curl 'http://127.0.0.1:8080/api/v1/account/path/alpha/beta'
curl 'http://127.0.0.1:8080/api/v1/account/async-path/%C4%B0zmir/%C3%87a%C4%9Fr%C4%B1'
```

The first returns `alpha|beta`. The second returns `İzmir|Çağrı` as UTF-8 text.
Both use ordinary `@PathVariable` parameters. The async method returns a
`CompletionStage<RawResponse>`. The JNI queue carries the already encoded path
text, rather than an inline eight-pair container. Parameterless jobs carry an
empty string with no path-buffer allocation. No application setting is needed.

## Choose Settings By Need

| Need | Complete setting and effect |
| --- | --- |
| Native HTTP without a breaker | `sample.outbound.breaker.enabled=false`; only `StockClient` is created |
| Dependency fault isolation | `sample.outbound.breaker.enabled=true`; only `ProtectedStockClient` is created |
| HTTPS upstream | `reactor.http-client.transport=jdk`; native outbound workers are not created |
| Invalid HS256 traffic | `sample.edge.jwt.enabled=true`; requires the matching native feature and Java security |
| Inspect native counters locally | `sample.diagnostics.enabled=true`; enables `/api/v1/account/metrics`; do not expose it publicly |

The source file `src/outbound/resources/rust-spring.properties` shows all example
defaults. Start with `micro-rest`, nine logical call slots, eight connections and
one completion worker. One slot covers a still-running completion; its resources
cannot be freed before application continuations finish. The stock route admits eight operations and
waits at most 125 ms for admission. Overload can therefore return 503 by design.
Increasing a queue is not a substitute for upstream capacity.

For a measured c256 burst, the comparison recipe overrides
`reactor.rust.jni.queue-capacity=512` **equally for every transport**. It retains
one JNI worker, one CPU and a 128 MiB application limit. The ordinary `micro-rest`
default remains 128. Measure p99 and RSS before adopting that recipe; do not copy
it merely to hide errors. Keep request timeouts and bounded response sizes.

The capacity comparison also uses
`reactor.rust.route-budget.stock.route-admission.queue-timeout-ms=1000` equally
for both transports. This is separate from the 125 ms fail-fast example default.
It allows queued bursts to wait; it does not promise 125 ms tail latency.

The native 3 MiB byte budget limits transport reservations, not total container
RSS. See the [HTTP client guide](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-http-client/README.md)
and [HS256 guide](https://github.com/esasmer-dou/rust-java-rest/blob/master/NATIVE_JWT.md)
for limits, cancellation, startup errors and unsupported features.
