# Herkese Açık Katalog İçin Edge Örneği (Kaynak Kod Önizlemesi)

[English](public-edge.md) | [Türkçe](public-edge.tr.md)

Bu örneği, bütün ziyaretçilere aynı ürün JSON'unu sunmak için kullanın.
Müşteri, kullanıcı ve yetkiye bağlı verileri normal sample içinde bırakın.
Bu ekler yayımlanmış `0.8.0` sample veya `0.9.0` cache paketinde henüz yoktur.

## Hangi Yolu Seçmeliyim?

| İhtiyaç | Seçim |
| --- | --- |
| Handler yetki kontrolü veya iş işlemi yapacak | Normal Java endpoint'i |
| Herkes aynı yayımlanmış katalog verisini okuyacak | İsteğe bağlı public edge endpoint'i |
| Pahalı bir endpoint'e gelen çağrılar sınırlanacak | `@RustEdgeRateLimit` |
| Kullanıcı kotası bütün replica'larda ortak tutulacak | Kimlik doğrulamalı, dağıtık bir kota çözümü; bu sınırlayıcı bunu yapmaz |

Cache isabetinde Rust, Java handler'ını ve guard'larını çalıştırmadan JSON döner.
Veri yoksa, query parametresi veya kimlik bilgisi varsa normal Java yolu kullanılır.
Hız sınırı her iki yoldan önce kontrol edilir. Her endpoint ve replica için ortaktır.
Reddedilen çağrılar kuyrukta tutulmaz ve kendiliğinden tekrarlanmaz.

## Derleme ve Çalıştırma

`public-edge`, bir **Maven derleme profilidir**. Yalnız `src/public-edge/java`
altını derler. Varsayılan derleme, müşteri endpoint'leriyle birlikte
`src/main/java` altını kullanmaya devam eder. İki uygulama girişini aynı pakette derlemeyin.
Çıktılar ayrıdır: normal uygulama `target/`, bu profil `target-public-edge/` altına
yazılır. Profil değiştirince eski müşteri uygulamasının sınıfları public örneğe karışmaz.

```sh
mvn -B -ntp clean package dependency:copy-dependencies -Ppublic-edge \
  -DincludeScope=runtime -DoutputDirectory=target-public-edge/dependency
java -Drust.lib.path=/app/native/librust_hyper.so \
  -Dreactor.cache.redis.host=redis \
  -Dsample.public.edge.enabled=true \
  -cp 'target-public-edge/rest-sample-cache-reader-0.8.0.jar:target-public-edge/dependency/*' \
  com.reactor.sample.cache.publiccatalog.PublicCatalogApplication
```

Windows'ta classpath ayıracı olarak `:` yerine `;` kullanın. Eşleşen DLL yolunu verin.
Bu kaynak kod önizlemesi, birlikte derlenmiş bağımlılıklar ve `edge-cache`
özelliğini içeren eşleşen native dosya gerektirir. Yayımlanmış normal SO dosyasına
bir property değişikliğiyle bu özellik eklenmez. Native destek eksikse başlangıç
hata verir; farklı bir politika sessizce devreye alınmaz.

Varsayılan değer `sample.public.edge.enabled=false` olur. Native cache ve hız
sınırı kurulmaz. Aynı katalog Java ve Redis üzerinden okunur.
`micro-rest` ise **çalışma profilidir**. Maven profilinden bağımsızdır.

Kubernetes için aynı derleme profilini uygulama image'ına koyun. Eşleşen ve
edge desteği içeren native dosyayı da ekleyin. Ardından şu değişkenleri verin:

```yaml
env:
  - name: SAMPLE_PUBLIC_EDGE_ENABLED
    value: "true"
  - name: REACTOR_RUNTIME_PROFILE
    value: "micro-rest"
  - name: REACTOR_CACHE_REDIS_HOST
    value: "redis.cache.svc.cluster.local"
```

Redis servis adresini kendi ortamınıza göre değiştirin. Node ayarı veya ek yetki
gerekmez. İlk değeri `false` yapıp pod'u yeniden başlatırsanız Java okuma yolu
korunur. Bu ayar çalışma sırasında canlı olarak değişmez.

## Uygulama Kodu

```java
@EnableRustCache(
    publicEdgePolicies = PublicCatalogHandler.class,
    publicEdgeEnabledProperty = "sample.public.edge.enabled")
@ReactorApplication(scanBasePackages = "com.reactor.sample.cache.publiccatalog")
public final class PublicCatalogApplication {
    public static void main(String[] args) {
        RestApplication.run(PublicCatalogApplication.class, args);
    }
}
```

