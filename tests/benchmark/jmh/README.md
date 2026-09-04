# Benchmarks

## JMH

The `jmh` profile compiles and runs the benchmark tests in this module.

For the standalone controlled-host campaign covering the HTTP/3 client, HTTP/3 server, and QUIC implementation, see
[HTTP3_QUIC_LINUX_BENCHMARK_CAMPAIGN.md](HTTP3_QUIC_LINUX_BENCHMARK_CAMPAIGN.md).

The legacy webserver benchmark runner compares its current result with
`tests/benchmark/jmh/jmh-baseline.json`. It creates the baseline when the file does not exist and fails when a
regression exceeds its configured error margin, which is 15% by default.

```text
[ERROR] Failures:
[ERROR]   JunitJMHRunnerTest.renderResult:69
==================== HttpJMH.http2 (-31.41%)
     Baseline 44877.472 ops/s
      Current 30781.420 ops/s
HttpJMH.http2 regression detected. Error margin 10% (3078.14)
```

New benchmark classes belong under `tests/benchmark/jmh/src/main/java`. Benchmarks that use the legacy webserver
baseline runner end with `JmhTest`; a benchmark that needs specialized configuration may instead use a dedicated JUnit
runner under `src/test/java` and does not have to participate in the legacy baseline comparison.

### QPACK Huffman request decoding

`Http3QpackDecodingJmhBenchmark` decodes a fixed h2load-shaped request field section whose literal values use QPACK
Huffman encoding. The shared connection state and finite 16 KiB decoded-header limit exercise the same bounded decoder
path at one and eight threads. Run a short real-fork smoke check with:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3QpackDecodingJmhRunnerTest \
    -Dhttp3.qpack.decoding.jmh.forks=1 \
    -Dhttp3.qpack.decoding.jmh.warmupIterations=0 \
    -Dhttp3.qpack.decoding.jmh.measurementIterations=1 \
    -Dhttp3.qpack.decoding.jmh.measurementMillis=100 \
    -Dhttp3.qpack.decoding.jmh.gcProfiler=true test -ntp
```

For retained comparisons, use at least three warmup iterations, five measurement iterations, and multiple forks, with
distinct `http3.qpack.decoding.jmh.result` and `http3.qpack.decoding.jmh.output` paths for each source revision.

### JDBC client creation

The JDBC client creation benchmark isolates the statement-stage path before
connection acquisition. It compares the cached imperative path with the
compile-time marker-count path used by generated repositories for one repeated
SQL string and 32 pre-warmed SQL strings. By default its runner executes at 1,
8, 32, and 128 threads and writes one JSON result per thread count. A short
smoke run can use:

```shell
mvn test -Ptests,jmh -pl :helidon-data-jdbc,:helidon-tests-benchmark-jmh -am \
    -Dtest=JdbcClientCreationJmhRunnerTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Djdbc.client.creation.jmh.threads=1,8 \
    -Djdbc.client.creation.jmh.warmupIterations=1 -Djdbc.client.creation.jmh.warmupMillis=100 \
    -Djdbc.client.creation.jmh.measurementIterations=1 -Djdbc.client.creation.jmh.measurementMillis=100 -ntp
```

### Messaging runtime

The messaging benchmarks separate payload and batch construction, runtime dispatch, singleton-versus-batch
amortization, retained incoming-connector settlement, and saturated admission latency. Runtime results include the JMH
GC profiler. Saturation results compare a single-capacity messaging channel with a fair-lock same-work control at 1, 8,
and 32 callers. The sampled `messagingEmit` method returns only after a successful delivery, so transient `SATURATED`
retries are included in its caller-observed latency. An in-fork profiler reports aggregate `deliveries` and
`saturatedRetries` events from the exact same iterations for both sampled latency and throughput. Use
`emitPrebuiltMessagesIndividually` versus `emitPrebuiltBatch` for the equal-input batching comparison; `emitSingletons`
additionally measures the payload convenience path's per-message envelope construction.

Run a short executable smoke test with:

```shell
mvn -ntp test -Ptests,jmh \
    -pl :helidon-messaging,:helidon-tests-benchmark-jmh -am \
    -Dtest=MessagingJmhRunnerTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Dmessaging.jmh.smoke=true
```

Run the evidence profile with three forks, five one-second warmups, eight one-second measurements, a fixed 1 GiB heap,
and separate saturation results for each caller count with:

```shell
mvn -ntp test -Ptests,jmh \
    -pl :helidon-messaging,:helidon-tests-benchmark-jmh -am \
    -Dtest=MessagingJmhRunnerTest -Dsurefire.failIfNoSpecifiedTests=false
