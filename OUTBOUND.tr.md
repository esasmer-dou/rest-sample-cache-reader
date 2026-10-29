# Stok Servisini Çağırma ve Hesap API'lerini Koruma

[English](OUTBOUND.md) | [Turkish](OUTBOUND.tr.md)

Bu örnek kaynak koddan derlenir. Generated HTTP istemcisi, devre kesici ve native
HS256 filtresi kullanır. Bu ekler yayımlanmış 4.8.0 native paketinde yoktur.
Normal Redis okuyucusu değişmez. `outbound`, `native-view` ve varsayılan uygulama
profillerini aynı derlemede birleştirmeyin.

## Hangi İşi Kim Yapar?

| İş | Sorumlu |
| --- | --- |
| Stok işlemi ve döndürülecek yanıt | Java |
| Üretilmiş istemci ve metot başına devre kararı | Java; dynamic proxy kullanılmaz |
| HTTP/1 bağlantısı ve bağlantıların tekrar kullanımı | Sunucudan ayrı Rust Tokio runtime'ı |
| Sonucu alma, çözme ve uygulamanın devam işlemi | Sınırlı Java tamamlama worker'ı |
| Geçersiz HS256 token'ını erken reddetme | İstenirse Rust; JNI kuyruğundan önce |
| Geçerli kimlik, rol ve iş yetkisi | Mevcut Java security starter'ı |

Bu derlemede Redis veya veritabanı çağrısı yoktur. Native HTTP yalnız `http://`
kabul eder. HTTPS, proxy veya HTTP/2 gerekiyorsa
`reactor.http-client.transport=jdk` seçin. Native HTTPS adresi başlangıçta hata
verir. Bağlantı gizlice HTTP'ye çevrilmez.

## Eşleşen Kaynakları Derleyin

Önce eşleşen core, platform ve starter kaynaklarını yerel Maven deponuza kurun.
İki örneği de açacaksanız native kütüphanede `outbound-http,edge-jwt` Cargo
özellikleri bulunmalıdır. [Native derleme rehberi](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-http-client/NATIVE-INTEGRATION.tr.md)
gerekli artifact'leri açıklar.

```sh
mvn -Poutbound -DskipTests package
```

Sonuç `target-outbound` içine yazılır. Normal Redis okuyucusunun `target` dizini
değişmez. İstemciler ve başlangıç indeksi derleme sırasında üretilir. Bu örnekte
çalışma anında tarama veya reflection gerekmez.

## Yerel Stok Servisini Başlatın

Bu repository içinde bir terminal açın. Örnek Linux içindir. Windows'ta aynı
özellikleri ortam değişkenleriyle verin ve eşleşen DLL'nin mutlak yolunu kullanın.

```sh
SAMPLE_API_ENABLED=false SAMPLE_OUTBOUND_ENABLED=false \
SAMPLE_BACKEND_ENABLED=true REACTOR_SECURITY_ENABLED=false SERVER_PORT=8082 \
mvn -Poutbound -Drust.lib.path=/opt/native/librust_hyper.so exec:java
```

Aynı ana sınıf bu ayarlarla yalnız `StockBackend` bileşenini başlatır.
Kullanılmayan istemciler oluşturulmaz. Servis küçük JSON, daha büyük JSON ve
bilerek HTTP 500 döndüren bir hata adresi sunar.

## REST Uygulamasını Başlatın

Başka bir terminal kullanın. Aşağıdaki anahtar **yalnız yerel deneme içindir**.
Gerçek ortamda güçlü bir anahtarı deployment Secret üzerinden verin.

```sh
export REACTOR_SECURITY_JWT_HMAC_SECRET='test-only-012345678901234567890123456789'
export SAMPLE_STOCK_BASE_URL=http://127.0.0.1:8082
mvn -Poutbound -Drust.lib.path=/opt/native/librust_hyper.so exec:java
```

```sh
curl -i http://127.0.0.1:8080/api/v1/account/health
curl -i http://127.0.0.1:8080/api/v1/stock/small
curl -i http://127.0.0.1:8080/api/v1/stock/heavy
curl -i http://127.0.0.1:8080/api/v1/stock/error
curl -i http://127.0.0.1:8080/api/v1/stock/error
```

İlk hata çağrısı stok servisine ulaşır. Hemen yaptığınız ikinci çağrı, ilgili
metodun devresi açıkken 503 alır. Small ve heavy metotlarının devreleri ayrıdır.
Örnekte kısa bir toparlanma süresi vardır. Gerçek servisiniz için uygun süreyi
seçin.

