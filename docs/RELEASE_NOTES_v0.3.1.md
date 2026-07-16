# rest-sample-cache-reader v0.3.1

This sample aligns with `rust-java-rest:3.4.1` and `java-rust-cache:0.4.1`.

- Uses the current `24/7/6` packaged native runtime.
- Keeps Redis in `read-only` mode, so unused native write pools are not allocated.
- Keeps all reader endpoints and native JSON response-handle behavior unchanged.
- This is a dependency and native provenance refresh; application code does not change.