```

The runner writes `target/messaging-runtime-jmh-result.json` and
`target/messaging-saturation-jmh-result-t{1,8,32}.json`. Runtime operations parameterized by `batchSize` process that
many messages per invocation; multiply operations per second by `batchSize` for messages per second and divide
`gc.alloc.rate.norm` by `batchSize` for bytes per message. Saturation JSON contains sampled p50, p90, p99, and p99.9
latency plus delivery and retry totals collected from each result's own trial. The event totals are summed across
measurement iterations and forks; divide `saturatedRetries` by `deliveries` to obtain the retry ratio for that benchmark
result. Override defaults with `messaging.jmh.forks`,
`messaging.jmh.warmupIterations`, `messaging.jmh.warmupMillis`, `messaging.jmh.measurementIterations`,
`messaging.jmh.measurementMillis`, `messaging.jmh.heap`, `messaging.jmh.runtimeInclude`,
`messaging.jmh.saturationInclude`, `messaging.jmh.runtimeResult`, `messaging.jmh.saturationResultPrefix`, and
comma-separated `messaging.jmh.saturationThreads`. The two include properties are Java regular expressions matched
against fully qualified benchmark method names. For example, this selects one runtime and one saturation method while
still allowing both runner methods to complete:

```shell
'-Dmessaging.jmh.runtimeInclude=.*MessagingRuntimeJmhBenchmark\.emitPayload' \
    '-Dmessaging.jmh.saturationInclude=.*MessagingSaturationJmhBenchmark\.messagingEmit'
```

### Initial HTTP/3 request handoff

`QuicInitialRemoteStreamJmhBenchmark` compares the former publish-then-process sequence with applying the first STREAM
frame before publishing a newly created remote stream. `Http3InitialRequestHandoffJmhBenchmark` complements it by
measuring the HTTP/3 request-worker handoff when the request stream is published before or after its initial data. Both
dedicated runners execute one- and eight-thread cells. Run short real-fork smoke checks with:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicInitialRemoteStreamJmhRunnerTest \
    -Dquic.initial.remote.stream.jmh.forks=1 \
    -Dquic.initial.remote.stream.jmh.warmupIterations=0 \
    -Dquic.initial.remote.stream.jmh.measurementIterations=1 \
    -Dquic.initial.remote.stream.jmh.measurementMillis=100 test -ntp

mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3InitialRequestHandoffJmhRunnerTest \
    -Dhttp3.initial.request.handoff.jmh.forks=1 \
    -Dhttp3.initial.request.handoff.jmh.warmupIterations=0 \
    -Dhttp3.initial.request.handoff.jmh.measurementIterations=1 \
    -Dhttp3.initial.request.handoff.jmh.measurementMillis=100 test -ntp
```

For retained comparisons, use at least three warmup iterations, five measurement iterations, and multiple forks. The
following runs use five forks and save both JSON results and human-readable output; replace the output paths as needed:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicInitialRemoteStreamJmhRunnerTest \
    -Dquic.initial.remote.stream.jmh.forks=5 \
    -Dquic.initial.remote.stream.jmh.warmupIterations=3 \
    -Dquic.initial.remote.stream.jmh.warmupMillis=1000 \
    -Dquic.initial.remote.stream.jmh.measurementIterations=5 \
    -Dquic.initial.remote.stream.jmh.measurementMillis=1000 \
    -Dquic.initial.remote.stream.jmh.result=./target/quic-initial-remote-stream-jmh-result.json \
    -Dquic.initial.remote.stream.jmh.output=./target/quic-initial-remote-stream-jmh.log test -ntp

mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3InitialRequestHandoffJmhRunnerTest \
    -Dhttp3.initial.request.handoff.jmh.forks=5 \
    -Dhttp3.initial.request.handoff.jmh.warmupIterations=3 \
    -Dhttp3.initial.request.handoff.jmh.warmupMillis=1000 \
    -Dhttp3.initial.request.handoff.jmh.measurementIterations=5 \
    -Dhttp3.initial.request.handoff.jmh.measurementMillis=1000 \
    -Dhttp3.initial.request.handoff.jmh.result=./target/http3-initial-request-handoff-jmh-result.json \
    -Dhttp3.initial.request.handoff.jmh.output=./target/http3-initial-request-handoff-jmh.log test -ntp
