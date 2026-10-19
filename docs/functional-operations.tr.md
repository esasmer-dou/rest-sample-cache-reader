# Veri Yaşı Ve Hata Davranışı

[English](functional-operations.md) | [Turkish](functional-operations.tr.md)

Bu özellikler geliştirme kaynaklarındadır. Önce yerel kütüphaneleri derleyin.
Yayımlanmış paketlerde henüz yoktur.

## Müşteri Detayı Güncel Olmalı

Harici properties dosyanıza ekleyin:

```properties
sample.errors.common-contract=true
reactor.rust.errors.format=problem-details
sample.cache.customer.detail.max-age-ms=120000
sample.cache.customer.detail.serve-stale=false
```

Varsayılan yaş sınırı sıfırdır. Eski davranış değişmez.
Bu örnekte iki dakikadan eski detay snapshot'ı 503 döner.
Güncel snapshot içinde müşteri yoksa 404 döner.
Redis'e ulaşılamıyorsa 503 döner.
Yaş kontrolü için JSON tekrar okunmaz.
Yaş, writer veriyi hazırlamaya başladığında hesaplanır.
Yenileme aralığı ve hazırlama süresinin toplamı bu sınırdan kısa olmalıdır.

Eski katalog verisine geçici olarak izin vermek için `serve-stale=true` seçin.
Ortak hata adaptörü açıkken eski verinin yanıtında `X-Cache-Stale: true` bulunur.
TTL nedeniyle silinen veri yine sunulamaz.

Her projection ayrı sınır kullanabilir.
`sample.cache.customer.max-age-ms` ortak sınırdır.
Önce projection'a özel runtime ayarı, sonra ortak runtime ayarı seçilir.
Bunlar yoksa dosyadaki değerler kullanılır.
Writer TTL ve reader yaş sınırını birlikte değerlendirin.