Cache kod üreteci, başlangıçta generated policy sınıfını doğrudan çağırır.
İstek sırasında annotation taramaz. Geçersiz veya tekrarlanan policy sınıfları
derlemede reddedilir. Policy kurulumu başarısız olursa cache istemcisi kapatılır.
`publicEdgePolicies` verilmezse mevcut generated başlangıç koduna yeni bir edge
çağrısı veya koşul eklenmez. Yeni annotation yalnız kaynak kodda bulunur.

## Yayımlanmış Bir Ürünü Okuyun

Yalnız geçici geliştirme verisi için önce gövdeyi, sonra sürüm işaretçisini yazın:

```sh
version="$(($(date +%s) * 1000))-demo"
redis-cli -h redis SET "public.catalog.product:v:$version:idx:sku:TR-01" \
  '{"sku":"TR-01","name":"İstanbul Çay","details":{"stock":7}}' PX 60000
redis-cli -h redis SET public.catalog.product:current "1|$version" PX 60000
curl -i http://localhost:8080/api/v1/public/catalog/products/TR-01
curl -i http://localhost:8080/api/v1/public/catalog/limited/products/TR-01
curl -i http://localhost:8080/api/v1/public/catalog/rate-check
```

İlk adres public cache ve geniş bir endpoint hız sınırı kullanır.
İkinci adres edge cache kullanmaz. Saniyede iki çağrıya, anlık olarak da iki
çağrıya izin verir. Hızlı bir çağrı serisi HTTP `429` ve `application/problem+json`
yanıtı üretir. Yeniden denemeden önce bekleyin. Yazma işlemlerini otomatik tekrarlamayın.

`/rate-check`, public kullanılabilirlik yanıtını Rust'tan döndürür. Bu adreste
edge cache veya Redis okuması yoktur. Geniş hız sınırı ayrı ölçülebilir.
İş verisi içeren ürün adresinin yerine kullanılmaz.

Gerçek yayıncıda sürümlü ve fencing korumalı writer kullanın. Bu komutlar,
writer'ın sahiplik, atomik yayın ve temizlik mekanizmasının yerine geçmez.

## Ayarlar ve Sınırlar

| Ayar veya kural | Etkisi |
| --- | --- |
| `sample.public.edge.enabled` | Yalnız başlangıçta açılır; varsayılan `false` |
| `reactor.cache.redis.access-mode=read-only` | Reader'da yazma havuzu oluşturmaz |
| `reactor.cache.redis.read-connections=2` | Redis okuma bağlantılarını sınırlar |
| `reactor.cache.redis.max-read-inflight=8` | Aynı anda yürüyen Redis okumalarını sınırlar |
| `reactor.cache.redis.max-response-bytes=16384` | Büyük Redis yanıtlarının sınırsız bellek ayırmasını önler |
| `sample.public.catalog.max-age-ms=60000` | Java yolunun veri yaşı sınırıdır; annotation ile aynı kalmalıdır |
| `sample.public.catalog.serve-stale=false` | Yaşı geçmiş snapshot'ı sunmaz |
| Derlenmiş namespace: `public.catalog.product` | Redis anahtarları, edge annotation ve generated reader ile eşleşmelidir |
| Native edge alanı | Cache kullanılan her endpoint için 32 kayıt; her gövde en fazla 8192 bayt |
| Native edge process bütçesi | Anahtar ve kontrol payı dahil en fazla 2 MiB |
| Policy sayısı | En fazla 64 policy; bunların en fazla altısı cache kullanabilir. Registry ve bounded okumalar için bütçe ayrılır |
| Aynı kaydın veya sürümün eşzamanlı yenilenmesi | Bir native okuma paylaşılır. En fazla 50 ms beklenir; hata veya süre aşımında Java yolu korunur |
| Cache gövdesinin sahipliği | Dolumda hazırlanır; hit yanıtlarında aynı baytlar paylaşılır. Yavaş istemci tutarken gövdenin üzerine yazılmaz |
| Yanıt bütçesinin yaşam süresi | Son yanıt baytı referansı bırakılana kadar izin korunur; erken serbest bırakılmaz |
| Sürüm ve cache kaydı görünürlüğü | En fazla bir saniye; arka planda sorgu thread'i yoktur |

Namespace, veri yaşı ve eski veri politikası derlenmiş edge sözleşmesidir.
Annotation farklı kalırken yalnız Java property'lerini değiştirmeyin.
Sözleşme değiştiğinde uygulamayı yeniden üretip derleyin. Büyük yanıtlar Java
yoluna döner. 8 KiB kayıt sınırı, HTTP yanıt boyutu sınırı değildir.

Bu örnek özel endpoint'leri kendiliğinden herkese açmaz. Bu önizlemede native JWT
doğrulaması yoktur. Yetkiye bağlı endpoint'ler Java güvenlik yolunda kalmalıdır.
Bellek ve hız sonucu seçtiğiniz kullanıma bağlıdır. Edge cache, uygulamanın toplam
RSS belleğinin mutlaka düşeceği anlamına gelmez.
