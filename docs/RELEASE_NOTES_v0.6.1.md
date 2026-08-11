# rest-sample-cache-reader 0.6.1

`0.6.1` aligns the runnable Redis read sample with `rust-java-rest:4.3.0`,
`java-rust-cache:0.7.1`, and `rust-sample-model:0.4.1`.

- REST endpoints and Redis read behavior are unchanged.
- The application keeps the read-only cache runtime surface.
- Startup, generated route, and native provenance improvements come from the aligned platform.

Build and run:

```powershell
mvn clean verify
mvn exec:java
```
