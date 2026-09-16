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

### HPACK and QPACK static tables

`HttpStaticTableJmhBenchmark` measures one static-table operation per invocation, with average time in ns/op and GC
allocation in B/op. It calls the production tables through same-package benchmark code without changing production access.
Inputs and expected matches are checked during trial setup; header creation, randomization, and string hashing happen
outside measurement. The two protocols receive the same deterministic sequence of prebuilt header names and equal,
non-interned values.

- `hpackIndexedGet` and `qpackIndexedGet` read valid static indexes used for request/response fields and name references.
  They call each protocol's production indexed lookup. Some indexed entries have different values between protocols.
- `qpackArrayGetControl` reads a benchmark-only array containing the same QPACK entry objects. This is an array-access
  control, including the same input selection, rather than a replacement production implementation with identical checks.
- `hpackEncodingLookup` and `qpackEncodingLookup` find an exact match, fall back to a name match, or report a miss.
  They include match classification but exclude dynamic-table search, encoding-plan allocation, and wire/Huffman encoding.
  `NAME_ONLY` and `UNKNOWN` hold the match category constant. `REQUEST_REGULAR` and `RESPONSE_REGULAR` use the same headers
  with each protocol's actual table coverage, so QPACK can find additional exact matches.

`EXACT_COMMON` uses pseudoheader values present in both tables. HTTP/2 has dedicated encoding paths for these common
pseudoheaders that bypass table search, so this case compares lookup primitives, not complete pseudoheader encoding.
The request/response encoding mixes contain only regular headers. Results are steady-state lookup costs, not request
latency or protocol throughput predictions.

To compare QPACK lookup implementations, select only `qpackEncodingLookup` and use the same workload and measurement
settings for both revisions. This keeps the static-table contents fixed; the HPACK/QPACK comparison also varies table
contents and cannot isolate the cost of a collection implementation.

After preparing current reactor artifacts with the repository's build JDK, run the focused comparison from the root:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=HttpStaticTableJmhRunnerTest test
```

The runner selects only this class, uses three forks, three one-second warmups and five one-second measurements, and always
enables the GC profiler. Results default to `tests/benchmark/jmh/target/http-static-table-jmh-1.json`. Override timing,
`forks`, `threads`, `result`, or `output` with the `http.static.table.jmh.` prefix. Use distinct numbered output paths for
repeated runs. A short smoke run can set `forks=1`, `warmupIterations=0`, `measurementIterations=1`, and
`measurementMillis=100`. To select a subset, pair the method suffix expression with its supported workload values:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=HttpStaticTableJmhRunnerTest \
    '-Dhttp.static.table.jmh.methods=(hpack|qpack)EncodingLookup' \
    -Dhttp.static.table.jmh.workload=REQUEST_REGULAR,RESPONSE_REGULAR test
```

### QPACK request encoding

`Http3QpackEncodingJmhBenchmark.encodeRequestProtocolStaticOnly` calls `Http3Protocol.encodeRequestHeaders` for a complete
request HEADERS frame. It includes authority selection from `Host`, authority validation, pseudo-header creation, QPACK encoding,
and frame construction. URI and ordinary request fields are prepared once per trial; the `Host` value overrides the URI
authority. `sensitiveHost=false,true` selects ordinary and sensitive Host fields with identical values. The connection
context is reused with the peer dynamic table disabled and is closed after each trial. The returned byte array is consumed
by JMH; no bodies or network I/O are included.

This method reports average time in ns/op; enable the GC profiler for B/op. Existing methods that call QPACK directly with
prebuilt pseudo-headers retain their throughput mode and measure a different boundary. Run only the protocol method with:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3QpackEncodingJmhRunnerTest \
    '-Dhttp3.qpack.encoding.jmh.include=^io\.helidon\.http\.http3\.Http3QpackEncodingJmhBenchmark\.encodeRequestProtocolStaticOnly$' \
    -Dhttp3.qpack.encoding.jmh.sensitiveHost=false,true \
    -Dhttp3.qpack.encoding.jmh.gcProfiler=true \
    -Dhttp3.qpack.encoding.jmh.result=./target/http3-request-protocol-encoding-jmh-1.json test
