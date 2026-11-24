# Use Cache 0.9.3

[English](CACHE_0.9.3.md) | [Turkish](CACHE_0.9.3.tr.md)

Build this repository from `master` using the existing [Quick Start](../README.md#quick-start).
The POM overrides the cache library to `0.9.3` while retaining platform `4.9.0`.
No new settings are required. Keep the same Redis data and Cache Writer.

After starting the reader, check a customer ID that your writer has published:

```sh
curl --fail http://localhost:18080/api/v1/cache/customers/1
```

The JSON response does not change. Missing IDs still follow the existing cache
miss policy. The `v0.8.3` release tag and package are unchanged.
