# Public-key JWT And A Small Token Cache

[English](JWT_SECURITY.md) | [Turkish](JWT_SECURITY.tr.md)

This isolated sample profile uses the published REST/security 4.8.0 artifacts.
The sample sources are in this repository, not inside the framework JAR. This
profile describes this checkout; it is not a claim that these additions shipped
in the published sample 0.8.0 JAR. Build the selected profile below. The
default Redis reader and its HS256 settings do not change. See the bilingual
[feature/consumer map](https://github.com/esasmer-dou/rust-java-rest/blob/master/docs/feature-examples.md).

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

Run the following in Bash from the current `rest-sample-cache-reader` checkout.
Prerequisites: full JDK 21 (including `jdk.crypto.ec`), Maven 3.9+, curl, a free
8080 port and authenticated Maven access to the aligned REST/platform 4.8.0 set.
For changed framework sources, install the matching artifacts first;
source installation is not a prerequisite for using the published packages.

```sh
set -eu
mvn -B -ntp -Pjwt-security clean package dependency:copy-dependencies \
  -DskipTests -DincludeScope=runtime "-DoutputDirectory=$PWD/target-jwt-security/lib"
```

This profile uses REST and security only. It does not start Redis, Dubbo or an
outbound HTTP client. Its service-provider file belongs only to this profile.

For a custom `jlink` runtime, include `jdk.crypto.ec` for P-256/Ed25519 support.
Missing algorithms fail startup; the sample never falls back to HS256.

The example loads three **X.509 DER public-key** files from a trusted directory:
`RS256.der`, `ES256.der` and `EdDSA.der`. `kid` must equal the corresponding name.
For an actual service, keep only the trusted algorithms/keys in your startup
provider. See [the single-key provider example](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-security/README.md).

The [JDK-only development issuer](https://github.com/esasmer-dou/rust-java-rest/blob/master/examples/tools/AsymmetricJwtDemo.java)
generates RSA-2048, P-256 and Ed25519 keys, then signs actual tokens. No OpenSSL,
Node or runtime dependency is needed. It deliberately refuses an existing key
directory. Private keys stay outside the checkout and the REST public-key directory.
On POSIX they are owner-only; on Windows restrict the temporary directory ACL to
the issuer account before use. This is not a production identity provider.

```sh
JWT_TOOL=../rust-java-rest/examples/tools/AsymmetricJwtDemo.java
JWT_WORK=$(mktemp -d)
java "$JWT_TOOL" keys "$JWT_WORK/issuer"
java "-Dsample.jwt.public-key-directory=$JWT_WORK/issuer/public" \
  -Dsample.jwt.cache.enabled=true \
  -cp 'target-jwt-security/rest-sample-cache-reader-0.8.0.jar:target-jwt-security/lib/*' \
  com.reactor.sample.jwt.JwtApplication >"$JWT_WORK/server.log" 2>&1 &
JWT_PID=$!
trap 'kill "$JWT_PID" 2>/dev/null || true' EXIT
curl --fail --retry 20 --retry-connrefused --retry-delay 1 --retry-max-time 45 \
  --max-time 2 http://localhost:8080/api/v1/jwt/health

for ALG in RS256 ES256 EdDSA; do
  ACCESS_TOKEN=$(java "$JWT_TOOL" token "$JWT_WORK/issuer" "$ALG" reader)
  curl --fail --max-time 5 -H "Authorization: Bearer $ACCESS_TOKEN" \
    http://localhost:8080/api/v1/jwt/me
done
READER_TOKEN=$(java "$JWT_TOOL" token "$JWT_WORK/issuer" RS256 reader)
ADMIN_TOKEN=$(java "$JWT_TOOL" token "$JWT_WORK/issuer" ES256 admin)
curl --fail --max-time 5 -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/jwt/admin
test "$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $READER_TOKEN" http://localhost:8080/api/v1/jwt/admin)" = 403
for CASE in expired wrong-issuer wrong-audience wrong-kid; do
  BAD_TOKEN=$(java "$JWT_TOOL" token "$JWT_WORK/issuer" RS256 "$CASE")
  test "$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $BAD_TOKEN" http://localhost:8080/api/v1/jwt/me)" = 401
done
test "$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' \
  http://localhost:8080/api/v1/jwt/me)" = 401
unset ACCESS_TOKEN READER_TOKEN ADMIN_TOKEN BAD_TOKEN
kill "$JWT_PID"
wait "$JWT_PID" || true
trap - EXIT
```

Use the Linux classpath separator above; Windows uses `;`. The helper emits only
the token on stdout. `alg` and `kid` are exactly `RS256`, `ES256` or `EdDSA`;
claims are `iss=sample-issuer`, `aud=sample-api`, `sub=sample-user`,
`roles=["reader"]` (admin adds `admin`), `iat=now`, `exp=now+300` seconds.
ES256 uses JWS's 64-byte P1363 `r||s`, not DER. Never log tokens or use shell tracing.
After stopping the server, remove the disposable `$JWT_WORK` directory including
its private keys. Mount/copy only `issuer/public` into a REST image, never `issuer`.

### Errors And Helper Checks

| Symptom | Action |
| --- | --- |
| Startup cannot read keys | Set an absolute public directory; all three X.509 DER files must exist, each 1..4096 bytes. PEM and PKCS8 are not public-key inputs. |
| Algorithm unavailable | Use a full JDK 21 / include `jdk.crypto.ec` in jlink; do not fall back to HS256. |
| Connection refused / readiness fails | Inspect `$JWT_WORK/server.log`, port 8080 and matching Java/native versions; do not retry another server on the same port. |
| Valid test token returns 401 | Issue it again after startup (five-minute TTL), check clock, matching public keys, `alg`/`kid`, issuer/audience. |
| Reader on `/admin` returns 403 | Expected authorization result; use the admin scenario to obtain 200. |
| 503 | Bounded crypto/admission capacity is exhausted; this is not a bad-token 401. |

Run these independent signature/claim/negative-input checks with a full JDK 21:

```sh
mkdir -p target-jwt-security/helper-checks
javac -d target-jwt-security/helper-checks \
  ../rust-java-rest/examples/tools/AsymmetricJwtDemo.java \
  ../rust-java-rest/examples/tools/AsymmetricJwtDemoChecks.java
java -cp target-jwt-security/helper-checks AsymmetricJwtDemoChecks
```

These checks do not replace service HTTP/security or paired-container acceptance.

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
