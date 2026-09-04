# `h3i-runner`

This crate is the protocol-driving side of the Helidon `tests/integration/h3i`
module.

It exists so the Java integration test can stay small and Docker-focused while
the detailed malformed HTTP/3 assertions live next to the `h3i` actions that
produce them.

The binary accepts:

```text
h3i-runner <scenario> <host> <port>
```

and currently implements:

- `content-length-mismatch`
- `reserved-http2-setting-on-control-stream`
- `uppercase-header-name`

Each scenario establishes an HTTP/3 connection with certificate verification
disabled, drives a specific malformed or edge-case exchange, validates the
result, and prints the final `ConnectionSummary` as JSON for the surrounding
Java test to archive.
