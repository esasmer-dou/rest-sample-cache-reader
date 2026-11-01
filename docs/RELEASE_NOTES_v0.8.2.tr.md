# rest-sample-cache-reader 0.8.2

[English](RELEASE_NOTES_v0.8.2.md) | [Türkçe](RELEASE_NOTES_v0.8.2.tr.md)

Koordineli güncelleme: 1 Kasım 2026.

## Bağımlılıklar

Cache `0.9.2` ile hizalayan `com.reactor:rust-java-platform-parent:4.8.2` parent'ını
kullanın. Mevcut uygulama POM'u için bağımlılık tanımları:

```xml
<dependency>
  <groupId>com.reactor</groupId>
  <artifactId>rust-java-starter-cache-reader</artifactId>
  <version>4.8.2</version>
</dependency>
<dependency>
  <groupId>com.reactor.sample</groupId>
  <artifactId>rust-sample-model</artifactId>
  <version>0.5.2</version>
</dependency>
```

Mevcut compiler/profil ayarlarını koruyun. Kimlik doğrulamalı bağımlılık erişimi için
[Maven erişimine](../README.tr.md#maven-package-erişimi) bakın;
`mvn -B -ntp -DskipTests clean package` ile yeniden paketleyin.
Testlerin atlanması yalnız paketleme içindir; kabul kanıtı değildir.

## Mevcut Örnekler

[CustomerCacheHandler](../src/main/java/com/reactor/sample/cache/reader/handler/CustomerCacheHandler.java),
hem `CacheReadResult` hem `CacheReadValue` için `CacheResponses.reply(..., COMMON_ERRORS)`
kullanır. Detail/segment ikinci tipi kullanır: Redis-only okuma byte dizisini aktarır;
isteğe bağlı L1 hit'i, yazma veya iptal sonrası bırakılan native yanıt sahipliğini devreder.
Açık bayrak, eski JSON ile problem details seçimini korur. L1 ilk kez `0.8.0` ile
sunuldu; varsayılan olarak kapalıdır ve Java handler'ı atlayan Rust edge yolu değildir.
[L1 tarifine](../README.tr.md#isteğe-bağlı-l1-okuma) ve [Compose başlangıcına](../README.tr.md#compose) bakın.

## Geçiş Ve Kanıt

JNI ABI, HTTP rotası veya ayar anahtarı geçişi gerekmez. Hizalı bağımlılığın paketlediği
native dosyayı kullanın; eski SO/DLL dosyasını kopyalamayın.
Altı servisli Compose smoke, performans/soak garantisi değildir; native overlay yalnız
katalog sunar. Bu notlar yayın veya tam kabul kanıtı değildir; ilgili adayın kesin
doğrulama kaydını esas alın. Hosted platform uyumluluğu ile EmaTech performans/soak
kanıtı ayrı değerlendirilir.