```

Each runner also accepts an `include` regular expression and a `gcProfiler=true` switch under its respective property
prefix. Treat the direct QUIC benchmark as a relative manager-path comparison, not an absolute connection-ingress
measurement. Invocation-level Mockito fixture setup also makes its optional GC-profiler allocation data unsuitable for
attributing the timed path alone.

### QUIC ordered ACK publication

`QuicPathJmhBenchmark.orderedAckPublication` measures the normal application packet-space path that publishes a
growing, single contiguous ACK range. Each invocation feeds two consecutive ACK-eliciting 1-RTT packet numbers through
`PacketSpaceManager.packetReceived`; the second packet makes the ACK immediately publishable, and the benchmark reads
the immutable snapshot through the overdue-only `nextAckFrame(true)` path and verifies that both packets form one ACK
range. A fixed timeline plus a synchronous, non-transmitting, no-op-reschedule emitter lets the scheduler complete while
excluding packet transmission, timers, and network I/O. JMH normalizes throughput and allocation by the two received
packets.

Run bounded real-fork, GC-profiled one- and eight-thread cells separately. For a short smoke check, use:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicPathJmhRunnerTest \
    '-Dquic.path.jmh.include=^io\.helidon\.quic\.QuicPathJmhBenchmark\.orderedAckPublication$' \
    -Dquic.path.jmh.forks=1 \
    -Dquic.path.jmh.threads=1 \
    -Dquic.path.jmh.warmupIterations=0 \
    -Dquic.path.jmh.measurementIterations=1 \
    -Dquic.path.jmh.measurementMillis=100 \
    -Dquic.path.jmh.gcProfiler=true test -ntp
```

Repeat with `threads=8` and distinct `quic.path.jmh.result` and `quic.path.jmh.output` paths. For retained comparisons,
use at least three warmup iterations, five measurement iterations, and multiple forks.

### HTTP authority parsing

`UriAuthorityNormalizationJmhTest.sniAuthorityReparse` measures the former SNI check path that reparses a request
authority, while `sniAuthorityReuse` measures consuming the `UriAuthority` retained by HTTP/3 request decoding. The
dedicated runner executes only these two methods by default and verifies that both cells completed. Run a short smoke
check with:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=UriAuthorityNormalizationJmhRunnerTest \
    -Duri.authority.jmh.forks=1 \
    -Duri.authority.jmh.warmupIterations=0 \
    -Duri.authority.jmh.measurementIterations=1 \
    -Duri.authority.jmh.measurementMillis=100 test -ntp
```

For retained allocation and throughput evidence, run one- and eight-thread cells separately, with distinct output
paths. For example, the one-thread invocation is:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=UriAuthorityNormalizationJmhRunnerTest \
    -Duri.authority.jmh.forks=5 \
    -Duri.authority.jmh.threads=1 \
    -Duri.authority.jmh.warmupIterations=3 \
    -Duri.authority.jmh.warmupMillis=1000 \
    -Duri.authority.jmh.measurementIterations=5 \
    -Duri.authority.jmh.measurementMillis=1000 \
    -Duri.authority.jmh.gcProfiler=true \
    -Duri.authority.jmh.result=./target/uri-authority-normalization-jmh-t1.json \
    -Duri.authority.jmh.output=./target/uri-authority-normalization-jmh-t1.log test -ntp
```

Repeat with `threads=8` and different result and output paths. The runner also accepts an `include` regular expression
under the `uri.authority.jmh.` property prefix.

### gRPC streaming

The gRPC streaming benchmark covers the resource-owning server-streaming, client-streaming, and bidirectional APIs,
same-run legacy iterator baselines, mixed new-client/legacy-server and legacy-client/new-server pairs, and a deliberately
CPU-bound slow-consumer bidirectional path. Payloads exercise both sides of the client's readiness threshold. The JSON
result includes GC allocation metrics; compare each resource API method with its `legacy` counterpart for the same payload
size, then use the mixed pairs to attribute a difference to the client or server adapter. The legacy paths collect or eagerly
enqueue a whole stream, while the resource-owning paths apply bounded transport demand. Treat a legacy throughput advantage
as a buffering tradeoff rather than removing readiness checks; normalized allocation does not measure peak retained stream
data. The small-payload cases stream enough messages per RPC to amortize connection setup and keep a full multi-fork run below
ephemeral-port limits. Run a short smoke test with:

```shell
mvn test -Ptests,jmh -pl :helidon-http-http2,:helidon-webclient-grpc,:helidon-webserver-grpc,:helidon-tests-benchmark-jmh \
    -Dtest=GrpcStreamingJmhRunnerTest -Dsurefire.failIfNoSpecifiedTests=false \
    '-Dgrpc.streaming.jmh.include=.*GrpcStreamingJmhBenchmark.*' \
    -Dgrpc.streaming.jmh.warmupIterations=1 -Dgrpc.streaming.jmh.warmupMillis=100 \
    -Dgrpc.streaming.jmh.measurementIterations=1 -Dgrpc.streaming.jmh.measurementMillis=100 -ntp
```

For a full comparison, omit the gRPC JMH iteration and time properties and add
`-Dgrpc.streaming.jmh.forks=3`.

`GrpcTransportCompatibilityJmhBenchmark` uses only raw gRPC handlers and legacy client calls so the identical source
can run on both the pre-change and current trees. It fixes the message count and tests payloads immediately below, at,
and above the 64 KiB framed boundary. Run its per-operation methods with three forks and one thread on both trees. For
the eight-thread comparison, select only `bidirectionalSteadyState`; it keeps one long-lived stream per thread instead
of measuring new TCP connections until the operating system runs out of ephemeral ports. Compare throughput confidence
intervals and `·gc.alloc.rate.norm` from the JSON results. For the one-thread comparison, run:

