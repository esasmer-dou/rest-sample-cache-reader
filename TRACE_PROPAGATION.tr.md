# Bir İsteği HTTP Servisleri Arasında İzleyin

[English](TRACE_PROPAGATION.md) | [Turkish](TRACE_PROPAGATION.tr.md)

Bu kaynak kod örneği Redis reader'dan ayrıdır.
`trace-propagation` Maven profili ve birlikte derlenen framework kaynaklarıyla çalışır.
Yeni codec ve parent ID değişiklikleri henüz yayımlanmadı.
Mevcut `4.8.0` paketinde burada anlatılan her yeniliğin bulunduğunu varsaymayın.

## İçindekiler

- [Ne çalışır?](#ne-çalışır)
- [İki servisi başlatın](#iki-servisi-başlatın)
- [Sipariş API'sini çağırın](#sipariş-apisini-çağırın)
- [Ayarları seçin](#ayarları-seçin)
- [Sınırları bilin](#sınırları-bilin)

## Ne Çalışır?

Sipariş API'si, stok bilgisi için başka bir HTTP servisini çağırır.
İki serviste de handler Java'dadır. I/O işlemlerini Rust yürütür.
Redis, veritabanı, security starter, ortak model JAR'ı veya telemetry SDK'sı gerekmez.

| Terim | Bu örnekteki anlamı |
| --- | --- |
| Trace ID | İstek zincirinin ortak kimliğidir. |
| Span ID | Çalışan handler çağrısının kimliğidir. |
| Parent span ID | Bu handler'ı çağıran işlemin kimliğidir. |
| Retry | Aynı mantıksal isteğin yeniden denenmesidir. |

Yalnız sipariş route'larında `@Traced` vardır.
`/app/health`, izlenmeyen bir Java kontrol route'udur.
Stok servisi kendisine gelen trace başlığını doğrular.

## İki Servisi Başlatın

Önce framework README'sindeki birlikte kaynak derleme adımını tamamlayın.
Ardından sample dizininde yalnız bu profili derleyin:

```bash
mvn -B -ntp -Ptrace-propagation clean package -DskipTests \
  dependency:copy-dependencies -DincludeScope=runtime \
  -DoutputDirectory=target-trace-propagation/lib
```

Linux'ta ilk terminalde stok servisini başlatın:

```bash
java -Xms8m -Xmx48m -Xss256k \
  -Dserver.port=8082 -Dsample.api.enabled=false \
  -Dsample.inventory.enabled=true \
  -cp 'target-trace-propagation/rest-sample-cache-reader-0.8.0.jar:target-trace-propagation/lib/*' \
  com.reactor.sample.trace.TraceApplication
```

İkinci terminalde sipariş API'sini başlatın:

```bash
java -Xms8m -Xmx48m -Xss256k \
  -Dserver.port=8080 -Dsample.inventory.base-url=http://127.0.0.1:8082 \
  -Dsample.diagnostics.enabled=true -Dreactor.tracing.sample-ratio=1.0 \
  -cp 'target-trace-propagation/rest-sample-cache-reader-0.8.0.jar:target-trace-propagation/lib/*' \
  com.reactor.sample.trace.TraceApplication
```

Bu seçenekler Semeru/OpenJ9 Java 21 içindir.
Windows'ta classpath içinde `:` yerine `;` kullanın.
Örnek diagnostic endpoint'lerini public ingress'e açmayın.
Bunlar kimlik doğrulaması olan bir production yönetim API'si değildir.

## Sipariş API'sini Çağırın

```bash
curl -i http://localhost:8080/api/v1/orders/small \
  -H 'traceparent: 00-0123456789abcdef0123456789abcdef-fedcba9876543210-01'
curl -s http://localhost:8080/internal/trace/last
```

İlk yanıt `{"stock":42}` olur.
Son span, verdiğiniz trace ID'yi ve `fedcba9876543210` parent ID'sini içerir.
Kendi span ID'si yenidir. Stok servisine gönderilen başlıkta bu yeni ID bulunur.

| Route | Kullanım amacı |
| --- | --- |
| `GET /app/health` | İzlenmeyen sağlık kontrolüdür. |
| `GET /api/v1/orders/small` | Küçük stok yanıtını getirir. |
| `GET /api/v1/orders/heavy` | Aynı akışı daha büyük JSON yanıtıyla gösterir. |
| `GET /api/v1/orders/retry` | Kontrollü yeniden deneme örneğidir. |
| `GET /internal/trace/last` | Yalnız son sampled span'i gösterir. Diagnostics açık olmalıdır. |
| `GET /internal/trace/metrics` | Native ve sample sayaçlarını gösterir. Diagnostics açık olmalıdır. |

Stok servisini yeni başlattıktan sonra `/api/v1/orders/retry` adresini **bir kez**
çağırın. İlk akış `503 -> 503 -> 200` olur. Üç denemede de başlığın aynı kaldığı
kontrol edilir. Sonraki çağrılar normal yanıt alır.
Bu tek akışlık örnek, production hata politikası veya eşzamanlı retry simülatörü değildir.
Sipariş istemcisinde yalnız bu örnek metotta `retries=2` vardır.

Java HTTP sayaçlarını görmek isterseniz sipariş API'sine ayrıca
`-Dreactor.metrics.collection-enabled=true` verin.
Bellek öncelikli modda bu sayaçlar sıfır kalabilir. Başlık taşıma yine çalışır.
Sample exporter yalnız son span'i ve bir sayacı tutar. Bütün istekleri biriktirmez.

## Ayarları Seçin

| Ayar | Örnek değer | Ne zaman kullanılır? |
| --- | --- | --- |
| `reactor.tracing.enabled` | `false` | Handler'ı değiştirmeden tracing guard'larını kapatır. |
| `reactor.tracing.annotated-only` | `true` | Yalnız `@Traced` işaretli route'ları izler. |
| `reactor.tracing.sample-ratio` | `0.01` | Yerel kök izlerin %1'ini örnekler. Gelen başlığın örnekleme kararı önceliklidir. |
| `sample.inventory.base-url` | `http://inventory:8082` | Docker/Kubernetes ortamında karşı servisin DNS adresidir. |
| `sample.diagnostics.enabled` | `false` | Normal dağıtımda örnek diagnostic endpoint'lerini kapalı tutar. |

Kubernetes'te karşılıkları arasında `REACTOR_TRACING_ENABLED`,
`REACTOR_TRACING_ANNOTATED_ONLY`, `REACTOR_TRACING_SAMPLE_RATIO` ve
`SAMPLE_INVENTORY_BASE_URL` bulunur.
Worker, kuyruk ve byte bütçelerini framework runtime profili yönetmeye devam eder.
Tracing bu sınırları büyütmez. Native ABI'yi değiştirmez.

## Sınırları Bilin

Bu starter kimlik, süre ve tamamlanma sonucunu taşır.
Payload'ları veya tüm metotları otomatik izlemez. Glowroot agent kurmaz.
Buradaki exporter, örneği görmek içindir. Uzak collector değildir.
Kendi hedefiniz için tek ve sınırlı bir `TraceExporter` uygulaması sağlayın.

Hazır buffer'a yazan codec geçici dizi üretmez.
HTTP akışının tamamında ise context, span, başlık ve istek nesneleri oluşur.
Codec'in sıfır tahsisat sonucunu bütün isteğin sıfır tahsisatlı olduğu şeklinde yorumlamayın.

[Starter ayarları ve retry kuralları](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-tracing/README.tr.md).
