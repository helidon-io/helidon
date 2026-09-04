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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.SplittableRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import io.helidon.http.Status;
import io.helidon.tests.benchmark.BenchmarkSourceIdentity;
import io.helidon.tests.benchmark.Http3QuicEvidenceScope;

import org.junit.jupiter.api.Test;

class Http3AdverseNetworkEvidence {
    private static final String PREFIX = "http3.adverse.network.";
    private static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final int BODY_SIZE = 64 * 1024;
    private static final byte[] BODY = new byte[BODY_SIZE];
    private static final int BOOTSTRAP_REPLICATES = 2000;
    private static final String CHILD_PROPERTY = PREFIX + "child";
    private static final String LEGACY_RESULT_PROPERTY = PREFIX + "result";
    private static final String CHILD_RESULT_PROPERTY = PREFIX + "internal.childResult";
    private static final String SOURCE_MANIFEST_NAME_PROPERTY = PREFIX + "sourceManifestName";
    private static final String BUILD_MANIFEST_NAME_PROPERTY = PREFIX + "buildManifestName";
    private static final String BUILD_LOG_NAME_PROPERTY = PREFIX + "buildLogName";
    private static final String CHILD_COMPLETE = "HTTP3_ADVERSE_COMPLETE";
    private static final String CHILD_PROGRESS = "HTTP3_ADVERSE_PROGRESS";
    private static final String OUTPUT_EOF = "\u0000";
    private static final long STALLED_CLOSE_STALL_AFTER_DATAGRAMS = 0;
    private static final long STALLED_CLOSE_STALL_SAFETY_MILLIS = 2000;
    private static final long STALLED_CLOSE_QUIET_MILLIS = 50;
    private static final int STALLED_CLOSE_QUIET_ATTEMPTS = 5;
    private static final int STALLED_CLOSE_MAX_QUEUED_DATAGRAMS = 4096;

    @Test
    void run() throws Exception {
        BenchmarkSourceIdentity.freezeRuntimeClassPath();
        Invocation invocation = Invocation.create();
        if (!Boolean.getBoolean(CHILD_PROPERTY)) {
            runForked(invocation);
            return;
        }

        runWorkload(invocation);
    }