Controller `CompletionStage<RawResponse>` döndürür. JNI worker çağrıyı başlatır;
socket sonucunu beklerken bloke olmaz. Yanıt yine Java `byte[]` ve `RawResponse`
nesnelerine dönüşür. Bu örnek sıfır kopyalı native response handle kullanmaz.

## Geçersiz Token'ı Rust'ta Reddedin

REST uygulamasını başlatmadan önce `SAMPLE_EDGE_JWT_ENABLED=true` verin.
Başlangıç kodu, rotalardan önce üretilmiş `AuthHandler_EdgeJwtPolicies` sınıfını
kurar. Bu ayar verilmezse kimlik doğrulama Java'da kalır.

```sh
export SAMPLE_EDGE_JWT_ENABLED=true
mvn -Poutbound -Drust.lib.path=/opt/native/librust_hyper.so exec:java
```

Üçüncü terminalde aynı deneme anahtarıyla bir token üretin:

```sh
export REACTOR_SECURITY_JWT_HMAC_SECRET='test-only-012345678901234567890123456789'
TOKEN=$(mvn -q -Poutbound exec:java \
  -Dexec.mainClass=com.reactor.sample.outbound.TokenTool -Dexec.args=valid)
curl -i -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/v1/account/me
curl -i -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/v1/account/admin
curl -i -H 'Authorization: Bearer invalid' http://127.0.0.1:8080/api/v1/account/me
```

Geçerli reader token'ı `/me` için 200, `/admin` için 403 alır. Geçersiz token,
Java'ya ulaşmadan 401 alır. `TokenTool expired` süresi geçmiş imzalı token üretir.
`TokenTool admin` yönetici token'ı üretir. Bu araç kimlik sunucusu değildir.
Geçerli kimliği ve rolü Java doğrulamaya devam eder. Daha hızlı sonuç almak için
korumalı bir rotayı herkese açık edge cache rotasına çevirmeyin.

Üretilmiş installer kullanıldığında yerleşik HS256 guard, Rust'ın o isteğe ait
kanıtını kullanır. Aynı imza iki kez doğrulanmaz. Java, guard çalıştığı andaki
süreyi, kimliği ve rolü kontrol eder. Farklı ayarlar başlangıçta hata verir.
Sahte HTTP başlıkları kanıt yerine geçmez. `/api/v1/account/async-me` aynı guard'ı
`CompletionStage<RawResponse>` rotasında gösterir. Kanıt, sonraki uygulama
callback'lerine taşınmaz. Özel Java verifier'ın mevcut kontrolleri korunur.

Özel metrics endpoint'inde şu sayaçlar vardır:
`reactor_native_edge_jwt_signature_checks_total`,
`reactor_native_edge_jwt_proof_accepted_total` ve
`reactor_native_edge_jwt_proof_rejected_total`.
Generated guard native kanıtı kabul edince sayaç artar. Sonraki rol kontrolü 403
döndürebilir. Bu sayaç yetki kontrolünün başarılı olduğunu göstermez.
Sınırlı özet cache'i kullanıldığı için imza kontrol sayısı istek sayısına eşit
olmak zorunda değildir. Beklenmeyen kanıt reddini inceleyin.
Sayaçlar kullanıcı veya token bilgisi içermez.

## Yanıt Gövdesi Nasıl Taşınır?

Native HTTP, her çağrı için tek bir gövde dizisi döndürür. Tamamlama bilgisi ayrı
taşınır. Java, gövdeyi ikinci bir yanıt dizisinden çıkarıp yeniden kopyalamaz.
Mevcut `byte[]`, String ve DTO decoder kullanımı değişmez. Aynı kaynak setine ait
native binary'yi kullanın. Gerekli split-completion sembolü başlangıçta denetlenir.

Örnekteki `ProtectedStockClient.small()` ve `heavy()` metotları yalnız gövde
döndürür. Native istemci bu yolda ek `HttpClientResponse` nesnesi ve gövdeyi
ayıran future üretmez. Kullanılmayan yanıt başlıklarını da Java nesnelerine
çevirmez. HTTP başlık ve boyut kontrolleri çalışmaya devam eder.

Status veya yanıt başlıklarına ihtiyacınız varsa istemci dönüşünü
`CompletionStage<HttpClientResponse<byte[]>>` olarak tanımlayın. Bu yolda
başlıklar korunur. İki kullanım da çağrıya ait bağımsız `byte[]` döndürür;
iş mantığınız Java'da kalır. Gövdenin JNI üzerinden kopyalanması tamamen kalkmaz.

