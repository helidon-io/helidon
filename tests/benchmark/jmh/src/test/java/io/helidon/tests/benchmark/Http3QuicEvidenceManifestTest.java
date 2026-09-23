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

package io.helidon.tests.benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class Http3QuicEvidenceManifestTest {
    private static final String CHILD_PROPERTY = "helidon.benchmark.evidence.generatorChild";
    private static final String STAGING_PREFIX_PROPERTY =
            "helidon.benchmark.evidence.stagingRepositoryPrefix";
    private static final String MAVEN_TIMEOUT_PROPERTY =
            "helidon.benchmark.evidence.mavenTimeoutSeconds";
    private static final String ACTIVE_PROCESSOR_COUNT_PROPERTY =
            "helidon.benchmark.evidence.activeProcessorCount";
    private static final String RESOLVER_SPLIT_PROPERTY = "aether.enhancedLocalRepository.split";
    private static final String RESOLVER_REMOTE_PREFIX_PROPERTY =
            "aether.enhancedLocalRepository.remotePrefix";
    private static final long DEFAULT_MAVEN_TIMEOUT_SECONDS = 1800;
    private static final long MINIMUM_MAVEN_TIMEOUT_SECONDS = 60;
    private static final long MAXIMUM_MAVEN_TIMEOUT_SECONDS = 21_600;
    private static final int DEFAULT_ACTIVE_PROCESSOR_COUNT = 2;
    private static final int MINIMUM_ACTIVE_PROCESSOR_COUNT = 1;
    private static final int MAXIMUM_ACTIVE_PROCESSOR_COUNT = 64;
    private static final long PROCESS_CLEANUP_SECONDS = 10;
    private static final long PROCESS_POLL_MILLIS = 100;
    private static final List<String> INHERITED_JAVA_MAVEN_OPTIONS = List.of(
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS",
            "MAVEN_OPTS");

    static void publishCampaignIdentity(BenchmarkSourceIdentity.EvidenceBundle campaignIdentity,
                                        Path stagedEvidenceRoot,
                                        Path campaignRoot) throws IOException {
        String sourceManifest = "http3-quic-controlled-source.manifest";
        for (String file : List.of("http3-quic-controlled-build.log",
                                   "http3-quic-controlled-build.manifest",
                                   sourceManifest)) {
            Path staged = stagedEvidenceRoot.resolve(file);
            if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Staged campaign identity is not a regular file: " + staged);
            }
            Path destination = campaignRoot.resolve(file);
            Path temporary = Files.createTempFile(campaignRoot, "." + file + "-", ".tmp");
            try {
                Files.copy(staged, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (file.equals(sourceManifest)) {
                    campaignIdentity.commit(temporary);
                } else {
                    campaignIdentity.publish(temporary, destination);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    @Test
    void generateAndVerify() throws Exception {
        Path repositoryRoot = Http3QuicEvidenceScope.repositoryRoot();
        if (Boolean.getBoolean(CHILD_PROPERTY)) {
            BenchmarkSourceIdentity.freezeRuntimeClassPath();
            String stagingPrefix = System.getProperty(STAGING_PREFIX_PROPERTY);
            var identity = BenchmarkSourceIdentity.createStaged(
                    repositoryRoot,
                    Http3QuicEvidenceScope.manifest(repositoryRoot),
                    Http3QuicEvidenceScope.buildManifest(repositoryRoot),
                    Http3QuicEvidenceScope.sourcePaths(),
                    Http3QuicEvidenceScope.artifacts(),
                    stagingPrefix);
            System.out.println("HTTP/3 and QUIC evidence source manifest SHA-256: "
                                       + identity.manifestSha256());
            return;
        }

        Path campaignRoot = Http3QuicEvidenceScope.evidenceRoot(repositoryRoot);
        String localRepositoryProperty =
                System.getProperty(Http3QuicEvidenceScope.MAVEN_LOCAL_REPOSITORY_PROPERTY);
        if (localRepositoryProperty == null || localRepositoryProperty.isBlank()) {
            throw new IllegalStateException("Maven did not expose its effective local repository");
        }
        Path localRepository = Path.of(localRepositoryProperty).toRealPath();
        if (!Boolean.parseBoolean(System.getProperty(RESOLVER_SPLIT_PROPERTY))) {
            throw new IllegalStateException("Controlled benchmark evidence requires split Maven local repositories");
        }
        String remotePrefix = System.getProperty(RESOLVER_REMOTE_PREFIX_PROPERTY);
        if (remotePrefix == null || remotePrefix.isBlank()) {
            throw new IllegalStateException("Maven did not expose its effective remote repository prefix");
        }
        long timeoutSeconds = parseTimeoutSeconds();
        int activeProcessorCount = parseActiveProcessorCount();
        Path maven = mavenExecutable();

        try (var campaignIdentity = BenchmarkSourceIdentity.reserveEvidenceBundle(
                Http3QuicEvidenceScope.manifest(repositoryRoot),
                Http3QuicEvidenceScope.buildManifest(repositoryRoot),
                Http3QuicEvidenceScope.buildLog(repositoryRoot));
             var snapshot = BenchmarkSourceIdentity.createBuildSnapshot(repositoryRoot);
             var controlledRepository =
                     BenchmarkSourceIdentity.createControlledRepository(localRepository, remotePrefix)) {
            try {
                List<String> build = new ArrayList<>();
                build.add(maven.toString());
                build.addAll(controlledRepository.mavenArguments());
                build.add("-Dmaven.buildNumber.revisionOnScmFailure=" + snapshot.head());
                build.add("-Ptests");
                build.add("-pl");
                build.add(":helidon-tests-benchmark-jmh");
                build.add("-am");
                build.add("clean");
                build.add("install");
                build.add("-DskipTests=true");
                build.add("-Dmaven.test.skip=true");
                build.add("-ntp");
                build.add("-V");
                MavenRun buildRun = runMaven(
                        snapshot.root(),
                        build,
                        "controlled HTTP/3 and QUIC reactor build",
                        timeoutSeconds,
                        activeProcessorCount);

                BenchmarkSourceIdentity.RepositoryIdentity repositoryIdentity =
                        controlledRepository.identity();
                String sanitizedInvocation = sanitize(
                        String.join(" ", build),
                        repositoryRoot,
                        snapshot.root(),
                        localRepository,
                        repositoryIdentity,
                        maven).stripTrailing();
                String invocationSha256;
                try {
                    invocationSha256 = HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(sanitizedInvocation.getBytes(StandardCharsets.UTF_8)));
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("SHA-256 is not available", e);
                }
                String sanitizedOutput = sanitize(
                        buildRun.output,
                        repositoryRoot,
                        snapshot.root(),
                        localRepository,
                        repositoryIdentity,
                        maven);
                String mavenVersion = sanitizedOutput.lines()
                        .filter(line -> line.startsWith("Apache Maven "))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Controlled Maven output did not report its version"));
                String buildLog = "format=1\n"
                        + "phase=controlled HTTP/3 and QUIC reactor build\n"
                        + "invocationSha256=" + invocationSha256 + "\n"
                        + "mavenTimeoutSeconds=" + timeoutSeconds + "\n"
                        + "activeProcessorCount=" + activeProcessorCount + "\n"
                        + "mavenVersion=" + mavenVersion + "\n"
                        + "javaVersion=" + System.getProperty("java.version") + "\n"
                        + "javaVendor=" + System.getProperty("java.vendor") + "\n"
                        + "command=" + sanitizedInvocation + "\n"
                        + "--- output ---\n"
                        + sanitizedOutput;

                Path stagedEvidenceRoot = snapshot.root().resolve(".http3-quic-evidence");
                Files.createDirectories(stagedEvidenceRoot);
                Path stagedBuildManifest =
                        stagedEvidenceRoot.resolve("http3-quic-controlled-build.manifest");
                Path stagedBuildLog =
                        stagedEvidenceRoot.resolve("http3-quic-controlled-build.log");
                snapshot.writeBuildEvidence(
                        stagedBuildManifest,
                        stagedBuildLog,
                        Http3QuicEvidenceScope.artifacts(),
                        repositoryIdentity,
                        new BenchmarkSourceIdentity.BuildMetadata(
                                invocationSha256,
                                mavenVersion,
                                System.getProperty("java.version"),
                                System.getProperty("java.vendor"),
                                timeoutSeconds,
                                activeProcessorCount),
                        buildLog);

                List<String> generate = new ArrayList<>();
                generate.add(maven.toString());
                generate.addAll(controlledRepository.mavenArguments());
                generate.add("-Dmaven.buildNumber.revisionOnScmFailure=" + snapshot.head());
                generate.add("-Ptests,jmh");
                generate.add("-pl");
                generate.add(":helidon-tests-benchmark-jmh");
                generate.add("-Dtest=" + Http3QuicEvidenceManifestTest.class.getSimpleName());
                generate.add("-D" + CHILD_PROPERTY + "=true");
                generate.add("-D" + STAGING_PREFIX_PROPERTY + "="
                                     + controlledRepository.stagingLocalPrefix());
                generate.add("-D" + Http3QuicEvidenceScope.LIVE_REPOSITORY_ROOT_PROPERTY
                                     + "=" + repositoryRoot);
                generate.add("-D" + BenchmarkSourceIdentity.RUNTIME_CLASSPATH_ROOT_PROPERTY
                                     + "=" + snapshot.root());
                generate.add("-D" + Http3QuicEvidenceScope.EVIDENCE_ROOT_PROPERTY
                                     + "=" + stagedEvidenceRoot);
                generate.add("clean");
                generate.add("test");
                generate.add("-o");
                generate.add("-ntp");
                runMaven(
                        snapshot.root(),
                        generate,
                        "clean HTTP/3 and QUIC benchmark build and manifest verification",
                        timeoutSeconds,
                        activeProcessorCount);

                snapshot.verifyUnchanged();
                if (!repositoryIdentity.equals(controlledRepository.identity())) {
                    throw new IllegalStateException(
                            "Controlled Maven repository changed during benchmark verification");
                }
                Path stagedSourceManifest =
                        stagedEvidenceRoot.resolve("http3-quic-controlled-source.manifest");
                if (!Files.isRegularFile(stagedSourceManifest)) {
                    throw new IllegalStateException("Clean benchmark verification did not create a source manifest");
                }

                controlledRepository.publish(repositoryIdentity);
                publishCampaignIdentity(campaignIdentity, stagedEvidenceRoot, campaignRoot);
                System.out.println("Controlled Maven repository local prefix: "
                                           + repositoryIdentity.localPrefix());
                System.out.println("Persistent HTTP/3 and QUIC evidence root: " + campaignRoot);
            } catch (MavenProcessSurvivedException e) {
                controlledRepository.retain();
                snapshot.retain();
                throw e;
            }
        }
    }

    static long parseTimeoutSeconds() {
        String configured = System.getProperty(
                MAVEN_TIMEOUT_PROPERTY,
                Long.toString(DEFAULT_MAVEN_TIMEOUT_SECONDS));
        long timeout;
        try {
            timeout = Long.parseLong(configured);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Controlled Maven timeout is not a number: " + configured, e);
        }
        if (timeout < MINIMUM_MAVEN_TIMEOUT_SECONDS || timeout > MAXIMUM_MAVEN_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("Controlled Maven timeout must be between "
                                                       + MINIMUM_MAVEN_TIMEOUT_SECONDS
                                                       + " and "
                                                       + MAXIMUM_MAVEN_TIMEOUT_SECONDS
                                                       + " seconds");
        }
        return timeout;
    }

    static int parseActiveProcessorCount() {
        String configured = System.getProperty(
                ACTIVE_PROCESSOR_COUNT_PROPERTY,
                Integer.toString(DEFAULT_ACTIVE_PROCESSOR_COUNT));
        int activeProcessorCount;
        try {
            activeProcessorCount = Integer.parseInt(configured);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Controlled Maven active processor count is not a number: " + configured,
                    e);
        }
        if (activeProcessorCount < MINIMUM_ACTIVE_PROCESSOR_COUNT
                || activeProcessorCount > MAXIMUM_ACTIVE_PROCESSOR_COUNT) {
            throw new IllegalArgumentException("Controlled Maven active processor count must be between "
                                                       + MINIMUM_ACTIVE_PROCESSOR_COUNT
                                                       + " and "
                                                       + MAXIMUM_ACTIVE_PROCESSOR_COUNT);
        }
        return activeProcessorCount;
    }

    private static Path mavenExecutable() {
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome == null || mavenHome.isBlank()) {
            mavenHome = System.getenv("MAVEN_HOME");
        }
        if (mavenHome == null || mavenHome.isBlank()) {
            mavenHome = System.getenv("M2_HOME");
        }
        if (mavenHome == null || mavenHome.isBlank()) {
            return Path.of(System.getProperty("os.name").startsWith("Windows") ? "mvn.cmd" : "mvn");
        }
        return Path.of(mavenHome,
                       "bin",
                       System.getProperty("os.name").startsWith("Windows") ? "mvn.cmd" : "mvn");
    }

    static MavenRun runMaven(Path repositoryRoot,
                             List<String> command,
                             String phase,
                             long timeoutSeconds,
                             int activeProcessorCount) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(repositoryRoot.toFile())
                .redirectErrorStream(true);
        configureProcessEnvironment(processBuilder, activeProcessorCount);
        Process process = processBuilder.start();
        Set<ProcessHandle> observedHandles = ConcurrentHashMap.newKeySet();
        observedHandles.add(process.toHandle());
        Thread shutdownHook = Thread.ofPlatform()
                .name("http3-quic-evidence-maven-shutdown")
                .unstarted(() -> {
                    try {
                        terminateProcessTree(process, observedHandles, phase);
                    } catch (RuntimeException | Error failure) {
                        System.err.println("Could not terminate controlled Maven during JVM shutdown: "
                                                   + failure.getMessage());
                    }
                });
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (RuntimeException | Error failure) {
            try {
                terminateProcessTree(process, observedHandles, phase);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        CompletableFuture<String> outputComplete = new CompletableFuture<>();
        Thread outputThread = Thread.ofVirtual()
                .name("http3-quic-evidence-maven-output")
                .start(() -> {
                    StringBuilder output = new StringBuilder();
                    try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                            output.append(line).append('\n');
                        }
                        outputComplete.complete(output.toString());
                    } catch (Exception | Error failure) {
                        outputComplete.completeExceptionally(failure);
                    }
                });
        MavenRun result = null;
        Throwable failure = null;
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try {
            while (process.isAlive()) {
                observedHandles.addAll(process.descendants().toList());
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException("Timed out during " + phase);
                }
                process.waitFor(
                        Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(PROCESS_POLL_MILLIS)),
                        TimeUnit.NANOSECONDS);
            }
            observedHandles.addAll(process.descendants().toList());
            String output;
            try {
                output = outputComplete.get(PROCESS_CLEANUP_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Could not read Maven output during " + phase, e.getCause());
            } catch (TimeoutException e) {
                throw new MavenProcessSurvivedException(
                        "Maven output remained open after " + phase,
                        e);
            }
            if (observedHandles.stream().anyMatch(ProcessHandle::isAlive)) {
                throw new MavenProcessSurvivedException(
                        "Maven descendants survived successful " + phase);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Maven failed during " + phase
                                                        + " with exit " + process.exitValue());
            }
            result = new MavenRun(output);
        } catch (Throwable caught) {
            failure = caught;
            interrupted = caught instanceof InterruptedException;
        }

        if (failure != null
                || process.isAlive()
                || observedHandles.stream().anyMatch(ProcessHandle::isAlive)) {
            try {
                terminateProcessTree(process, observedHandles, phase);
            } catch (RuntimeException | Error cleanupFailure) {
                var survived = cleanupFailure instanceof MavenProcessSurvivedException survivedFailure
                        ? survivedFailure
                        : new MavenProcessSurvivedException(
                                "Could not clean up Maven process tree during " + phase,
                                cleanupFailure);
                if (failure != null) {
                    survived.addSuppressed(failure);
                }
                failure = survived;
            }
        }

        try {
            process.getOutputStream().close();
        } catch (IOException _) {
            // Best-effort close after process completion.
        }
        if (outputThread.isAlive()) {
            try {
                process.getInputStream().close();
            } catch (IOException _) {
                // Best-effort close before bounded reader join.
            }
            outputThread.interrupt();
            long outputDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_CLEANUP_SECONDS);
            while (outputThread.isAlive() && System.nanoTime() < outputDeadline) {
                try {
                    outputThread.join(PROCESS_POLL_MILLIS);
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
            if (outputThread.isAlive()) {
                var readerFailure = new MavenProcessSurvivedException(
                        "Maven output reader survived cleanup during " + phase);
                if (failure != null) {
                    readerFailure.addSuppressed(failure);
                }
                failure = readerFailure;
            }
        }

        try {
            if (!Runtime.getRuntime().removeShutdownHook(shutdownHook)) {
                throw new IllegalStateException("Controlled Maven shutdown hook was not registered");
            }
        } catch (IllegalStateException shutdownInProgress) {
            if (failure == null) {
                failure = shutdownInProgress;
            } else {
                failure.addSuppressed(shutdownInProgress);
            }
        }

        interrupted |= Thread.interrupted();
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException("Unexpected controlled Maven failure", failure);
        }
        return result;
    }

    static void configureProcessEnvironment(ProcessBuilder processBuilder,
                                            int activeProcessorCount) {
        if (activeProcessorCount < MINIMUM_ACTIVE_PROCESSOR_COUNT
                || activeProcessorCount > MAXIMUM_ACTIVE_PROCESSOR_COUNT) {
            throw new IllegalArgumentException("Controlled Maven active processor count is outside safe bounds");
        }
        Map<String, String> environment = processBuilder.environment();
        for (String option : INHERITED_JAVA_MAVEN_OPTIONS) {
            String value = environment.get(option);
            if (value != null && !value.isBlank()) {
                throw new IllegalStateException(
                        "Controlled Maven evidence does not accept inherited " + option);
            }
        }
        environment.remove("MAVEN_ARGS");
        environment.put("JAVA_TOOL_OPTIONS", "-XX:ActiveProcessorCount=" + activeProcessorCount);
    }

    private static void terminateProcessTree(Process process,
                                             Set<ProcessHandle> observedHandles,
                                             String phase) {
        boolean interrupted = Thread.interrupted();
        RuntimeException cleanupFailure = null;
        try {
            try {
                process.getOutputStream().close();
            } catch (IOException _) {
                // The process may already have closed its input.
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_CLEANUP_SECONDS);
            while (System.nanoTime() < deadline) {
                try {
                    observedHandles.addAll(process.descendants().toList());
                    List<ProcessHandle> snapshot = List.copyOf(observedHandles);
                    for (ProcessHandle handle : snapshot) {
                        if (handle.isAlive()) {
                            observedHandles.addAll(handle.descendants().toList());
                        }
                    }
                } catch (RuntimeException observationFailure) {
                    if (cleanupFailure == null) {
                        cleanupFailure = observationFailure;
                    } else {
                        cleanupFailure.addSuppressed(observationFailure);
                    }
                }
                for (ProcessHandle handle : observedHandles) {
                    try {
                        if (handle.pid() != process.pid() && handle.isAlive()) {
                            handle.destroyForcibly();
                        }
                    } catch (RuntimeException destroyFailure) {
                        if (cleanupFailure == null) {
                            cleanupFailure = destroyFailure;
                        } else {
                            cleanupFailure.addSuppressed(destroyFailure);
                        }
                    }
                }
                try {
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                } catch (RuntimeException destroyFailure) {
                    if (cleanupFailure == null) {
                        cleanupFailure = destroyFailure;
                    } else {
                        cleanupFailure.addSuppressed(destroyFailure);
                    }
                }
                if (observedHandles.stream().noneMatch(ProcessHandle::isAlive)) {
                    if (cleanupFailure != null) {
                        throw new MavenProcessSurvivedException(
                                "Could not prove Maven process-tree termination during " + phase,
                                cleanupFailure);
                    }
                    return;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
            String survivors = observedHandles.stream()
                    .filter(ProcessHandle::isAlive)
                    .map(handle -> Long.toString(handle.pid()))
                    .sorted()
                    .collect(Collectors.joining(","));
            var survived = new MavenProcessSurvivedException(
                    "Could not terminate Maven process tree during " + phase + "; surviving PIDs: " + survivors);
            if (cleanupFailure != null) {
                survived.addSuppressed(cleanupFailure);
            }
            throw survived;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String sanitize(String value,
                                   Path repositoryRoot,
                                   Path snapshotRoot,
                                   Path localRepository,
                                   BenchmarkSourceIdentity.RepositoryIdentity repositoryIdentity,
                                   Path maven) {
        String result = sanitizeMavenPaths(value, maven);
        result = result.replace(repositoryIdentity.stagingRoot().toString(), "<staging-repository>");
        result = result.replace(snapshotRoot.toString(), "<source-snapshot>");
        result = result.replace(repositoryRoot.toString(), "<live-repository>");
        result = result.replace(System.getProperty("java.home"), "<java-home>");
        result = result.replace(localRepository.toString(), "<maven-local-repository>");
        result = result.replace(repositoryIdentity.stagingLocalPrefix(), "<staging-local-prefix>");
        return result.endsWith("\n") ? result : result + "\n";
    }

    static String sanitizeMavenPaths(String value, Path maven) {
        String result = value.replace("\r\n", "\n").replace('\r', '\n');
        result = result.replaceAll("(?m)^Maven home: .+$", "Maven home: <maven-home>");
        if (maven.isAbsolute()) {
            Path mavenParent = maven.normalize().getParent();
            if (mavenParent != null) {
                result = result.replace(mavenParent.toString(), "<maven-bin>");
            }
        }
        return result;
    }

    record MavenRun(String output) {
    }

    private static final class MavenProcessSurvivedException extends IllegalStateException {
        private MavenProcessSurvivedException(String message) {
            super(message);
        }

        private MavenProcessSurvivedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
