Benchmarks
---

# JMH

JMH tests are being executed with maven profile `mvn clean install -Pjmh`, baseline result is created if
file `benchmarks/jmh/jmh-baseline.json` doesn't exist. Baseline is used for current result comparison, if regression larger than
error margin(15% by default)
is detected, build fails.

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

## Troubleshooting

When tests fails repeatedly without any code change, try regenerating baseline file
with `mvn clean install -Pjmh -Dwebserver.jmh.resetBaseline=true`. If that doesn't help, you can also set different error margin (in
percents) with
`mvn clean install -Pjmh -Dwebserver.jmh.errorMargin=15`.