```

The runner accepts `forks`, `warmupIterations`, `warmupMillis`, `measurementIterations`, `measurementMillis`, and `output`
under the same prefix. Use identical benchmark bytecode, JVM settings, and parameters against each production revision,
with distinct numbered result paths. Trial setup verifies the decoded request and logs the encoded authority's sensitivity;
older implementations that drop sensitive Host metadata remain measurable, so that case includes changed wire semantics.

### QPACK request and literal decoding

`Http3QpackDecodingJmhBenchmark` decodes a fixed h2load-shaped request field section with plain and Huffman-encoded literal
values. The shared connection state and finite 16 KiB decoded-header limit exercise the same bounded decoder path at one
and eight threads. `Http3QpackLiteralDecodingJmhBenchmark` isolates plain literal conversion at 16, 64, and 256 octets,
with ASCII or high Latin-1 octets and with or without a decoded-size limit. Both benchmarks include input-buffer creation;
the request benchmark also includes stream opening and completion. Enable the GC profiler to compare allocated bytes per
operation as well as time or throughput. Run a short real-fork smoke check with:

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

### QPACK instruction fragmentation and frame observation

`Http3QpackInstructionIngressJmhBenchmark.processEncoderInstruction` processes one complete literal insertion on a reused
connection, delivered as one-byte, 64-byte, or whole-instruction fragments. Trial setup builds the fragments and verifies
dynamic-table insertion through eviction; measurement includes parsing, buffering, and insertion. Select `nameSize`,
`valueSize`, and `fragmentation` independently to distinguish literal size from fragmentation cost.

`Http3FrameObservationJmhBenchmark.writeDataFrame` compares a raw QUIC writer, a permanent no-op HTTP/3 listener, and an
enabled metadata listener. It reuses buffers and writers, uses a constant-time sink, and measures whole or segmented DATA
frames without networking. `constructWriter` measures connection setup separately. The raw control uses equivalent
scheduler and underlying writer construction. Neither benchmark predicts network throughput.

After preparing reactor artifacts, select only the relevant runner and method. For example:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3QpackInstructionIngressJmhRunnerTest \
    -Dhttp3.qpack.instruction.ingress.jmh.nameSize=8,256 \
    -Dhttp3.qpack.instruction.ingress.jmh.valueSize=1024 \
    -Dhttp3.qpack.instruction.ingress.jmh.gcProfiler=true test

mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3FrameObservationJmhRunnerTest \
    '-Dhttp3.frame.observation.jmh.include=^io\.helidon\.http\.http3\.Http3FrameObservationJmhBenchmark\.writeDataFrame$' \
    -Dhttp3.frame.observation.jmh.payloadSize=8 \
    -Dhttp3.frame.observation.jmh.gcProfiler=true test
```

Both runners accept `include`, `forks`, `threads`, `warmupIterations`, `warmupMillis`, `measurementIterations`,
`measurementMillis`, `result`, and `output` under their respective prefixes. Use identical benchmark sources, JVM settings,
parameters, and dependencies for before/after comparisons, changing only the production implementation being measured.
Report GC-profiler allocated bytes per operation alongside timing, with distinct numbered result paths for each run.

### HTTP/3 message-head validation

`Http3MessageHeadValidationJmhBenchmark.readRequestHead` and `readResponseHead` measure complete valid head parsing,
including per-stream reader construction and close, with average time in ns/op and GC-profiler allocation in B/op.
Inputs use static request pseudo-headers (`GET`, `https`, `/`), a literal `localhost` authority, or static response status
`200`, plus 4 or 32 regular fields. Regular fields contain `content-length: 0` and literal values of 16 or 256 characters.
QPACK connection contexts are reused and closed after each trial; input encoding, bodies, and network I/O are excluded.
There are no invocation setup/teardown hooks contributing allocations outside the timed operation.

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3MessageHeadValidationJmhRunnerTest \
    -Dhttp3.message.head.validation.jmh.headerCount=4,32 \
    -Dhttp3.message.head.validation.jmh.valueSize=16,256 \
    -Dhttp3.message.head.validation.jmh.gcProfiler=true \
    -Dhttp3.message.head.validation.jmh.result=./target/http3-message-head-validation-jmh-1.json test
