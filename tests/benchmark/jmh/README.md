Benchmarks
---

# JMH

JMH tests are being executed with maven profile `mvn clean install -Pjmh`, baseline result is created if
file `benchmarks/jmh/jmh-baseline.json` doesn't exist. Baseline is used for current result comparison, if regression larger than
error margin(15% by default)
is detected, build fails.

The comparison treats higher throughput and lower average, sampled, or single-shot time as improvements. The error margin
is a percentage of the current score. Histograms show the score unit reported by JMH; an existing baseline with a different
unit must be regenerated before comparison. Deterministic runner tests execute with the ordinary `tests` profile;
benchmark campaigns additionally require the `jmh` profile.

```shell
[ERROR] Failures: 
[ERROR]   JunitJMHRunnerTest.renderResult:69 
==================== HttpJMH.http2 (-31.41%)
     Baseline ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ 44877.472 ops/s
      Current ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ 30781.420 ops/s
HttpJMH.http2 regression detected. Error margin 10%(3078.14)
Expected: a value equal to or greater than <44877.471663551245>
     but: <33859.562325823645> was less than <44877.471663551245>
[INFO] 
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
```

## Adding JMH tests

New JMH benchmark classes should be created under `src/main/java` using a package appropriate for the subsystem being measured.
Benchmarks that use the legacy webserver baseline runner end with `JmhTest`; subsystem benchmarks can instead provide a dedicated
JUnit runner under `src/test/java`.

Before running any module-scoped benchmark command below from a clean checkout, install the repository artifacts from
the repository root:

```shell
mvn -ntp clean install -DskipTests
```

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

## Messaging runtime

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

## HTTP transport provider dispatch

`HttpTransportMetricsDispatchJmhBenchmark` measures cold, filtered registration attempts through the real HTTP transport
metrics dispatcher. Each producer has a distinct configured registry wrapper and recorder cache; all wrappers unwrap the
same native registry and therefore share one dispatcher. Each transport identifier is used once per iteration. Only the
connection-open counter is selected by name. The provider counts its tag-aware selection calls and returns `false`, so every
attempt exercises queued cold resolution without native meter construction, timers, or percentile computation. This is a
dispatcher comparison, not a measurement of Micrometer registration cost or warmed recording throughput.

The dedicated runner selects only `producerAdmission` and `completeWave`, at one and four producer threads, with 32 and 128
registrations per producer. It uses single-shot iterations with batch size one, three forks, 1,000 warmups, 100 measurements,
and a fixed 1 GiB heap. Setup acquires fresh leases and precomputes identifiers outside timing. `producerAdmission` measures
one producer's batch of connection-open/close callbacks while the provider can drain concurrently. `completeWave` additionally
waits for all producers, all expected provider callbacks, and the final observation dispatcher thread to terminate, including
its outstanding-task accounting. Results are microseconds per producer batch; concurrent complete-wave samples are each
producer's time to the shared completion boundary, not independent waves. Divide admission time by registrations per producer
only when interpreting amortized callback cost.

Every iteration verifies that the number of executed provider callbacks equals the number of attempts. Missing work fails
the run instead of improving the score. Teardown closes every lease, awaits release completion, and closes the owned registry.
The GC profiler includes iteration setup and teardown allocation as well as the measured calls; compare matched configurations
and source-identical harnesses, and do not describe normalized allocation as dispatcher-only allocation.

After installing the repository artifacts, compile and run the ordinary correctness checks without executing JMH:

```shell
mvn verify -Ptests,jmh -pl :helidon-tests-benchmark-jmh -Dtest=HttpTransportMetricsDispatchTest
```

The explicit test selection is required: the `jmh` profile enables test execution in this module. These checks directly invoke
both benchmark paths, cover serial and concurrent producers and fresh iterations, and hold a provider callback while submitting
1,280 cold attempts across five recorder caches. Exactly 1,024 must execute after release. No sockets or containers are used.

Run the timing comparison on the benchmark host using matched build JDKs and artifacts, with identical harness source on the
pre-dispatcher-fix and candidate revisions:

```shell
mvn test -Ptests,jmh -pl :helidon-tests-benchmark-jmh -Dtest=HttpTransportMetricsDispatchJmhRunnerTest
```

The runner writes `target/http-transport-dispatch-jmh-t1.json` and `target/http-transport-dispatch-jmh-t4.json`. Properties
`http.transport.dispatch.jmh.threads`, `http.transport.dispatch.jmh.forks`,
`http.transport.dispatch.jmh.warmupIterations`, `http.transport.dispatch.jmh.measurementIterations`, and
`http.transport.dispatch.jmh.resultPrefix` override the corresponding defaults. Timed waves above the 1,024-action budget,
repeated invocations within an iteration, and modes other than single-shot with batch size one are rejected.

## HTTP request JFR events

`HttpRequestJfrJmhBenchmark.keepAliveExchange` measures a single client performing complete, clear-text HTTP/1.1
or HTTP/2 prior-knowledge exchanges. Both sockets use TCP_NODELAY, the client owns a private connection cache,
discovery is disabled, and setup/teardown verify connection reuse. The fixture has no tracing observer: this measures
the lower bound for the core request event, excluding trace-context supplementation. Client work and full response
consumption are included; these results do not establish independent server throughput.

The fixed six-cell matrix is `protocol={HTTP1,HTTP2}` and `recordingMode={OFF,DISABLED,ENABLED}`. OFF has no active
recording; DISABLED runs an otherwise empty recording with the request event disabled; ENABLED enables only
`io.helidon.HttpServerRequest` with zero threshold and no stack trace. All other Helidon events are explicitly disabled.
No default/profile JDK configuration or CPU sampler runs during timing. Recordings exist only in this fixture.
Active recordings are in memory: the comparison isolates in-memory JFR cost and makes no disk recording throughput claim.
Smoke tests and the qualification probe stop the server producers, dump a snapshot while the recording is still running,
then stop the recording; stopping an in-memory recording first would discard its data.

After installing repository artifacts, run the deterministic fixture smoke tests separately from timing:

```shell
mvn test -Ptests -pl :helidon-tests-benchmark-jmh -Dtest=HttpRequestJfrSmokeTest
```

Use the identical benchmark/runner source overlay, JDK, flags and dependency versions on the comparison base and
candidate. The base without request events can run the smoke test with `-Dhttp.request.jfr.expectEvents=false`.
On the authorized benchmark host, run only the anchored runner:

```shell
mvn test -Ptests,jmh -pl :helidon-tests-benchmark-jmh -Dtest=HttpRequestJfrJmhRunnerTest \
    -Dhttp.request.jfr.jmh.result=target/http-request-jfr-1.json
```

The runner uses three forks, five one-second warmups, eight one-second measurements, one client thread, a fixed
1 GiB heap, and the GC profiler. Properties `http.request.jfr.jmh.forks`, `http.request.jfr.jmh.warmupIterations`,
`http.request.jfr.jmh.measurementIterations`, and `http.request.jfr.jmh.result` override these defaults.
Compare matched-cell confidence intervals and normalized allocation, retaining base and candidate JSON separately.

`HttpRequestJfrSamplesProbe` is a separate explicitly invoked qualification main class under `src/test/java`;
Surefire never selects it. It requires JDK 28 build 19 or newer (JDK-8386622). With the compiled main/test classes
and benchmark dependencies on `BENCHMARK_TEST_CLASSPATH`, invoke it using the JDK being qualified and an existing
output directory:

```shell
java -cp "$BENCHMARK_TEST_CLASSPATH" io.helidon.webserver.benchmark.jmh.HttpRequestJfrSamplesProbe target/jfr-samples-1
```