Tamamlama worker'ı başlangıçta park eder. Bir yanıt sonrasındaki kısa boşlukta
mevcut native beklemeyi kullanır; sonraki yanıt geldiğinde hemen uyanır. Gerçekten
boşta kalınca yeniden süresiz park eder. Her isteğe sabit gecikme, yeni timer
veya thread eklenmez. Bunun için ek bir property vermeniz gerekmez.

Small ve heavy örneklerinin parametreleri yoktur. Üretilmiş native istemci,
sabit URI ve başlık tanımını başlangıçta bir kez hazırlar. Bu hazırlık normal
`StockClient` için de devre kesicili `ProtectedStockClient` için de geçerlidir.
Yanıt tutulmaz; her çağrı stok servisine gider. Parametre veya çağrıya özel başlık
sağlayıcısı olan metotlar dinamik hazırlığı kullanır. Aynı `traceparent` veya
kullanıcı başlığı farklı çağrılara kopyalanmaz. Yeni annotation/property gerekmez.

## Path Parametrelerini Deneyin

İzole örnekte senkron ve asenkron path kullanımı da bulunur:

```sh
curl 'http://127.0.0.1:8080/api/v1/account/path/alpha/beta'
curl 'http://127.0.0.1:8080/api/v1/account/async-path/%C4%B0zmir/%C3%87a%C4%9Fr%C4%B1'
```

İlk çağrı `alpha|beta`, ikinci çağrı UTF-8 metin olarak `İzmir|Çağrı` döndürür.
İkisi de normal `@PathVariable` parametrelerini kullanır. Asenkron metot
`CompletionStage<RawResponse>` döndürür. JNI kuyruğu sekiz parametre çifti için
yer ayırmak yerine hazırlanmış path metnini taşır. Parametresiz işte boş string
kullanılır; path buffer'ı ayrılmaz. Uygulamada ek ayar yapmanız gerekmez.

## İhtiyacınıza Göre Ayar Seçin

| İhtiyaç | Tam ayar ve etkisi |
| --- | --- |
| Devre kesici olmadan native HTTP | `sample.outbound.breaker.enabled=false`; yalnız `StockClient` oluşturulur |
| Bağımlılık hatasını sınırlamak | `sample.outbound.breaker.enabled=true`; yalnız `ProtectedStockClient` oluşturulur |
| HTTPS servisini çağırmak | `reactor.http-client.transport=jdk`; native outbound worker'ları oluşturulmaz |
| Geçersiz HS256 trafiğini erken kesmek | `sample.edge.jwt.enabled=true`; eşleşen native özellik ve Java güvenliği gerekir |
| Native sayaçları yerelde görmek | `sample.diagnostics.enabled=true`; `/api/v1/account/metrics` açılır; dışarıya açmayın |

Örnek varsayılanlarının tamamı `src/outbound/resources/rust-spring.properties`
dosyasındadır. Başlangıç seçimi `micro-rest`, dokuz mantıksal çağrı slotu,
sekiz bağlantı ve bir tamamlama worker'ıdır. Bir slot, henüz devam işlemi bitmemiş
tamamlama için pay bırakır. Kaynaklar bu işlem bitmeden serbest bırakılamaz.
Stok rotası aynı anda sekiz işlem
kabul eder. Kabul sırasını en fazla 125 ms bekler. Aşırı yükte 503 dönmesi bu
yüzden normaldir. Kuyruğu büyütmek, stok servisinin kapasitesini artırmaz.

Ölçülmüş c256 ani yük reçetesinde
`reactor.rust.jni.queue-capacity=512` bütün transport kollarına eşit verilir.
Bir JNI worker, bir CPU ve 128 MiB uygulama sınırı korunur. Normal `micro-rest`
varsayılanı 128 kalır. Bu reçeteyi seçmeden önce p99 ve RSS ölçün. Yalnız hatayı
gizlemek için kopyalamayın. Çağrı zaman aşımını ve sınırlı yanıt boyutunu koruyun.

Kapasite karşılaştırması ayrıca iki transport için eşit olarak
`reactor.rust.route-budget.stock.route-admission.queue-timeout-ms=1000` kullanır.
Bu ayar, örnekteki 125 ms hızlı ret varsayılanından ayrıdır. Ani yükü sırada
bekletir; p99'un 125 ms altında kalacağını garanti etmez.

Native 3 MiB byte bütçesi, transport rezervasyonunu sınırlar. Container'ın toplam
RSS sınırı değildir. [HTTP istemci rehberi](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-http-client/README.tr.md)
ve [HS256 rehberi](https://github.com/esasmer-dou/rust-java-rest/blob/master/NATIVE_JWT.tr.md)
iptali, sınırları ve desteklenmeyen özellikleri açıklar.
