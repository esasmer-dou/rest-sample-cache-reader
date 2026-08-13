# rest-sample-cache-reader 0.6.2

`0.6.2`, çalışan cache reader uygulamasını `rust-java-rest:4.4.0`,
`java-rust-cache:0.7.2` ve `rust-sample-model:0.4.1` ile hizalar.

- REST endpoint'leri, Redis key'leri, projection kontratları ve read-only davranış değişmez.
- Native runtime temiz CI provenance kontrolünden geçer. REST ABI `28`, Redis ABI `6` ve Glowroot
  ABI `1` kullanır.
- Sınırlandırılmış Glowroot HTTP/native Redis telemetry kullanılabilir; varsayılan olarak kapalıdır.

