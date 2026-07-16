# rest-sample-cache-reader v0.3.0

This sample aligns with `rust-java-rest:3.4.0` and `java-rust-cache:0.4.0`.

- Uses `reactor.cache.redis.access-mode=read-only`.
- Avoids allocating unused Redis write pools, permits, and topology state.
- Keeps native JSON response handles and existing REST endpoints unchanged.
- Refreshes the production overlay and Jlink workspace image.
