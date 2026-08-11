# rest-sample-cache-reader 0.6.1

`0.6.1`, çalışan Redis read sample'ını `rust-java-rest:4.3.0`, `java-rust-cache:0.7.1` ve
`rust-sample-model:0.4.1` ile hizalar.

- REST endpoint'leri ve Redis okuma davranışı değişmedi.
- Uygulama yalnız read-only cache runtime yüzeyini kullanır.
- Startup, generated route ve native provenance iyileştirmeleri uyumlu platformdan gelir.

Build alın ve çalıştırın:

```powershell
mvn clean verify
mvn exec:java
```