For each protocol, the probe performs 24 bounded requests containing Java CPU work and native Deflater compression.
It enables both `jdk.ExecutionSample` and `jdk.NativeMethodSample` with a one-millisecond period, verifies recorded
request contextual metadata and same-thread interval overlap for each sample type, then runs that JDK's `jfr print`.
Both sample types must actually display the request name and matching request/socket/listener identifiers in contextual
output. Merely coexisting events cannot pass. The JDK tool synthesizes `Context:` lines; these are not ordinary
RecordingFile fields or JSON print output. Missing samples or contextual output fail with a host/JDK qualification
message; sampling is probabilistic and this probe is intentionally excluded from deterministic reactor tests.
Retain its protocol-specific `.jfr` and text outputs as qualification evidence, separately from JMH results.

## Tracing observer JFR events

`TracingRequestJfrJmhBenchmark.tracedKeepAliveExchange` is a separate tracing slice; the six-cell core request
benchmark above retains its own selector and results. The tracing slice reuses that HTTP transport fixture,
including protocol validation, connection reuse, private client cache, TCP_NODELAY, bounded timeouts and complete
response consumption. Its real `TracingObserver` uses an owned OpenTelemetry SDK and SDK tracer provider with no
span processors or exporters. It creates no exporter traffic and does not register or replace the global SDK.

The twelve cells are `protocol={HTTP1,HTTP2}`, `recordingMode={OFF,DISABLED,ENABLED}`, and
`tracingMode={SERVER_SPAN,INBOUND_CONTEXT}`. SERVER_SPAN sends no inbound trace headers: the real SDK creates
a new root server span and unique trace ID for each request, including trace-ID encoding and JFR string-pool costs.
INBOUND_CONTEXT sends a valid sampled W3C `traceparent` with a fixed nonzero trace ID, disables the HTTP Request
server span and retains the extracted remote parent context. OFF has no active recording;
DISABLED disables both request and trace-context events in an otherwise empty in-memory recording; ENABLED enables
both `io.helidon.HttpServerRequest` and `io.helidon.RequestTraceContext` with zero threshold and no stack trace,
representing their combined default workflow. Other Helidon events stay disabled and no JDK samplers run.
The measurement includes tracing and client work, and does not measure export or disk recording throughput.

Run the deterministic twelve-case smoke test separately from JMH:

```shell
mvn verify -Ptests -pl :helidon-tests-benchmark-jmh -Dtest=HttpRequestJfrSmokeTest,TracingRequestJfrSmokeTest
```

The smoke test checks both branch executions using the handler's actual context and span presence, valid nonzero
trace and span IDs, unique root trace IDs versus retained remote-parent IDs, exact request/trace event counts,
recorded trace IDs correlated with actual handler observations and threads, trace origin, and
same-thread trace intervals enclosed by request intervals. It stops server producers before dumping the running
in-memory recording. Copy the same benchmark, shared HTTP fixture, runner, smoke test and benchmark dependency
overlay to each comparison revision; they reference event names rather than new event classes. The base without
these events uses `-Dhttp.request.jfr.expectEvents=false` for smoke validation while retaining the tracing checks.

On the authorized benchmark host, select only the dedicated runner:

```shell
mvn test -Ptests,jmh -pl :helidon-tests-benchmark-jmh -Dtest=TracingRequestJfrJmhRunnerTest \
    -Dtracing.request.jfr.jmh.result=target/tracing-request-jfr-1.json
```

The anchored selector executes only `TracingRequestJfrJmhBenchmark.tracedKeepAliveExchange`: three forks,
five one-second warmups, eight one-second measurements, one client thread, a fixed 1 GiB heap and the GC profiler.
Properties `tracing.request.jfr.jmh.forks`, `tracing.request.jfr.jmh.warmupIterations`,
`tracing.request.jfr.jmh.measurementIterations`, and `tracing.request.jfr.jmh.result` override these defaults.
Keep these twelve-cell results separate from core-only evidence and compare matched-cell confidence intervals
and normalized allocation on identical JDKs, flags and dependencies.

## Troubleshooting

When tests fails repeatedly without any code change, try regenerating baseline file
with `mvn clean install -Pjmh -Dwebserver.jmh.resetBaseline=true`. If that doesn't help, you can also set different error margin (in
percents) with
`mvn clean install -Pjmh -Dwebserver.jmh.errorMargin=15`.
