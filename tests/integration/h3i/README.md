# HTTP/3 `h3i` Integration

This module runs a small, pinned `h3i`-based malformed-peer suite against the
Helidon HTTP/3 server during Maven `verify`.

It exists because `quic-interop-runner` is strong at real implementation
interop, and `h3spec` is strong at protocol-suite coverage, but neither gives
us the same control over illegal frame ordering, literal header encoding, or
stream-level error paths that `h3i` exposes.

## How It Works

`H3iIT` builds two Docker images and connects them on an internal Docker
network:

- `Dockerfile.server` packages the already-built
  `helidon-tests-integration-quic-interop-runner` artifact and runs
  `io.helidon.tests.integration.quic.interop.Main`.
- `Dockerfile.h3i` builds the local `h3i-runner` Rust binary and keeps it in a
  tool container. Both stages are digest-pinned, Cargo verifies the locked
  crate graph, and APT resolves build and runtime packages from the immutable
  Debian snapshot recorded in `debian-snapshot.sources`.

The Java test then:

1. Creates temporary PEM files and static content.
2. Starts the shared Helidon HTTP/3 server container with those files mounted
   into `/certs` and `/www`.
3. Executes `h3i-runner` inside the tool container for each scenario.
4. Fails the build if any scenario exits nonzero and stores the raw per-scenario
   output under `target/h3i/`.

Both the server and the malformed client run inside containers. Nothing depends
on the host Java runtime layout, a host Rust toolchain, or host networking.

## Current Scope

The initial `h3i` suite covers three external-peer cases that are awkward to
express elsewhere:

- `content-length-mismatch`
- `reserved-http2-setting-on-control-stream`
- `uppercase-header-name`

The first two validate server behavior that Helidon already handles in local
tests. The content-length and uppercase-name scenarios require the
`H3_MESSAGE_ERROR` stream error mandated by RFC 9114. The uppercase-name
scenario exercises a real interoperability bug: literal uppercase header names
sent by an external peer were previously normalized and accepted instead of
being rejected.

## Run The Module

```bash
mvn -f tests/integration/pom.xml -pl h3i -am verify
```

That command also builds `quic-interop-runner`, because this module packages its
artifact into the server image.

## Debug Output

The test stores per-scenario output in:

```text
target/h3i/
```

and the aggregated server logs in:

```text
target/h3i/server.log
```