```shell
mvn test -Ptests,jmh \
    -pl :helidon-http-http2,:helidon-webclient-http2,:helidon-webserver-http2,:helidon-grpc-core,:helidon-webclient-grpc,:helidon-webserver-grpc,:helidon-tests-benchmark-jmh \
    -Dtest=GrpcStreamingJmhRunnerTest -Dsurefire.failIfNoSpecifiedTests=false \
    '-Dgrpc.streaming.jmh.include=.*GrpcTransportCompatibilityJmhBenchmark\.(serverStreaming|clientStreaming|bidirectional|earlyClose)$' \
    -Dgrpc.streaming.jmh.payloadSizes=65530,65531 -Dgrpc.streaming.jmh.forks=3 \
    -Dgrpc.streaming.jmh.threads=1 -Dgrpc.streaming.jmh.warmupIterations=3 \
    -Dgrpc.streaming.jmh.warmupMillis=1000 -Dgrpc.streaming.jmh.measurementIterations=5 \
    -Dgrpc.streaming.jmh.measurementMillis=2000 \
    -Dgrpc.streaming.jmh.result=./target/grpc-transport-baseline-t1.json
```

Run it again in the current tree with the result name changed from `baseline` to `current`. The pre-change transport
cannot complete the 131,072-byte per-operation bidirectional case, so the comparable payload set stops at 65,531.
For concurrent steady-state streaming, run this command in both trees, again changing the result name for the current
tree:

```shell
mvn test -Ptests,jmh \
    -pl :helidon-http-http2,:helidon-webclient-http2,:helidon-webserver-http2,:helidon-grpc-core,:helidon-webclient-grpc,:helidon-webserver-grpc,:helidon-tests-benchmark-jmh \
    -Dtest=GrpcStreamingJmhRunnerTest -Dsurefire.failIfNoSpecifiedTests=false \
    '-Dgrpc.streaming.jmh.include=.*GrpcTransportCompatibilityJmhBenchmark.bidirectionalSteadyState.*' \
    -Dgrpc.streaming.jmh.payloadSizes=65530,65531,131072 -Dgrpc.streaming.jmh.forks=3 \
    -Dgrpc.streaming.jmh.threads=8 -Dgrpc.streaming.jmh.warmupIterations=3 \
    -Dgrpc.streaming.jmh.warmupMillis=1000 -Dgrpc.streaming.jmh.measurementIterations=5 \
    -Dgrpc.streaming.jmh.measurementMillis=2000 \
    -Dgrpc.streaming.jmh.result=./target/grpc-transport-baseline-steady-t8.json
```

### QUIC endpoint ingress

`QuicEndpointIngressJmhBenchmark` measures local loopback UDP ingress through the QUIC endpoint receive, heap-copy,
connection-ID lookup, and receiver-callback path. It also provides matched raw-UDP controls. It does not measure a
physical network, QUIC packet protection or decryption, congestion control, streams, TLS, HTTP/3, or application work.

`QuicEndpointIngressJmhRunnerTest` is an evidence runner, not a regression-threshold test. It intentionally skips unless
`quic.endpoint.ingress.jmh.include` selects a bounded scenario. Use an anchored include so an invocation cannot
accidentally run the entire matrix. The focused runner command expects current `27.0.0-SNAPSHOT` reactor artifacts in the
local Maven repository. From a clean checkout, first seed them with the repository's Java 26 baseline:

```shell
mvn -T 1C clean install -Ptests -DskipTests -ntp
```

Then run the selected benchmark without `-am` or `-amd`:

```shell
mvn -Ptests,jmh \
    -pl :helidon-quic,:helidon-webclient-http3,:helidon-tests-benchmark-jmh \
    -Dtest=QuicEndpointIngressJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    '-Dquic.endpoint.ingress.jmh.include=^io\.helidon\.quic\.QuicEndpointIngressJmhBenchmark\.rawUdpPacedWindow$' \
    -Dquic.endpoint.ingress.jmh.result=target/quic-raw-open-loop.json \
    -Dquic.endpoint.ingress.jmh.output=target/quic-raw-open-loop.log \
    -Dquic.endpoint.ingress.jmh.packetSize=64,1452 \
    -Dquic.endpoint.ingress.jmh.targetPps=50000,75000,100000 \
    test -ntp
```

Comma-separated values define a parameter matrix. The runner accepts the benchmark parameters `strategy`,
`routeCount`, `packetSize`, `batchSize`, `executorMode`, `routeAccess`, `offerMillis`, `targetPps`, `senderCount`,
`senderWorkingSet`, `drainMillis`, and `lateMicros`.

