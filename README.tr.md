# rest-sample-cache-reader

[English](README.md) | [Türkçe](README.tr.md)

Redis'te hazır duran JSON snapshot'larını REST API ile sunan küçük bir uygulamadır.

- HTTP trafiğini `rust-java-rest` karşılar.
- Redis I/O işlemlerini `java-rust-cache` üzerinden Rust yapar.
- Handler ve iş akışı Java'da kalır.
- Bu uygulama PostgreSQL'e bağlanmaz.
- Bu uygulama Redis'e veri yazmaz.

Kullanılan sürümler: `rust-java-rest:4.0.0`, `java-rust-cache:0.5.0`, `rust-sample-model:0.3.0`.

## Buradan Başlayın

Başka bir uygulama Redis read modelini hazırlıyorsa bu sample'ı kullanın.

Snapshot, Redis'e belirli bir sürümle yazılmış hazır veri setidir.

```text
PostgreSQL -> cache writer -> Redis -> bu reader -> HTTP istemcisi
```

Snapshot üretmeniz gerekiyorsa önce
[`rest-sample-cache-writer`](https://github.com/esasmer-dou/rest-sample-cache-writer) projesini çalıştırın.

## Hızlı Başlangıç

### 1. Örnek veriyi yayınlayın

Writer sample'ı bir kez çalıştırın. Writer, PostgreSQL verisini okuyup Redis snapshot'larını oluşturur.

### 2. Reader'ı başlatın

Bu repo dizininde çalıştırın:

```powershell
$env:GITHUB_PACKAGES_TOKEN="READ_PACKAGES_YETKILI_TOKEN"

mvn -q `
  "-Dserver.port=18080" `
  "-Dreactor.cache.redis.host=127.0.0.1" `
  "-Dreactor.cache.redis.port=16379" `
  clean compile exec:java
```

Başlangıç sınıfı `pom.xml` içinde hazırdır.

### 3. API'yi çağırın

```powershell
curl.exe http://127.0.0.1:18080/app/health
curl.exe http://127.0.0.1:18080/app/readiness
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/1
curl.exe "http://127.0.0.1:18080/api/v1/cache/customers/by-customer-no?customerNo=CUST-1002"
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/segments/pilot
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/statuses/active
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/campaigns/retention/candidates
curl.exe http://127.0.0.1:18080/api/v1/cache/customers/meta
```

`/app/health` yalnızca uygulamayı kontrol eder. `/app/readiness`, Redis snapshot'ının hazır olup olmadığını da kontrol eder.

## Temel Endpoint'ler

| Endpoint | Dönen veri |
|---|---|
| `GET /api/v1/cache/customers/{id}` | Tek müşteri snapshot'ı |
| `GET /api/v1/cache/customers/by-customer-no?customerNo=...` | Müşteri numarasına göre tek müşteri |
| `GET /api/v1/cache/customers/segments/{segment}` | Bir segmentteki müşteriler |
| `GET /api/v1/cache/customers/statuses/{status}` | Bir durumdaki müşteriler |
| `GET /api/v1/cache/customers/campaigns/{campaign}/candidates` | Kampanya adayları |
| `GET /api/v1/cache/customers/meta` | Snapshot bilgisi |
| `GET /api/v1/cache/customers/cache-metrics` | JSON cache metrikleri |

## Redis Modunu Seçin

| Ortam | Ayar |
|---|---|
| Lokal Redis | `reactor.cache.redis.topology=standalone` |
| Redis Sentinel | `reactor.cache.redis.topology=sentinel`, Sentinel node'ları ve master adı |
| Redis Cluster | `reactor.cache.redis.topology=cluster` ve cluster node'ları |

Reader bilinçli olarak yalnızca okuma yapar:

```properties
reactor.cache.redis.access-mode=read-only
```

Bu uygulama Redis'e veri yazmayacaksa write kapasitesini açmayın.

## Konfigürasyon

Uygulama ayarları şu sırayla okur:

1. `src/main/resources/rust-spring.properties`
2. `reactor.config.file` veya `REACTOR_CONFIG_FILE` ile verilen dosyalar
3. JVM `-D...` değerleri ve desteklenen environment variable'lar

Önce lokal varsayılanlarla başlayın:

```properties
server.port=8080
reactor.runtime.profile=micro-rest
sample.cache.customer.namespace=crm.customer
reactor.cache.redis.host=127.0.0.1
reactor.cache.redis.port=6379
```

Deployment sırasında production ayarlarını ekleyin:

```powershell
java "-Dreactor.config.file=src/main/resources/config/production.properties" ...
```

İleri seviye ayarları yalnızca gecikme, reddedilen istek ve process memory (RSS) ölçümü yaptıktan sonra kullanın:

```powershell
java "-Dreactor.config.file=src/main/resources/config/production.properties;src/main/resources/config/advanced-tuning.properties" ...
```

| Dosya | Amacı |
|---|---|
| `rust-spring.properties` | Küçük lokal varsayılanlar |
| `config/production.properties` | Güvenli production limitleri ve timeout'lar |
| `config/advanced-tuning.properties` | Route limitleri, native trim ve namespace override'ları |

Reader ve writer namespace değerleri aynı olmalıdır. Writer `crm.customer.campaign` namespace'ine yazıyorsa reader da aynı değeri okumalıdır.

## Kod Haritası

| Dosya | Görevi |
|---|---|
| `RestSampleCacheReaderApplication.java` | Uygulamayı başlatır |
| `CacheReaderModule.java` | Cache, servis, handler ve readiness kontrolünü kurar |
| `CustomerCacheService.java` | Üst seviye cache okuma API'sini kullanır |
| `CustomerCacheHandler.java` | REST endpoint'lerini açar |
| `rust-spring.properties` | Lokal ayarları taşır |

Yoğun çağrı alan akış, Redis'te hazır duran JSON byte'larını `RawResponse` ile döner. Büyük bir Java nesne ağacını yeniden oluşturmaz.

## Maven Package Erişimi

GitHub Packages için `read:packages` yetkili token gerekir. Token'ın private ortak sample repolarına da erişimi olmalıdır.

Şu server kimliklerini `~/.m2/settings.xml` dosyasına ekleyin:

```xml
<servers>
  <server>
    <id>github-rust-java-rest</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github-rust-sample-model</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
</servers>
```

Maven `401` dönerse token'ı, repo erişimini, environment variable'ı ve server kimliklerini kontrol edin.

## Sık Karşılaşılan Sorunlar

| Belirti | Kontrol edin |
|---|---|
| Maven build sırasında `401 Unauthorized` | GitHub token ve `settings.xml` server kimlikleri |
| Readiness `DOWN` | Writer çalıştı mı ve `meta` snapshot'ı var mı? |
| Endpoint cache miss dönüyor | Reader ve writer veri grubu namespace değerleri |
| Redis timeout oluşuyor | Redis adresi, bağlantı biçimi ve timeout değerleri |
| Container native kütüphaneyi yükleyemiyor | Yazılabilir `reactor.cache.native.extract-dir` dizini |

## Ayrıntılı Bilgi

- [Türkçe kullanıcı rehberi](docs/USER_GUIDE.tr.md)
- [Türkçe PDF rehberi](docs/rest-sample-cache-reader-user-guide.tr.pdf)
- [Production ayarları](src/main/resources/config/production.properties)
- [Advanced tuning ayarları](src/main/resources/config/advanced-tuning.properties)
- [v0.4.0 release notları](docs/RELEASE_NOTES_v0.4.0.md)
