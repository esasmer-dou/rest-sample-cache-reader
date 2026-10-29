# Binary Account To JSON

[English](NATIVE_VIEW.md) | [Turkish](NATIVE_VIEW.tr.md)

This isolated source profile uses the published REST/codegen `4.8.0` APIs.
The sample itself is not bundled into the framework JAR. The default Redis reader
stays unchanged. See [feature selection/read costs](https://github.com/esasmer-dou/rust-java-rest/blob/master/docs/feature-examples.md).
This profile describes the current checkout, not the contents of a previously
published sample 0.8.0 JAR; build the source profile below.

## Contents

1. [Choose The Path](#choose-the-path)
2. [Run It](#run-it)
3. [Keep The Borrow Safe](#keep-the-borrow-safe)
4. [Limits](#limits)

## Choose The Path

Use `native-view` when the producer already sends this generated binary layout.
It does not decode arbitrary JSON or Hessian. Keep record DTOs for ordinary JSON APIs.

| Endpoint | Java work |
| --- | --- |
| `POST /api/v1/accounts/view` | Read the Rust buffer, validate fields and write JSON directly. |
| `POST /api/v1/accounts/dto` | Read the same buffer, create a record and String, then write JSON. |
| `GET /api/v1/accounts/health` | Return health. No Redis or database is used in this profile. |

The DTO endpoint demonstrates materialization cost. Both keep business validation in
Java. Their decoded JSON values match. Control characters can use different valid escapes.

This Maven profile has separate sources, resources, tests and `target-native-view`
output. It uses the REST starter, not the cache starter. Normal and public-edge
JARs do not gain account routes. The shared plain record does not auto-register a serializer.

## Run It

Use Java 21, Maven 3.9+, a free port 8080 and authenticated
GitHub Packages access to REST/platform 4.8.0 and model 0.5.0. From this sample
directory build the selected profile. Install matching framework/model sources
first only if you changed them; published artifacts need no source install.

```shell
mvn -B -ntp -Pnative-view clean package -DskipTests
mvn -B -ntp -Pnative-view dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target-native-view/lib
```

These are build commands, not test evidence. For GitHub Packages access, use the Maven
settings in the main README. Start the service in one terminal:

```shell
mvn -Pnative-view exec:java
```

Create a request in a second PowerShell terminal:

```powershell
$cp = 'target-native-view/rest-sample-cache-reader-0.8.0.jar;target-native-view/lib/*'
java -cp $cp com.reactor.sample.nativeview.AccountPayloadTool account.bin 'İstanbul'
curl.exe -H 'Content-Type: application/octet-stream' --data-binary '@account.bin' http://localhost:8080/api/v1/accounts/view
curl.exe -H 'Content-Type: application/octet-stream' --data-binary '@account.bin' http://localhost:8080/api/v1/accounts/dto
```

On Linux, replace the classpath separator `;` with `:` and use `curl`.
The offline tool needs no server, Redis or JNI activation. It uses the generated
`AccountView_NativeWriter`, not handwritten offsets.

The response fields are `id`, `code`, `status`, `balanceCents` and `active`.
Keep producer/consumer schema versions and enum order aligned. Compare generated
`META-INF/reactor/contracts.tsv` manifests before changing the binary contract.

## Keep The Borrow Safe

Rust owns the input buffer until the synchronous handler returns. Close the view first:

```java
try (var account = AccountView_NativeView.threadLocalView(body, 0, bodyLength)) {
    // Read fields without constructing a DTO or materializing account.code().
    AccountView_NativeWriter.writeJson(account, output);
}
```

See `src/native-view/java/com/reactor/sample/nativeview/AccountHandler.java` for the
complete route, including capacity retry. `@RawRequestData` disables unused raw
path/query/header strings. The direct-buffer signature still consumes the request body.

Do not save a view, slice or backing buffer in a field or asynchronous task.
Do not use it after the handler returns. Closing detaches Java references; Rust releases
the storage after JNI returns. Closed reads and overlapping same-thread borrows fail.
Discard partial JSON before retry. This sample's projection is side-effect-free.

The allocation gate covers the view/getter/generated-JSON loop, not the whole HTTP/JNI
path. Float/double formatting and explicit `materializeString()` can allocate.

## Limits

The separate profile contains these complete settings:

```properties
reactor.runtime.profile=micro-rest
reactor.rust.http.max-request-body-bytes=4096
reactor.rust.http.max-response-body-bytes=8192
reactor.rust.route-budget.account-read.route-admission.max-concurrent=64
reactor.rust.route-budget.account-read.route-admission.queue-timeout-ms=125
```

The body limits bound request/response bytes. An oversized request can return 413.
Admission allows 64 active projections. Extra requests wait at most 125 ms; overload
can return 503. This does not add JNI workers or an unbounded queue.
Invalid layout, UTF-8, enum or business values return 400. The account id must be
positive; `code` must contain 1..1024 UTF-8 bytes.

Change limits only after measuring your payloads, useful RPS, p99 and RSS.