| Runner property suffix | Default | Meaning |
| --- | ---: | --- |
| `include` | required | Bounded JMH include expression; the test skips when absent or blank |
| `result` | `./target/quic-endpoint-ingress-jmh-result.json` | JSON result file |
| `output` | unset | Optional human-readable JMH output file |
| `forks` | `1` | Forked JVMs per configuration |
| `threads` | `1` | JMH workers; open-loop scenarios require exactly one |
| `warmupIterations` | `3` | Warmup iterations |
| `warmupMillis` | `500` | Duration of each warmup iteration |
| `measurementIterations` | `5` | Measurement iterations |
| `measurementMillis` | `1000` | Duration of each measurement iteration |
| `timeoutMillis` | `30000` | Per-iteration timeout |
| `gcProfiler` | `false` | Enable the standard JMH GC profiler |
| `profiler` | unset | Optional additional JMH profiler class or specification |
| `processAllocationProfiler` | `false` | Enable process-wide JFR allocation accounting |
| `processCpuProfiler` | `true` | Enable process-wide CPU-core accounting |

Prefix each suffix with `quic.endpoint.ingress.jmh.`.

For open-loop scenarios, `targetPps` is the aggregate rate across all senders. `senderCount` creates that many
persistent platform sender threads and connected UDP sockets. `senderWorkingSet` is the number of packet buffers per
sender. Endpoint runs register `routeCount` connection IDs but actively use `senderCount * senderWorkingSet` routes.
Sequences are globally interleaved across senders, and overdue senders skip expired sequence slots instead of replaying
a backlog.

The open-loop benchmark primary score is the offer-plus-drain invocation duration, not the capacity result. Use its
secondary metrics:

- `planned = attempted + schedulerSkipped`
- `attempted = accepted + socketRejected`
- `routed` counts accepted sequences observed by the raw receiver or endpoint route callback
- `missingAtDrainDeadline = accepted - routed`
- `drainArrivals` counts packets arriving after the offer deadline but within the drain window
- `late` applies `lateMicros` to delivered-packet latency
- duplicate, cross-window, and outside-window counters must remain zero
- sender minima, maxima, scheduler-skip ratios, and completion skew describe sender balance
- `accepted.Gbit` and `routed.Gbit` count UDP payload bytes only
- receiver and sender socket-buffer metrics record the effective `SO_RCVBUF` and `SO_SNDBUF` values

`deliveredLatency` is conditional on packets that arrived. If more than 1% of accepted packets are missing, its p99 is
censored and is not an all-accepted p99 or an SLA. `process.cpu.cores` covers the complete forked JVM, including senders,
receivers, executors, and harness work; it is not receiver-thread attribution.

The process CPU and JFR allocation profilers omit the final measurement because trial teardown contaminates it. Use at
least two measurement iterations. For allocation runs, disable CPU profiling so CPU-result construction is not included
in the JFR recording. Allocation `B/op` is per burst invocation; divide by `batchSize` for a per-packet estimate.
Profiled runs are supporting evidence and are not identical to unprofiled capacity runs.

These benchmarks establish a local platform baseline. Validate separately on supported operating systems and real
network paths before treating a loopback result as a portable capacity limit.

### HTTP/3 and QUIC evidence identity

The QUIC lifecycle, HTTP/3 small-write, and HTTP/3 adverse-network evidence runners require controlled build and source
manifests. The generator copies every tracked and non-ignored worktree file into an isolated temporary snapshot while
retaining the live Git HEAD and complete porcelain status. It clean-installs
`:helidon-tests-benchmark-jmh` and its complete upstream reactor closure only from that snapshot into a unique Maven
Resolver staging prefix. This first install builds production artifacts only, using Helidon's execution/invoker skip
together with Maven's test-compilation skip; the subsequent isolated benchmark-module test build proves that the complete
runtime closure is present. The build uses the captured HEAD as the deterministic SCM fallback, requires every controlled
Helidon JAR to contain that exact `Scm-Revision`, and records a canonical build log.
A fresh Maven/JVM then clean-compiles the benchmark against the staged prefix and verifies every Helidon SNAPSHOT runtime
JAR, selected source file, declared artifact, and explicit runtime-class-path entry. The unchanged staging tree is
atomically published under a content-addressed immutable Resolver prefix only after those checks pass.

JMH emits the complete `META-INF/BenchmarkList` registry in nondeterministic order, so directory hashing sorts only those
intact registry records; every record's contents and every other class or resource byte remain exact. Choose a durable,
absolute campaign root outside the Helidon checkout and every Maven `target` directory, then use Java 26 to run the generator
only after the intended source is final:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3QuicEvidenceManifestTest \
    -Dhelidon.benchmark.evidence.root=/absolute/path/to/http3-quic-campaign \
    test -ntp
