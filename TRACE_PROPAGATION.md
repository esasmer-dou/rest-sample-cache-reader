# Follow A Request Across HTTP Services

[English](TRACE_PROPAGATION.md) | [Turkish](TRACE_PROPAGATION.tr.md)

This source example is separate from the Redis reader. It uses the
`trace-propagation` Maven profile and the coordinated framework checkout.
The new codec/parent-ID changes are pending publication; do not assume the existing
`4.8.0` package already includes every change shown here.

## Contents

- [What runs](#what-runs)
- [Start both services](#start-both-services)
- [Call the order API](#call-the-order-api)
- [Choose the settings](#choose-the-settings)
- [Understand the boundaries](#understand-the-boundaries)

## What Runs

An order API calls an inventory HTTP service. Both use Java handlers and Rust I/O.
No Redis, database, security starter, shared model JAR or telemetry SDK is needed.

| Term | Meaning in this example |
| --- | --- |
| Trace ID | Identifies the whole request chain. |
| Span ID | Identifies the current handler invocation. |
| Parent span ID | Identifies the invocation that called this handler. |
| Retry | Another attempt of the same logical downstream request. |

Only the order routes have `@Traced`. `/app/health` remains an untraced Java control
route. The inventory service validates the downstream trace header.

## Start Both Services

Use the coordinated source build described in the framework README first.
From the sample repository, build only this profile:

```bash
mvn -B -ntp -Ptrace-propagation clean package -DskipTests \
  dependency:copy-dependencies -DincludeScope=runtime \
  -DoutputDirectory=target-trace-propagation/lib
```

On Linux, start the inventory service in one terminal:

```bash
java -Xms8m -Xmx48m -Xss256k \
  -Dserver.port=8082 -Dsample.api.enabled=false \
  -Dsample.inventory.enabled=true \
  -cp 'target-trace-propagation/rest-sample-cache-reader-0.8.0.jar:target-trace-propagation/lib/*' \
  com.reactor.sample.trace.TraceApplication
```

Start the order API in another terminal:

```bash
java -Xms8m -Xmx48m -Xss256k \
  -Dserver.port=8080 -Dsample.inventory.base-url=http://127.0.0.1:8082 \
  -Dsample.diagnostics.enabled=true -Dreactor.tracing.sample-ratio=1.0 \
  -cp 'target-trace-propagation/rest-sample-cache-reader-0.8.0.jar:target-trace-propagation/lib/*' \
  com.reactor.sample.trace.TraceApplication
```

These flags target Semeru/OpenJ9 Java 21. On Windows, use `;` instead of `:` in the
classpath. The diagnostic endpoints are internal examples. Keep them off public
ingress; they are not an authenticated production administration API.

## Call The Order API

```bash
curl -i http://localhost:8080/api/v1/orders/small \
  -H 'traceparent: 00-0123456789abcdef0123456789abcdef-fedcba9876543210-01'
curl -s http://localhost:8080/internal/trace/last
```

The first response is `{"stock":42}`. The last span has the supplied trace ID and
parent ID `fedcba9876543210`. Its own span ID is new. That new ID is what inventory
receives in the downstream header.

| Route | Use case |
| --- | --- |
| `GET /app/health` | A health call that is not traced. |
| `GET /api/v1/orders/small` | A small inventory response. |
| `GET /api/v1/orders/heavy` | The same flow with a larger JSON response. |
| `GET /api/v1/orders/retry` | A controlled retry demonstration. |
| `GET /internal/trace/last` | The latest sampled span only; diagnostics must be enabled. |
| `GET /internal/trace/metrics` | Native counters and sample counters; diagnostics must be enabled. |

After starting a fresh inventory service, call `/api/v1/orders/retry` **once** to
exercise its first `503 -> 503 -> 200` sequence. The service checks that all three
attempts carry the same header. Later calls return normally. This intentionally
single-sequence fixture is not a production failure policy or a concurrent retry
simulator. The order API uses `retries=2` only on this demonstration method.

To inspect the demonstration's Java HTTP counters, also start the order API with
`-Dreactor.metrics.collection-enabled=true`. Otherwise those counters remain zero
in memory-first mode; propagation still works. The sample exporter retains one
completed span and a count, not a history of every request.

## Choose The Settings

| Setting | Example value | When to use it |
| --- | --- | --- |
| `reactor.tracing.enabled` | `false` | Turn off tracing guards without changing handlers. |
| `reactor.tracing.annotated-only` | `true` | Trace only routes explicitly marked `@Traced`. |
| `reactor.tracing.sample-ratio` | `0.01` | Sample 1% of locally started root traces. Incoming sampling takes precedence. |
| `sample.inventory.base-url` | `http://inventory:8082` | Use the downstream service DNS in Docker/Kubernetes. |
| `sample.diagnostics.enabled` | `false` | Keep example diagnostic endpoints disabled in ordinary deployments. |

In Kubernetes, the equivalent variables include `REACTOR_TRACING_ENABLED`,
`REACTOR_TRACING_ANNOTATED_ONLY`, `REACTOR_TRACING_SAMPLE_RATIO` and
`SAMPLE_INVENTORY_BASE_URL`. The framework's runtime profile still controls worker,
queue and byte budgets. Tracing does not increase them or change the native ABI.

## Understand The Boundaries

The starter captures IDs, duration and completion status. It does not automatically
capture payloads, weave arbitrary methods or install the Glowroot agent.
The exporter shown here is for inspecting the example, not a remote collector.
Replace it with one bounded `TraceExporter` implementation for your destination.

The codec's caller-buffer writer allocates no temporary array. The complete HTTP
flow still creates a context, span, headers and an HTTP request. Do not interpret a
zero-allocation codec result as a zero-allocation framework request.

[Starter configuration and retry rules](https://github.com/esasmer-dou/rust-java-rest/blob/master/platform/starter-tracing/README.md).
