# Cache 0.9.3 Kullanımı

[English](CACHE_0.9.3.md) | [Turkish](CACHE_0.9.3.tr.md)

Bu repoyu `master` dalından [Hızlı Başlangıç](../README.tr.md#hızlı-başlangıç)
adımlarıyla derleyin. POM, platformu `4.9.0` sürümünde tutar ve yalnızca
cache kütüphanesini `0.9.3` sürümüne yükseltir. Redis ayarını veya Cache
Writer'ı değiştirmeniz gerekmez.

Reader'ı başlattıktan sonra Writer'ın yayımladığı bir müşteri kimliğini sorgulayın:

```sh
curl --fail http://localhost:18080/api/v1/cache/customers/1
```

JSON yanıtı değişmez. Bulunamayan kimliklerde mevcut davranış korunur.
Yayımlanmış `v0.8.3` etiketi ve paketi değişmedi.