```

The campaign root receives `http3-quic-controlled-build.manifest`,
`http3-quic-controlled-source.manifest`, and `http3-quic-controlled-build.log`. The generator prints the exact immutable
Resolver local prefix. The build manifest binds that prefix, its complete content digest, the shared remote prefix,
captured HEAD, Maven and Java versions, invocation digest, effective timeout, controlled-build processor cap, and
controlled-build log digest. The timeout defaults to 1800 seconds and can be set from 60 through 21600 seconds with
`helidon.benchmark.evidence.mavenTimeoutSeconds`. Nested Maven, compiler, annotation-processor, GC, JIT, and common-pool
thread sizing is capped at two active processors by default; set a value from 1 through 64 with
`helidon.benchmark.evidence.activeProcessorCount`. This is a JVM concurrency-sizing cap rather than an operating-system
CPU quota. The controlled subprocess rejects inherited `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`, or
`MAVEN_OPTS` and clears inherited `MAVEN_ARGS`, so unrecorded launcher options cannot change the build.
Each controlled Maven phase tracks its parent and observed descendants. Timeout, interruption, shutdown, output-reader
failure, or a surviving descendant triggers repeated bounded process-tree termination and awaiting while preserving
interrupt status. If termination cannot be proved, generation fails, retains the exact source snapshot and staging prefix
for inspection, and publishes no controlled prefix or campaign identity files.

The generator requires Maven Resolver split-local-repository mode and a nonblank remote prefix. Reserved workspace
worktrees supply both through `.mvn/maven.config`; otherwise set
`-Daether.enhancedLocalRepository.split=true` and
`-Daether.enhancedLocalRepository.remotePrefix=<shared-remote-prefix>` on the generator invocation.

Every evidence Maven invocation must select the printed local prefix and the recorded remote prefix, in addition to the
same campaign root:

```text
-Daether.enhancedLocalRepository.split=true
-Daether.enhancedLocalRepository.localPrefix=<printed-immutable-prefix>
-Daether.enhancedLocalRepository.remotePrefix=<recorded-remote-prefix>
-Dhelidon.benchmark.evidence.root=/absolute/path/to/http3-quic-campaign
```

If Maven uses a non-default local repository, pass that same `maven.repo.local` value as well. Missing or different
Resolver settings fail before a workload starts. Every non-smoke runner verifies the full identity before and after its
workload and publishes results only after the second verification. A later source, Git-status, controlled-repository,
build-log, build-manifest, loaded-artifact, or runtime-class-path change invalidates the campaign.

Before a workload starts, its runner atomically creates a distinct reservation marker for every final bundle path, so
concurrent reuse of a run ID fails without placing reservation text at a result-shaped path. A successful run removes
the markers and publishes exactly six direct children of the campaign root: JSON, workload log, effective-properties
metadata, source-manifest snapshot, build-manifest snapshot, and controlled-build-log snapshot. The JSON result is the
atomic final commit action. A complete run therefore has the JSON file and no surviving reservation marker; a run without
JSON or with any reservation marker is incomplete. JMH evidence installs one explicit canonical class path and verifies
its aggregate digest once in every fork, before any process profiler starts. Archive the complete campaign root as one
unit. Retain the exact immutable Maven prefix until collection and independent verification finish; cleanup must target
only that manifest-recorded prefix, never an active worktree prefix or a broad local-repository path.
Before publication, staged JSON, workload-log, and effective-properties text normalizes line endings and replaces exact
campaign, live-repository, Maven-repository, Java-home, working-directory, temporary-directory, Maven-home, and user-home
paths with semantic placeholders. Numeric measurements and semantic OS/JVM/Maven version metadata remain unchanged.

### QUIC handshake lifecycle

`QuicServerAdmissionJmhBenchmark` contains two full client/server lifecycle measurements over loopback UDP:

- `serverLifecycleHandshakeReady` measures cold or resumed handshake readiness, ending when both peers have a usable
  connection.
- `serverLifecycleReconnectReadyPeerTermination` measures the same handshake followed by peer termination and
  reconnect readiness. Use this boundary for process profiling because it contains the complete lifecycle cleanup.

Both methods cover `COLD` and `RESUMED` handshakes with Retry disabled, a rejected Retry token, and a retained Retry
token. They require one JMH worker. The benchmark uses bounded connection, termination, and executor cleanup deadlines
and validates that the requested path was actually exercised.

Run the handshake-ready distribution without a profiler:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=<printed-immutable-prefix> \
    -Daether.enhancedLocalRepository.remotePrefix=<recorded-remote-prefix> \
    -Dhelidon.benchmark.evidence.root=/absolute/path/to/http3-quic-campaign \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.serverLifecycleHandshakeReady$' \
    -Dquic.server.admission.jmh.evidence=true \
    -Dquic.server.admission.jmh.runId=<run-id> \
    -Dquic.server.admission.jmh.forks=2 \
    clean test -ntp
```

