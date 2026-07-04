# rest-sample-cache-reader

[English](README.md) | [Turkish](README.tr.md)

`java-rust-cache` ile Redis'te hazır duran JSON'u servis eden minimum Rust-Java REST örneği.

Bu süreçte DB bağlantısı, scheduler, Java Redis client veya Dubbo yoktur. Java tarafı REST handler
şeklini korur. HTTP I/O ve Redis I/O Rust tarafındadır.

Bu örnek `com.reactor:java-rust-cache:0.2.1` ve `com.reactor:rust-java-rest:3.2.5` ile çalışır.
Bu iki sürümü birlikte kullanın. Cluster Redis native ABI sürümü `2` ister. Sentinel master failover
refresh ise ABI sürümü `3` ister. REST paketi güncel native runtime resource çizgisini taşır.

## Kopyala-Yapıştır: Redis Snapshot'ını REST API ile Oku

Bu senaryoda Redis içinde müşteri snapshot'ı hazırdır. Snapshot'ı hazırlamak için önce
`rest-sample-cache-writer` README dosyasındaki yazma örneğini bir kez çalıştırın.

Sonra bu komutları `rest-sample-cache-reader` dizininde çalıştırın:

```powershell
docker start rs-cache-redis-test

$env:GITHUB_PACKAGES_TOKEN="READ_PACKAGES_YETKILI_TOKEN"
mvn -q clean package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"

$cp = Get-Content target\cp.txt
java "-Dserver.port=18080" `
  "-Dreactor.cache.redis.host=127.0.0.1" `
  "-Dreactor.cache.redis.port=16379" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.reader.app.RestSampleCacheReaderApplication
```

Ayrı bir terminal açın ve endpoint'leri deneyin:

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

Bu örnekte handler DB'ye gitmez. Handler sadece cache abstraction çağırır. Redis I/O Rust native
tarafta çalışır. HTTP response `RawResponse.json(bytes)` ile döner.

## Maven Package Erişimi

Bu örnek `rust-java-rest` ve `java-rust-cache` bağımlılıklarını GitHub Packages üzerinden çeker. Maven bu paketleri indirebilmek için kimlik bilgisi ister; bu GitHub Packages'in normal erişim modelidir.

Maven `settings.xml` dosyana `read:packages` yetkisi olan bir GitHub token eklemelisin. Aşağıdaki `<id>` değerlerini aynı bırak; bu değerler `pom.xml` içindeki repository id'leriyle eşleşmelidir:

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

Maven çalıştırmadan önce `GITHUB_PACKAGES_TOKEN` environment variable olarak verilmelidir:

```powershell
$env:GITHUB_PACKAGES_TOKEN="READ_PACKAGES_YETKILI_TOKEN"
mvn -q dependency:resolve
```

Maven `401 Unauthorized` dönerse önce token'ın `read:packages` yetkisini, environment variable'ın shell tarafından görüldüğünü ve server id eşleşmesini kontrol et.

## Container Runtime Notu

Minimal container image kullanıyorsan native extract dizini yazılabilir olmalı:

```bash
-Dreactor.cache.native.extract-dir=/tmp/java-rust-cache/native
```

Paket içindeki Linux native binary manylinux2014/glibc 2.17 tabanında build edilir. CentOS 8, UBI 8/9, Ubuntu/Jammy ve Semeru/OpenJ9 gibi yaygın glibc tabanlı image’larda çalışması hedeflenir. Custom native build kullanırsan native library’yi platformunun desteklediği en eski Linux base üzerinde build et.

## Gerçek Senaryo

Response’u önceden hazırlanmış Redis read model’den dönebileceğin read-heavy API pod’ları için bu örnek doğru modeldir.

Tipik akış:

1. `rest-sample-cache-writer` yeni bir versioned snapshot yazar.
2. Bu reader HTTP isteğini alır.
3. Handler tek bir high-level metot çağırır: `customer(id)` veya `campaignCandidates("retention")`.
4. Cache library ilgili projection namespace içindeki current version, Redis key ve miss durumunu kendi çözer.
5. Handler `RawResponse.json(bytes)` döner; Java DTO graph tekrar kurulmaz.

Reader, writer ile aynı projection ayrımını kullanır:

