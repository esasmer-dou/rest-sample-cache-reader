# Veri Yaşı Ve Hata Davranışı

[English](functional-operations.md) | [Turkish](functional-operations.tr.md)

Bu örnekler yayımlanmış sample `0.8.0`, cache `0.9.0` ve REST/platform `4.8.0`
sürümlerine aittir (isteğe bağlı agent `0.7.1`). Hizalanmış paketleri ve native dosyaları kullanın.

## Özellik Ve Çalıştırma Tablosu

| Özellik / kodda bulunan örnek | Seçim ve çalıştırma | Sınır / hata davranışı |
| --- | --- | --- |
| Üretilmiş projection okuyucusu ve Java HTTP handler'ları | Maven profili yok; [Hızlı Başlangıç](../README.tr.md#hızlı-başlangıç) | APT bağlantı kodunu üretir, iş mantığını değil; varsayılan yalnız Redis |
| Sürümlü detail/segment L1 ve `CacheReadValue` | Maven profili yok; [L1 ayar dosyasıyla çalıştırma](../README.tr.md#isteğe-bağlı-l1-okuma) | Replica başına sınırlı bellek; varsayılan value yolunda slab ve snapshot sarmalayıcısı yok |
| Veri yaşı ve ortak hatalar | Aşağıdaki harici ayarlar; normal reader başlangıcı | İsteğe bağlı; aşağıdaki HTTP tablosuna bakın |
| Public katalog politikaları | `-Ppublic-edge`; [çalıştırma rehberi](public-edge.tr.md) | Ayrı katalog uygulaması; kendi kabul/hata politikasını kullanır |
| Binary native view | `-Pnative-view`; [çalıştırma rehberi](../NATIVE_VIEW.tr.md) | Ayrı kaynak kümesi; Redis projection yerine geçmez |
| Dış HTTP / native HS256 | `-Poutbound`; [çalıştırma rehberi](../OUTBOUND.tr.md) | Ayrı uygulama; sınırlı retry/devre kesici politikası writer yazmalarına uygulanmaz |
| Açık anahtarlı JWT / izleme aktarımı | `-Pjwt-security` veya `-Ptrace-propagation`; [JWT](../JWT_SECURITY.tr.md), [izleme](../TRACE_PROPAGATION.tr.md) çalıştırma rehberleri | Bağımsız örnekler; varsayılan cache route'larında kendiliğinden açılmaz |
| Shared slab / isteğe bağlı FFM | `-Pshared-slab`; [çalıştırma rehberi](../SHARED_SLAB.tr.md) | Ayrı ham Redis örneği; OpenJ9'da JNI, uygun HotSpot'ta isteğe bağlı FFM; depolama maliyeti isteğe bağlı |

Aynı anda tek bağımsız Maven profili seçin; ilgili rehberdeki ayar/JVM argümanlarını
kullanın. Bunlar kodda bulunan örneklerdir; normal başlangıçta hepsi açılmaz.
[Servis sözleşmesi](../src/main/java/com/reactor/sample/cache/reader/service/CustomerCacheService.java)
value/result/snapshot API'lerini tanımlar. [Mevcut handler](../src/main/java/com/reactor/sample/cache/reader/handler/CustomerCacheHandler.java)
ID/segment için value API'sini kullanır. Açık snapshot sahipliği alternatif API'dir; varsayılan implementasyon değildir.

## HTTP Hata Tablosu

| Durum | Varsayılan `sample.errors.common-contract=false` | İsteğe bağlı `sample.errors.common-contract=true` |
| --- | --- | --- |
| Yayımlanmış snapshot yok | Eski sözleşmede `404 customer_cache_not_ready` | Ortak cache-not-ready hatası, `503` |
| Güncel snapshot içinde kayıt yok | Endpoint'e özel `404` | Ortak cache-miss hatası, `404` |
| Redis bağımlılık hatası | Mevcut runtime exception davranışı; sample ortak hata eşlemesi yok | `CacheFailureHandler`, `RedisCacheException` için `503` döndürür |
| Snapshot ayarlanan yaşı aştı | Varsayılan yaş reddi yok (`max-age-ms=0`) | Aşağıdaki reçeteyle `503`; eski veriyle başarılı yanıtta `X-Cache-Stale: true` |

Bayrak adaptör/hata eşlemesini seçer; `reactor.rust.errors.format=problem-details`
ortak hata biçimini seçer. Bu ayarlar L1 açmaz veya writer TTL'ini değiştirmez.
Yaş kontrolü ayrı ayardır; ortak hata bayrağıyla kendiliğinden etkinleşmez.

## Müşteri Detayı Güncel Olmalı

Harici properties dosyanıza ekleyin:

```properties
sample.errors.common-contract=true
reactor.rust.errors.format=problem-details
sample.cache.customer.detail.max-age-ms=120000
sample.cache.customer.detail.serve-stale=false
```

Varsayılan yaş sınırı sıfırdır ve ortak hata sözleşmesi kapalıdır. Eski davranış değişmez.
Bu örnekte iki dakikadan eski detay snapshot'ı 503 döner.
Güncel snapshot içinde müşteri yoksa 404 döner.
Redis'e ulaşılamıyorsa 503 döner.
Yaş kontrolü için JSON tekrar okunmaz.
Yaş, writer veriyi hazırlamaya başladığında hesaplanır.
Yenileme aralığı ve hazırlama süresinin toplamı bu sınırdan kısa olmalıdır.

Eski katalog verisine geçici olarak izin vermek için bu reçetede
`sample.cache.customer.detail.serve-stale=true` seçin.
Ortak hata adaptörü açıkken eski verinin yanıtında `X-Cache-Stale: true` bulunur.
TTL nedeniyle silinen veri yine sunulamaz.

Her projection ayrı sınır kullanabilir.
`sample.cache.customer.max-age-ms` ortak sınırdır.
Önce projection'a özel runtime ayarı, sonra ortak runtime ayarı seçilir.
Bunlar yoksa dosyadaki değerler kullanılır.
Writer TTL ve reader yaş sınırını birlikte değerlendirin.
Snapshot yaşı, L1 kayıt yaşı ve sürüm işaretçisi önbelleği (`version-cache-ms`) ayrı
sınırlardır. L1, Redis'teki silinme/TTL bitişini her okumaya anında yansıtmaz.
