# Binary Hesap Verisinden JSON Üretme

[English](NATIVE_VIEW.md) | [Turkish](NATIVE_VIEW.tr.md)

Bu ayrı kaynak profili yayımlanmış REST/codegen `4.8.0` API'lerini kullanır.
Sample framework JAR'ına dahil değildir. Varsayılan Redis okuyucusu değişmez.
[Özellik seçimi/okuma maliyeti](https://github.com/esasmer-dou/rust-java-rest/blob/master/docs/feature-examples.tr.md).
Bu profil güncel checkout'u anlatır; önceki sample 0.8.0 JAR'ında bulunduğu iddia
edilmez. Aşağıdaki profili kaynaklardan derleyin.

## İçindekiler

1. [Hangi Yolu Seçmeliyim?](#hangi-yolu-seçmeliyim)
2. [Örneği Çalıştırın](#örneği-çalıştırın)
3. [Ödünç Veriyi Güvenli Kullanın](#ödünç-veriyi-güvenli-kullanın)
4. [Sınırlar](#sınırlar)

## Hangi Yolu Seçmeliyim?

Veriyi gönderen uygulama bu üretilmiş binary formatı kullanıyorsa `native-view`
profilini seçin. Bu profil rastgele JSON veya Hessian verisini çözmez. Normal JSON
API'lerinde record DTO kullanmaya devam edin.

| Endpoint | Java ne yapar? |
| --- | --- |
| `POST /api/v1/accounts/view` | Rust buffer'ındaki alanları okur. Doğrular. JSON'u doğrudan yazar. |
| `POST /api/v1/accounts/dto` | Aynı veriyi okur. Record ve String oluşturur. Sonra JSON yazar. |
| `GET /api/v1/accounts/health` | Servis durumunu döndürür. Bu profilde Redis veya veritabanı yoktur. |

DTO endpoint'i nesne oluşturma maliyetini gösterir. İş kararı ve doğrulama her iki
yolda da Java'dadır. JSON'dan okunan değerler aynıdır. Kontrol karakterleri farklı
geçerli escape biçimleriyle yazılabilir.

Profilin kaynakları, ayarları, testleri ve `target-native-view` çıktısı ayrıdır.
Cache starter yerine REST starter kullanır. Normal ve public-edge JAR'larına hesap
endpoint'leri eklenmez. Ortak modeldeki sade record, serializer'ı otomatik kaydetmez.

## Örneği Çalıştırın

Java 21, Maven 3.9+, boş 8080 portu ve REST/platform 4.8.0,
model 0.5.0 paketlerine GitHub Packages erişimi gerekir. Sample dizininde seçili
profili derleyin. Framework/model kaynağını değiştirdiyseniz önce
eşleşen paketleri install edin; yayımlanmış paket için kaynak install gerekmez.

```shell
mvn -B -ntp -Pnative-view clean package -DskipTests
mvn -B -ntp -Pnative-view dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target-native-view/lib
```

Bunlar derleme komutlarıdır. Test yapıldığı anlamına gelmez. GitHub Packages erişimi
için ana README'deki Maven ayarlarını kullanın. Bir terminalde servisi başlatın:

```shell
mvn -Pnative-view exec:java
```

İkinci PowerShell terminalinde istek hazırlayın:

```powershell
$cp = 'target-native-view/rest-sample-cache-reader-0.8.0.jar;target-native-view/lib/*'
java -cp $cp com.reactor.sample.nativeview.AccountPayloadTool account.bin 'İstanbul'
curl.exe -H 'Content-Type: application/octet-stream' --data-binary '@account.bin' http://localhost:8080/api/v1/accounts/view
curl.exe -H 'Content-Type: application/octet-stream' --data-binary '@account.bin' http://localhost:8080/api/v1/accounts/dto
```

Linux'ta classpath ayıracı olarak `;` yerine `:` kullanın. `curl.exe` yerine `curl`
yazın. Dosya üreten araç çalışan bir servise veya Redis'e bağlanmaz. JNI'yi de
başlatmaz. Alanların byte konumlarını elle yazmak yerine `AccountView_NativeWriter` kullanır.

Yanıtta `id`, `code`, `status`, `balanceCents` ve `active` alanları bulunur.
Gönderen ve alan uygulamada schema sürümünü ve enum sırasını aynı tutun.
Formatı değiştirirken üretilmiş `META-INF/reactor/contracts.tsv` dosyalarını karşılaştırın.

## Ödünç Veriyi Güvenli Kullanın

Senkron handler dönene kadar buffer'ın sahibi Rust'tır. Önce view'ı kapatın:

```java
try (var account = AccountView_NativeView.threadLocalView(body, 0, bodyLength)) {
    // DTO veya String oluşturmadan alanları okuyabilirsiniz.
    AccountView_NativeWriter.writeJson(account, output);
}
```

Tam örnek `src/native-view/java/com/reactor/sample/nativeview/AccountHandler.java`
dosyasındadır. Bu kod kapasite tekrarını da yönetir. `@RawRequestData`, kullanılmayan
path/query/header String'lerini kapalı tutar. Direct-buffer imzası istek gövdesini yine alır.

View, slice veya backing buffer'ı servis alanında saklamayın. Asenkron işe göndermeyin.
Handler döndükten sonra kullanmayın. Kapatma, Java referanslarını bırakır. Asıl alanı
JNI çağrısı bitince Rust bırakır. Kapalı view okunamaz. Aynı thread'de önceki okuma
kapanmadan yeni okuma açılamaz. Tekrar yazmadan önce yarım JSON kullanılmaz.
Bu örnekteki dönüşümün iş yan etkisi yoktur.

Tahsisat testi view okuma ve üretilmiş JSON yazma döngüsünü ölçer. Bütün HTTP/JNI
yolunda sıfır nesne oluşturulduğunu söylemez. Float/double biçimlendirme ve açıkça
çağrılan `materializeString()` nesne oluşturabilir.

## Sınırlar

Ayrı profilin tam ayarları şöyledir:

```properties
reactor.runtime.profile=micro-rest
reactor.rust.http.max-request-body-bytes=4096
reactor.rust.http.max-response-body-bytes=8192
reactor.rust.route-budget.account-read.route-admission.max-concurrent=64
reactor.rust.route-budget.account-read.route-admission.queue-timeout-ms=125
```

Boyut ayarları istek ve yanıt verisini sınırlar. Büyük istek 413 ile reddedilebilir.
Aynı anda 64 hesap dönüşümü çalışır. Fazlası en çok 125 ms bekler. Yoğunlukta 503
dönebilir. Yeni JNI worker veya sınırsız kuyruk eklenmez.
Hatalı format, UTF-8, enum veya iş alanı 400 döndürür. Hesap id değeri pozitif
olmalıdır. `code`, UTF-8 olarak 1..1024 byte içermelidir.

Kendi payload, başarılı RPS, p99 ve RSS ölçümünüzü görmeden sınırları büyütmeyin.
