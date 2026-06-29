# rest-sample-cache-reader

[English](README.md) | [Turkish](README.tr.md)

`java-rust-cache` ile Redis’te hazır duran JSON’u servis eden minimum Rust-Java REST örneği.

Bu process içinde DB bağlantısı, scheduler, Java Redis client veya Dubbo yoktur. Java REST business handler şeklini korur; HTTP I/O ve Redis I/O Rust tarafındadır.

## Gerçek Senaryo

Response’u önceden hazırlanmış Redis read model’den dönebileceğin read-heavy API pod’ları için bu örnek doğru modeldir.

Tipik akış:

1. `rest-sample-cache-writer` yeni bir versioned snapshot yazar.
2. Bu reader HTTP isteğini alır.
3. Handler tek bir high-level metot çağırır: `customer(id)` veya `campaignCandidates("retention")`.
4. Cache library current version, Redis key ve miss durumunu kendi çözer.
5. Handler `RawResponse.json(bytes)` döner; Java DTO graph tekrar kurulmaz.

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
java "-Djava.library.path=..\rust-spring\target\release" `
  "-Dreactor.cache.redis.port=16379" `
  "-Dserver.port=18080" `
  -cp "target\classes;$cp" `
  com.reactor.sample.cache.reader.app.RestSampleCacheReaderApplication
```

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
| `sample.cache.customer.namespace` | `crm.customer` | Writer namespace’i ile aynı olmalı. |
| `reactor.cache.redis.read-connections` | `2` | Redis read latency gerçek darboğaz ise ölçerek artır. |
| `reactor.cache.redis.max-read-inflight` | `128` | Eşzamanlı Redis read sayısını sınırlar. Memory-first pod’da düşür. |
| `reactor.rust.jni.workers` | `1` | Precomputed JSON read için iyi başlangıçtır. |
| `reactor.rust.route-admission.*` | route bazlı | Global queue büyütmeden hot endpoint’i ayrı tune etmek için kullan. |
