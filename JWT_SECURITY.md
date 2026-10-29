# Public-key JWT And A Small Token Cache

[English](JWT_SECURITY.md) | [Turkish](JWT_SECURITY.tr.md)

This is an isolated **source-checkout** example. It is not part of the published
4.8.0 package. The default Redis reader and its HS256 settings do not change.

## Choose The Right Path

| Your service | Start with |
| --- | --- |
| Already uses HS256 | Keep the existing security properties |
| Receives tokens signed by an identity provider | Pin that provider's public key and algorithm |
| Receives the same token many times | Enable the bounded positive-token cache |
| Usually receives a different token on every call | Leave the cache off; measure before enabling |

Rust still serves HTTP. Java keeps the authentication guard, business handler and
JCA public-key verification. This is **not** the native `@RustEdgeJwt` HS256 path.
There is no remote JWKS fetch, extra executor or refresh thread.

## Run The Example

Build the current framework/platform sources first. Then:

```sh
mvn -Pjwt-security clean package -DskipTests
```

This profile uses REST and security only. It does not start Redis, Dubbo or an
outbound HTTP client. Its service-provider file belongs only to this profile.

For a custom `jlink` runtime, include `jdk.crypto.ec` for P-256/Ed25519 support.
Missing algorithms fail startup; the sample never falls back to HS256.

The example loads three **X.509 DER public-key** files from a trusted directory:
`RS256.der`, `ES256.der` and `EdDSA.der`. `kid` must equal the corresponding name.
For an actual service, keep only the trusted algorithms/keys in your startup
provider. See [the single-key provider example](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-security/README.md).

For a local identity-provider fixture, public keys can be exported as follows:

```sh
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out issuer-rsa.pem
openssl pkey -in issuer-rsa.pem -pubout -outform DER -out keys/RS256.der
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:prime256v1 -out issuer-ec.pem
openssl pkey -in issuer-ec.pem -pubout -outform DER -out keys/ES256.der
openssl genpkey -algorithm ED25519 -out issuer-ed.pem
openssl pkey -in issuer-ed.pem -pubout -outform DER -out keys/EdDSA.der
```

Keep private keys with the issuer, never inside this REST image. These commands
do not issue access tokens. Your issuer must sign a compact JWT with matching
`alg`/`kid`, issuer `sample-issuer`, audience `sample-api`, subject `sample-user`,
role `reader`, and a future `exp`.

```sh
mvn -Pjwt-security exec:java \
  -Dsample.jwt.public-key-directory=/absolute/path/keys \
  -Dsample.jwt.cache.enabled=true
```

```sh
curl http://localhost:8080/api/v1/jwt/health
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/api/v1/jwt/me
```

`/health` is public. `/me` requires `reader` and checks the Java identity context.
`/admin` requires `admin`; a reader-only token receives `403`.
Invalid/missing/expired credentials receive `401`.

## Configure The Cache

These keys belong to this **sample provider**. They are not automatic global
framework settings. System properties override environment and packaged defaults
through the framework configuration loader.

| Key | Default | What it changes |
| --- | --- | --- |
| `sample.jwt.public-key-directory` | `keys` | Startup-only public-key directory; each file is limited to 4096 bytes |
| `sample.jwt.issuer` | `sample-issuer` | Required issuer claim |
| `sample.jwt.audience` | `sample-api` | Required audience claim |
| `sample.jwt.workers` | `1` | Bounded JCA workers and, when enabled, separate hash workers; power of two |
| `sample.jwt.cache.enabled` | `false` | Explicitly enables verified-claims reuse |
| `sample.jwt.cache.slots` | `256` | Maximum retained positive entries; power of two, two-way sets |
| `sample.jwt.cache.max-bytes` | `262144` | Conservative entry/claims retention budget; **not** RSS or heap limit |
| `sample.diagnostics.enabled` | `false` | Adds `/api/v1/jwt/metrics`; keep it off public ingress |

At one JNI worker, start with one crypto/hash worker. A larger worker setting is
not a thread count and does not increase the number of JNI request workers.
Worker exhaustion returns a dependency `503`, not an authentication `401`.
Do not increase queues to conceal overload.

For repeated-token traffic, the first request verifies the signature and claims.
Later hits hash the **whole** token, match all 32 SHA-256 bytes, and recheck current
`exp`/`nbf`. They skip signature and JSON parsing. Token text is not retained.
Concurrent cold misses can verify more than once. Failures are never cached.

With `sample.jwt.cache.max-bytes=0`, the wrapper bypasses caching. It allocates no
hash workers or slot table. Its capacity is zero and every call still verifies.
With `sample.jwt.cache.enabled=false`, even that wrapper is absent.

Budget/slot pressure permits full verification without retaining a new entry.
Expired idle entries remain bounded until a matching lookup, eviction or explicit
`clear()` removes them. There is no background expiry thread. Eviction/clear
releases references; it does not promise an immediate operating-system RSS drop.

## Kubernetes Example

Mount the public-key directory read-only. The issuer's private keys are not mounted.

```yaml
env:
  - name: SAMPLE_JWT_PUBLIC_KEY_DIRECTORY
    value: /etc/jwt-public
  - name: SAMPLE_JWT_ISSUER
    value: sample-issuer
  - name: SAMPLE_JWT_AUDIENCE
    value: sample-api
  - name: SAMPLE_JWT_WORKERS
    value: "1"
  - name: SAMPLE_JWT_CACHE_ENABLED
    value: "true"
  - name: SAMPLE_JWT_CACHE_SLOTS
    value: "256"
  - name: SAMPLE_JWT_CACHE_MAX_BYTES
    value: "262144"
volumeMounts:
  - name: jwt-public
    mountPath: /etc/jwt-public
    readOnly: true
```

The pod also needs a `jwt-public` volume referencing your public-key ConfigMap or
Secret. Keys are loaded once. Rotate by replacing the verifier/cache together,
normally with a rolling deployment. `clear()` is not distributed token revocation.
This sample requires expiration and allows no clock skew. Keep node clocks healthy.

## Diagnose Before Tuning

| Observation | Check or change |
| --- | --- |
| Every call is `401` | Check key, `kid`, pinned algorithm, issuer, audience and expiration; do not disable checks |
| Valid reader gets `403` at `/admin` | Expected role policy; issue the appropriate role instead of weakening the route |
| Hit counter stays low | Token reuse is low or the working set exceeds the slots; try cache off before increasing memory |
| Signature count rises only on misses | Expected; the cache is bypassing expensive verification on hits |
| `503` under concurrent verification | Check JNI/crypto-worker bounds and service capacity; do not relabel it `401` |
| RSS exceeds the entry budget | Expected distinction: JCA/JIT/heap/fixed worker state are outside the entry budget |

Watch useful HTTP200 RPS, p99, process RSS **and** container memory together.
Compare cache on/off under the same algorithm, token set, CPU and memory limits.
Do not compare RSA throughput to HS256 and label the difference a cache regression.
Never log bearer tokens or expose internal metrics on public ingress.
