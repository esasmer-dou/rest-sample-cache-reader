# rest-sample-cache-reader 0.6.2

`0.6.2` aligns the runnable cache reader with `rust-java-rest:4.4.0`,
`java-rust-cache:0.7.2`, and `rust-sample-model:0.4.1`.

- REST routes, Redis keys, projection contracts, and read-only behavior are unchanged.
- The packaged native runtime is clean-CI provenance checked and uses REST ABI `28`, Redis ABI `6`,
  and Glowroot ABI `1`.
- Bounded Glowroot HTTP/native Redis telemetry is available but remains disabled by default.