For allocation or CPU evidence, select `serverLifecycleReconnectReadyPeerTermination` and add exactly one of
`-Dquic.server.admission.jmh.processAllocationProfiler=true` or
`-Dquic.server.admission.jmh.processCpuProfiler=true`. Do not profile the handshake-ready boundary: profiler startup,
sampling, and cleanup change the latency distribution. Evidence mode requires at least two forks, three 500 ms
warmups, and five 1000 ms measurements; the defaults satisfy every minimum except `forks`.

The standard runner timing, profiler, and `scenario` properties use the `quic.server.admission.jmh.` prefix. `scenario`
accepts a comma-separated subset of the six names above. Keep all six for comparable full evidence. Evidence mode derives
all final paths from the campaign root and unique `runId`; independent result or output overrides are rejected. Every
evidence invocation exclusively claims its six-file JSON, log, properties, source-manifest, build-manifest, and
controlled-build-log bundle before JMH starts. The primary SampleTime distribution is end-to-end loopback lifecycle
latency, not packet-crypto cost or physical-network latency.

### HTTP/3 small writes

`Http3SmallWriteJmhBenchmark` runs a pure HTTP/3 client and server over loopback and measures four application-visible
paths:

- `bufferedUpload` and `bufferedDownload` coalesce writes through a 16 KiB application buffer.
- `eagerFrameUpload` flushes each upload chunk to request eager DATA-frame production; writes smaller than the client
  dispatch window are not synchronous dispatch barriers.
- `dispatchBarrierDownload` flushes every response chunk and includes the synchronous server dispatch barrier.

The bounded shape matrix is:

| Shape | Body | Application write |
| --- | ---: | ---: |
| `SHORT_8K_1` | 8 KiB | 1 B |
| `SHORT_8K_16` | 8 KiB | 16 B |
| `SHORT_8K_256` | 8 KiB | 256 B |
| `SHORT_8K_1200` | 8 KiB | 1200 B |
| `SHORT_8K_4096` | 8 KiB | 4096 B |
| `SUSTAINED_128K_1200` | 128 KiB | 1200 B |
| `SUSTAINED_128K_4096` | 128 KiB | 4096 B |

Run an unprofiled SampleTime distribution with a unique run ID:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3SmallWriteJmhRunnerTest \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=<printed-immutable-prefix> \
    -Daether.enhancedLocalRepository.remotePrefix=<recorded-remote-prefix> \
    -Dhelidon.benchmark.evidence.root=/absolute/path/to/http3-quic-campaign \
    -Dhttp3.small.write.jmh.mode=latency \
    -Dhttp3.small.write.jmh.runId=<run-id> \
    clean test -ntp
```

Repeat with `mode=allocation` and `mode=cpu` for supporting profiler evidence. The modes are:

| Mode | JMH mode | Purpose |
| --- | --- | --- |
| `smoke` | AverageTime | Minimal functional validation; no evidence claim |
| `latency` | SampleTime | Unprofiled latency distribution |
| `allocation` | AverageTime | Process-wide allocation-capacity counters plus sampled stack attribution |
| `cpu` | AverageTime | Process CPU time plus sampled stack attribution |

All non-smoke modes require a unique `http3.small.write.jmh.runId` and exclusively claim the complete six-file JSON, log,
properties, source-manifest, build-manifest, and controlled-build-log bundle before JMH starts. Use
`http3.small.write.jmh.shape` for a comma-separated subset; omit it for the complete matrix. The timing suffixes are
`forks`, `threads`,
`warmupIterations`, `warmupMillis`, `measurementIterations`, `measurementMillis`, and `timeoutMillis`, all with the
`http3.small.write.jmh.` prefix. Evidence requires one worker, at least two forks, three 500 ms warmups, and five
1000 ms measurements.

The four paths intentionally measure different API semantics. In particular, do not interpret eager upload versus
dispatch-barrier download as a client/server symmetry comparison. End-to-end allocation includes client, server, TLS,
QUIC, HTTP/3, JMH, and the benchmark's application buffers.

### HTTP/3 adverse network and reconnect cleanup

`Http3AdverseNetworkEvidence` uses a separate deterministic UDP impairment-proxy process between one HTTP/3 client and
server. It records successful 64 KiB downloads for these isolated and composite conditions:

| Scenario | Configuration |
| --- | --- |
| `CONTROL` | Forward without an injected impairment |
| `DELAY_JITTER` | 10 ms delay with up to 2 ms deterministic jitter |
| `LOSS` | 5% deterministic datagram loss |
| `REORDER` | 10% delayed reordering with a 20 ms reorder delay |
| `STALL` | 100 ms forwarding stall after 8 received datagrams |
| `COMPOSITE` | Delay/jitter, loss, reorder, and stall together |

Scenario order rotates and reverses between rounds. Every adverse observation uses the same seed as its round's
control, and exchange-phase proxy counters are captured before connection close. The result contains both raw samples
and paired adverse-minus-control exchange deltas. Evidence mode requires five warmups, 100 successful samples per
scenario, and 50 additional close samples while forwarding remains stalled. It reports nearest-rank p50, p95, and
maximum values with deterministic 2000-resample bootstrap 95% confidence intervals. The active-stall case first
establishes a clean HTTP/3 connection and blocks a one-byte request-body writer before it can write. It requires a quiet
and fully drained proxy interval, records both directional baselines, arms only against the next post-baseline client
datagram, and then releases that application write. The configured stall extends beyond the full close-relative
completion deadline, with an activation safety margin. The case requires the request to remain active at that wire
marker and to complete only after close begins, plus bounded client close and request abortion, complete proxy
accounting, bounded retained bytes/datagrams, and zero proxy overflow. Each active-stall sample uses and fully stops a
fresh server after the timed client-close interval, so peer idle timers and retained connections cannot contaminate a
later sample.

Run the complete evidence matrix with:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3AdverseNetworkEvidence \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=<printed-immutable-prefix> \
    -Daether.enhancedLocalRepository.remotePrefix=<recorded-remote-prefix> \
    -Dhelidon.benchmark.evidence.root=/absolute/path/to/http3-quic-campaign \
    -Dhttp3.adverse.network.mode=evidence \
    -Dhttp3.adverse.network.runId=<run-id> \
    clean test -ntp
```

