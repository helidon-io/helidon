# QUIC Interop Harness

This module provides a runnable Helidon HTTP/3 server image and a real
`quic-interop-runner` smoke test for that image.

It was created to cover the gap between Helidon's in-process HTTP/3 tests and
the external interoperability workflow recommended in `quic-testing.md`. The
first version only proved that Helidon could be packaged in the filesystem-and-env
shape expected by `quic-interop-runner`. The module now takes the next step and
runs the upstream runner against the Helidon server for the `http3` testcase.

The Java harness serves static content from a filesystem directory over HTTP/3,
exposes a simple `POST /echo` endpoint for request-body protocol tests, and
uses PEM-based TLS material:

- `HELIDON_QUIC_INTEROP_WEB_ROOT` defaults to `/www`
- `HELIDON_QUIC_INTEROP_CERT_CHAIN` defaults to `/certs/cert.pem`
- `HELIDON_QUIC_INTEROP_PRIVATE_KEY` defaults to `/certs/priv.key`
- `HELIDON_QUIC_INTEROP_PORT` defaults to `443`
- `HELIDON_QUIC_INTEROP_HOST` defaults to `0.0.0.0`

The container wrapper in `run_endpoint.sh` currently supports:

- `ROLE=server`
- `TESTCASE=http3`

All other roles and test cases exit with status `127`, which matches the interop
runner convention for unsupported functionality.

## How The Test Works

The module now has three layers of automated coverage:

- `MainTest` verifies the Java harness directly in-process. It starts the
  server from `Main`, feeds it PEM files generated from Helidon's existing
  HTTP/3 test keystore, and confirms that a Java HTTP/3 client can fetch the
  configured welcome file.
- `InteropContainerIT` verifies the packaged container shape. It runs during
  Maven `verify`, after the jar and `target/libs` have been produced. The test
  builds the Docker image from this module's `Dockerfile`, copies temporary
  `/certs` and `/www` content into the container, starts `run_endpoint.sh` with
  `ROLE=server` and `TESTCASE=http3`, and runs the packaged JDK HTTP/3 probe
  against the listener from inside the container. This avoids depending on the
  host runtime's UDP port-forwarding implementation.
- `QuicInteropRunnerIT` executes the upstream `quic-interop-runner` against the
  Helidon endpoint image for the `http3` testcase, currently using the
  `quic-go` client implementation. It builds a dedicated `Dockerfile.runner-tool`
  image, starts a nested Docker daemon inside that tool container, builds the
  Helidon endpoint image inside the nested daemon, and then runs `python3 run.py`
  with a pinned upstream runner snapshot.

That third test is the important interop step. It exercises the upstream
runner's compliance checks, the network simulator, an external client
implementation, the Helidon endpoint image, and the runner's result export
together.

The automated `verify` path currently covers the supported happy path. The
unsupported wrapper branches are still easiest to validate manually with short
`docker run` probes, because they are simple one-shot exit-code checks:

- `ROLE=client TESTCASE=http3` should exit `127`
- `ROLE=server TESTCASE=handshake` should exit `127`

The endpoint image is Dockerfile-only. It does not copy `${java.home}` or any
other host runtime into the image. Instead, the Dockerfile uses a pinned,
multi-platform Eclipse Temurin OpenJDK 26 stage and copies
`/opt/java/openjdk` from that stage into the final interop image. The runner
smoke test also keeps its Python, Docker CLI, Compose, and Wireshark tooling
inside `Dockerfile.runner-tool`, so the host does not need a local Python or
`quic-interop-runner` installation.

## Package The Harness

```bash
mvn -f tests/integration/pom.xml -pl quic-interop-runner -am package
```

## Run The Full Module Verification

```bash
mvn -f tests/integration/pom.xml -pl quic-interop-runner -am verify
```

That command runs all three layers. The upstream-runner smoke test writes its
main artifacts to `target/quic-interop-runner/`, including:

- `docker-build.txt`
- `docker-compose-version.txt`
- `runner-output.txt`
- `results-root.txt`
- `results.json`
- `interop-output.txt`
- `runner-tool.spdx.json`

If the nested Docker daemon needs a registry mirror, set
`HELIDON_QUIC_INTEROP_REGISTRY_MIRROR` to the mirror URL before running Maven.
The setting applies only to the daemon inside the runner tool container.