    private void runWorkload(Invocation invocation) throws Exception {
        boolean evidence = invocation.evidence;
        String modeName = invocation.modeName;
        String runId = invocation.runId;
        int warmups = Integer.getInteger(PREFIX + "warmups", evidence ? 5 : 1);
        int samples = Integer.getInteger(PREFIX + "samples", evidence ? 100 : 2);
        int stalledCloseSamples = Integer.getInteger(PREFIX + "stalledCloseSamples", evidence ? 50 : 2);
        long requestTimeoutMillis = Long.getLong(PREFIX + "requestTimeoutMillis", 15_000);
        long drainMillis = Long.getLong(PREFIX + "drainMillis", 250);
        long stalledCloseTimeoutMillis = Long.getLong(PREFIX + "stalledCloseTimeoutMillis", 2000);
        long stalledRequestCompletionTimeoutMillis =
                Long.getLong(PREFIX + "stalledRequestCompletionTimeoutMillis", 5000);
        long baseSeed = Long.getLong(PREFIX + "baseSeed", 0x4833_5155_4943L);
        if (warmups < 0
                || warmups > 100
                || samples < 1
                || samples > 1000
                || stalledCloseSamples < 1
                || stalledCloseSamples > 1000
                || requestTimeoutMillis < 1000
                || requestTimeoutMillis > 60_000
                || drainMillis < 0
                || drainMillis > 5000
                || stalledCloseTimeoutMillis < 1
                || stalledCloseTimeoutMillis > 10_000
                || stalledRequestCompletionTimeoutMillis < stalledCloseTimeoutMillis
                || stalledRequestCompletionTimeoutMillis > requestTimeoutMillis
                || stalledRequestCompletionTimeoutMillis
                > UdpImpairmentProxyMain.MAX_DURATION_MILLIS - STALLED_CLOSE_STALL_SAFETY_MILLIS) {
            throw new IllegalArgumentException("HTTP/3 adverse-network evidence configuration is outside safe bounds");
        }
        if (evidence && (warmups < 5 || samples < 100 || stalledCloseSamples < 50)) {
            throw new IllegalArgumentException(
                    "HTTP/3 adverse-network evidence requires at least five warmups, one hundred successful samples, "
                            + "and fifty stalled-close samples");
        }

        BenchmarkSourceIdentity.SourceIdentity verifiedIdentity = null;
        if (evidence) {
            var repositoryRoot = Http3QuicEvidenceScope.repositoryRoot();
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
        Path resultPath = invocation.resultPath;
        if (evidence && Files.exists(resultPath)) {
            throw new IllegalArgumentException("HTTP/3 adverse-network runId would overwrite an existing artifact");
        }

        Duration requestTimeout = Duration.ofMillis(requestTimeoutMillis);
        Duration stalledCloseTimeout = Duration.ofMillis(stalledCloseTimeoutMillis);
        Duration stalledRequestCompletionTimeout = Duration.ofMillis(stalledRequestCompletionTimeoutMillis);
        Map<Scenario, List<Sample>> results = new EnumMap<>(Scenario.class);
        for (Scenario scenario : Scenario.values()) {
            results.put(scenario, new ArrayList<>(samples));
        }
        List<StalledCloseSample> stalledCloseResults = new ArrayList<>(stalledCloseSamples);
        try (Http3BenchmarkEnvironment environment = Http3BenchmarkEnvironment.create(routing -> routing
                .get("/payload", (_, response) -> response.send(BODY))
                .post("/stalled-upload", (_, response) -> response.send()))) {
            for (int warmup = 0; warmup < warmups; warmup++) {
                Scenario[] order = scenarioOrder(warmup);
                long seed = Math.addExact(baseSeed, warmup);
                for (int orderIndex = 0; orderIndex < order.length; orderIndex++) {
                    executeSample(environment,
                                  order[orderIndex],
                                  warmup,
                                  orderIndex,
                                  seed,
                                  requestTimeout,
                                  stalledCloseTimeout,
                                  drainMillis);
                    progress("successful warmup", warmup, order[orderIndex].name());
                }
                executeStalledCloseSample(
                        warmup,
                        Math.addExact(baseSeed, Math.addExact(10_000_000, warmup)),
                        requestTimeout,
                        stalledCloseTimeout,
                        stalledRequestCompletionTimeout);
                progress("stalled-close warmup", warmup, "ACTIVE_STALL");
            }

            for (int sample = 0; sample < samples; sample++) {
                int round = warmups + sample;
                Scenario[] order = scenarioOrder(round);
                long seed = Math.addExact(baseSeed, round);
                for (int orderIndex = 0; orderIndex < order.length; orderIndex++) {
                    Scenario scenario = order[orderIndex];
                    results.get(scenario).add(executeSample(environment,
                                                            scenario,
                                                            round,
                                                            orderIndex,
                                                            seed,
                                                            requestTimeout,
                                                            stalledCloseTimeout,
                                                            drainMillis));
                    progress("successful measurement", round, scenario.name());
                }
            }
            for (int sample = 0; sample < stalledCloseSamples; sample++) {
                int round = warmups + sample;
                stalledCloseResults.add(executeStalledCloseSample(
                        round,
                        Math.addExact(baseSeed, Math.addExact(10_000_000, round)),
                        requestTimeout,
                        stalledCloseTimeout,
                        stalledRequestCompletionTimeout));
                progress("stalled-close measurement", round, "ACTIVE_STALL");
            }
        }

        List<Sample> controlSamples = results.get(Scenario.CONTROL);
        Map<Scenario, Summary> pairedExchangeDeltas = new EnumMap<>(Scenario.class);
        for (Scenario scenario : Scenario.values()) {
            if (scenario == Scenario.CONTROL) {
                continue;
            }
            List<Sample> scenarioSamples = results.get(scenario);
            List<Long> pairedDeltas = new ArrayList<>(samples);
            for (int sample = 0; sample < samples; sample++) {
                pairedDeltas.add(Math.subtractExact(scenarioSamples.get(sample).exchangeNanos,
                                                   controlSamples.get(sample).exchangeNanos));
            }
            Summary deltaSummary = summary(
                    pairedDeltas,
                    baseSeed ^ ((long) scenario.ordinal() << 32) ^ 0x51A7_71C4L);
            pairedExchangeDeltas.put(scenario, deltaSummary);
            long loss =
                    scenarioSamples.stream().mapToLong(value -> value.exchangeMetrics.value("droppedLoss")).sum();
            long reorderSelections =
                    scenarioSamples.stream().mapToLong(value -> value.exchangeMetrics.value("reorderDelayed")).sum();
            long actualReorders =
                    scenarioSamples.stream().mapToLong(value -> value.exchangeMetrics.value("actuallyReordered")).sum();
            long stalls =
                    scenarioSamples.stream().mapToLong(value -> value.exchangeMetrics.value("stallTriggered")).sum();
            long stalledDatagrams =
                    scenarioSamples.stream().mapToLong(value -> value.exchangeMetrics.value("stalledDatagrams")).sum();
            switch (scenario) {
            case DELAY_JITTER -> {
                if (deltaSummary.p50 <= 0) {
                    throw new IllegalStateException("Delay/jitter evidence did not increase paired median latency");
                }
            }
            case LOSS -> {
                if (loss == 0) {
                    throw new IllegalStateException("Loss evidence did not drop a packet");
                }
            }
            case REORDER -> {
                if (reorderSelections == 0 || (evidence && actualReorders == 0)) {
                    throw new IllegalStateException("Reorder evidence did not delay and reorder packets");
                }
            }
            case STALL -> {
                if (stalls == 0 || stalledDatagrams == 0) {
                    throw new IllegalStateException("Stall evidence did not trigger a forwarding stall");
                }
            }
            case COMPOSITE -> {
                if (loss == 0
                        || reorderSelections == 0
                        || (evidence && actualReorders == 0)
                        || stalls == 0
                        || stalledDatagrams == 0) {
                    throw new IllegalStateException("Composite evidence did not exercise every configured impairment");
                }
            }
            default -> throw new IllegalStateException("Unexpected paired scenario");
            }
        }

        StringBuilder json = new StringBuilder(256_000);
        json.append("{\n")
                .append("  \"generatedAt\": ").append(jsonString(Instant.now().toString())).append(",\n")
                .append("  \"mode\": ").append(jsonString(modeName)).append(",\n")
                .append("  \"runId\": ").append(jsonString(runId)).append(",\n")
                .append("  \"sourceIdentity\": ").append(jsonString(sourceIdentity)).append(",\n")
                .append("  \"sourceManifest\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "none"
                                           : System.getProperty(SOURCE_MANIFEST_NAME_PROPERTY,
                                                                verifiedIdentity.manifestFile()))).append(",\n")
                .append("  \"buildManifest\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "none"
                                           : System.getProperty(BUILD_MANIFEST_NAME_PROPERTY,
                                                                verifiedIdentity.buildManifestFile()))).append(",\n")
                .append("  \"buildLog\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "none"
                                           : System.getProperty(BUILD_LOG_NAME_PROPERTY,
                                                                verifiedIdentity.buildLogFile()))).append(",\n")
                .append("  \"buildManifestSha256\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "unverified-smoke"
                                           : verifiedIdentity.buildManifestSha256())).append(",\n")
                .append("  \"buildLogSha256\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "unverified-smoke"
                                           : verifiedIdentity.buildLogSha256())).append(",\n")
                .append("  \"controlledRepositorySha256\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "unverified-smoke"
                                           : verifiedIdentity.controlledRepositorySha256())).append(",\n")
                .append("  \"repositoryLocalPrefix\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "unverified-smoke"
                                           : verifiedIdentity.repositoryLocalPrefix())).append(",\n")
                .append("  \"remoteRepositoryPrefix\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "unverified-smoke"
                                           : verifiedIdentity.remoteRepositoryPrefix())).append(",\n")
                .append("  \"scmRevision\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "unverified-smoke"
                                           : verifiedIdentity.scmRevision())).append(",\n")
                .append("  \"mavenTimeoutSeconds\": ")
                .append(verifiedIdentity == null ? 0 : verifiedIdentity.mavenTimeoutSeconds()).append(",\n")
                .append("  \"buildActiveProcessorCount\": ")
                .append(verifiedIdentity == null ? 0 : verifiedIdentity.activeProcessorCount()).append(",\n")
                .append("  \"sourceHead\": ")
                .append(jsonString(verifiedIdentity == null ? "unverified-smoke" : verifiedIdentity.head())).append(",\n")
                .append("  \"sourceStatusSha256\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "unverified-smoke"
                                           : verifiedIdentity.statusSha256())).append(",\n")
                .append("  \"sourceFileCount\": ")
                .append(verifiedIdentity == null ? 0 : verifiedIdentity.fileCount()).append(",\n")
                .append("  \"sourceArtifactCount\": ")
                .append(verifiedIdentity == null ? 0 : verifiedIdentity.artifactCount()).append(",\n")
                .append("  \"sourceRuntimeArtifactCount\": ")
                .append(verifiedIdentity == null ? 0 : verifiedIdentity.runtimeArtifactCount()).append(",\n")
                .append("  \"sourceClasspathEntryCount\": ")
                .append(verifiedIdentity == null ? 0 : verifiedIdentity.classpathEntryCount()).append(",\n")
                .append("  \"sourceClasspathSha256\": ")
                .append(jsonString(verifiedIdentity == null
                                           ? "unverified-smoke"
                                           : verifiedIdentity.classpathSha256())).append(",\n")
                .append("  \"javaVersion\": ").append(jsonString(System.getProperty("java.version"))).append(",\n")
                .append("  \"javaVm\": ").append(jsonString(System.getProperty("java.vm.name"))).append(",\n")
                .append("  \"osName\": ").append(jsonString(System.getProperty("os.name"))).append(",\n")
                .append("  \"osVersion\": ").append(jsonString(System.getProperty("os.version"))).append(",\n")
                .append("  \"osArch\": ").append(jsonString(System.getProperty("os.arch"))).append(",\n")
                .append("  \"bodyBytes\": ").append(BODY_SIZE).append(",\n")
                .append("  \"warmupsPerScenario\": ").append(warmups).append(",\n")
                .append("  \"samplesPerScenario\": ").append(samples).append(",\n")
                .append("  \"stalledCloseSamples\": ").append(stalledCloseSamples).append(",\n")
                .append("  \"bootstrapReplicates\": ").append(BOOTSTRAP_REPLICATES).append(",\n")
                .append("  \"requestTimeoutMillis\": ").append(requestTimeoutMillis).append(",\n")
                .append("  \"proxyDrainMillis\": ").append(drainMillis).append(",\n")
                .append("  \"stalledCloseTimeoutMillis\": ").append(stalledCloseTimeoutMillis).append(",\n")
                .append("  \"stalledRequestCompletionTimeoutMillis\": ")
                .append(stalledRequestCompletionTimeoutMillis).append(",\n")
                .append("  \"stalledServerIsolation\": ")
                .append(jsonString("fresh server per stalled-close sample; startup and stop excluded from closeNanos"))
                .append(",\n")
                .append("  \"baseSeed\": ").append(baseSeed).append(",\n")
                .append("  \"scenarioOrder\": ")
                .append(jsonString("rotating and direction-reversing by round; common seed within each round"))
                .append(",\n")
                .append("  \"comparisonStatistic\": ")
                .append(jsonString("paired adverse-minus-control exchange p95 with bootstrap 95% confidence interval"))
                .append(",\n")
                .append("  \"scenarios\": [\n");
        int scenarioIndex = 0;
        for (Scenario scenario : Scenario.values()) {
            if (scenarioIndex++ > 0) {
                json.append(",\n");
            }
            List<Sample> scenarioSamples = results.get(scenario);
            Summary exchangeSummary = summary(
                    scenarioSamples.stream().map(Sample::exchangeNanos).toList(),
                    baseSeed ^ ((long) scenario.ordinal() << 32) ^ 0xE71C_48A2L);
            Summary closeSummary = summary(
                    scenarioSamples.stream().map(Sample::closeNanos).toList(),
                    baseSeed ^ ((long) scenario.ordinal() << 32) ^ 0xC105_EA19L);
            UdpImpairmentProxy.Config config = scenario.config;
            json.append("    {\n")
                    .append("      \"name\": ").append(jsonString(scenario.name())).append(",\n")
                    .append("      \"delayMillis\": ").append(config.delayMillis()).append(",\n")
                    .append("      \"jitterMillis\": ").append(config.jitterMillis()).append(",\n")
                    .append("      \"lossPermille\": ").append(config.lossPermille()).append(",\n")
                    .append("      \"reorderPermille\": ").append(config.reorderPermille()).append(",\n")
                    .append("      \"reorderDelayMillis\": ").append(config.reorderDelayMillis()).append(",\n")
                    .append("      \"stallAfterDatagrams\": ").append(config.stallAfterDatagrams()).append(",\n")
                    .append("      \"stallMillis\": ").append(config.stallMillis()).append(",\n")
                    .append("      \"maxQueuedDatagrams\": ").append(config.maxQueuedDatagrams()).append(",\n")
                    .append("      \"exchangeNanos\": ");
            appendSummary(json, exchangeSummary, 6);
            json.append(",\n")
                    .append("      \"closeNanos\": ");
            appendSummary(json, closeSummary, 6);
            if (scenario != Scenario.CONTROL) {
                json.append(",\n")
                        .append("      \"pairedExchangeDeltaNanos\": ");
                appendSummary(json, pairedExchangeDeltas.get(scenario), 6);
            }
            json.append(",\n")
                    .append("      \"samples\": [\n");
            for (int sampleIndex = 0; sampleIndex < scenarioSamples.size(); sampleIndex++) {
                if (sampleIndex > 0) {
                    json.append(",\n");
                }
                Sample sample = scenarioSamples.get(sampleIndex);
                json.append("        {\n")
                        .append("          \"round\": ").append(sample.round).append(",\n")
                        .append("          \"order\": ").append(sample.order).append(",\n")
                        .append("          \"seed\": ").append(sample.seed).append(",\n")
                        .append("          \"exchangeNanos\": ").append(sample.exchangeNanos).append(",\n")
                        .append("          \"closeNanos\": ").append(sample.closeNanos).append(",\n")
                        .append("          \"exchangeProxyMetrics\": ");
                appendMetrics(json, sample.exchangeMetrics, 10);
                json.append(",\n")
                        .append("          \"finalProxyMetrics\": ");
                appendMetrics(json, sample.finalMetrics, 10);
                json.append("\n        }");
            }
            json.append("\n      ]\n")
                    .append("    }");
        }

        Summary stalledCloseSummary = summary(
                stalledCloseResults.stream().map(StalledCloseSample::closeNanos).toList(),
                baseSeed ^ 0x571A_11ED_C105EL);
        Summary stalledCompletionSummary = summary(
                stalledCloseResults.stream().map(StalledCloseSample::requestCompletionNanos).toList(),
                baseSeed ^ 0x571A_11ED_C0A9L);
        json.append("\n  ],\n")
                .append("  \"stalledClose\": {\n")
                .append("    \"stallAfterDatagrams\": ").append(STALLED_CLOSE_STALL_AFTER_DATAGRAMS).append(",\n")
                .append("    \"stallMillis\": ")
                .append(Math.addExact(stalledRequestCompletionTimeoutMillis,
                                      STALLED_CLOSE_STALL_SAFETY_MILLIS)).append(",\n")
                .append("    \"closeNanos\": ");
        appendSummary(json, stalledCloseSummary, 4);
        json.append(",\n")
                .append("    \"requestCompletionNanos\": ");
        appendSummary(json, stalledCompletionSummary, 4);
        json.append(",\n")
                .append("    \"samples\": [\n");
        for (int sampleIndex = 0; sampleIndex < stalledCloseResults.size(); sampleIndex++) {
            if (sampleIndex > 0) {
                json.append(",\n");
            }
            StalledCloseSample sample = stalledCloseResults.get(sampleIndex);
            json.append("      {\n")
                    .append("        \"round\": ").append(sample.round).append(",\n")
                    .append("        \"seed\": ").append(sample.seed).append(",\n")
                    .append("        \"closeNanos\": ").append(sample.closeNanos).append(",\n")
                    .append("        \"requestCompletionNanos\": ")
                    .append(sample.requestCompletionNanos).append(",\n")
                    .append("        \"proxyMetrics\": ");
            appendMetrics(json, sample.metrics, 8);
            json.append("\n      }");
        }
        json.append("\n    ]\n")
                .append("  }\n")
                .append("}\n");
        Files.createDirectories(resultPath.getParent());
        Files.writeString(resultPath, json, StandardCharsets.UTF_8);
        System.out.println(CHILD_COMPLETE);
        System.out.flush();
    }

    private void runForked(Invocation invocation) throws Exception {
        Path resultPath = invocation.resultPath.toAbsolutePath().normalize();
        Http3QuicEvidenceScope.EvidencePaths evidencePaths = null;
        BenchmarkSourceIdentity.SourceIdentity sourceIdentity = null;
        if (invocation.evidence) {
            var repositoryRoot = Http3QuicEvidenceScope.repositoryRoot();
            evidencePaths = Http3QuicEvidenceScope.evidencePaths(
                    repositoryRoot,
                    "http3-adverse-network-" + invocation.modeName + "-" + invocation.runId);
            if (!resultPath.equals(evidencePaths.result())) {
                throw new IllegalStateException("HTTP/3 adverse-network result is outside its evidence bundle");
            }
            sourceIdentity = BenchmarkSourceIdentity.verify(
                    repositoryRoot,
                    Http3QuicEvidenceScope.manifest(repositoryRoot),
                    Http3QuicEvidenceScope.buildManifest(repositoryRoot),
                    Http3QuicEvidenceScope.sourcePaths(),
                    Http3QuicEvidenceScope.artifacts());
        }
        Path outputPath = evidencePaths == null ? null : evidencePaths.output();
        Path metadataPath = evidencePaths == null ? null : evidencePaths.metadata();
        Path bundleManifest = evidencePaths == null ? null : evidencePaths.sourceManifest();
        Path bundleBuildManifest = evidencePaths == null ? null : evidencePaths.buildManifest();
        Path bundleBuildLog = evidencePaths == null ? null : evidencePaths.buildLog();

        try (BenchmarkSourceIdentity.EvidenceBundle evidenceBundle = invocation.evidence
                ? BenchmarkSourceIdentity.reserveEvidenceBundle(
                        resultPath,
                        outputPath,
                        metadataPath,
                        bundleManifest,
                        bundleBuildManifest,
                        bundleBuildLog)
                : null) {
            Files.createDirectories(resultPath.getParent());
            Path childResult = Files.createTempFile(resultPath.getParent(),
                                                    "." + resultPath.getFileName() + "-",
                                                    ".tmp");
            Files.delete(childResult);
            Path stagedOutput = invocation.evidence
                    ? Files.createTempFile(resultPath.getParent(),
                                           "." + outputPath.getFileName() + "-",
                                           ".tmp")
                    : null;
            Path stagedMetadata = invocation.evidence
                    ? Files.createTempFile(resultPath.getParent(),
                                           "." + metadataPath.getFileName() + "-",
                                           ".tmp")
                    : null;
            List<String> command = new ArrayList<>();
            command.add(ForkedTestProcess.javaExecutable());
            List<String> propertyNames = System.getProperties()
                    .stringPropertyNames()
                    .stream()
                    .filter(name -> name.startsWith(PREFIX)
                            && !CHILD_PROPERTY.equals(name)
                            && !LEGACY_RESULT_PROPERTY.equals(name)
                            && !CHILD_RESULT_PROPERTY.equals(name)
                            && !SOURCE_MANIFEST_NAME_PROPERTY.equals(name)
                            && !BUILD_MANIFEST_NAME_PROPERTY.equals(name)
                            && !BUILD_LOG_NAME_PROPERTY.equals(name))
                    .sorted()
                    .toList();
            for (String propertyName : propertyNames) {
                command.add("-D" + propertyName + "=" + System.getProperty(propertyName));
            }
            String evidenceRootProperty =
                    System.getProperty(Http3QuicEvidenceScope.EVIDENCE_ROOT_PROPERTY);
            if (evidenceRootProperty != null) {
                command.add("-D" + Http3QuicEvidenceScope.EVIDENCE_ROOT_PROPERTY
                                    + "=" + evidenceRootProperty);
            }
            for (String propertyName : List.of(
                    Http3QuicEvidenceScope.MAVEN_LOCAL_REPOSITORY_PROPERTY,
                    "aether.enhancedLocalRepository.split",
                    "aether.enhancedLocalRepository.localPrefix",
                    "aether.enhancedLocalRepository.remotePrefix")) {
                String value = System.getProperty(propertyName);
                if (value != null) {
                    command.add("-D" + propertyName + "=" + value);
                }
            }
            command.add("-D" + CHILD_PROPERTY + "=true");
            command.add("-D" + CHILD_RESULT_PROPERTY + "=" + childResult);
            if (invocation.evidence) {
                command.add("-D" + SOURCE_MANIFEST_NAME_PROPERTY + "=" + bundleManifest.getFileName());
                command.add("-D" + BUILD_MANIFEST_NAME_PROPERTY + "=" + bundleBuildManifest.getFileName());
                command.add("-D" + BUILD_LOG_NAME_PROPERTY + "=" + bundleBuildLog.getFileName());
            }
            command.add("-cp");
            command.add(ForkedTestProcess.classPath());
            command.add(Http3AdverseNetworkWorkloadMain.class.getName());

            Process process = null;
            Thread outputThread = null;
            try {
                process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();
                BlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
                Process runningProcess = process;
                outputThread = Thread.ofVirtual()
                        .name("http3-adverse-network-child-output")
                        .start(() -> {
                            try (BufferedReader reader = runningProcess.inputReader(StandardCharsets.UTF_8)) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    outputLines.add(line);
                                }
                            } catch (IOException e) {
                                outputLines.add("HTTP3_ADVERSE_OUTPUT_ERROR " + e);
                            } finally {
                                outputLines.add(OUTPUT_EOF);
                            }
                        });

                long requestTimeoutMillis = Long.getLong(PREFIX + "requestTimeoutMillis", 15_000);
                long progressTimeoutMillis = Math.addExact(requestTimeoutMillis, 20_000);
                long progressDeadline = System.nanoTime()
                        + TimeUnit.MILLISECONDS.toNanos(Math.max(30_000, progressTimeoutMillis));
                StringBuilder childOutput = new StringBuilder();
                boolean complete = false;
                while (true) {
                    long remaining = progressDeadline - System.nanoTime();
                    if (remaining <= 0) {
                        throw new TimeoutException(
                                "HTTP/3 adverse-network child made no bounded progress:\n" + childOutput);
                    }
                    String line = outputLines.poll(remaining, TimeUnit.NANOSECONDS);
                    if (line == null) {
                        throw new TimeoutException(
                                "HTTP/3 adverse-network child made no bounded progress:\n" + childOutput);
                    }
                    if (OUTPUT_EOF.equals(line)) {
                        break;
                    }
                    childOutput.append(line).append('\n');
                    if (line.startsWith(CHILD_PROGRESS)) {
                        progressDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(progressTimeoutMillis);
                    } else if (CHILD_COMPLETE.equals(line)) {
                        complete = true;
                    }
                }
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    throw new TimeoutException("HTTP/3 adverse-network child did not terminate after completion");
                }
                if (process.exitValue() != 0 || !complete || !Files.isRegularFile(childResult)) {
                    throw new IllegalStateException("HTTP/3 adverse-network child failed with exit "
                                                            + process.exitValue() + ":\n" + childOutput);
                }
                if (sourceIdentity != null) {
                    var repositoryRoot = Http3QuicEvidenceScope.repositoryRoot();
                    Files.writeString(stagedOutput, childOutput, StandardCharsets.UTF_8);
                    String childResultText = Files.readString(childResult, StandardCharsets.UTF_8);
                    List<String> embeddedIdentity = List.of(
                            "  \"sourceIdentity\": " + jsonString(sourceIdentity.manifestSha256()) + ",",
                            "  \"buildManifestSha256\": "
                                    + jsonString(sourceIdentity.buildManifestSha256()) + ",",
                            "  \"buildLogSha256\": "
                                    + jsonString(sourceIdentity.buildLogSha256()) + ",",
                            "  \"controlledRepositorySha256\": "
                                    + jsonString(sourceIdentity.controlledRepositorySha256()) + ",",
                            "  \"sourceHead\": " + jsonString(sourceIdentity.head()) + ",",
                            "  \"sourceStatusSha256\": " + jsonString(sourceIdentity.statusSha256()) + ",",
                            "  \"sourceClasspathSha256\": "
                                    + jsonString(sourceIdentity.classpathSha256()) + ",");
                    for (String expected : embeddedIdentity) {
                        int first = childResultText.indexOf(expected);
                        if (first < 0 || first != childResultText.lastIndexOf(expected)) {
                            throw new IllegalStateException(
                                    "HTTP/3 adverse-network child embedded a different source identity");
                        }
                    }
                    Properties metadata = new Properties();
                    metadata.setProperty("generatedAt", Instant.now().toString());
                    metadata.setProperty("mode", invocation.modeName);
                    metadata.setProperty("runId", invocation.runId);
                    metadata.setProperty("sourceIdentity", sourceIdentity.manifestSha256());
                    metadata.setProperty("sourceManifest", bundleManifest.getFileName().toString());
                    metadata.setProperty("buildManifest", bundleBuildManifest.getFileName().toString());
                    metadata.setProperty("buildLog", bundleBuildLog.getFileName().toString());
                    metadata.setProperty("buildManifestSha256", sourceIdentity.buildManifestSha256());
                    metadata.setProperty("buildLogSha256", sourceIdentity.buildLogSha256());
                    metadata.setProperty("controlledRepositorySha256",
                                         sourceIdentity.controlledRepositorySha256());
                    metadata.setProperty("repositoryLocalPrefix", sourceIdentity.repositoryLocalPrefix());
                    metadata.setProperty("remoteRepositoryPrefix", sourceIdentity.remoteRepositoryPrefix());
                    metadata.setProperty("scmRevision", sourceIdentity.scmRevision());
                    metadata.setProperty("mavenTimeoutSeconds",
                                         Long.toString(sourceIdentity.mavenTimeoutSeconds()));
                    metadata.setProperty("buildActiveProcessorCount",
                                         Integer.toString(sourceIdentity.activeProcessorCount()));
                    metadata.setProperty("sourceHead", sourceIdentity.head());
                    metadata.setProperty("sourceStatusSha256", sourceIdentity.statusSha256());
                    metadata.setProperty("sourceRuntimeArtifactCount",
                                         Integer.toString(sourceIdentity.runtimeArtifactCount()));
                    metadata.setProperty("sourceClasspathSha256", sourceIdentity.classpathSha256());
                    metadata.setProperty("warmups",
                                         Integer.toString(Integer.getInteger(PREFIX + "warmups", 5)));
                    metadata.setProperty("samples",
                                         Integer.toString(Integer.getInteger(PREFIX + "samples", 100)));
                    metadata.setProperty(
                            "stalledCloseSamples",
                            Integer.toString(Integer.getInteger(PREFIX + "stalledCloseSamples", 50)));
                    metadata.setProperty(
                            "requestTimeoutMillis",
                            Long.toString(Long.getLong(PREFIX + "requestTimeoutMillis", 15_000)));
                    metadata.setProperty("drainMillis",
                                         Long.toString(Long.getLong(PREFIX + "drainMillis", 250)));
                    metadata.setProperty(
                            "stalledCloseTimeoutMillis",
                            Long.toString(Long.getLong(PREFIX + "stalledCloseTimeoutMillis", 2000)));
                    metadata.setProperty(
                            "stalledRequestCompletionTimeoutMillis",
                            Long.toString(Long.getLong(
                                    PREFIX + "stalledRequestCompletionTimeoutMillis",
                                    5000)));
                    metadata.setProperty(
                            "baseSeed",
                            Long.toString(Long.getLong(PREFIX + "baseSeed", 0x4833_5155_4943L)));
                    metadata.setProperty(
                            "stalledServerIsolation",
                            "fresh server per stalled-close sample; startup and stop excluded from closeNanos");
                    metadata.setProperty("result", resultPath.getFileName().toString());
                    metadata.setProperty("output", outputPath.getFileName().toString());
                    metadata.setProperty("javaVersion", System.getProperty("java.version"));
                    metadata.setProperty("javaVm", System.getProperty("java.vm.name"));
                    metadata.setProperty("osName", System.getProperty("os.name"));
                    metadata.setProperty("osVersion", System.getProperty("os.version"));
                    metadata.setProperty("osArch", System.getProperty("os.arch"));
                    try (var output = Files.newOutputStream(stagedMetadata)) {
                        metadata.store(output, "HTTP/3 adverse-network effective configuration");
                    }
                    BenchmarkSourceIdentity.sanitizeEvidenceArtifacts(
                            repositoryRoot,
                            resultPath.getParent(),
                            childResult,
                            childResult,
                            stagedOutput,
                            stagedMetadata);

                    BenchmarkSourceIdentity.SourceIdentity finalIdentity = BenchmarkSourceIdentity.verify(
                            repositoryRoot,
                            Http3QuicEvidenceScope.manifest(repositoryRoot),
                            Http3QuicEvidenceScope.buildManifest(repositoryRoot),
                            Http3QuicEvidenceScope.sourcePaths(),
                            Http3QuicEvidenceScope.artifacts());
                    if (!sourceIdentity.equals(finalIdentity)) {
                        throw new IllegalStateException("HTTP/3 adverse-network source identity changed during the run");
                    }
                    evidenceBundle.publishBuildLog(sourceIdentity, bundleBuildLog);
                    evidenceBundle.publishBuildManifest(sourceIdentity, bundleBuildManifest);
                    evidenceBundle.publishManifest(sourceIdentity, bundleManifest);
                    evidenceBundle.publish(stagedOutput, outputPath);
                    evidenceBundle.publish(stagedMetadata, metadataPath);
                    evidenceBundle.commit(childResult);
                } else {
                    BenchmarkSourceIdentity.publishReplacing(childResult, resultPath);
                }
            } catch (Exception | Error failure) {
                if (process != null && process.isAlive()) {
                    try {
                        terminateProcessTree(process);
                    } catch (Exception | Error cleanupFailure) {
                        if (cleanupFailure != failure) {
                            failure.addSuppressed(cleanupFailure);
                        }
                    }
                }
                throw failure;
            } finally {
                if (outputThread != null && outputThread.isAlive()) {
                    outputThread.interrupt();
                }
                Files.deleteIfExists(childResult);
                if (stagedOutput != null) {
                    Files.deleteIfExists(stagedOutput);
                }
                if (stagedMetadata != null) {
                    Files.deleteIfExists(stagedMetadata);
                }
            }
        }
    }

    private static void terminateProcessTree(Process process) throws Exception {
        List<ProcessHandle> descendants = process.descendants().toList();
        for (ProcessHandle descendant : descendants) {
            descendant.destroyForcibly();
        }
        process.destroyForcibly();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (process.isAlive() && System.nanoTime() < deadline) {
            process.waitFor(100, TimeUnit.MILLISECONDS);
        }
        for (ProcessHandle descendant : descendants) {
            while (descendant.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
        }
        if (process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
            throw new IllegalStateException("Could not terminate the HTTP/3 adverse-network process tree");
        }
    }

    private static void progress(String phase, int round, String scenario) {
        System.out.println(CHILD_PROGRESS + " phase=" + phase.replace(' ', '_')
                                   + " round=" + round
                                   + " scenario=" + scenario);
        System.out.flush();
    }

    private static Sample executeSample(Http3BenchmarkEnvironment environment,
                                        Scenario scenario,
                                        int round,
                                        int order,
                                        long seed,
                                        Duration requestTimeout,
                                        Duration closeTimeout,
                                        long drainMillis) throws Exception {
        Http3Client client = null;
        try (UdpImpairmentProxy proxy = UdpImpairmentProxy.start(environment, scenario.config, seed)) {
            client = environment.client(environment.baseUri(proxy.port()), requestTimeout);
            long exchangeStarted = System.nanoTime();
            try {
                readSuccessfulPayload(client, requestTimeout);
            } catch (Exception | Error failure) {
                try {
                    UdpImpairmentProxy.Metrics failureMetrics = proxy.stopAndMetrics();
                    failure.addSuppressed(new IllegalStateException(
                            "HTTP/3 adverse-network exchange failed for scenario " + scenario
                                    + ", round " + round
                                    + ", order " + order
                                    + ", seed " + seed
                                    + ", proxy metrics " + failureMetrics.values()));
                } catch (Exception | Error metricsFailure) {
                    failure.addSuppressed(metricsFailure);
                }
                throw failure;
            }
            long exchangeNanos = System.nanoTime() - exchangeStarted;
            UdpImpairmentProxy.Metrics exchangeMetrics = proxy.snapshotMetrics();
            validateAccountedMetrics(exchangeMetrics, seed, false);
            if (scenario == Scenario.CONTROL
                    && (exchangeMetrics.value("droppedLoss") != 0
                    || exchangeMetrics.value("reorderDelayed") != 0
                    || exchangeMetrics.value("actuallyReordered") != 0
                    || exchangeMetrics.value("stallTriggered") != 0
                    || exchangeMetrics.value("stalledDatagrams") != 0)) {
                throw new IllegalStateException("Control HTTP/3 sample applied an impairment: "
                                                        + exchangeMetrics.values());
            }

            long closeStarted = System.nanoTime();
            Http3Client closingClient = client;
            client = null;
            closingClient.closeResource();
            long closeNanos = System.nanoTime() - closeStarted;
            if (closeNanos > closeTimeout.toNanos()) {
                throw new TimeoutException("HTTP/3 client close exceeded the successful-exchange evidence threshold");
            }

            if (drainMillis > 0) {
                Thread.sleep(drainMillis);
            }
            UdpImpairmentProxy.Metrics finalMetrics = proxy.stopAndMetrics();
            validateAccountedMetrics(finalMetrics, seed, true);
            return new Sample(round,
                              order,
                              seed,
                              exchangeNanos,
                              closeNanos,
                              exchangeMetrics,
                              finalMetrics);
        } catch (Exception | Error failure) {
            boolean interrupted = failure instanceof InterruptedException || Thread.currentThread().isInterrupted();
            if (interrupted) {
                Thread.interrupted();
            }
            if (client != null) {
                try {
                    client.closeResource();
                } catch (Exception | Error cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            throw failure;
        }
    }

    private static StalledCloseSample executeStalledCloseSample(int round,
                                                                 long seed,
                                                                 Duration requestTimeout,
                                                                 Duration closeTimeout,
                                                                 Duration requestCompletionTimeout) throws Exception {
        Http3Client client = null;
        Thread requestThread = null;
        CountDownLatch requestWriteAllowed = new CountDownLatch(1);
        UdpImpairmentProxy.Config proxyConfig =
                new UdpImpairmentProxy.Config(0, 0, 0, 0, 0, 0, 0, STALLED_CLOSE_MAX_QUEUED_DATAGRAMS);
        try (Http3BenchmarkEnvironment environment = Http3BenchmarkEnvironment.create(routing -> routing
                .get("/payload", (_, response) -> response.send(BODY))
                .post("/stalled-upload", (_, response) -> response.send()));
             UdpImpairmentProxy proxy = UdpImpairmentProxy.start(environment, proxyConfig, seed)) {
            client = environment.client(environment.baseUri(proxy.port()), requestTimeout);
            readSuccessfulPayload(client, requestTimeout);
            UdpImpairmentProxy.Metrics establishedMetrics = proxy.snapshotMetrics();
            validateAccountedMetrics(establishedMetrics, seed, true);
            if (establishedMetrics.value("droppedLoss") != 0
                    || establishedMetrics.value("reorderDelayed") != 0
                    || establishedMetrics.value("stallTriggered") != 0
                    || establishedMetrics.value("stalledDatagrams") != 0) {
                throw new IllegalStateException("HTTP/3 stalled-close sample did not establish a clean connection: "
                                                        + establishedMetrics.values());
            }

            Http3Client activeClient = client;
            CountDownLatch requestWriterReady = new CountDownLatch(1);
            CompletableFuture<RequestOutcome> requestOutcome = new CompletableFuture<>();
            requestThread = Thread.ofVirtual()
                    .name("http3-adverse-stalled-request")
                    .start(() -> {
                        try (Http3ClientResponse ignored = activeClient.post("/stalled-upload")
                                .outputStream(outputStream -> {
                                    requestWriterReady.countDown();
                                    try {
                                        if (!requestWriteAllowed.await(requestTimeout.toMillis(),
                                                                       TimeUnit.MILLISECONDS)) {
                                            throw new IOException(
                                                    "Timed out before releasing the active-stall request write");
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new IOException("Interrupted before the active-stall request write", e);
                                    }
                                    outputStream.write(1);
                                    outputStream.flush();
                                })) {
                            requestOutcome.complete(new RequestOutcome(null, System.nanoTime()));
                        } catch (Throwable failure) {
                            requestOutcome.complete(new RequestOutcome(failure, System.nanoTime()));
                        }
                    });
            if (!requestWriterReady.await(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("HTTP/3 stalled-close request writer did not become ready");
            }
            if (requestOutcome.isDone()) {
                throw new IllegalStateException("HTTP/3 stalled-close request failed before impairment arming");
            }

            boolean quiescent = false;
            for (int attempt = 0; attempt < STALLED_CLOSE_QUIET_ATTEMPTS; attempt++) {
                Thread.sleep(STALLED_CLOSE_QUIET_MILLIS);
                UdpImpairmentProxy.Metrics quietMetrics = proxy.snapshotMetrics();
                validateAccountedMetrics(quietMetrics, seed, true);
                if (quietMetrics.value("receivedClient") == establishedMetrics.value("receivedClient")
                        && quietMetrics.value("receivedServer") == establishedMetrics.value("receivedServer")) {
                    establishedMetrics = quietMetrics;
                    quiescent = true;
                    break;
                }
                establishedMetrics = quietMetrics;
            }
            if (!quiescent) {
                throw new IllegalStateException("HTTP/3 connection did not become quiescent before active-stall arming");
            }
            long stallMillis = Math.addExact(requestCompletionTimeout.toMillis(),
                                             STALLED_CLOSE_STALL_SAFETY_MILLIS);
            UdpImpairmentProxy.StallArm stallArm =
                    proxy.armClientStall(STALLED_CLOSE_STALL_AFTER_DATAGRAMS, stallMillis);
            if (stallArm.clientReceived() != establishedMetrics.value("receivedClient")
                    || stallArm.serverReceived() != establishedMetrics.value("receivedServer")) {
                throw new IllegalStateException("Traffic arrived between quiescence and active-stall arming");
            }
            long activationStarted = System.nanoTime();
            requestWriteAllowed.countDown();
            proxy.awaitClientStall(stallArm);
            if (requestOutcome.isDone()) {
                throw new IllegalStateException("HTTP/3 request was not active when the forwarding stall began");
            }

            long closeStarted = System.nanoTime();
            if (closeStarted - activationStarted
                    >= TimeUnit.MILLISECONDS.toNanos(STALLED_CLOSE_STALL_SAFETY_MILLIS)) {
                throw new TimeoutException("Active-stall activation consumed its close-relative safety margin");
            }
            long completionDeadline = Math.addExact(closeStarted, requestCompletionTimeout.toNanos());
            Http3Client closingClient = client;
            client = null;
            closingClient.closeResource();
            long closeNanos = System.nanoTime() - closeStarted;
            if (closeNanos > closeTimeout.toNanos()) {
                throw new TimeoutException("HTTP/3 client close exceeded the active-stall evidence threshold");
            }

            long remaining = completionDeadline - System.nanoTime();
            if (remaining <= 0) {
                throw new TimeoutException("Stalled HTTP/3 request did not complete within the close-relative deadline");
            }
            RequestOutcome outcome;
            try {
                outcome = requestOutcome.get(remaining, TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Stalled HTTP/3 request observer failed", e.getCause());
            }
            long requestCompletionNanos = outcome.completedAtNanos - closeStarted;
            if (requestCompletionNanos < 0) {
                throw new IllegalStateException("Stalled HTTP/3 request completed before client close began");
            }
            if (requestCompletionNanos > requestCompletionTimeout.toNanos()) {
                throw new TimeoutException("Stalled HTTP/3 request exceeded the close-relative completion deadline");
            }
            if (outcome.failure == null) {
                throw new IllegalStateException("Stalled HTTP/3 request completed successfully during client close");
            }
            while (requestThread.isAlive() && System.nanoTime() < completionDeadline) {
                long joinNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(100),
                                          Math.max(1, completionDeadline - System.nanoTime()));
                requestThread.join(TimeUnit.NANOSECONDS.toMillis(joinNanos),
                                   (int) (joinNanos % TimeUnit.MILLISECONDS.toNanos(1)));
            }
            if (requestThread.isAlive()) {
                throw new TimeoutException("Stalled HTTP/3 request thread remained active after client close");
            }
            requestThread = null;

            UdpImpairmentProxy.Metrics metrics = proxy.stopAndMetrics();
            validateAccountedMetrics(metrics, seed, false);
            if (metrics.value("seed") != seed
                    || metrics.value("receivedClient") == 0
                    || metrics.value("receivedServer") == 0
                    || metrics.value("stallTriggered") != 1
                    || metrics.value("stalledDatagrams") == 0
                    || metrics.value("droppedOverflow") != 0
                    || metrics.value("droppedNoClient") != 0
                    || metrics.value("queuedDatagrams") == 0
                    || metrics.value("queuedBytes") == 0) {
                throw new IllegalStateException("Active-stall close did not retain bounded in-flight datagrams: "
                                                        + metrics.values());
            }
            return new StalledCloseSample(round, seed, closeNanos, requestCompletionNanos, metrics);
        } catch (Exception | Error failure) {
            boolean interrupted = failure instanceof InterruptedException || Thread.currentThread().isInterrupted();
            if (interrupted) {
                Thread.interrupted();
            }
            requestWriteAllowed.countDown();
            if (client != null) {
                try {
                    client.closeResource();
                } catch (Exception | Error cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (requestThread != null && requestThread.isAlive()) {
                requestThread.interrupt();
                try {
                    requestThread.join(1000);
                } catch (InterruptedException cleanupFailure) {
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            throw failure;
        }
    }

    private static void readSuccessfulPayload(Http3Client client, Duration requestTimeout) throws Exception {
        byte[] readBuffer = new byte[8192];
        try (Http3ClientResponse response = client.get("/payload")
                .readTimeout(requestTimeout)
                .request();
             var inputStream = response.inputStream()) {
            if (!Status.OK_200.equals(response.status())
                    || !Http3Client.PROTOCOL_ID.equals(response.protocolId())) {
                throw new IllegalStateException("Expected successful HTTP/3 exchange, observed "
                                                        + response.status() + " over " + response.protocolId());
            }
            long byteCount = 0;
            int read;
            try {
                while ((read = inputStream.read(readBuffer)) != -1) {
                    byteCount += read;
                }
            } catch (Exception | Error failure) {
                failure.addSuppressed(new IllegalStateException(
                        "HTTP/3 adverse-network response read failed after " + byteCount
                                + " of " + BODY_SIZE + " expected bytes"));
                throw failure;
            }
            if (byteCount != BODY_SIZE) {
                throw new IllegalStateException("HTTP/3 adverse-network exchange completed with "
                                                        + byteCount + " bytes instead of " + BODY_SIZE);
            }
        }
    }

    private static void validateAccountedMetrics(UdpImpairmentProxy.Metrics metrics,
                                                 long seed,
                                                 boolean requireDrained) {
        long received = Math.addExact(metrics.value("receivedClient"), metrics.value("receivedServer"));
        long forwarded = Math.addExact(metrics.value("forwardedClient"), metrics.value("forwardedServer"));
        long accounted = Math.addExact(
                Math.addExact(forwarded, metrics.value("droppedLoss")),
                metrics.value("queuedDatagrams"));
        if (metrics.value("seed") != seed
                || received == 0
                || metrics.value("receivedClient") == 0
                || metrics.value("receivedServer") == 0
                || metrics.value("forwardedClient") == 0
                || metrics.value("forwardedServer") == 0
                || metrics.value("droppedOverflow") != 0
                || metrics.value("droppedNoClient") != 0
                || received != accounted
                || (requireDrained
                && (metrics.value("queuedDatagrams") != 0 || metrics.value("queuedBytes") != 0))) {
            throw new IllegalStateException("UDP impairment proxy reported incomplete or overflowed metrics: "
                                                    + metrics.values());
        }
    }

    private static Scenario[] scenarioOrder(int round) {
        Scenario[] values = Scenario.values();
        Scenario[] order = new Scenario[values.length];
        int shift = round / 2 % values.length;
        for (int index = 0; index < values.length; index++) {
            int source = (round & 1) == 0
                    ? (shift + index) % values.length
                    : Math.floorMod(shift - index, values.length);
            order[index] = values[source];
        }
        return order;
    }

    private static Summary summary(List<Long> values, long seed) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot summarize an empty evidence distribution");
        }
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        long[] p50Bootstrap = new long[BOOTSTRAP_REPLICATES];
        long[] p95Bootstrap = new long[BOOTSTRAP_REPLICATES];
        long[] resample = new long[sorted.length];
        SplittableRandom random = new SplittableRandom(seed);
        for (int replicate = 0; replicate < BOOTSTRAP_REPLICATES; replicate++) {
            for (int index = 0; index < resample.length; index++) {
                resample[index] = sorted[random.nextInt(sorted.length)];
            }
            Arrays.sort(resample);
            p50Bootstrap[replicate] = percentile(resample, 0.50);
            p95Bootstrap[replicate] = percentile(resample, 0.95);
        }
        Arrays.sort(p50Bootstrap);
        Arrays.sort(p95Bootstrap);
        return new Summary(percentile(sorted, 0.50),
                           percentile(sorted, 0.95),
                           sorted[sorted.length - 1],
                           percentile(p50Bootstrap, 0.025),
                           percentile(p50Bootstrap, 0.975),
                           percentile(p95Bootstrap, 0.025),
                           percentile(p95Bootstrap, 0.975));
    }

    private static long percentile(long[] sortedValues, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sortedValues.length) - 1);
        return sortedValues[index];
    }

    private static void appendSummary(StringBuilder json, Summary summary, int indent) {
        String indentation = " ".repeat(indent);
        json.append("{\n")
                .append(indentation).append("  \"p50\": ").append(summary.p50).append(",\n")
                .append(indentation).append("  \"p95\": ").append(summary.p95).append(",\n")
                .append(indentation).append("  \"max\": ").append(summary.max).append(",\n")
                .append(indentation).append("  \"p50Bootstrap95Low\": ")
                .append(summary.p50Bootstrap95Low).append(",\n")
                .append(indentation).append("  \"p50Bootstrap95High\": ")
                .append(summary.p50Bootstrap95High).append(",\n")
                .append(indentation).append("  \"p95Bootstrap95Low\": ")
                .append(summary.p95Bootstrap95Low).append(",\n")
                .append(indentation).append("  \"p95Bootstrap95High\": ")
                .append(summary.p95Bootstrap95High).append("\n")
                .append(indentation).append('}');
    }

    private static void appendMetrics(StringBuilder json, UdpImpairmentProxy.Metrics metrics, int indent) {
        String indentation = " ".repeat(indent);
        json.append("{");
        List<String> metricNames = UdpImpairmentProxy.metricNames();
        for (int metricIndex = 0; metricIndex < metricNames.size(); metricIndex++) {
            if (metricIndex > 0) {
                json.append(',');
            }
            String metricName = metricNames.get(metricIndex);
            json.append("\n")
                    .append(indentation)
                    .append(jsonString(metricName))
                    .append(": ")
                    .append(metrics.value(metricName));
        }
        json.append("\n").append(" ".repeat(Math.max(0, indent - 2))).append('}');
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
            case '"' -> result.append("\\\"");
            case '\\' -> result.append("\\\\");
            case '\b' -> result.append("\\b");
            case '\f' -> result.append("\\f");
            case '\n' -> result.append("\\n");
            case '\r' -> result.append("\\r");
            case '\t' -> result.append("\\t");
            default -> {
                if (character < 0x20) {
                    result.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                } else {
                    result.append(character);
                }
            }
            }
        }
        return result.append('"').toString();
    }

    private enum RunMode {
        SMOKE,
        EVIDENCE
    }

    private record Invocation(RunMode runMode,
                              boolean evidence,
                              String modeName,
                              String runId,
                              Path resultPath) {
        private static Invocation create() {
            String modeText = System.getProperty(PREFIX + "mode", "smoke");
            RunMode runMode;
            try {
                runMode = RunMode.valueOf(modeText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown HTTP/3 adverse-network mode: " + modeText, e);
            }
            boolean evidence = runMode == RunMode.EVIDENCE;
            String runId = System.getProperty(PREFIX + "runId", evidence ? null : "smoke");
            if (runId == null || !RUN_ID.matcher(runId).matches()) {
                throw new IllegalArgumentException(
                        "HTTP/3 adverse-network evidence requires an alphanumeric runId containing only '.', '_', or '-'");
            }
            String modeName = runMode.name().toLowerCase(Locale.ROOT);
            boolean child = Boolean.getBoolean(CHILD_PROPERTY);
            String legacyResult = System.getProperty(LEGACY_RESULT_PROPERTY);
            String childResult = System.getProperty(CHILD_RESULT_PROPERTY);
            if (evidence && !child && legacyResult != null) {
                throw new IllegalArgumentException(
                        "HTTP/3 adverse-network evidence derives its result from the common evidence root");
            }
            if (!child && childResult != null) {
                throw new IllegalArgumentException("HTTP/3 adverse-network child result is internal");
            }
            Path resultPath;
            if (child) {
                if (childResult == null || childResult.isBlank() || legacyResult != null) {
                    throw new IllegalArgumentException(
                            "HTTP/3 adverse-network child requires exactly one internal result path");
                }
                resultPath = Path.of(childResult);
            } else if (evidence) {
                resultPath = Http3QuicEvidenceScope.evidencePaths(
                        Http3QuicEvidenceScope.repositoryRoot(),
                        "http3-adverse-network-" + modeName + "-" + runId).result();
            } else {
                resultPath = Path.of(legacyResult == null
                                             ? "./target/http3-adverse-network-" + modeName + "-" + runId + ".json"
                                             : legacyResult);
            }
            return new Invocation(runMode, evidence, modeName, runId, resultPath);
        }
    }

    private enum Scenario {
        CONTROL(new UdpImpairmentProxy.Config(0, 0, 0, 0, 0, 0, 0, 4096)),
        DELAY_JITTER(new UdpImpairmentProxy.Config(10, 2, 0, 0, 0, 0, 0, 4096)),
        LOSS(new UdpImpairmentProxy.Config(0, 0, 50, 0, 0, 0, 0, 4096)),
        REORDER(new UdpImpairmentProxy.Config(0, 0, 0, 100, 20, 0, 0, 4096)),
        STALL(new UdpImpairmentProxy.Config(0, 0, 0, 0, 0, 8, 100, 4096)),
        COMPOSITE(new UdpImpairmentProxy.Config(10, 2, 50, 100, 20, 8, 100, 4096));

        private final UdpImpairmentProxy.Config config;

        Scenario(UdpImpairmentProxy.Config config) {
            this.config = config;
        }
    }

    private record Sample(int round,
                          int order,
                          long seed,
                          long exchangeNanos,
                          long closeNanos,
                          UdpImpairmentProxy.Metrics exchangeMetrics,
                          UdpImpairmentProxy.Metrics finalMetrics) {
    }

    private record StalledCloseSample(int round,
                                      long seed,
                                      long closeNanos,
                                      long requestCompletionNanos,
                                      UdpImpairmentProxy.Metrics metrics) {
    }

    private record RequestOutcome(Throwable failure, long completedAtNanos) {
    }

    private record Summary(long p50,
                           long p95,
                           long max,
                           long p50Bootstrap95Low,
                           long p50Bootstrap95High,
                           long p95Bootstrap95Low,
                           long p95Bootstrap95High) {
    }
}