The result is `<campaign-root>/http3-adverse-network-evidence-<run-id>.json`; an existing bundle is never overwritten.
Advanced properties use the `http3.adverse.network.` prefix: `warmups`, `samples`, `stalledCloseSamples`,
`requestTimeoutMillis`, `drainMillis`, `stalledCloseTimeoutMillis`, `stalledRequestCompletionTimeoutMillis`, and
`baseSeed`. Reducing the evidence minima is rejected. The `smoke` mode uses smaller defaults only for functional
validation; because its sample count is deliberately tiny, it verifies that reordering was selected but makes no claim
that a packet completed an actual overtaking event.

The entire workload runs in a child JVM. The parent requires progress after every sample and repeatedly terminates and
awaits the child and its observed proxy-process descendants within a fixed cleanup deadline when progress or final
termination exceeds its deadline. A survivor fails the run and retains any source/repository staging that could still be
in use. The complete six-file bundle—JSON result, workload log, effective properties, source-manifest snapshot,
build-manifest snapshot, and controlled-build-log snapshot—is published only after the child exits cleanly and the parent
re-verifies identity; the JSON result is the final atomic commit action.

Predeclare any regression threshold before collecting evidence. Compare like-for-like source identity, host, JVM,
mode, and shape. For adverse-network impact, use the paired exchange-delta distribution and its bootstrap interval;
for lifecycle and small writes, use the raw per-scenario SampleTime distribution. Do not set a portable performance
limit from these loopback results. Packet-protection benchmarks cover crypto separately, and
`Http3EligibilityJmhBenchmark` covers shared-cache route cardinality separately.

### Process-profiler interpretation

`ProcessAllocationProfiler` reports observed TLAB refill capacity, exact sizes for observed outside-TLAB allocation
events, and weighted JFR allocation samples. `process.alloc.capacity.*` combines the first two as a supporting
process-wide capacity counter; it is not an exact live-object or retained-byte total because an active TLAB can span a
recording boundary. Rates use MiB/second. Refill and outside-TLAB event counts are reported as events/second and
events/operation. Invalid recording intervals or operation counts fail allocation evidence instead of publishing an
empty metric set. `ProcessCpuProfiler` reports process CPU time as effective cores and ns/op plus JFR execution-sample
percentages. Allocation rates use the JFR recording's own start/stop interval. CPU counters are conservatively
wall-clock bracketed and CPU evidence fails instead of silently omitting totals when the platform counter is
unavailable.

Sample stacks are assigned to `webclient`, `webserver`, `http3`, `quic`, `harness`, or `other` by the first matching
Helidon frame. Component metrics are sampled attribution, not accounting totals. Use them to locate likely
contributors; use the allocation-capacity counters and process CPU time only as end-to-end supporting measurements.
Both profilers include client, server, TLS, executors, and harness work, add measurement overhead, and omit the final
measurement because JMH trial teardown would contaminate it. Keep at least two measurement iterations and do not
compare a profiled primary score directly with the unprofiled latency/capacity score.

## Troubleshooting the legacy baseline runner

If a legacy benchmark fails repeatedly without a code change, regenerate its baseline with
`mvn clean install -Pjmh -Dwebserver.jmh.resetBaseline=true`. The allowed regression margin can be changed, for example,
with `mvn clean install -Pjmh -Dwebserver.jmh.errorMargin=15`.
