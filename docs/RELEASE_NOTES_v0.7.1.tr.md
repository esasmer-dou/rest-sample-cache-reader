# rest-sample-cache-reader 0.7.1

[English](RELEASE_NOTES_v0.7.1.md) | [Türkçe](RELEASE_NOTES_v0.7.1.tr.md)

İsteğe bağlı bootstrap örnekleri agent 0.7.0 ile eşlendi. Normal uygulama REST 4.7.0 içindeki exporter'ı kullanır. Ek agent bağımlılığı gerekmez.

Platform 4.7.0, cache/Dubbo 0.8.0 ve ortak sözleşmeler 0.4.4 olarak kalır.
Mevcut endpoint, SQL, payload ve hata sözleşmeleri değişmez. Agent varsayılan olarak kapalıdır.
İş mantığı Java'da kalır. Agent açıksa sınırlı gönderim ve kaynak yönetimi Rust'tadır.

[README'deki kurulum](../README.tr.md) ve [agent kullanım rehberi](https://github.com/esasmer-dou/java-rust-glowroot-agent/blob/v0.7.0/docs/functional-operations.tr.md) adımlarını izleyin.
Agent'ın micro HTTP ölçümleri, bu uygulamanın her iş yükünde 3 MiB ek bellek altında kalacağını garanti etmez.
Daha ayrıntılı profilleri kendi iş yükünüzde ölçün.
