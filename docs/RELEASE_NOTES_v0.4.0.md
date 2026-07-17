# rest-sample-cache-reader 0.4.0

`0.4.0` updates the sample to the declarative REST and cache library line.

## What's New

- Uses `rust-java-rest:4.0.0`, `java-rust-cache:0.5.0`, and `rust-sample-model:0.3.0`.
- Uses generated startup indexes instead of handwritten resource lists.
- Uses declarative projection readers and explicit cache response policies.
- Keeps the process read-only: no scheduler, database, Redis writer pool, or Dubbo runtime is added.
- Removes the duplicate local health handler and reuses the framework health component.

## Run

```powershell
mvn clean package
java -jar target/rest-sample-cache-reader-0.4.0.jar
```

Existing REST URLs and Redis projection keys remain unchanged.
