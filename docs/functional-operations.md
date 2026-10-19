# Freshness And Failure Policy

[English](functional-operations.md) | [Turkish](functional-operations.tr.md)

Development checkout; rebuild the local libraries first. Published versions do not yet contain these changes.

## Customer Details Must Be Fresh

Add to your external properties file:

```properties
sample.errors.common-contract=true
reactor.rust.errors.format=problem-details
sample.cache.customer.detail.max-age-ms=120000
sample.cache.customer.detail.serve-stale=false
```

The default age limit is zero: existing behavior is unchanged.
These settings reject detail snapshots older than two minutes with HTTP 503.
A missing customer in a fresh snapshot returns 404. Redis failure returns 503.
The JSON body is not parsed again to decide its age.
Age starts when the writer begins building the snapshot.
Keep its refresh interval plus build duration comfortably below this bound.

For a catalogue that may temporarily show old data, set `serve-stale=true`.
Successful stale responses have `X-Cache-Stale: true` with the common error adapter.
Expired Redis data still cannot be served.

Each projection may have a separate limit. A root-level
`sample.cache.customer.max-age-ms` supplies the shared limit.
Specific runtime overrides win, then global runtime overrides, then specific file values.
Do not change writer TTL and reader age independently without checking their relationship.
