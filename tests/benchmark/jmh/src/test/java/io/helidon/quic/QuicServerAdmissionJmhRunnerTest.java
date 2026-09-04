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

package io.helidon.quic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.regex.Pattern;

import io.helidon.tests.benchmark.BenchmarkSourceIdentity;
import io.helidon.tests.benchmark.Http3QuicEvidenceScope;
import io.helidon.tests.benchmark.ProcessAllocationProfiler;
import io.helidon.tests.benchmark.ProcessCpuProfiler;
import io.helidon.tests.benchmark.RuntimeClasspathVerifier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class QuicServerAdmissionJmhRunnerTest {
    private static final String PREFIX = "quic.server.admission.jmh.";
    private static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9._-]+");

    @Test
    void run() throws Exception {
        String include = System.getProperty(PREFIX + "include");
        Assumptions.assumeTrue(include != null && !include.isBlank(),
                               "Set quic.server.admission.jmh.include to select a bounded benchmark scenario");
        int forks = Integer.getInteger(PREFIX + "forks", 1);
        int threads = Integer.getInteger(PREFIX + "threads", 1);
        int warmupIterations = Integer.getInteger(PREFIX + "warmupIterations", 3);
        int measurementIterations = Integer.getInteger(PREFIX + "measurementIterations", 5);
        long warmupMillis = Long.getLong(PREFIX + "warmupMillis", 500);
        long measurementMillis = Long.getLong(PREFIX + "measurementMillis", 1000);
        long timeoutMillis = Long.getLong(PREFIX + "timeoutMillis", 90_000);
        if (forks < 1
                || threads < 1
                || warmupIterations < 0
                || measurementIterations < 1
                || warmupMillis < 1
                || measurementMillis < 1
                || timeoutMillis < 1) {
            throw new IllegalArgumentException(
                    "QUIC benchmark requires at least one fork and worker, non-negative warmup iterations, "
                            + "positive measurement iterations, and positive timings");
        }
        Pattern includePattern = Pattern.compile(include);
        boolean lifecycleHandshakeReadySelected = includePattern.matcher(
                "io.helidon.quic.QuicServerAdmissionJmhBenchmark.serverLifecycleHandshakeReady").find();
        boolean lifecyclePeerTerminationSelected = includePattern.matcher(
                "io.helidon.quic.QuicServerAdmissionJmhBenchmark"
                        + ".serverLifecycleReconnectReadyPeerTermination").find();
        if ((lifecycleHandshakeReadySelected || lifecyclePeerTerminationSelected) && threads != 1) {
            throw new IllegalArgumentException("QUIC handshake lifecycle benchmarks require exactly one benchmark worker");
        }
        long minimumLifecycleTimeout = QuicServerAdmissionJmhBenchmark.MINIMUM_LIFECYCLE_RUNNER_TIMEOUT_MILLIS;
        if ((lifecycleHandshakeReadySelected || lifecyclePeerTerminationSelected)
                && timeoutMillis < minimumLifecycleTimeout) {
            throw new IllegalArgumentException("QUIC handshake lifecycle benchmark timeout must be at least "
                                                       + minimumLifecycleTimeout + " ms");
        }
        boolean evidence = Boolean.getBoolean(PREFIX + "evidence");
        if (evidence
                && (forks < 2
                || warmupIterations < 3
                || measurementIterations < 5
                || warmupMillis < 500
                || measurementMillis < 1000)) {
            throw new IllegalArgumentException(
                    "QUIC evidence runs require at least two forks, three 500 ms warmups, "
                            + "and five 1000 ms measurements");
        }
        BenchmarkSourceIdentity.freezeRuntimeClassPath();
        var repositoryRoot = Http3QuicEvidenceScope.repositoryRoot();
        BenchmarkSourceIdentity.SourceIdentity verifiedIdentity = null;
        if (evidence) {
            verifiedIdentity = BenchmarkSourceIdentity.verify(
                    repositoryRoot,
                    Http3QuicEvidenceScope.manifest(repositoryRoot),
                    Http3QuicEvidenceScope.buildManifest(repositoryRoot),
                    Http3QuicEvidenceScope.sourcePaths(),
                    Http3QuicEvidenceScope.artifacts());
        }
        String runId = System.getProperty(PREFIX + "runId", evidence ? null : "functional");
        if (runId == null || !RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException(
                    "QUIC lifecycle evidence requires an alphanumeric runId containing only '.', '_', or '-'");
        }
        validateEvidenceOutputOverrides(
                evidence,
                System.getProperty(PREFIX + "result"),
                System.getProperty(PREFIX + "output"));
        Http3QuicEvidenceScope.EvidencePaths evidencePaths = evidence
                ? Http3QuicEvidenceScope.evidencePaths(
                        repositoryRoot,
                        "quic-server-admission-" + runId)
                : null;
        Path resultPath = evidence
                ? evidencePaths.result()
                : Path.of(System.getProperty(
                        PREFIX + "result",
                        Path.of("./target/quic-server-admission-jmh-result.json").toString()));
        String configuredOutput = evidence
                ? evidencePaths.output().toString()
                : System.getProperty(PREFIX + "output");
        Path outputPath = configuredOutput == null ? null : Path.of(configuredOutput);
        Path metadataPath = evidence
                ? evidencePaths.metadata()
                : resultPath.resolveSibling(resultPath.getFileName() + ".properties");
        Path bundleManifest = evidence ? evidencePaths.sourceManifest() : null;
        Path bundleBuildManifest = evidence ? evidencePaths.buildManifest() : null;
        Path bundleBuildLog = evidence ? evidencePaths.buildLog() : null;

        boolean gcProfiler = Boolean.getBoolean(PREFIX + "gcProfiler");
        boolean processAllocationProfiler =
                Boolean.getBoolean(PREFIX + "processAllocationProfiler");
        boolean processCpuProfiler = Boolean.getBoolean(PREFIX + "processCpuProfiler");
        String profiler = System.getProperty(PREFIX + "profiler");
        validateProfilerSelection(lifecycleHandshakeReadySelected,
                                  lifecyclePeerTerminationSelected,
                                  gcProfiler,
                                  processAllocationProfiler,
                                  processCpuProfiler,
                                  profiler,
                                  measurementIterations);

        Path stagedResult = evidence ? staged(resultPath) : resultPath;
        Path stagedOutput = evidence ? staged(outputPath) : outputPath;
        Path stagedMetadata = evidence ? staged(metadataPath) : null;
        try (BenchmarkSourceIdentity.EvidenceBundle evidenceBundle = evidence
                ? BenchmarkSourceIdentity.reserveEvidenceBundle(
                        resultPath,
                        outputPath,
                        metadataPath,
                        bundleManifest,
                        bundleBuildManifest,
                        bundleBuildLog)
                : null) {
            ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                    .include(include)
                    .forks(forks)
                    .threads(threads)
                    .resultFormat(ResultFormatType.JSON)
                    .result(stagedResult.toString())
                    .warmupIterations(warmupIterations)
                    .warmupTime(TimeValue.milliseconds(warmupMillis))
                    .measurementIterations(measurementIterations)
                    .measurementTime(TimeValue.milliseconds(measurementMillis))
                    .timeout(TimeValue.milliseconds(timeoutMillis))
                    .shouldFailOnError(true);

            if (stagedOutput != null) {
                optionsBuilder.output(stagedOutput.toString());
            }
            applyParam(optionsBuilder, "pendingLimit");
            applyParam(optionsBuilder, "scheduledEvents");
            applyParam(optionsBuilder, "expiringEvents");
            applyParam(optionsBuilder, "scenario");
            if (lifecycleHandshakeReadySelected || lifecyclePeerTerminationSelected) {
                optionsBuilder.param("sourceIdentity",
                                     verifiedIdentity == null
                                             ? "functional-smoke"
                                             : verifiedIdentity.manifestSha256());
            }
            if (evidence) {
                optionsBuilder.jvmArgsAppend(
                                "-D" + BenchmarkSourceIdentity.RUNTIME_CLASSPATH_SHA256_PROPERTY
                                        + "=" + verifiedIdentity.classpathSha256())
                        .addProfiler(RuntimeClasspathVerifier.class);
            }
            if (gcProfiler) {
                optionsBuilder.addProfiler(GCProfiler.class);
            }
            if (profiler != null) {
                optionsBuilder.addProfiler(profiler);
            }
            if (processAllocationProfiler) {
                optionsBuilder.addProfiler(ProcessAllocationProfiler.class);
            }
            if (processCpuProfiler) {
                optionsBuilder.addProfiler(ProcessCpuProfiler.class);
            }

            Options options = optionsBuilder.build();
            new Runner(options).run();
            if (!evidence) {
                return;
            }

            Properties metadata = new Properties();
            metadata.setProperty("generatedAt", Instant.now().toString());
            metadata.setProperty("runId", runId);
            metadata.setProperty("include", include);
            metadata.setProperty("sourceIdentity", verifiedIdentity.manifestSha256());
            metadata.setProperty("sourceManifest", bundleManifest.getFileName().toString());
            metadata.setProperty("buildManifest", bundleBuildManifest.getFileName().toString());
            metadata.setProperty("buildLog", bundleBuildLog.getFileName().toString());
            metadata.setProperty("buildManifestSha256", verifiedIdentity.buildManifestSha256());
            metadata.setProperty("buildLogSha256", verifiedIdentity.buildLogSha256());
            metadata.setProperty("controlledRepositorySha256", verifiedIdentity.controlledRepositorySha256());
            metadata.setProperty("repositoryLocalPrefix", verifiedIdentity.repositoryLocalPrefix());
            metadata.setProperty("remoteRepositoryPrefix", verifiedIdentity.remoteRepositoryPrefix());
            metadata.setProperty("scmRevision", verifiedIdentity.scmRevision());
            metadata.setProperty("mavenTimeoutSeconds",
                                 Long.toString(verifiedIdentity.mavenTimeoutSeconds()));
            metadata.setProperty("buildActiveProcessorCount",
                                 Integer.toString(verifiedIdentity.activeProcessorCount()));
            metadata.setProperty("sourceHead", verifiedIdentity.head());
            metadata.setProperty("sourceStatusSha256", verifiedIdentity.statusSha256());
            metadata.setProperty("sourceFileCount", Integer.toString(verifiedIdentity.fileCount()));
            metadata.setProperty("sourceArtifactCount", Integer.toString(verifiedIdentity.artifactCount()));
            metadata.setProperty("sourceRuntimeArtifactCount",
                                 Integer.toString(verifiedIdentity.runtimeArtifactCount()));
            metadata.setProperty("sourceClasspathEntryCount",
                                 Integer.toString(verifiedIdentity.classpathEntryCount()));
            metadata.setProperty("sourceClasspathSha256", verifiedIdentity.classpathSha256());
            metadata.setProperty("forks", Integer.toString(forks));
            metadata.setProperty("threads", Integer.toString(threads));
            metadata.setProperty("warmupIterations", Integer.toString(warmupIterations));
            metadata.setProperty("warmupMillis", Long.toString(warmupMillis));
            metadata.setProperty("measurementIterations", Integer.toString(measurementIterations));
            metadata.setProperty("measurementMillis", Long.toString(measurementMillis));
            metadata.setProperty("timeoutMillis", Long.toString(timeoutMillis));
            metadata.setProperty("gcProfiler", Boolean.toString(gcProfiler));
            metadata.setProperty("processAllocationProfiler", Boolean.toString(processAllocationProfiler));
            metadata.setProperty("processCpuProfiler", Boolean.toString(processCpuProfiler));
            metadata.setProperty("profiler", profiler == null ? "none" : profiler);
            metadata.setProperty("result", resultPath.getFileName().toString());
            metadata.setProperty("output", outputPath.getFileName().toString());
            metadata.setProperty("javaVersion", System.getProperty("java.version"));
            metadata.setProperty("javaVm", System.getProperty("java.vm.name"));
            metadata.setProperty("osName", System.getProperty("os.name"));
            metadata.setProperty("osVersion", System.getProperty("os.version"));
            metadata.setProperty("osArch", System.getProperty("os.arch"));
            try (var metadataOutput = Files.newOutputStream(stagedMetadata)) {
                metadata.store(metadataOutput, "QUIC lifecycle benchmark effective configuration");
            }
            BenchmarkSourceIdentity.sanitizeEvidenceArtifacts(
                    repositoryRoot,
                    resultPath.getParent(),
                    stagedResult,
                    stagedResult,
                    stagedOutput,
                    stagedMetadata);

            BenchmarkSourceIdentity.SourceIdentity finalIdentity = BenchmarkSourceIdentity.verify(
                    repositoryRoot,
                    Http3QuicEvidenceScope.manifest(repositoryRoot),
                    Http3QuicEvidenceScope.buildManifest(repositoryRoot),
                    Http3QuicEvidenceScope.sourcePaths(),
                    Http3QuicEvidenceScope.artifacts());
            if (!verifiedIdentity.equals(finalIdentity)) {
                throw new IllegalStateException("QUIC lifecycle source identity changed during the run");
            }

            evidenceBundle.publishBuildManifest(verifiedIdentity, bundleBuildManifest);
            evidenceBundle.publishManifest(verifiedIdentity, bundleManifest);
            evidenceBundle.publishBuildLog(verifiedIdentity, bundleBuildLog);
            evidenceBundle.publish(stagedOutput, outputPath);
            evidenceBundle.publish(stagedMetadata, metadataPath);
            evidenceBundle.commit(stagedResult);
        } finally {
            if (evidence) {
                Files.deleteIfExists(stagedResult);
                Files.deleteIfExists(stagedOutput);
                Files.deleteIfExists(stagedMetadata);
            }
        }
    }

    static void validateProfilerSelection(boolean lifecycleHandshakeReadySelected,
                                          boolean lifecyclePeerTerminationSelected,
                                          boolean gcProfiler,
                                          boolean processAllocationProfiler,
                                          boolean processCpuProfiler,
                                          String profiler,
                                          int measurementIterations) {
        String profilerClass = profiler == null ? null : profiler.split(":", 2)[0];
        boolean genericGcProfiler = "gc".equalsIgnoreCase(profilerClass)
                || GCProfiler.class.getSimpleName().equals(profilerClass)
                || GCProfiler.class.getName().equals(profilerClass);
        boolean genericProcessAllocationProfiler =
                ProcessAllocationProfiler.class.getName().equals(profilerClass);
        boolean genericProcessCpuProfiler = ProcessCpuProfiler.class.getName().equals(profilerClass);
        int gcProfilerCount = (gcProfiler ? 1 : 0) + (genericGcProfiler ? 1 : 0);
        int processProfilerCount = (processAllocationProfiler ? 1 : 0)
                + (processCpuProfiler ? 1 : 0)
                + (genericProcessAllocationProfiler ? 1 : 0)
                + (genericProcessCpuProfiler ? 1 : 0);
        if (gcProfilerCount > 1) {
            throw new IllegalArgumentException("Select the GC profiler only once");
        }
        if (processProfilerCount > 1) {
            throw new IllegalArgumentException("Select only one QUIC process allocation or CPU profiler");
        }
        if (gcProfilerCount > 0 && processProfilerCount > 0) {
            throw new IllegalArgumentException("Do not combine the GC profiler with a QUIC process profiler");
        }
        if (processProfilerCount > 0 && measurementIterations < 2) {
            throw new IllegalArgumentException("QUIC process profilers require at least two measurement iterations");
        }
        if (lifecycleHandshakeReadySelected
                && (gcProfilerCount > 0
                || profiler != null
                || processAllocationProfiler
                || processCpuProfiler)) {
            throw new IllegalArgumentException(
                    "Profile the reconnect-ready peer-termination lifecycle, not the handshake-ready latency boundary");
        }
        if (lifecyclePeerTerminationSelected && gcProfilerCount > 0) {
            throw new IllegalArgumentException(
                    "Use the teardown-aware QUIC process profilers for lifecycle evidence, not the GC profiler");
        }
    }

    static void validateEvidenceOutputOverrides(boolean evidence, String result, String output) {
        if (evidence && (result != null || output != null)) {
            throw new IllegalArgumentException(
                    "QUIC lifecycle evidence derives result and output paths from the common evidence root");
        }
    }

    private static void applyParam(ChainedOptionsBuilder optionsBuilder, String name) {
        String value = System.getProperty(PREFIX + name);
        if (value != null) {
            optionsBuilder.param(name, value.split(","));
        }
    }

    private static Path staged(Path resultPath) throws Exception {
        Path result = resultPath.toAbsolutePath().normalize();
        Files.createDirectories(result.getParent());
        return Files.createTempFile(result.getParent(), "." + result.getFileName() + "-", ".tmp");
    }
}
