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

## JDBC batch capture

[`JdbcBatchCaptureJmhBenchmark`](src/main/java/io/helidon/data/jdbc/JdbcBatchCaptureJmhBenchmark.java)
measures complete batch capture before connection acquisition. Each invocation constructs the bindings and retains all
captured items until it returns. Application buffers and named SQL metadata are prepared in trial setup.

| Method | Workload |
| --- | --- |
| `indexedBinary` | Repeats the same binary input at multiple indexed markers within each item. |
| `namedBinary` | Binds one binary input to repeated named markers. |
| `indexedScalar` | Repeats an immutable string as a scalar control. |
| `indexedMutatingBinary` | Mutates the final byte of the same array before each indexed bind. |
| `indexedIndependentBinary` | Uses a distinct array for each indexed binary marker. |

Each item also binds a scalar identifier. The following parameters control the captured inputs:

| Parameter | Default values | Meaning |
| --- | --- | --- |
| `rowCount` | `32` | The number of items captured per invocation. |
| `repetitions` | `1,8` | The number of physical markers for the binary or string input in each item. |
| `payloadBytes` | `64,65536` | The length of each application byte array. This parameter does not affect the string control. |

The default Maven benchmark runners do not select this class. Run it through the JMH command line. From the repository
root, install the prerequisites and build the benchmark:

```shell
mvn -ntp -DskipTests install
mvn -ntp -f tests/benchmark/jmh/pom.xml -DskipTests package dependency:build-classpath \
    -Dmdep.outputFile=target/jdbc-batch-classpath
JDBC_BATCH_CLASSPATH="tests/benchmark/jmh/target/classes:$(cat tests/benchmark/jmh/target/jdbc-batch-classpath)"
```

Run all five methods with the default input parameters, one thread, two forks, a fixed 256 MiB heap, and the GC profiler:

```shell
java -cp "$JDBC_BATCH_CLASSPATH" org.openjdk.jmh.Main \
    '^io\.helidon\.data\.jdbc\.JdbcBatchCaptureJmhBenchmark\..*$' \
    -t 1 -f 2 -wi 3 -w 1s -i 5 -r 1s -jvmArgs '-Xms256m -Xmx256m' \
    -prof gc -rf json -rff tests/benchmark/jmh/target/jdbc-batch-capture.json
```

Override inputs with options such as `-p rowCount=1,32,128`. For a quick smoke check, select only `indexedBinary` in the
method filter and use `-p rowCount=1 -p repetitions=1 -p payloadBytes=64 -f 1 -wi 1 -w 100ms -i 1 -r 100ms`.
Smoke results establish that the benchmark runs and are unsuitable for performance comparisons.

For comparisons, use identical benchmark source, JDKs, JVM settings, parameters, forks, and iteration settings across
revisions. Rebuild and install the JDBC runtime in each checkout before building its benchmark. Save each revision's JSON
under a separate result name and compare matching configurations, including the scalar, mutation, and distinct array
controls. The legacy baseline file, reset option, and automatic regression checks described elsewhere in this README do
not apply to these direct JMH runs.

Average time is reported in microseconds per complete capture invocation. The GC profiler's `gc.alloc.rate.norm` reports
allocated bytes per invocation. Divide either value by `rowCount` for the corresponding per item value. Allocation does
not establish retained or peak heap usage. These measurements exclude JDBC execution, driver buffering, transaction
completion, and database throughput. They also do not measure the effect of `@Jdbc.BatchSize`, which limits submissions
after complete capture.

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

## Troubleshooting

When tests fails repeatedly without any code change, try regenerating baseline file
with `mvn clean install -Pjmh -Dwebserver.jmh.resetBaseline=true`. If that doesn't help, you can also set different error margin (in
percents) with
`mvn clean install -Pjmh -Dwebserver.jmh.errorMargin=15`.