| Endpoint grubu | Reader namespace | Eşleşmesi gereken writer property |
|---|---|---|
| Müşteri detayı ve müşteri numarası lookup | `sample.cache.customer.detail.namespace` | `sample.writer.detail.namespace` |
| Segment listesi | `sample.cache.customer.segment.namespace` | `sample.writer.segment.namespace` |
| Status listesi | `sample.cache.customer.status.namespace` | `sample.writer.status.namespace` |
| Kampanya adayları | `sample.cache.customer.campaign.namespace` | `sample.writer.campaign.namespace` |
| Metadata | `sample.cache.customer.meta.namespace` | `sample.writer.meta.namespace` |

TTL değerleri farklıysa her veri tipini ayrı namespace’te tut. Writer ayrı projection snapshot publish ediyorsa reader endpoint’lerini tek namespace’e yönlendirme.

## Reader TTL ve Namespace Reçeteleri

Reader Redis data TTL değerini belirlemez. TTL kararı writer tarafındadır. Reader aynı namespace adlarını kullanmalı ve sadece current-version pointer değerini ne kadar cache’leyeceğini `sample.cache.customer.version-cache-ms` ile ayarlamalıdır.

### Senaryo: Writer Base Namespace Override Kullanıyor

Writer şu şekilde çalışıyorsa:

```powershell
java "-Dsample.writer.namespace=crm.customer.prod" `
  "-Dsample.writer.cache-ttl-ms=900000" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.writer.app.RestSampleCacheWriterApplication
```

Reader aynı base namespace ile başlatılmalıdır:

```powershell
java "-Dsample.cache.customer.namespace=crm.customer.prod" `
  "-Dsample.cache.customer.version-cache-ms=1000" `
  "-Dreactor.cache.redis.port=16379" `
  "-Dserver.port=18080" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.reader.app.RestSampleCacheReaderApplication
```

Reader otomatik olarak `crm.customer.prod.detail`, `crm.customer.prod.segment`, `crm.customer.prod.status`, `crm.customer.prod.campaign` ve `crm.customer.prod.meta` namespace’lerini kullanır. Bütün projection adları aynı base altında duruyorsa en sade deployment şekli budur.

### Senaryo: Production’da Namespace’ler Açık Verilecek

Projection sahipliği ekipler arasında ayrıldıysa veya `<base>.<projection>` formatını kullanmak istemiyorsan namespace’leri açık ver.

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

Operasyonel etkisi: Endpointlerin hangi projection’dan okuduğu açıktır. Bedeli, config yüzeyinin artmasıdır. Cache sahipliği netleşmiş production ortamlarında bu model daha güvenlidir.

### Senaryo: Kampanya Yeni Snapshot’ı Hızlı Görmeli

Writer kampanya verisini `30000 ms` aralıkla publish ediyor ve campaign TTL kısa tutuluyorsa reader current pointer cache değeri düşük olmalıdır.

```properties
sample.cache.customer.version-cache-ms=250
```

Operasyonel etkisi: Yeni publish daha hızlı görünür. Bedeli, Redis `current` pointer okumasının artmasıdır. Bunu sadece tazeliğin küçük Redis-read azaltımından daha önemli olduğu endpointlerde kullan.

### Senaryo: Çok Yoğun Okunan Endpoint

Endpoint çok trafik alıyor ve yeni publish’in birkaç saniye geç görünmesi kabul edilebiliyorsa pointer cache artırılabilir.

```properties
sample.cache.customer.version-cache-ms=5000
```

Operasyonel etkisi: Hot endpointlerde Redis `current` pointer okuması azalır. Bedeli, yeni publish edilen version’ın bu JVM tarafından yaklaşık `5000 ms` daha geç görülebilmesidir.

### Cache Miss Kontrol Listesi

Endpoint `customer_cache_not_ready` veya benzer bir cache miss kodu dönerse önce şunları kontrol et:

```bash
redis-cli keys 'crm.customer.detail:*'
redis-cli get crm.customer.detail:current
redis-cli pttl crm.customer.detail:current
```

İlk bakılacak noktalar: writer başarılı çalıştı mı, writer ve reader namespace değerleri aynı mı, TTL dolmuş mu, reader aynı Redis topolojisine mi bağlanıyor?

## Endpoint’ler

Local testte servisi `18080` portunda açtıktan sonra:

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

## Local Çalıştırma

Önce `rest-sample-cache-writer` bir kez çalışmış ve Redis’e snapshot yazmış olmalı.

Sonra reader’ı başlat:

```powershell
mvn -q clean package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"
$cp = Get-Content target\cp.txt
java "-Dreactor.cache.redis.port=16379" `
  "-Dserver.port=18080" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.reader.app.RestSampleCacheReaderApplication
