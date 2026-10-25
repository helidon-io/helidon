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

package io.helidon.tests.integration.h2spec.client;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.logging.common.LogConfig;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers(disabledWithoutDocker = true)
class H2SpecClientIT {
    private static final Path OUTPUT_PATH = Path.of("./target/h2spec-client-output.txt");
    private static final Path OUTPUT_DIR = Path.of("./target/h2spec-client-sections");
    private static final String CONTAINER_APP_DIR = "/opt/helidon/app";
    private static final String CONTAINER_APPLICATION_JAR = CONTAINER_APP_DIR + "/application.jar";
    private static final String CONTAINER_APPLICATION_LIBS = CONTAINER_APP_DIR + "/libs";
    private static final int H2SPEC_TIMEOUT_SECONDS = 30;
    private static final int EXPECTED_TOTAL_CASES = 57;
    private static final int MAX_PARALLEL_SECTION_RUNS = 2;
    private static final Path APPLICATION_JAR = Path.of("./target/helidon-tests-integration-h2spec-client.jar");
    private static final Path APPLICATION_LIBS = Path.of("./target/libs");
    private static final String IMAGE_NAME = "helidon-h2spec-client";
    private static final Duration IMAGE_TIMEOUT = timeout("image", 600);
    private static final Duration SECTIONS_TIMEOUT = timeout("sections", 300);
    private static final Duration CLEANUP_TIMEOUT = timeout("cleanup", 15);
    private static final List<H2SpecSection> SECTIONS = List.of(
            new H2SpecSection("client/1", 34_000),
            new H2SpecSection("client/4", 34_100),
            new H2SpecSection("client/5", 34_200),
            new H2SpecSection("client/6.1", 34_300),
            new H2SpecSection("client/6.2", 34_400),
            new H2SpecSection("client/6.3", 34_500),
            new H2SpecSection("client/6.4", 34_600),
            new H2SpecSection("client/6.5", 34_700),
            new H2SpecSection("client/6.7", 34_800),
            new H2SpecSection("client/6.8", 34_900),
            new H2SpecSection("client/6.9", 35_000),
            new H2SpecSection("client/6.10", 35_100)
    );
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Pattern SUMMARY_PATTERN =
            Pattern.compile("(?m)^(\\d+) tests, (\\d+) passed, (\\d+) skipped, (\\d+) failed$");
    private static final Pattern GROUP_PATTERN =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)\\.\\s+(.*)$");
    private static final Pattern CASE_PATTERN =
            Pattern.compile("^\\s*(?:([\\u2714\\u00D7])\\s+)?(\\d+):\\s+(.*)$");

    private static final Set<KnownFailure> KNOWN_FAILURES = Set.of(
            new KnownFailure("client/6.9.1/1",
                             "Sends multiple WINDOW_UPDATE frames increasing the flow control window to above 2^31-1"),
            new KnownFailure("client/6.9.1/2",
                             "Sends multiple WINDOW_UPDATE frames increasing the flow control window to above 2^31-1 on a stream")
    );
    private static final Logger LOGGER = LoggerFactory.getLogger(H2SpecClientIT.class);

    static {
        LogConfig.configureRuntime();
    }

    private static Stream<Arguments> runH2Spec() {
        Assertions.assertTrue(APPLICATION_JAR.toFile().isFile(),
                              "Expected packaged client launcher jar: " + APPLICATION_JAR);
        Assertions.assertTrue(APPLICATION_LIBS.toFile().isDirectory(),
                              "Expected packaged runtime libs directory: " + APPLICATION_LIBS);

        try {
            prepareOutputFiles();
            List<H2SpecSectionResult> sectionResults;
            try (var execution = new H2SpecExecution(CLEANUP_TIMEOUT)) {
                H2SpecExecution.progress("Image preparation started; deadline " + IMAGE_TIMEOUT);
                long imageDeadline = System.nanoTime() + IMAGE_TIMEOUT.toNanos();
                String imageName = execution.await(execution.submit(() -> resolveImageName(execution)),
                                                  imageDeadline, "image preparation");
                H2SpecExecution.progress("Image preparation completed: " + imageName);
                sectionResults = runSections(execution, imageName);
            }
            persistOutput(sectionResults);

            List<H2SpecCaseResult> caseResults = sectionResults.stream()
                    .flatMap(sectionResult -> sectionResult.caseResults().stream())
                    .toList();

            Assertions.assertEquals(EXPECTED_TOTAL_CASES,
                                    caseResults.size(),
                                    "Parallel section execution did not cover the expected full client suite.");

            return caseResults.stream()
                    .map(caseResult -> Arguments.of(caseResult.caseName(),
                                                    caseResult.description(),
                                                    caseResult.id(),
                                                    caseResult.error(),
                                                    caseResult.skipped()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute h2specd client suite.", e);
        }
    }

    private static Duration timeout(String phase, long defaultSeconds) {
        long seconds = Long.getLong("h2spec.client." + phase + ".timeout.seconds", defaultSeconds);
        if (seconds <= 0) {
            throw new IllegalArgumentException("h2spec client " + phase + " timeout must be positive");
        }
        return Duration.ofSeconds(seconds);
    }

    private static String resolveImageName(H2SpecExecution execution) throws Exception {
        DockerClient docker = DockerClientFactory.instance().client();
        AtomicReference<String> imageId = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        try (var callback = new H2SpecExecution.Callback<BuildResponseItem>(item -> {
            String stream = item.getStream();
            if (stream != null && !stream.isBlank()) {
                H2SpecExecution.progress("image build: " + stream.stripTrailing());
            } else if (item.getStatus() != null) {
                H2SpecExecution.progress("image build: " + item.getStatus());
            }
            if (item.isErrorIndicated()) {
                error.set(item.getError());
                H2SpecExecution.progress("image build error: " + item.getError());
            }
            if (item.isBuildSuccessIndicated()) {
                imageId.set(item.getImageId());
            }
        });
             var build = docker.buildImageCmd(Path.of("Dockerfile").toAbsolutePath().toFile())
                     .withTags(Set.of(IMAGE_NAME))
                     .withLabels(DockerClientFactory.DEFAULT_LABELS)
                     .withRemove(true)
                     .withForcerm(true)) {
            execution.own(callback);
            execution.own(build);
            H2SpecExecution.progress("Docker image build started");
            build.exec(callback);
            callback.await();
            if (error.get() != null || imageId.get() == null) {
                throw new IllegalStateException("Docker image build failed: " + error.get());
            }
            return IMAGE_NAME;
        }
    }

    private static List<H2SpecSectionResult> runSections(H2SpecExecution execution, String imageName) {
        // Starting too many one-shot containers at once makes the shortest jobs flaky under Testcontainers.
        H2SpecExecution.progress("Section suite started; workers " + MAX_PARALLEL_SECTION_RUNS
                                         + "; deadline " + SECTIONS_TIMEOUT);
        long deadline = System.nanoTime() + SECTIONS_TIMEOUT.toNanos();
        List<SectionFuture> futures = new ArrayList<>(SECTIONS.size());
        for (H2SpecSection section : SECTIONS) {
            futures.add(new SectionFuture(section, execution.submit(() -> runSection(execution, imageName, section))));
        }

        List<H2SpecSectionResult> results = new ArrayList<>(SECTIONS.size());
        for (SectionFuture sectionFuture : futures) {
            results.add(execution.await(sectionFuture.future(), deadline,
                                        "section suite (waiting for " + sectionFuture.section().id() + ")"));
        }
        H2SpecExecution.progress("Section suite completed");
        return results;
    }

    private static H2SpecSectionResult runSection(H2SpecExecution execution, String imageName, H2SpecSection section)
            throws Exception {
        H2SpecExecution.progress(section.id() + " container startup started");

        try (var cont = new SectionContainer(imageName, execution, section)
                .withCopyFileToContainer(MountableFile.forHostPath(APPLICATION_JAR), CONTAINER_APPLICATION_JAR)
                .withCopyFileToContainer(MountableFile.forHostPath(APPLICATION_LIBS), CONTAINER_APPLICATION_LIBS)
                .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(365)))
                .withCommand("sh", "-c", "while true; do sleep 3600; done")
                .withStartupAttempts(1)) {

            cont.start();
            H2SpecExecution.progress(section.id() + " container startup completed");
            SectionExecResult exec = execSection(execution, cont, section);
            String output = exec.output();
            H2SpecExecution.progress(section.id() + " execution completed");
            Long exitCode = exec.exitCode();
            if (exitCode == null || exitCode != 0) {
                throw new IllegalStateException("h2specd failed for " + section.id()
                                                        + " with exit code " + exitCode
                                                        + ".\nOutput:\n" + output);
            }

            return new H2SpecSectionResult(section, output, parseOutput(output));
        }
    }

    private static SectionExecResult execSection(H2SpecExecution execution, GenericContainer<?> cont, H2SpecSection section) {
        Path stdout = OUTPUT_DIR.resolve(section.fileName());
        Path stderr = OUTPUT_DIR.resolve(section.logName() + "-stderr.txt");
        try (OutputStream out = Files.newOutputStream(stdout);
             OutputStream err = Files.newOutputStream(stderr)) {
            DockerClient docker = cont.getDockerClient();
            String execId = docker.execCreateCmd(cont.getContainerId()).withAttachStdout(true).withAttachStderr(true).withCmd(
                    "sh",
                    "-c",
                    "/usr/local/bin/h2specd " + section.id() + " "
                            + "--host 127.0.0.1 "
                            + "--from-port " + section.fromPort() + " "
                            // Containerized JVM startup is slower than the host-side ad hoc run.
                            + "--timeout " + H2SPEC_TIMEOUT_SECONDS + " "
                            + "-e 'java -cp " + CONTAINER_APPLICATION_JAR + ":" + CONTAINER_APPLICATION_LIBS + "/* "
                            + "io.helidon.tests.integration.h2spec.client.H2SpecClientMain'").exec().getId();
            H2SpecExecution.progress(section.id() + " exec started: " + execId);
            try (var callback = new H2SpecExecution.Callback<Frame>(frame -> {
                try {
                    OutputStream destination = frame.getStreamType() == StreamType.STDERR ? err : out;
                    destination.write(frame.getPayload());
                    destination.flush();
                    System.out.write(frame.getPayload());
                    System.out.flush();
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot persist output for " + section.id(), e);
                }
            })) {
                execution.own(callback);
                docker.execStartCmd(execId).exec(callback);
                callback.await();
            }
            return new SectionExecResult(combineExecOutput(Files.readString(stdout), Files.readString(stderr)),
                                         docker.inspectExecCmd(execId).exec().getExitCodeLong());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing h2specd inside container for " + section.id(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to execute h2specd inside container for " + section.id(), e);
        }
    }

    // h2specd client mode does not implement --junit-report, so the IT derives per-case
    // results from the plain-text output and keeps the raw log under target/ for debugging.
    private static List<H2SpecCaseResult> parseOutput(String output) {
        String normalized = normalizeOutput(output);
        var summaryMatcher = SUMMARY_PATTERN.matcher(normalized);
        Assertions.assertTrue(summaryMatcher.find(), "Missing h2specd client summary.\nOutput:\n" + normalized);

        int expectedTotal = Integer.parseInt(summaryMatcher.group(1));
        int expectedPassed = Integer.parseInt(summaryMatcher.group(2));
        int expectedSkipped = Integer.parseInt(summaryMatcher.group(3));
        int expectedFailed = Integer.parseInt(summaryMatcher.group(4));
        Assertions.assertEquals(0,
                                expectedSkipped,
                                "h2specd reported skipped client cases; update the allowlist if this is expected.");

        String executionOutput = normalized;
        int failuresStart = normalized.indexOf("\nFailures:");
        if (failuresStart >= 0) {
            executionOutput = normalized.substring(0, failuresStart);
        }

        List<H2SpecCaseResult> result = new ArrayList<>();
        String currentCaseName = null;
        String currentSection = null;
        String currentDescription = null;
        String currentId = null;
        String currentSkipped = null;
        StringBuilder currentError = null;

        for (String line : executionOutput.split("\n")) {
            if (line.isBlank()) {
                continue;
            }

            var groupMatcher = GROUP_PATTERN.matcher(line);
            if (groupMatcher.matches()) {
                if (currentId != null) {
                    addCase(result, currentCaseName, currentDescription, currentId, currentError, currentSkipped);
                    currentDescription = null;
                    currentId = null;
                    currentSkipped = null;
                    currentError = null;
                }
                currentSection = groupMatcher.group(1);
                currentCaseName = currentSection + ". " + groupMatcher.group(2);
                continue;
            }

            var caseMatcher = CASE_PATTERN.matcher(line);
            if (caseMatcher.matches() && currentSection != null) {
                if (currentId != null) {
                    addCase(result, currentCaseName, currentDescription, currentId, currentError, currentSkipped);
                }

                String status = caseMatcher.group(1);
                String sequence = caseMatcher.group(2);
                currentDescription = caseMatcher.group(3);
                currentId = "client/" + currentSection + "/" + sequence;
                currentSkipped = status == null ? "Skipped by h2specd" : null;
                currentError = "\u00D7".equals(status) ? new StringBuilder() : null;
                continue;
            }

            if (currentError != null) {
                String trimmed = line.stripLeading();
                if (!trimmed.isEmpty()) {
                    if (!currentError.isEmpty()) {
                        currentError.append('\n');
                    }
                    currentError.append(trimmed);
                }
            }
        }

        if (currentId != null) {
            addCase(result, currentCaseName, currentDescription, currentId, currentError, currentSkipped);
        }

        long actualFailed = result.stream()
                .filter(caseResult -> caseResult.error() != null)
                .count();
        long actualSkipped = result.stream()
                .filter(caseResult -> caseResult.skipped() != null)
                .count();
        long actualPassed = result.size() - actualFailed - actualSkipped;

        Assertions.assertEquals(expectedTotal,
                                result.size(),
                                "Parsed h2specd client case count does not match the reported total.");
        Assertions.assertEquals(expectedPassed,
                                actualPassed,
                                "Parsed h2specd client passed count does not match the reported summary.");
        Assertions.assertEquals(expectedSkipped,
                                actualSkipped,
                                "Parsed h2specd client skipped count does not match the reported summary.");
        Assertions.assertEquals(expectedFailed,
                                actualFailed,
                                "Parsed h2specd client failed count does not match the reported summary.");

        return result;
    }

    private static void addCase(List<H2SpecCaseResult> result,
                                String caseName,
                                String description,
                                String id,
                                StringBuilder error,
                                String skipped) {
        result.add(new H2SpecCaseResult(caseName,
                                        description,
                                        id,
                                        error == null ? null : error.toString(),
                                        skipped));
    }

    private static String normalizeOutput(String output) {
        return ANSI_PATTERN.matcher(output)
                .replaceAll("")
                .replace("\r", "\n");
    }

    private static String combineExecOutput(String stdout, String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return stdout;
        }
        if (stdout == null || stdout.isBlank()) {
            return stderr;
        }
        return stdout + System.lineSeparator() + stderr;
    }

    private static void prepareOutputFiles() {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Files.deleteIfExists(OUTPUT_PATH);

            // Remove stale per-section logs so an older shard layout does not leave misleading files behind.
            try (Stream<Path> paths = Files.list(OUTPUT_DIR)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare h2specd client output files.", e);
        }
    }

    private static void persistOutput(List<H2SpecSectionResult> sectionResults) {
        try {
            Files.createDirectories(OUTPUT_PATH.getParent());
            StringBuilder combined = new StringBuilder();
            for (H2SpecSectionResult sectionResult : sectionResults) {
                if (!combined.isEmpty()) {
                    combined.append('\n');
                }
                combined.append("=== ").append(sectionResult.section().id()).append(" ===").append('\n')
                        .append(sectionResult.output());
            }
            Files.writeString(OUTPUT_PATH, combined.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist h2specd client output to " + OUTPUT_PATH, e);
        }
    }

    private static boolean isKnownFailure(String id, String desc) {
        return KNOWN_FAILURES.contains(new KnownFailure(id, desc));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("runH2Spec")
    void h2spec(String caseName, String desc, String id, String err, String skipped) {
        LOGGER.info("{}: \n - {} \nID: {}", caseName, desc, id);

        if (skipped != null) {
            Assertions.fail("Unexpected h2specd skip for " + id + " (" + desc + "): " + skipped);
        }

        if (err != null && isKnownFailure(id, desc)) {
            Assumptions.abort("Known failure tracked by #11771:\n" + err);
        }

        if (err != null) {
            Assertions.fail(err);
        }
    }

    private record KnownFailure(String id, String description) {
    }

    private record H2SpecCaseResult(String caseName,
                                    String description,
                                    String id,
                                    String error,
                                    String skipped) {
    }

    record H2SpecSection(String id, int fromPort) {
        String fileName() {
            return id.replace('/', '-') + ".txt";
        }

        String logName() {
            return id.replace('/', '.');
        }
    }

    private record H2SpecSectionResult(H2SpecSection section,
                                       String output,
                                       List<H2SpecCaseResult> caseResults) {
    }

    private record SectionFuture(H2SpecSection section,
                                 Future<H2SpecSectionResult> future) {
    }

    private record SectionExecResult(String output, Long exitCode) {
    }

    static class SectionContainer extends GenericContainer<SectionContainer> {
        private final H2SpecExecution execution;
        private final H2SpecSection section;
        private boolean owned;

        SectionContainer(String imageName, H2SpecExecution execution, H2SpecSection section) {
            super(imageName);
            this.execution = execution;
            this.section = section;
        }

        @Override
        protected void containerIsCreated(String containerId) {
            ownCreatedContainer(containerId);
        }

        @Override
        public void copyFileToContainer(Transferable transferable, String containerPath) {
            // GenericContainer creates its ID before copying, but calls containerIsCreated only after copying.
            // Register first so a blocked startup archive request is included in timeout diagnostics and cleanup.
            ownCreatedContainer(getContainerId());
            super.copyFileToContainer(transferable, containerPath);
        }

        private void ownCreatedContainer(String containerId) {
            if (owned) {
                return;
            }
            try {
                DockerClient docker = getDockerClient();
                execution.own(() -> {
                    try {
                        docker.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
                    } catch (NotFoundException _) {
                        // Normal section completion already removed this container.
                    }
                });
                execution.diagnostic(section.fileName() + "-state.txt",
                                     () -> docker.inspectContainerCmd(containerId).exec().toString());
                execution.diagnostic(section.fileName() + "-logs.txt", this::getLogs);
                owned = true;
                H2SpecExecution.progress(section.id() + " container created: " + containerId);
            } catch (IOException e) {
                throw new IllegalStateException("Suite stopped while creating " + section.id(), e);
            }
        }
    }
}
