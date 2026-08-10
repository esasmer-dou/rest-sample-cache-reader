# rest-sample-cache-reader 0.6.0

`0.6.0` is the reference read-only Redis REST application for the `4.2.0` platform line.

## What Changed

- Uses `rust-java-platform-parent:4.2.0`, `rust-java-starter-cache-reader`,
  `java-rust-cache:0.7.0`, and `rust-sample-model:0.4.0`.
- Repeated runtime, codegen, and compiler declarations were removed from the application POM.
- `@EnableRustCache` and generated projection readers remain the declarative application surface.
- The process remains read-only; it does not create PostgreSQL, scheduler, lock, or writer resources.

## Run

```powershell
mvn clean verify
mvn exec:java
```

REST URLs, Redis key contracts, readiness behavior, and response JSON remain unchanged.