```

The runner accepts `include`, `forks`, `threads`, `warmupIterations`, `warmupMillis`, `measurementIterations`,
`measurementMillis`, `result`, and `output` under the same prefix. The GC profiler is enabled by default. Trial setup
adapts public reader factories with method handles: old signatures receive `validateHeaderValues=false`, while current
signatures enforce validation. Use identical benchmark bytecode, inputs, JVM settings, and parameters against each
production revision; setup logs the input SHA-256 hashes and any legacy factory selection. Differences include all
message-reader and QPACK changes between those revisions, so they do not isolate validation alone. Use distinct numbered
result paths for matched local diagnostic runs.

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

### QUIC ACK accounting

`QuicPathJmhBenchmark.establishedPathAckRanges` measures one real `PacketSpaceManager.processAckFrame` call in
microseconds per ACK. Flight construction, ACK-frame construction, and cleanup occur outside its timed invocation.
`ackPacketSpaceLifecycle=REUSED` retains one packet space per worker and primes its ACK workspace before measurement;
`FIRST_ACK` creates a new packet space before each invocation, exposing first-use ACK workspace allocation. These modes
use identical flights and reset recovery state through the existing packet-space cleanup API before each flight.

The fixture uses Handshake packet numbering to avoid random application-space packet-number skips, a fixed packet-space
clock to avoid setup-duration-dependent time-threshold loss, current-path congestion accounting, and the production
CUBIC controller. It does not send or retransmit datagrams. Packet-threshold loss detection and synchronous recovery
callbacks remain inside the measured operation. This isolates ACK accounting, not handshake or network performance.

One ACK range acknowledges the whole flight. Fragmented shapes acknowledge approximately half the packets, with positive
gaps and ranges spread from the first through the last packet. The bounded comparison matrix is:

| In-flight packets | Approximate flight size | ACK ranges |
| ---: | ---: | --- |
| 64 | 75 KiB | 1, 32 |
| 4096 | 4.69 MiB | 1, 32, 1024 |
| 13981 | 16 MiB | 1, 32, 1024 |

The largest flight is `floor(16 MiB / 1200)`, and 1024 ranges is the default incoming ACK-range limit. Defaults select
only `64 / 1 / REUSED / NEW_ACK`. The runner accepts comma-separated parameter values and rejects combinations exceeding
1024 ranges, `ceil(inFlightPackets / 2)` ranges, or the default flight limit. Run the small and larger flights separately
to avoid the invalid `64 / 1024` Cartesian combination. For example, after preparing reactor artifacts:

```shell
mvn -Ptests,jmh -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicPathJmhRunnerTest \
    '-Dquic.path.jmh.include=^io\.helidon\.quic\.QuicPathJmhBenchmark\.establishedPathAckRanges$' \
    -Dquic.path.jmh.inFlightPackets=64 \
    -Dquic.path.jmh.ackRangeCount=1,32 \
    -Dquic.path.jmh.ackPacketSpaceLifecycle=REUSED,FIRST_ACK \
    -Dquic.path.jmh.ackWorkload=NEW_ACK \
    -Dquic.path.jmh.threads=1 -Dquic.path.jmh.forks=2 \
    -Dquic.path.jmh.warmupIterations=3 -Dquic.path.jmh.warmupMillis=500 \
    -Dquic.path.jmh.measurementIterations=5 -Dquic.path.jmh.measurementMillis=1000 \
    -Dquic.path.jmh.result=./target/quic-ack-accounting-1.json \
    -Dquic.path.jmh.output=./target/quic-ack-accounting-1.log test
