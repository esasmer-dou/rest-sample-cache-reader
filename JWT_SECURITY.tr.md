# Açık Anahtarla JWT ve Sınırlı Token Önbelleği

[English](JWT_SECURITY.md) | [Turkish](JWT_SECURITY.tr.md)

Bu örnek yalnız **güncel kaynak kodla** çalışır. Yayımlanan 4.8.0 paketinde henüz
yoktur. Varsayılan Redis reader ve mevcut HS256 ayarları değişmez.

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

Önce güncel framework/platform kaynaklarını derleyin. Ardından:

```sh
mvn -Pjwt-security clean package -DskipTests
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

Yerel kimlik sunucusu denemesinde açık anahtarları şu şekilde çıkarabilirsiniz:

```sh
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out issuer-rsa.pem
openssl pkey -in issuer-rsa.pem -pubout -outform DER -out keys/RS256.der
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:prime256v1 -out issuer-ec.pem
openssl pkey -in issuer-ec.pem -pubout -outform DER -out keys/ES256.der
openssl genpkey -algorithm ED25519 -out issuer-ed.pem
openssl pkey -in issuer-ed.pem -pubout -outform DER -out keys/EdDSA.der
```

Özel anahtarlar kimlik sunucusunda kalır. REST image'ına koymayın. Bu komutlar
token üretmez. Kimlik sunucusu eşleşen `alg`/`kid` ile JWT imzalamalıdır.
Örnek için issuer `sample-issuer`, audience `sample-api`, subject `sample-user`,
rol `reader` ve gelecekte bir `exp` gerekir.

```sh
mvn -Pjwt-security exec:java \
  -Dsample.jwt.public-key-directory=/absolute/path/keys \
  -Dsample.jwt.cache.enabled=true
```

```sh
curl http://localhost:8080/api/v1/jwt/health
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/api/v1/jwt/me
```

`/health` herkese açıktır. `/me`, `reader` rolünü ister ve Java kimlik bağlamını
kontrol eder. `/admin`, `admin` rolünü ister. Yalnız reader rolü olan token burada
`403` alır. Eksik, geçersiz veya süresi dolmuş token `401` alır.

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
