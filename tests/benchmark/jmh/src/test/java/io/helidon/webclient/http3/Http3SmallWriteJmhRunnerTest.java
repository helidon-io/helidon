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
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class Http3SmallWriteJmhRunnerTest {
    private static final String PREFIX = "http3.small.write.jmh.";
    private static final String INCLUDE =
            "^io\\.helidon\\.webclient\\.http3\\.Http3SmallWriteJmhBenchmark\\."
                    + "(bufferedUpload|eagerFrameUpload|bufferedDownload|dispatchBarrierDownload)$";
    private static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9._-]+");

    @Test
    void run() throws Exception {
        String modeText = System.getProperty(PREFIX + "mode");
        Assumptions.assumeTrue(modeText != null && !modeText.isBlank(),
                               "Set http3.small.write.jmh.mode to smoke, latency, allocation, or cpu");
        RunMode runMode;
        try {
            runMode = RunMode.valueOf(modeText.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown HTTP/3 small-write benchmark mode: " + modeText, e);
        }
        boolean smoke = runMode == RunMode.SMOKE;
        int forks = Integer.getInteger(PREFIX + "forks", smoke ? 1 : 2);
        int threads = Integer.getInteger(PREFIX + "threads", 1);
        int warmupIterations = Integer.getInteger(PREFIX + "warmupIterations", smoke ? 0 : 3);
        int measurementIterations = Integer.getInteger(PREFIX + "measurementIterations", smoke ? 1 : 5);
        long warmupMillis = Long.getLong(PREFIX + "warmupMillis", smoke ? 100 : 500);
        long measurementMillis = Long.getLong(PREFIX + "measurementMillis", smoke ? 100 : 1000);
        long timeoutMillis = Long.getLong(PREFIX + "timeoutMillis", 30_000);
        if (forks < 1
                || threads != 1
                || warmupIterations < 0
                || measurementIterations < 1
                || warmupMillis < 1
                || measurementMillis < 1
                || timeoutMillis < 1) {
            throw new IllegalArgumentException(
                    "HTTP/3 small-write runs require at least one fork, exactly one worker, non-negative warmups, "
                            + "positive measurements, and positive timings");
        }
        if (!smoke
                && (forks < 2
                || warmupIterations < 3
                || measurementIterations < 5
                || warmupMillis < 500
                || measurementMillis < 1000)) {
            throw new IllegalArgumentException(
                    "HTTP/3 small-write evidence requires at least two forks, three 500 ms warmups, "
                            + "and five 1000 ms measurements");
        }
        BenchmarkSourceIdentity.freezeRuntimeClassPath();

        var repositoryRoot = Http3QuicEvidenceScope.repositoryRoot();
        String runId = System.getProperty(PREFIX + "runId", smoke ? "smoke" : null);
        if (runId == null || !RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException(
                    "HTTP/3 small-write evidence requires an alphanumeric runId containing only '.', '_', or '-'");
        }
        BenchmarkSourceIdentity.SourceIdentity verifiedIdentity = null;
        if (!smoke) {
            verifiedIdentity = BenchmarkSourceIdentity.verify(
                    repositoryRoot,
                    Http3QuicEvidenceScope.manifest(repositoryRoot),
                    Http3QuicEvidenceScope.buildManifest(repositoryRoot),
                    Http3QuicEvidenceScope.sourcePaths(),
                    Http3QuicEvidenceScope.artifacts());
        }
        String sourceIdentity = verifiedIdentity == null
                ? "functional-smoke"
                : verifiedIdentity.manifestSha256();

        String shapeProperty = System.getProperty(PREFIX + "shape");
        String[] shapes;
        if (shapeProperty == null || shapeProperty.isBlank()) {
            shapes = Arrays.stream(Http3SmallWriteJmhBenchmark.Shape.values())
                    .map(Enum::name)
                    .toArray(String[]::new);
        } else {
            shapes = Arrays.stream(shapeProperty.split(","))
                    .map(String::trim)
                    .toArray(String[]::new);
            for (String shape : shapes) {
                Http3SmallWriteJmhBenchmark.Shape.valueOf(shape);
            }
        }

        String modeName = runMode.name().toLowerCase(Locale.ROOT);
        String resultStem = "http3-small-write-" + modeName + "-" + runId;
        Http3QuicEvidenceScope.EvidencePaths evidencePaths = smoke
                ? null
                : Http3QuicEvidenceScope.evidencePaths(repositoryRoot, resultStem);
        Path resultPath = smoke
                ? Path.of("./target").resolve(resultStem + ".json")
                : evidencePaths.result();
        Path outputPath = smoke
                ? Path.of("./target").resolve(resultStem + ".log")
                : evidencePaths.output();
        Path metadataPath = smoke
                ? Path.of("./target").resolve(resultStem + ".properties")
                : evidencePaths.metadata();
        Path bundleManifest = smoke ? null : evidencePaths.sourceManifest();
        Path bundleBuildManifest = smoke ? null : evidencePaths.buildManifest();
        Path bundleBuildLog = smoke ? null : evidencePaths.buildLog();
        Path stagedResult = staged(resultPath);
        Path stagedOutput = staged(outputPath);
        Path stagedMetadata = staged(metadataPath);
        try (BenchmarkSourceIdentity.EvidenceBundle evidenceBundle = smoke
                ? null
                : BenchmarkSourceIdentity.reserveEvidenceBundle(
                        resultPath,
                        outputPath,
                        metadataPath,
                        bundleManifest,
                        bundleBuildManifest,
                        bundleBuildLog)) {
            ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                    .include(INCLUDE)
                    .mode(runMode == RunMode.LATENCY ? Mode.SampleTime : Mode.AverageTime)
                    .timeUnit(TimeUnit.MILLISECONDS)
                    .forks(forks)
                    .threads(threads)
                    .resultFormat(ResultFormatType.JSON)
                    .result(stagedResult.toString())
                    .output(stagedOutput.toString())
                    .param("shape", shapes)
                    .param("sourceIdentity", sourceIdentity)
                    .warmupIterations(warmupIterations)
                    .warmupTime(TimeValue.milliseconds(warmupMillis))
                    .measurementIterations(measurementIterations)
                    .measurementTime(TimeValue.milliseconds(measurementMillis))
                    .timeout(TimeValue.milliseconds(timeoutMillis))
                    .shouldFailOnError(true);
            if (verifiedIdentity != null) {
                optionsBuilder.jvmArgsAppend(
                                "-D" + BenchmarkSourceIdentity.RUNTIME_CLASSPATH_SHA256_PROPERTY
                                        + "=" + verifiedIdentity.classpathSha256())
                        .addProfiler(RuntimeClasspathVerifier.class);
            }
            if (runMode == RunMode.ALLOCATION) {
                optionsBuilder.addProfiler(ProcessAllocationProfiler.class);
            } else if (runMode == RunMode.CPU) {
                optionsBuilder.addProfiler(ProcessCpuProfiler.class);
            }

            Options options = optionsBuilder.build();
            new Runner(options).run();

            Properties metadata = new Properties();
            metadata.setProperty("generatedAt", Instant.now().toString());
            metadata.setProperty("mode", modeName);
            metadata.setProperty("runId", runId);
            metadata.setProperty("sourceIdentity", sourceIdentity);
            if (verifiedIdentity != null) {
                metadata.setProperty("sourceManifest", bundleManifest.getFileName().toString());
                metadata.setProperty("buildManifest", bundleBuildManifest.getFileName().toString());
                metadata.setProperty("buildLog", bundleBuildLog.getFileName().toString());
                metadata.setProperty("buildManifestSha256", verifiedIdentity.buildManifestSha256());
                metadata.setProperty("buildLogSha256", verifiedIdentity.buildLogSha256());
                metadata.setProperty("controlledRepositorySha256",
                                     verifiedIdentity.controlledRepositorySha256());
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
            }
            metadata.setProperty("include", INCLUDE);
            metadata.setProperty("shapes", String.join(",", shapes));
            metadata.setProperty(
                    "shapeDefinitions",
                    Arrays.stream(Http3SmallWriteJmhBenchmark.Shape.values())
                            .map(shape -> shape.name() + ":" + shape.bodySize() + ":" + shape.chunkSize())
                            .collect(Collectors.joining(",")));
            metadata.setProperty("applicationBufferBytes",
                                 Integer.toString(Http3SmallWriteJmhBenchmark.APPLICATION_BUFFER_SIZE));
            metadata.setProperty("forks", Integer.toString(forks));
            metadata.setProperty("threads", Integer.toString(threads));
            metadata.setProperty("warmupIterations", Integer.toString(warmupIterations));
            metadata.setProperty("warmupMillis", Long.toString(warmupMillis));
            metadata.setProperty("measurementIterations", Integer.toString(measurementIterations));
            metadata.setProperty("measurementMillis", Long.toString(measurementMillis));
            metadata.setProperty("timeoutMillis", Long.toString(timeoutMillis));
            metadata.setProperty("jmhMode",
                                 runMode == RunMode.LATENCY ? Mode.SampleTime.name() : Mode.AverageTime.name());
            metadata.setProperty("profiler", switch (runMode) {
            case ALLOCATION -> ProcessAllocationProfiler.class.getName();
            case CPU -> ProcessCpuProfiler.class.getName();
            default -> "none";
            });
            metadata.setProperty("result", resultPath.getFileName().toString());
            metadata.setProperty("output", outputPath.getFileName().toString());
            metadata.setProperty("javaVersion", System.getProperty("java.version"));
            metadata.setProperty("javaVm", System.getProperty("java.vm.name"));
            metadata.setProperty("osName", System.getProperty("os.name"));
            metadata.setProperty("osVersion", System.getProperty("os.version"));
            metadata.setProperty("osArch", System.getProperty("os.arch"));
            try (var outputStream = Files.newOutputStream(stagedMetadata)) {
                metadata.store(outputStream, "HTTP/3 small-write benchmark effective configuration");
            }
            if (!smoke) {
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
                    throw new IllegalStateException("HTTP/3 small-write source identity changed during the run");
                }
            }

            if (smoke) {
                BenchmarkSourceIdentity.publishReplacing(stagedOutput, outputPath);
                BenchmarkSourceIdentity.publishReplacing(stagedMetadata, metadataPath);
                BenchmarkSourceIdentity.publishReplacing(stagedResult, resultPath);
            } else {
                evidenceBundle.publishBuildManifest(verifiedIdentity, bundleBuildManifest);
                evidenceBundle.publishManifest(verifiedIdentity, bundleManifest);
                evidenceBundle.publishBuildLog(verifiedIdentity, bundleBuildLog);
                evidenceBundle.publish(stagedOutput, outputPath);
                evidenceBundle.publish(stagedMetadata, metadataPath);
                evidenceBundle.commit(stagedResult);
            }
        } finally {
            Files.deleteIfExists(stagedResult);
            Files.deleteIfExists(stagedOutput);
            Files.deleteIfExists(stagedMetadata);
        }
    }

    private static Path staged(Path resultPath) throws Exception {
        Path result = resultPath.toAbsolutePath().normalize();
        Files.createDirectories(result.getParent());
        return Files.createTempFile(result.getParent(), "." + result.getFileName() + "-", ".tmp");
    }

    private enum RunMode {
        SMOKE,
        LATENCY,
        ALLOCATION,
        CPU
    }
}
