# rest-sample-cache-reader 0.5.0

`0.5.0`, deklaratif ve read-only Redis snapshot uygulama modelini gösterir.

## Neler Değişti?

- `rust-java-rest:4.1.0`, `java-rust-cache:0.6.0` ve `rust-sample-model:0.3.1` kullanılır.
- `@EnableRustCache` tek managed native cache lifecycle oluşturur.
- `@GenerateProjectionReader`, ID, index, metadata ve metrics read metotlarını üretir.
- `@ReactorApplication`, elle yazılmış reader module sınıfının yerini alır.
- Constructor injection kullanan `@RestController` handler'larının endpoint davranışı değişmez.

## Uyumluluk

Redis key'leri, projection namespace'leri, response JSON, readiness davranışı ve REST adresleri
değişmedi. Process read-only kalır; PostgreSQL, scheduler veya writer kaynağı eklenmez.

## Çalıştırma

```powershell
mvn clean package
mvn exec:java
```

Release JAR'ı uygulama sınıflarını içerir. Runtime bağımlılıklarının ve native cache kütüphanesinin
doğru yüklenmesi için yukarıdaki Maven komutunu veya dokümandaki jlink image'ını kullanın.