```

Repeat with `inFlightPackets=4096,13981` and `ackRangeCount=1,32,1024`, using the next numbered output paths. To check
duplicate ACK handling with no tracked packets, select `ackWorkload=DUPLICATE`, `ackPacketSpaceLifecycle=REUSED`,
`inFlightPackets=13981`, and `ackRangeCount=1,1024`. Duplicate trial setup prepares one flight, consumes its ACK, and clears
residual gap packets. Every measured invocation repeats that same ACK on the now-empty packet space; invocation setup is
a no-op and teardown only verifies callback accounting. This avoids flight reconstruction limiting duplicate-ACK JIT
warmup. `FIRST_ACK` cannot be combined with `DUPLICATE`.

For allocation, repeat the same explicit matrices selecting only `establishedPathAckRangesAllocation`. This method
brackets the production call with the JMH worker's `com.sun.management.ThreadMXBean` allocated-byte counter. Its
`allocatedBytes` and `ackOperations` auxiliary results are unnormalized JMH event totals; divide the former by the latter
for **bytes per ACK**. This includes synchronous callbacks on that worker and excludes fixture setup/cleanup. Unsupported
or unavailable allocation counters fail the run. Allocation-counter reads add timing overhead, so use only the separate
uninstrumented method for timing comparisons.

The runner rejects `gcProfiler=true` when either ACK method is selected: JMH's GC profiler includes invocation fixture
allocation and cannot isolate ACK allocation here. Even with operation-only timing and allocation boundaries, fixture
work can affect cache and GC conditions. Keep identical benchmark bytecode, JVM settings, matrix, and timing settings
for before/after runs, changing only the production implementation, and use distinct numbered outputs. Local results are
diagnostic; a controlled Linux run is needed for release-level performance claims.

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
local Maven repository. From a clean checkout, first seed them with the repository's Java 27 baseline:

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
absolute campaign root outside the Helidon checkout and every Maven `target` directory, then use Java 27 to run the generator
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

### QUIC diagnostic hot paths

`QuicDiagnosticsJmhBenchmark` compares the actual timer queue, packet-space deadline calculation,
connection outgoing-buffer pool, and endpoint sync/async send and receive-dispatch paths with `logLevel=OFF,DEBUG,TRACE`.
DEBUG and TRACE use a JUL handler retaining only a message count and the most recent message; it does not write to the console.
The fixture checks that `System.Logger` honors the selected level and restores logger levels, handlers, and parent routing
at trial teardown. Run one JMH worker in a fork because logging configuration is process-wide.

| Method | Measured operation |
| --- | --- |
| `timerOffer` | Offer and cancel one event with `queueDepth` other future timers retained. |
| `timerReschedule` | Reschedule, refresh, and cancel one future event with the same bounded timer population. |
| `packetDeadline` | Repeated `PacketSpaceManager.computeNextDeadline()` with `deadlineWorkload=IDLE,ACK,PTO`. |
| `outgoingBuffer` | Real `QuicConnectionImpl` buffer acquisition and release, including the datagram wrapper, with `bufferPool=true,false`. |
| `synchronousDatagram` | One actual synchronous endpoint send and sender completion over loopback. |
| `queuedDatagrams` | 64 real `QuicEndpoint.pushDatagram` submissions, reported per datagram, against a preserved async backlog. |
| `receiveDispatch` | 64 prepared datagrams through the endpoint's actual scheduled read loop, reported per datagram, with `receivePath=PACKET,STATELESS_RESET` and `receiverKind=SOCKET_CONTEXT,FALLBACK`. |

Timer and packet-space fixtures are created once per trial. Packet-space deadlines remain stable while diagnostic reads
use the production monotonic clock. The buffer fixture primes one direct buffer when pooling is
enabled; the measured disabled-pool path allocates a fresh heap buffer. TLS is a trial fixture and no handshake is measured.
The queued-submission fixture allocates its payload ring once and parks a real writer in its completion callback.
It initially retains `queueDepth` datagrams and each measured batch grows this to `queueDepth + 64`. Invocation teardown
lets exactly 64 sends complete and parks the writer again, preserving the original backlog without rebuilding it.
The sink remains bound on loopback; only sender completion is checked. Socket writes, draining,
buffer recycling, and the writer rendezvous are outside submission timing. This isolates queued submission costs and does
not model endpoint throughput, congestion, or producer/writer contention.
The synchronous variant includes its socket call and buffer-recycling completion callback, so syscall cost can dominate it.
Both endpoint variants measure sender acceptance.

Receive invocation setup sends 64 small loopback datagrams and drains the nonblocking channel into the real endpoint queue,
retaining the read-loop task through the endpoint's executor. The measured method runs that task on the JMH worker, including
queue polling/release, packet header inspection, diagnostic tag/log work, the scheduler, and minimal receiver counters.
The packet receiver records a payload checksum and the last callback metadata; teardown checks counts, content, and an empty
queue. Stateless resets must match a registered token and peer address before entering the measured queue. Both receiver
shapes use the same callback implementation; `SOCKET_CONTEXT` adds the socket-ID accessors used by production connections,
while `FALLBACK` exercises ordinary receiver identity tags. Endpoint creation, UDP sends, channel reads, ingress heap copies,
route/token lookup, and fixture verification are excluded from both timing and operation-scoped allocation. This is receive
dispatch cost, not full connection processing, packet decryption, network throughput, or an end-to-end latency measurement.
No receive thread, selector thread, or background executor is started. The fixture closes both channels at trial teardown.
Raw packet logging remains disabled; TRACE includes DEBUG metadata without raw payload dumps.

Run only named diagnostic methods; the runner generates an anchored include for this class and the selected methods:

```shell
mvn test -pl tests/benchmark/jmh -Ptests,jmh \
    -Dtest=QuicDiagnosticsJmhRunnerTest \
    -Dquic.diagnostics.jmh.methods=timerOffer,timerReschedule,packetDeadline,outgoingBuffer,synchronousDatagram,queuedDatagrams \
    -Dquic.diagnostics.jmh.logLevel=OFF,TRACE \
    -Dquic.diagnostics.jmh.queueDepth=0,64 \
    -Dquic.diagnostics.jmh.forks=2 \
    -Dquic.diagnostics.jmh.result=./target/quic-diagnostics-timing-1.json