```

## Production Redis Topolojisi

Local geliştirmede standalone Redis kullanıyoruz çünkü çalıştırması kolay. Production read pod’larında normal tercih Sentinel veya Cluster olmalıdır.

Redis tek writable primary ile çalışıyor ve failover Sentinel tarafından yönetiliyorsa Sentinel kullan:

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

Redis verisi node’lara bölünüyorsa Cluster kullan:

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

Cluster’da `reactor.cache.redis.database=0` kalmalıdır. Birbiriyle ilişkili key’lerin aynı Redis slot’ta durması gerekiyorsa key tasarımında hash tag kullan: `customer:{1001}:profile` ve `customer:{1001}:orders`.

## Bu Akış Neden Doğru?

| Seçim | Kazanç | Bedel |
|---|---|---|
| `RawResponse.json(bytes)` | Her request’te DTO kurma ve JSON serialize etme maliyetini kaldırır | Writer geçerli JSON publish etmeli |
| Versioned snapshot | Reader yarım yazılmış veri görmez | Writer publish akışı gerekir |
| Route admission | Düşük memory pod’u burst altında korur | Saturated route kontrollü `503` dönebilir |
| Java Redis client yok | Classpath küçülür, Redis I/O Rust’a geçer | Redis feature set bilinçli olarak minimaldir |

## Ana Property’ler

| Property | Default | Ne zaman değiştirirsin? |
|---|---:|---|
| `reactor.runtime.profile` | `micro-rest` | Cache-backed read endpointler için düşük RSS REST profili. |
| `sample.cache.customer.namespace` | `crm.customer` | Base namespace. Runtime override verirsen projection namespace açıkça verilmediği sürece `<base>.detail`, `<base>.segment`, `<base>.status`, `<base>.campaign` ve `<base>.meta` üretilir. |
| `sample.cache.customer.detail.namespace` | `crm.customer.detail` | `sample.writer.detail.namespace` ile aynı olmalı. |
| `sample.cache.customer.segment.namespace` | `crm.customer.segment` | `sample.writer.segment.namespace` ile aynı olmalı. |
| `sample.cache.customer.status.namespace` | `crm.customer.status` | `sample.writer.status.namespace` ile aynı olmalı. |
| `sample.cache.customer.campaign.namespace` | `crm.customer.campaign` | `sample.writer.campaign.namespace` ile aynı olmalı. |
| `sample.cache.customer.meta.namespace` | `crm.customer.meta` | `sample.writer.meta.namespace` ile aynı olmalı. |
| `sample.cache.customer.version-cache-ms` | `1000` | Reader’ın current snapshot pointer’ını Java memory’de ne kadar tutacağını belirler. Yeni publish daha hızlı görünsün istiyorsan düşür; çok hot read path’lerde Redis lookup azaltmak için artır. |
| `reactor.cache.redis.read-connections` | `2` | Redis read latency gerçek darboğaz ise ölçerek artır. |
| `reactor.cache.redis.max-read-inflight` | `128` | Eşzamanlı Redis read sayısını sınırlar. Memory-first pod’da düşür. |
| `reactor.cache.redis.topology` | `standalone` | Production HA/sharding için `sentinel` veya `cluster` seç. |
| `reactor.cache.redis.nodes` | empty | Sentinel node listesi veya Cluster startup node listesi. |
| `reactor.cache.redis.sentinel.master-name` | empty | Sadece Sentinel için zorunludur. |
| `reactor.cache.redis.sentinel.master-check-ms` | `1000` | Sentinel modunda failover sonrası master değişimini ne sıklıkla kontrol edeceğini belirler. Daha hızlı toparlanma için düşür; ölçmeden agresif azaltma. |
| `reactor.rust.jni.workers` | `1` | Precomputed JSON read için iyi başlangıçtır. |
| `reactor.rust.route-admission.*` | route bazlı | Global queue büyütmeden hot endpoint’i ayrı tune etmek için kullan. |

## Production Config Kopyası

Default `src/main/resources/rust-spring.properties` dosyası localde kolay çalışmak için sade tutuldu. Kubernetes veya container production için başlangıç dosyası olarak `src/main/resources/rust-spring.production.properties` kullan. Bu dosya component/route index üretimini zorunlu tutar, runtime classpath scan fallback’i kapatır, footprint gate’i `enforce` moduna alır ve native pool değerlerini düşük memory hedefiyle küçük başlatır.
