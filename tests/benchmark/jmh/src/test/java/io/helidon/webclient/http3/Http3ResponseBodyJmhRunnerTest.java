/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.webclient.http3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.helidon.tests.benchmark.BenchmarkSourceIdentity;
import io.helidon.tests.benchmark.Http3QuicEvidenceScope;
import io.helidon.tests.benchmark.ProcessAllocationProfiler;
import io.helidon.tests.benchmark.ProcessCpuProfiler;
import io.helidon.tests.benchmark.RuntimeClasspathVerifier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class Http3ResponseBodyJmhRunnerTest {
    private static final String PREFIX = "http3.response.body.jmh.";

    @Test
    void run() throws Exception {
        String requestedMode = System.getProperty(PREFIX + "mode");
        Assumptions.assumeTrue(requestedMode != null && !requestedMode.isBlank(),
                "Set http3.response.body.jmh.mode to smoke, throughput, latency, allocation or cpu");
        RunMode mode = RunMode.valueOf(requestedMode.toUpperCase(Locale.ROOT));
        boolean smoke = mode == RunMode.SMOKE;
        String method = System.getProperty(PREFIX + "method", "materializedGet");
        if (!method.equals("materializedGet") && !method.equals("streamedGet")) {
            throw new IllegalArgumentException("Select exactly materializedGet or streamedGet");
        }
        String[] sizes = values("payloadSize", "0,1024,131072");
        for (String size : sizes) {
            if (!size.equals("0") && !size.equals("1024") && !size.equals("131072")) {
                throw new IllegalArgumentException("Unsupported exact response payload size: " + size);
            }
        }
        String[] executors = values("runtimeExecutorMode", "DEFAULT,FJP2");
        for (String executor : executors) {
            Http3ResponseBodyJmhBenchmark.RuntimeExecutorMode.valueOf(executor);
        }
        int threads = Integer.getInteger(PREFIX + "threads", 1);
        int forks = Integer.getInteger(PREFIX + "forks", smoke ? 1 : 2);
        int warmups = Integer.getInteger(PREFIX + "warmupIterations", smoke ? 0 : 3);
        int measurements = Integer.getInteger(PREFIX + "measurementIterations", smoke ? 1 : 5);
        long warmupMillis = Long.getLong(PREFIX + "warmupMillis", smoke ? 100 : 500);
        long measurementMillis = Long.getLong(PREFIX + "measurementMillis", smoke ? 100 : 1000);
        if ((threads != 1 && threads != 64) || forks < 1 || forks > 3 || warmups < 0 || warmups > 10
                || measurements < 1 || measurements > 10 || warmupMillis < 1 || warmupMillis > 10_000
                || measurementMillis < 1 || measurementMillis > 10_000) {
            throw new IllegalArgumentException(
                    "Select 1 or 64 threads, 1 to 3 forks, at most 10 iterations and durations of 1 to 10000 ms");
        }
        if (!smoke && (forks < 2 || warmups < 3 || measurements < 5
                || warmupMillis < 500 || measurementMillis < 1000)) {
            throw new IllegalArgumentException(
                    "Response evidence requires two forks, three 500 ms warmups and five 1000 ms measurements");
        }
        String runId = System.getProperty(PREFIX + "runId", smoke ? "smoke" : null);
        if (runId == null || !runId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("A fresh numbered response evidence runId is required");
        }
        BenchmarkSourceIdentity.freezeRuntimeClassPath();
        Path repository = Http3QuicEvidenceScope.repositoryRoot();
        var identity = smoke ? null : BenchmarkSourceIdentity.verify(repository,
                Http3QuicEvidenceScope.manifest(repository), Http3QuicEvidenceScope.buildManifest(repository),
                Http3QuicEvidenceScope.sourcePaths(), Http3QuicEvidenceScope.artifacts());
        String stem = "http3-response-body-" + mode.name().toLowerCase(Locale.ROOT) + "-" + runId;
        var paths = smoke ? null : Http3QuicEvidenceScope.evidencePaths(repository, stem);
        Path result = smoke ? Path.of("target", stem + ".json") : paths.result();
        Path output = smoke ? Path.of("target", stem + ".log") : paths.output();
        Path metadata = smoke ? Path.of("target", stem + ".properties") : paths.metadata();
        Path stagedResult = staged(result);
        Path stagedOutput = staged(output);
        Path stagedMetadata = staged(metadata);
        try (var bundle = smoke ? null : BenchmarkSourceIdentity.reserveEvidenceBundle(result, output, metadata,
                paths.sourceManifest(), paths.buildManifest(), paths.buildLog())) {
            String include = "^io\\.helidon\\.webclient\\.http3\\.Http3ResponseBodyJmhBenchmark\\." + method + "$";
            Mode jmhMode = switch (mode) {
                case THROUGHPUT -> Mode.Throughput;
                case LATENCY -> Mode.SampleTime;
                default -> Mode.AverageTime;
            };
            TimeUnit timeUnit = mode == RunMode.THROUGHPUT ? TimeUnit.SECONDS : TimeUnit.MILLISECONDS;
            var options = new OptionsBuilder().include(include)
                    .mode(jmhMode).timeUnit(timeUnit)
                    .forks(forks).warmupForks(0).threads(threads)
                    .warmupIterations(warmups).warmupTime(TimeValue.milliseconds(warmupMillis))
                    .measurementIterations(measurements).measurementTime(TimeValue.milliseconds(measurementMillis))
                    .timeout(TimeValue.seconds(60)).shouldFailOnError(true)
                    .param("payloadSize", sizes).param("runtimeExecutorMode", executors)
                    .param("sourceIdentity", smoke ? "functional-smoke" : identity.manifestSha256())
                    .resultFormat(ResultFormatType.JSON)
                    .result(stagedResult.toString()).output(stagedOutput.toString());
            if (identity != null) {
                options.jvmArgsAppend("-D" + BenchmarkSourceIdentity.RUNTIME_CLASSPATH_SHA256_PROPERTY
                        + "=" + identity.classpathSha256()).addProfiler(RuntimeClasspathVerifier.class);
            }
            if (mode == RunMode.ALLOCATION) {
                options.addProfiler(ProcessAllocationProfiler.class);
            } else if (mode == RunMode.CPU) {
                options.addProfiler(ProcessCpuProfiler.class);
            }
            new Runner(options.build()).run();
            Properties details = new Properties();
            details.setProperty("include", include);
            details.setProperty("payloadSize", String.join(",", sizes));
            details.setProperty("runtimeExecutorMode", String.join(",", executors));
            details.setProperty("threads", Integer.toString(threads));
            details.setProperty("forks", Integer.toString(forks));
            details.setProperty("warmupForks", "0");
            details.setProperty("warmupIterations", Integer.toString(warmups));
            details.setProperty("warmupMillis", Long.toString(warmupMillis));
            details.setProperty("measurementIterations", Integer.toString(measurements));
            details.setProperty("measurementMillis", Long.toString(measurementMillis));
            details.setProperty("mode", mode.name());
            details.setProperty("jmhMode", jmhMode.name());
            details.setProperty("timeUnit", timeUnit.name());
            details.setProperty("measurementProfiler", switch (mode) {
                case ALLOCATION -> ProcessAllocationProfiler.class.getName();
                case CPU -> ProcessCpuProfiler.class.getName();
                default -> "none";
            });
            details.setProperty("sourceIdentity", smoke ? "functional-smoke" : identity.manifestSha256());
            details.setProperty("measurementScope",
                    "Both loopback endpoints, full payload validation, and client consumption");
            details.setProperty("explicitExecutor",
                    "Borrowed FJP2, asyncMode=true, standard worker factory and compensation");
            try (var stream = Files.newOutputStream(stagedMetadata)) {
                details.store(stream, "Actual HTTP/3 response-body JMH effective selection");
            }
            if (!smoke) {
                var completedIdentity = BenchmarkSourceIdentity.verify(repository,
                        Http3QuicEvidenceScope.manifest(repository), Http3QuicEvidenceScope.buildManifest(repository),
                        Http3QuicEvidenceScope.sourcePaths(), Http3QuicEvidenceScope.artifacts());
                if (!identity.equals(completedIdentity)) {
                    throw new IllegalStateException(
                            "Response-body source/classpath identity changed during measurement");
                }
                BenchmarkSourceIdentity.sanitizeEvidenceArtifacts(repository, result.getParent(),
                        stagedResult, stagedResult, stagedOutput, stagedMetadata);
                bundle.publishBuildManifest(identity, paths.buildManifest());
                bundle.publishManifest(identity, paths.sourceManifest());
                bundle.publishBuildLog(identity, paths.buildLog());
                bundle.publish(stagedOutput, output);
                bundle.publish(stagedMetadata, metadata);
                bundle.commit(stagedResult);
            } else {
                BenchmarkSourceIdentity.publishReplacing(stagedOutput, output);
                BenchmarkSourceIdentity.publishReplacing(stagedMetadata, metadata);
                BenchmarkSourceIdentity.publishReplacing(stagedResult, result);
            }
        } finally {
            Files.deleteIfExists(stagedResult);
            Files.deleteIfExists(stagedOutput);
            Files.deleteIfExists(stagedMetadata);
        }
    }

    private static String[] values(String parameter, String defaults) {
        String[] values = Arrays.stream(System.getProperty(PREFIX + parameter, defaults).split(",", -1))
                .map(String::trim).toArray(String[]::new);
        if (values.length == 0 || Arrays.stream(values).anyMatch(String::isEmpty)
                || Arrays.stream(values).distinct().count() != values.length) {
            throw new IllegalArgumentException("Select distinct nonempty values for " + parameter);
        }
        return values;
    }

    private static Path staged(Path target) throws Exception {
        Path path = target.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        return Files.createTempFile(path.getParent(), "." + path.getFileName() + "-", ".tmp");
    }

    private enum RunMode {
        SMOKE,
        THROUGHPUT,
        LATENCY,
        ALLOCATION,
        CPU
    }
}