```

For a focused receive comparison, select only the new method (use `receiveDispatchAllocation` in a separate allocation run):

```shell
mvn test -pl tests/benchmark/jmh -Ptests,jmh \
    -Dtest=QuicDiagnosticsJmhRunnerTest \
    -Dquic.diagnostics.jmh.methods=receiveDispatch \
    -Dquic.diagnostics.jmh.logLevel=OFF,DEBUG,TRACE \
    -Dquic.diagnostics.jmh.receivePath=PACKET,STATELESS_RESET \
    -Dquic.diagnostics.jmh.receiverKind=SOCKET_CONTEXT,FALLBACK \
    -Dquic.diagnostics.jmh.forks=2 \
    -Dquic.diagnostics.jmh.warmupIterations=3 -Dquic.diagnostics.jmh.warmupMillis=500 \
    -Dquic.diagnostics.jmh.measurementIterations=5 -Dquic.diagnostics.jmh.measurementMillis=500 \
    -Dquic.diagnostics.jmh.result=./target/quic-receive-timing-1.json
```

Each method has a separate `Allocation` variant, for example
`-Dquic.diagnostics.jmh.methods=queuedDatagramsAllocation`. These use supported `ThreadMXBean` worker-allocation counters
around only the operation. Divide the `allocatedBytes` event total by the `operations` event total to obtain bytes per
operation. For queued submissions and receive dispatch, one operation is one datagram. Instrumented timing includes counter
overhead and must not be used as latency evidence. Allocation excludes fixture setup/cleanup, the async writer, retained/native direct-buffer
memory, and other threads. The runner rejects `gcProfiler=true` because a process-wide profiler would count that excluded
work. Keep the benchmark harness, JVM, selected parameters, and runtime classpath identical across baseline/candidate runs.

Defaults are one fork, one worker, three 1-second warmups, and five 1-second measurements. The runner bounds forks to 1–4,
iteration counts to 1–10, and iteration durations to 500–10000 milliseconds; shorter runs are diagnostic smoke checks.
`queueDepth` is bounded to 0–4096 and defaults to `0,64`; select `4096` explicitly for a longer queue-size traversal.
Use `warmupIterations`, `measurementIterations`, `warmupMillis`, `measurementMillis`, `result`, and `output` under the
`quic.diagnostics.jmh.` prefix to configure a targeted run. `QuicDiagnosticsJmhRunnerValidationTest` exercises reusable
fixtures, backlog preservation through payload-ring wraparound, receive dispatch and content checks across all path/receiver/level
combinations, logger restoration, and owned-writer termination.

### QUIC 1-RTT codec diagnostics

`QuicCodecDiagnosticsJmhBenchmark` measures the actual 1-RTT packet codec with `OFF` and `DEBUG` logging.
`encodeOneRtt` includes packet construction, STREAM frame serialization, AES-128-GCM encryption, and header protection.
`decodeOneRtt` uses the production `decodeOwned` path to remove header protection, authenticate and decrypt the packet,
and parse its STREAM frame. `payloadSize=64,1200` specifies STREAM data bytes; frame and packet headers and the
authentication tag add to the protected packet size. The decoded packet or encoded buffer is returned to JMH.

The fixture loads existing benchmark TLS material once per trial. Each iteration completes an in-memory handshake between
the actual Helidon client and server TLS engines, restricted to AES-128-GCM, and prepares one immutable protected decode
input. Handshakes, key derivation, and input preparation are excluded from the measurements. Invocation setup copies the
ciphertext into reusable heap storage because decoding modifies the header and payload in place. The caller releases the
decoded packet before that storage is reused. Decode measures repeated authentication of the same valid ciphertext; it
does not include connection-level duplicate-packet filtering. Encoding advances packet numbers beyond the boxing caches,
with a stable two-byte encoded packet number. Fresh iteration keys avoid nonce reuse when the sequence restarts. If an
iteration reaches the production AES-GCM confidentiality limit, the fixture fails and the run must use shorter iterations.
No sockets, network transport, congestion control, or application callbacks are involved.

DEBUG uses a bounded in-memory handler retaining a count and the latest message; it includes codec formatting and logger
dispatch but excludes console or file I/O. Raw protocol logging stays disabled. Logger settings are restored after the trial.
Run only explicitly named methods; omitting `methods` fails instead of selecting the whole benchmark class:

```shell
mvn test -pl tests/benchmark/jmh -Ptests,jmh \
    -Dtest=QuicCodecDiagnosticsJmhRunnerTest \
    -Dquic.codec.diagnostics.jmh.methods=encodeOneRtt,decodeOneRtt \
    -Dquic.codec.diagnostics.jmh.payloadSize=64,1200 \
    -Dquic.codec.diagnostics.jmh.logLevel=OFF,DEBUG \
    -Dquic.codec.diagnostics.jmh.forks=2 \
    -Dquic.codec.diagnostics.jmh.warmupIterations=3 -Dquic.codec.diagnostics.jmh.warmupMillis=500 \
    -Dquic.codec.diagnostics.jmh.measurementIterations=5 -Dquic.codec.diagnostics.jmh.measurementMillis=500 \
    -Dquic.codec.diagnostics.jmh.result=./target/quic-codec-timing-1.json
