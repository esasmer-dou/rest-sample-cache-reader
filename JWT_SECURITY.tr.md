# Açık Anahtarla JWT ve Sınırlı Token Önbelleği

[English](JWT_SECURITY.md) | [Turkish](JWT_SECURITY.tr.md)

Bu ayrı sample profili yayımlanmış REST/security 4.8.0 paketlerini kullanır.
Sample kaynakları bu depodadır; framework JAR'ına dahil değildir. Varsayılan Redis
profili dışındaki eklemeler bu checkout'u anlatır; yayımlanmış sample 0.8.0 JAR'ında
bulundukları iddia edilmez. Aşağıdaki seçili profili kaynaklardan derleyin. Normal
reader ve HS256 ayarları değişmez. [Özellik/tüketici haritası](https://github.com/esasmer-dou/rust-java-rest/blob/master/docs/feature-examples.tr.md).

## Hangi Yolu Seçmeliyim?

| Senaryonuz | Başlangıç seçimi |
| --- | --- |
| Servisiniz zaten HS256 kullanıyor | Mevcut güvenlik property'lerini koruyun |
| Token'ları kimlik sunucusu imzalıyor | Sunucunun açık anahtarını ve algoritmasını sabitleyin |
| Aynı token birçok istekte geliyor | Sınırlı doğrulanmış-token önbelleğini açın |
| Her istekte farklı token geliyor | Önbelleği kapalı tutun; açmadan önce ölçün |

HTTP sunucusu yine Rust'tır. Doğrulama guard'ı, iş mantığı ve JCA açık anahtar
doğrulaması Java'dadır. Bu yol, native `@RustEdgeJwt` HS256 yolu değildir.
Ağdan anahtar arama, yeni executor veya yenileme thread'i başlatılmaz.

## Örneği Çalıştırın

Aşağıdaki komutları güncel checkout'ta Bash ile `rest-sample-cache-reader`
dizininde çalıştırın. Tam JDK 21 (`jdk.crypto.ec` dahil), Maven 3.9+, curl, boş
8080 portu ve hizalı REST/platform 4.8.0 paketlerine Maven erişimi gerekir.
Framework kaynağı değiştiyse eşleşen paketleri önce install edin;
yayımlanmış paketleri kullanmak için kaynak derlemesi zorunlu değildir.

```sh
set -eu
mvn -B -ntp -Pjwt-security clean package dependency:copy-dependencies \
  -DskipTests -DincludeScope=runtime "-DoutputDirectory=$PWD/target-jwt-security/lib"
```

Bu profil yalnız REST ve security kullanır. Redis, Dubbo veya dış HTTP istemcisi
başlatmaz. Service-provider tanımı yalnız bu profile aittir.

Özel `jlink` runtime hazırlıyorsanız P-256/Ed25519 için `jdk.crypto.ec` modülünü
ekleyin. Algoritma yoksa başlangıç durur. Örnek kendiliğinden HS256'ya dönmez.

Örnek, güvenilir bir dizinden üç **X.509 DER açık anahtar** dosyası okur:
`RS256.der`, `ES256.der`, `EdDSA.der`. Token'ın `kid` değeri ilgili adla aynı olmalıdır.
Gerçek servisinizde yalnız güvendiğiniz anahtarları ve algoritmaları tanımlayın.
[Tek anahtarlı provider örneği](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-security/README.tr.md)
bu seçimi gösterir.

[Yalnız JDK kullanan geliştirme issuer'ı](https://github.com/esasmer-dou/rust-java-rest/blob/master/examples/tools/AsymmetricJwtDemo.java)
RSA-2048, P-256 ve Ed25519 anahtarlarını ve gerçek imzalı token'ları üretir.
OpenSSL, Node veya runtime bağımlılığı gerekmez. Var olan anahtar dizinini
üzerine yazmaz. Özel anahtarlar checkout ve REST açık-anahtar dizininin dışında
kalır. POSIX izinleri yalnız sahibine açıktır; Windows'ta geçici dizinin ACL'ini
kullanmadan önce issuer hesabıyla sınırlayın. Bu production kimlik sunucusu değildir.

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

Yukarıdaki classpath ayıracı Linux içindir; Windows'ta `;` kullanın. Yardımcı stdout'a
yalnız token yazar. `alg` ve `kid`, `RS256`, `ES256` veya `EdDSA` olarak birebir
eşleşir. Claim'ler: `iss=sample-issuer`, `aud=sample-api`, `sub=sample-user`,
`roles=["reader"]` (admin senaryosu `admin` ekler), `iat=şimdi`, `exp=şimdi+300`
saniye. ES256, DER yerine JWS'nin 64-byte P1363 `r||s` biçimini kullanır.
Token loglamayın, shell tracing açmayın. Sunucuyu durdurduktan sonra özel
anahtarları içeren geçici `$JWT_WORK` dizinini kaldırın. REST image'ına yalnız
`issuer/public` bağlanır/kopyalanır; `issuer` dizininin tamamı bağlanmaz.

### Hatalar Ve Yardımcı Kontrolleri

| Belirti | Yapılacak işlem |
| --- | --- |
| Başlangıçta anahtar okunamıyor | Mutlak açık-anahtar dizini kullanın; üç X.509 DER dosyası da 1..4096 byte olmalı. PEM ve PKCS8 açık-anahtar girdisi değildir. |
| Algoritma bulunamıyor | Tam JDK 21 kullanın; jlink'e `jdk.crypto.ec` ekleyin. HS256'ya düşmeyin. |
| Bağlantı reddi / readiness hatası | `$JWT_WORK/server.log`, 8080 portu ve Java/native sürüm eşleşmesini kontrol edin; aynı porta ikinci sunucu açmayın. |
| Geçerli test token'ı 401 alıyor | Başlangıçtan sonra yeniden üretin (ömür beş dakika); saati, açık anahtarları, `alg`/`kid`, issuer/audience eşleşmesini kontrol edin. |
| Reader `/admin` üzerinde 403 alıyor | Beklenen yetki sonucudur; 200 için admin senaryosunu kullanın. |
| 503 | Sınırlı crypto/admission kapasitesi dolmuştur; hatalı-token 401 değildir. |

Bağımsız imza/claim/hatalı-girdi kontrollerini tam JDK 21 ile çalıştırın:

```sh
mkdir -p target-jwt-security/helper-checks
javac -d target-jwt-security/helper-checks \
  ../rust-java-rest/examples/tools/AsymmetricJwtDemo.java \
  ../rust-java-rest/examples/tools/AsymmetricJwtDemoChecks.java
java -cp target-jwt-security/helper-checks AsymmetricJwtDemoChecks
```

Bu kontroller servis HTTP/güvenlik veya eşlenmiş container kabulünün yerine geçmez.

## Önbelleği Ayarlayın

Bu key'ler **örneğin provider sınıfına** aittir. Framework'ün kendiliğinden açtığı
genel ayarlar değildir. Framework yapılandırma okuyucusunda `-D` değerleri ortam
değişkenlerinden ve paket içindeki varsayılanlardan önce gelir.

| Key | Varsayılan | Ne işe yarar? |
| --- | --- | --- |
| `sample.jwt.public-key-directory` | `keys` | Başlangıçta okunacak açık anahtar dizini; dosya başına en fazla 4096 byte |
| `sample.jwt.issuer` | `sample-issuer` | Kabul edilen token üreticisi |
| `sample.jwt.audience` | `sample-api` | Token'ın kullanılabileceği hedef servis |
| `sample.jwt.workers` | `1` | Sınırlı JCA ve ayrı hash çalışma alanı sayısı; ikinin kuvveti olmalı |
| `sample.jwt.cache.enabled` | `false` | Doğrulanmış bilgilerin yeniden kullanımını açar |
| `sample.jwt.cache.slots` | `256` | Tutulabilecek olumlu kayıt sayısı; iki yollu sabit tablo |
| `sample.jwt.cache.max-bytes` | `262144` | Kayıt/claim saklama bütçesi; RSS veya heap limiti değildir |
| `sample.diagnostics.enabled` | `false` | `/api/v1/jwt/metrics` ekler; herkese açık ingress'e bağlamayın |

Tek JNI worker ile bir crypto/hash worker iyi başlangıçtır. Bu değer yeni thread
açmaz ve JNI worker sayısını artırmaz. Çalışma alanları dolarsa bağımlılık hatası
olan `503` döner. Bu, kimlik doğrulama hatası `401` değildir. Aşırı yükü gizlemek
için kuyrukları büyütmeyin.

İlk istek imzayı ve claim'leri doğrular. Sonraki eşleşmeler token'ın **tamamını**
hash'ler. SHA-256'nın 32 byte'ı karşılaştırılır. Güncel `exp`/`nbf` tekrar kontrol
edilir. İmza ve JSON çözümleme atlanır. Token metni saklanmaz. Eşzamanlı ilk
istekler birden fazla imza doğrulaması yapabilir. Hatalar önbelleğe alınmaz.

`sample.jwt.cache.max-bytes=0` olduğunda wrapper saklamayı atlar. Hash worker ve
slot tablosu ayırmaz. Kapasite sıfırdır; her çağrı tam doğrulanır.
`sample.jwt.cache.enabled=false` olduğunda wrapper da oluşturulmaz.

Bütçe dolsa bile geçerli token tam doğrulanarak kullanılabilir. Kullanılmayan,
süresi dolmuş kayıtlar eşleşen bir okuma, yer değiştirme veya açık `clear()`
çağrısına kadar sınırlı tabloda kalabilir. Arka plan temizleme thread'i yoktur.
Referansın bırakılması RSS'in hemen işletim sistemine dönmesini garanti etmez.

## Kubernetes Örneği

Açık anahtar dizinini salt okunur bağlayın. Özel anahtarları bağlamayın.

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

Pod'da ayrıca açık anahtar ConfigMap veya Secret'ına bağlı `jwt-public` volume'u
bulunmalıdır. Anahtarlar bir kez okunur. Değiştirmek için doğrulayıcıyı ve
önbelleğini birlikte yenileyin. Genellikle rolling deployment kullanılır.
`clear()`, dağıtık token iptal servisi değildir. Örnek süre sonunu zorunlu tutar
ve saat toleransı tanımaz. Node saatlerini güncel tutun.

## Soruna Göre Doğru Ayarı Bulun

| Gözlem | Kontrol veya çözüm |
| --- | --- |
| Her çağrı `401` | Anahtar, `kid`, algoritma, issuer, audience ve süreyi kontrol edin; denetimleri kapatmayın |
| Reader `/admin` yolunda `403` alıyor | Beklenen rol kuralı; uygun rolü verin, rotayı zayıflatmayın |
| Hit sayısı düşük | Token tekrar kullanımı az veya çalışan veri seti büyük; bütçeyi büyütmeden önce cache kapalı ölçün |
| İmza sayısı yalnız miss'te artıyor | Beklenen davranış; hit'lerde pahalı doğrulama atlanıyor |
| Eşzamanlı yükte `503` | JNI/crypto sınırını ve servis kapasitesini inceleyin; hatayı `401` yapmayın |
| RSS kayıt bütçesinden büyük | JCA, JIT, heap ve sabit worker alanları kayıt bütçesine dahil değildir |

Başarılı HTTP200 RPS, p99, process RSS ve container belleğini birlikte izleyin.
Cache açık/kapalı karşılaştırmasını aynı algoritma, veri seti ve kaynak sınırında
yapın. RSA ile HS256 farkını cache gerilemesi olarak yorumlamayın.
Bearer token'ları loglamayın. İç metrikleri herkese açmayın.
