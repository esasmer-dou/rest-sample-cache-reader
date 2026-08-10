# rest-sample-cache-reader 0.6.0

`0.6.0`, `4.2.0` platform çizgisi için read-only Redis REST referans uygulamasıdır.

## Neler Değişti?

- `rust-java-platform-parent:4.2.0`, `rust-java-starter-cache-reader`,
  `java-rust-cache:0.7.0` ve `rust-sample-model:0.4.0` kullanılır.
- Tekrar eden runtime, codegen ve compiler tanımları uygulama POM'undan kaldırıldı.
- `@EnableRustCache` ve generated projection reader'lar deklaratif kullanım yüzeyi olarak kalır.
- Process read-only'dir. PostgreSQL, scheduler, lock veya writer kaynağı oluşturmaz.

## Çalıştırma

```powershell
mvn clean verify
mvn exec:java
```

REST adresleri, Redis key kontratları, readiness davranışı ve response JSON değişmedi.
