# HTTP/3 `h3spec` Integration

This module runs the upstream `h3spec` QUIC and HTTP/3 error-case suite against
Helidon during Maven `verify`.

It follows the same overall strategy as the `quic-interop-runner` work, but the
target here is protocol-focused negative testing instead of interop-container
shape validation.

## How It Works

`H3SpecIT` builds two Docker images and connects them on an internal Docker
network:

- `Dockerfile.h3spec` installs `h3spec` 0.1.13 in a container. Its Haskell
  base is digest-pinned, the exact upstream commit archive is checksum-verified
  before extraction, and Cabal resolves against a fixed Hackage index state.
- `Dockerfile.server` packages the already-built
  `helidon-tests-integration-quic-interop-runner` artifact and runs
  `io.helidon.tests.integration.quic.interop.Main`.

The test then:

1. Creates temporary PEM files and static content.
2. Starts the Helidon HTTP/3 server container with those files mounted into
   `/certs` and `/www`.
3. Executes `h3spec` inside the tool container against the server container over
   the Docker network.
4. Parses `h3spec` output for failed RFC cases and fails the build if any are
   reported.

Nothing in this module depends on a host Java runtime layout or on host
networking. The server under test and the protocol checker both run inside
containers.

## Current Scope

The module runs the full `h3spec` suite without exclusions. It covers QUIC,
TLS, HTTP/3, and QPACK negative cases and requires every RFC example to pass.

## Run The Module

```bash
mvn -f tests/integration/pom.xml -pl h3spec -am verify
```

That command also builds `quic-interop-runner`, because this module packages its
artifact into the server image.

## Debug Output

The test stores the latest raw `h3spec` output in:

```text
target/h3spec-output.txt
```

and the corresponding server logs in:

```text
target/h3spec-server.log
```