## Run The Module Quality Gates

The module inherits from the normal integration-test project and explicitly
enables the repository Checkstyle, SpotBugs, and strict Javadoc profiles.
Checkstyle covers both main and test sources.

Run these commands from the repository root:

```bash
mvn -Ptests,checkstyle \
    -pl :helidon-tests-integration-quic-interop-runner \
    validate \
    -DskipTests
```

```bash
mvn -Ptests,spotbugs \
    -pl :helidon-tests-integration-quic-interop-runner \
    verify \
    -DskipTests
```

```bash
mvn -Ptests,javadoc \
    -pl :helidon-tests-integration-quic-interop-runner \
    package \
    -DskipTests
```

## Run The Jar Directly

```bash
java -jar \
  tests/integration/quic-interop-runner/target/helidon-tests-integration-quic-interop-runner.jar
```

## Build The Interop Image

```bash
docker build -t helidon/quic-interop-runner tests/integration/quic-interop-runner
```

That command expects `mvn ... package` or `mvn ... verify` to have run first,
because the Docker context consumes:

- `target/helidon-tests-integration-quic-interop-runner.jar`
- `target/libs/`

## Build The Runner Tool Image

```bash
docker build \
  -f tests/integration/quic-interop-runner/Dockerfile.runner-tool \
  -t helidon/quic-interop-runner-tool \
  tests/integration/quic-interop-runner
```

That image contains:

- a pinned upstream `quic-interop-runner` snapshot
- Docker-in-Docker for nested server, client, and simulator containers
- hash-locked Python dependencies required by the runner
- `tshark` and `editcap` for trace analysis
- the packaged Helidon server image build context under `/workspace`
- an SPDX 2.3 SBOM at
  `/usr/share/helidon/sbom/quic-interop-runner-tool.spdx.json`
- the normalized package identities used to validate that SBOM at
  `/usr/share/helidon/sbom/quic-interop-runner-tool.packages.json`

The runner-tool image pins or verifies its privileged-build inputs and fails
closed when downloaded content does not match the recorded hashes. Its Docker
base is pinned by multi-platform digest. The architecture-specific
`apk.runner-tool.*.lock` files record every APK in the recursively resolved
closure, including packages already supplied by the base. The build first
checks that the exact requested root versions are solvable, then verifies the
complete filename set and SHA-256 hashes before validating each Alpine package
signature and installing the closure offline. This intentionally performs a
locked partial distribution upgrade so the image does not retain vulnerable
base revisions merely because they were installed already.

The upstream runner archive is verified before extraction. The generated SPDX
is normalized to stable package identities, with only the architecture
qualifier removed from PURLs, and compared byte-for-byte with
`sbom.runner-tool.lock.json`. Package names, versions, distro and upstream PURL
qualifiers, and packages without PURLs remain part of that checked-in semantic
contract.
`requirements.runner-tool.lock` pins the complete Python dependency graph and
accepts only the recorded CPython 3.12 musl wheels for Linux amd64 and arm64.
The Docker base already supplies its checksum-verified Compose plugin, so the
image does not replace it from Alpine repositories.

When updating the runner snapshot or a dependency, update the corresponding
version and digest or hash in `Dockerfile.runner-tool`. Regenerate each APK lock
with the matching native architecture image, regenerate the Python lock for
both supported architectures, and build the `runner-tool-sbom` stage once on
amd64 and arm64. Both builds must produce the same normalized package JSON
before replacing `sbom.runner-tool.lock.json`. A byte-for-byte raw SPDX
comparison is not expected because SBOM creation metadata includes the build
time.

## Local Wrapper Smoke Check

This bypasses the simulator setup script and exercises only the wrapper's role and
testcase filtering:

```bash
cd tests/integration/quic-interop-runner
ROLE=client TESTCASE=http3 HELIDON_QUIC_INTEROP_SKIP_SETUP=1 ./run_endpoint.sh
```

Unsupported testcase gating can be checked the same way:

```bash
cd tests/integration/quic-interop-runner
ROLE=server TESTCASE=handshake HELIDON_QUIC_INTEROP_SKIP_SETUP=1 ./run_endpoint.sh
```
