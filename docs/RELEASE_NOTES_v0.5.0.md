# rest-sample-cache-reader 0.5.0

`0.5.0` shows the declarative, read-only Redis snapshot application model.

## What Changed

- Uses `rust-java-rest:4.1.0`, `java-rust-cache:0.6.0`, and `rust-sample-model:0.3.1`.
- `@EnableRustCache` creates one managed native cache lifecycle.
- `@GenerateProjectionReader` generates bound ID, index, metadata, and metrics reads.
- `@ReactorApplication` replaces the handwritten reader module.
- Constructor-injected `@RestController` handlers keep the same endpoint behavior.

## Compatibility

Redis keys, projection namespaces, response JSON, readiness behavior, and REST URLs are unchanged.
The process remains read-only and does not add PostgreSQL, scheduler, or writer resources.

## Run

```powershell
mvn clean package
mvn exec:java
```

The release JAR contains the application classes. Use Maven as above, or build the documented jlink
image, so runtime dependencies and the native cache library are available.
