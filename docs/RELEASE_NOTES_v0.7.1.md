# rest-sample-cache-reader 0.7.1

[English](RELEASE_NOTES_v0.7.1.md) | [Türkçe](RELEASE_NOTES_v0.7.1.tr.md)

Optional bootstrap examples now use agent 0.7.0. Normal applications use the embedded REST 4.7.1 exporter; no extra agent dependency is needed.

Platform moves to 4.7.1; cache 0.8.1, Dubbo 0.8.0 and shared contracts 0.4.4.
The updated cache native runtime also fixes rapid telemetry close/restart ownership.
Existing endpoints, SQL, payloads and error contracts do not change. Telemetry remains disabled by default.
Java keeps business logic. Enabled telemetry uses bounded Rust export and resource management.

Follow the [README setup](../README.md) and [agent guide](https://github.com/esasmer-dou/java-rust-glowroot-agent/blob/v0.7.0/docs/functional-operations.md).
The agent's micro HTTP measurements are not a guarantee of under 3 MiB incremental memory for every workload in this application.
Measure richer profiles with your own workload.