```

Run `encodeOneRttAllocation,decodeOneRttAllocation` separately for allocation. Divide the `allocatedBytes` event total by
the `operations` event total to obtain worker-allocated bytes per packet. Those methods bracket only the codec call using
`ThreadMXBean`; their timing includes counter overhead and is not latency evidence. Fixture setup, ciphertext restoration,
TLS handshakes, and allocations on other threads are excluded. The runner rejects `gcProfiler=true` because it would count
excluded work. Both event totals also include codec calls in JMH's iteration synchronization loops, so their ratio is
worker allocation per codec invocation rather than allocation restricted to the primary timing window. Before/after
comparisons must use the same compiled harness, JVM arguments, dependencies, and parameters.

The runner fixes one worker, runs forks serially, and bounds forks to 1–4, iteration counts to 1–10, and iteration times to
500–10000 milliseconds. Defaults are one fork, three 1-second warmups, and five 1-second measurements. It accepts only the
four named methods, the two payload sizes, and OFF/DEBUG. Configure `result` and `output` under `quic.codec.diagnostics.jmh.`
for JSON and text output. `QuicCodecDiagnosticsJmhRunnerValidationTest` exercises all method/size/level combinations,
repeated invocation and iteration preparation, packet metadata, exact STREAM payloads, ciphertext authentication failure,
logging and restoration, allocation operation counts, and bounded runner selection.

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
