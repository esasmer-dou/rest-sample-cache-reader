# Ham Redis JSON Verisini Shared Slab İle Okuma

[English](SHARED_SLAB.md) | [Türkçe](SHARED_SLAB.tr.md)

Redis anahtarında döndürmek istediğiniz JSON zaten hazırsa bu ayrı örneği kullanın.
Anahtarı ve yanıtı Java seçer. Rust, Redis'i okur ve HTTP yazımı bitene kadar
veriyi tutar. FFM yolunda Java payload `byte[]` dizisi oluşmaz. Bu örnek,
varsayılan sürümlü okuyucunun yerine geçmez.
Bu checkout'un profilini derleyin; POM'un 0.8.0 kalması bu eklemelerin önceki
sample yayımında bulunduğunun kanıtı değildir.

Slab, sabit boyutlu native bellek alanıdır. Lease, bir slotun geçici sahipliğidir.
Snapshot kapanınca slot yeniden kullanılabilir. JNI mevcut Java/Rust köprüsüdür.
FFM ise Java'nın isteğe bağlı foreign-function API'sidir. OpenJ9, JNI slab kullanır.

## Örneği Çalıştırma

Bu sample dizininden çalıştırın. JDK 21, Maven 3.9+,
curl, redis-cli, 127.0.0.1:6379 üzerinde geçici Redis ve boş 8080 portu gerekir.
Yayımlanmış 4.8.0 `rust-java-ffm-fast`/`rust-java-ffm-cache` modüllerini Maven
erişimiyle çözümleyin. Yayım her native binary'nin slab/FFM yeteneği taşıdığı
anlamına gelmez. Eşleşen, kabul edilmiş native dosyayı kullanın; yetenek yoksa
eşleşen kaynak revision'dan ilgili feature'ı derleyin.
[Özellik seçimi/okuma maliyeti](https://github.com/esasmer-dou/rust-java-rest/blob/master/docs/feature-examples.tr.md).
OpenJ9 JNI slab için Cargo `--release --features shared-slab` kullanılır.
Normal panic-abort motoru korunur. Yalnız HotSpot FFM için
`--profile release-ffm --features ffm-fast` gerekir. Abort profilinde FFM yeteneği
sıfırdır; foreign API etkinleşmez. Mevcut native ABI numaraları değişmez.

Native kaynak derlemesi gerekiyorsa kardeş `rust-spring` dizininde
`cargo build --release --features shared-slab` (OpenJ9 JNI) veya
`cargo build --profile release-ffm --features ffm-fast` (HotSpot FFM) çalıştırın.
Sırasıyla `target/release/librust_hyper.so` veya `target/release-ffm/librust_hyper.so`
çıktısını `rust.lib.path` olarak kullanın. Aşağıdaki `/app/native/librust_hyper.so`,
seçtiğiniz dosyanın deployment konumudur; Maven bu yolu oluşturmaz.

```sh
mvn -B -ntp -Pshared-slab clean package dependency:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory=target-shared-slab/lib
redis-cli SET phase54:catalog:small '{"stock":42}'
redis-cli SET phase54:catalog:heavy '{"description":"Depo stoku","stock":42}'

SAMPLE_REDIS_HOST=127.0.0.1 SAMPLE_REDIS_PORT=6379 \
SAMPLE_SLAB_ENABLED=true SAMPLE_SLAB_REQUIRED=true \
java -Drust.lib.path=/app/native/librust_hyper.so \
  -cp 'target-shared-slab/rest-sample-cache-reader-0.8.0.jar:target-shared-slab/lib/*' \
  com.reactor.sample.slab.SlabApplication
```

Bu classpath ayıracı Linux/macOS içindir. Windows üzerinde `:` yerine `;` kullanın.

```sh
curl --fail http://localhost:8080/api/v1/catalog/health
curl --fail http://localhost:8080/api/v1/catalog/small
curl --fail http://localhost:8080/api/v1/catalog/heavy
```

SET örneklerini geçici bir Redis üzerinde deneyin. Burada ham anahtar kullanılır.
Writer örneğinin sürümlü namespace yapısı kullanılmaz. Başka uygulamanın
anahtarlarını değiştirmeyin. Çalışan okuyucu salt okunurdur; SET yapamaz.

## Ayarlar

Bu profile ait `rust-spring.properties` dosyası okunur. Ortam değişkeni veya JVM
property değeriyle framework'ün normal kurallarına göre override edebilirsiniz.

| Property | Varsayılan | Ne işe yarar? |
| --- | --- | --- |
| `sample.redis.host` | `127.0.0.1` | Redis adresi; Kubernetes'te Service DNS kullanın |
| `sample.redis.port` | `6379` | Redis TCP portu |
| `sample.redis.max-response-bytes` | `65536` | Çerçeve dahil RESP wire byte bütçesi; slab üst sınırı |
| `sample.slab.enabled` | `false` | Başlangıçta native slab ister; false ise normal JNI okuması kalır |
| `sample.slab.ffm` | `false` | False ise JNI shared slab; true ise açıkça FFM istenir |
| `sample.slab.required` | `false` | True ise seçilen slab taşıyıcısı açılamadığında başlangıcı durdurur |
| `sample.slab.slots` | `4` | Native veri slotu; dört slot 256 KiB ayırır |
| `sample.slab.max-key-bytes` | `256` | Anahtarın UTF-8 byte sınırı |
| `sample.slab.key.small` | `phase54:catalog:small` | `/small` rotasının okuduğu anahtar |
| `sample.slab.key.heavy` | `phase54:catalog:heavy` | `/heavy` rotasının okuduğu anahtar |
| `sample.diagnostics.enabled` | `false` | Korunan test ağında `/internal/slab/metrics` açar |
| `reactor.runtime.profile` | `micro-rest` | Mevcut sınırlı REST runtime profili |

Kubernetes örneği; eşleşen native dosyanın `/app/native` altında bulunduğu varsayılır:

```yaml
env:
  - name: SAMPLE_REDIS_HOST
    value: redis.cache.svc.cluster.local
  - name: SAMPLE_SLAB_ENABLED
    value: "true"
  - name: SAMPLE_SLAB_REQUIRED
    value: "true"
  - name: SAMPLE_SLAB_SLOTS
    value: "4"
  - name: JAVA_TOOL_OPTIONS
    value: "-Drust.lib.path=/app/native/librust_hyper.so"
```

Bu bayrakları mevcut sınırlı JVM ayarlarınıza ekleyin. Mevcut ayarları silmeyin.

65536 wire byte sınırında, beş basamaklı Redis bulk yanıtı65526 byte değer
taşıyabilir. Başlık ve bitiş10 byte kullanır. Slab64KiB olsa da wire sınırı
korunur. Daha büyük değer için normal JNI'yı daha büyük bütçeyle kullanın.
Örnek YAML native dosyayı bağlamaz ve pod limitlerini tanımlamaz. Bunları kendi
deployment'ınızda sağlayın. Çalışma uyumu Semeru/OpenJ9 Java 21 Linux x64 üzerinde
doğrulandı. Başka JVM, işletim sistemi veya native dosya için aynı performansı
varsaymayın.

## Kendi Handler'ınızı Ekleme

`CatalogReader`, `SharedSlabCacheReader` nesnesini başlangıçta bir kez açar ve
uygulama kapanırken kapatır. `CatalogHandler` tek satır döndürür:

```java
return CacheResponses.ownedProblem(reader.read(key));
```

Yanıt yazıcısı snapshot'ı başarıda veya hatada kapatır. Direct çıktı buffer'ı
büyüdüğünde Redis tekrar okunmaz. Aynı JSON'u döndürmek için String, record veya
`byte[]` dönüşümü yapmayın. Açık snapshot'ı terk etmeyin. Snapshot başına Cleaner
yoktur. Böyle bir hata sabit slot kapasitesini tüketebilir.

Seçim yalnız başlangıçta yapılır. Preview/native erişim yoksa, semboller uygun
değilse veya yanıt sınırı 64 KiB üzerindeyse isteğe bağlı FFM yerine JNI seçilir.
Zorunlu modda başlangıç durur. Aktif FFM hatasında JNI ile işlem tekrarlanmaz.
Örnek, cache hatasını 503'e çevirir. Bellek etkisini ölçmeden slot sayısını
artırmayın. Dört slot sınırlı depolamadır; sınırsız eşzamanlılık garantisi değildir.

Etkin slab'ın varsayılan çağrı yolu JNI'dır. OpenJ9 21 FFM çağrılarında scope
birikimi yük altında tekrarlandı. Bu nedenle FFM seçimi linker açılmadan engellenir.
JNI shared slab aynı Rust GET/lease/copy kodunu kullanır; sorunlu JVM yoluna girmez.
Ayrıca doğrulanmış HotSpot Java 21 için `sample.slab.ffm=true` seçip
`--enable-preview --enable-native-access=ALL-UNNAMED` ekleyebilirsiniz. Birikimi
gizlemek için JDK reflection, her çağrıda yeni thread veya daha büyük heap kullanmayın.

Slab süreç ömürlüdür. Okuyucu kapanınca istemci ve girdi wrapper'ları bırakılır;
slab OS'a verilmez. Snapshot ve FFM segment nesneleri hâlâ oluşur. Bu akış uçtan
uca sıfır allocation veya sıfır kopya değildir. Normal örneğin varsayılanları ve
sürümlü snapshot tutarlılığı değişmez.
[Modül ayrıntıları](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/ffm-cache/README.tr.md).

## Native Bellek

İstekler mevcut `reader.read(key)` ve sahiplikli yanıtı kullanır.
Her istekte yine GET yapılır. Yanıt cache'i, DTO ağacı veya Java payload dizisi
eklenmez. Yeni bir anahtar hazırlama API'si veya Java tarafında yeni bir havuz
gerekmez.

JNI yolu, mevcut String anahtarınızı Rust'a verir. Rust anahtarı sınırlı stack
alanında doğrular ve kodlar. Java anahtar buffer'ına veya sarmalayıcısına gerek
yoktur. Unicode anahtarlar için de `reader.read(key)` çağrısını kullanın. Bozuk
UTF-16 veya UTF-8 byte sınırını aşan anahtar GET başlamadan reddedilir; kesilmez.

Tek sunuculu Redis'te yanıt doğrudan ayrılmış slota okunur. Geçici payload `Vec`
ve onun kopyası kaldırıldı. Sentinel/Cluster mevcut toparlama yolunu ve geçici `Vec`
kullanımını korur. HTTP çıktısına slot içinden kopyalama hâlâ yapılır.

Okuyucu; JIT, heap, allocator eşiği veya thread ayarlarınızı değiştirmez.
Kararlı ve tepe RSS'i ayrı ölçün. Payload tahsisatının kaldırılması, JVM derleme
tepelerini ortadan kaldırmaz. Bütün süreç sıfır kopyalı hâle gelmez.
