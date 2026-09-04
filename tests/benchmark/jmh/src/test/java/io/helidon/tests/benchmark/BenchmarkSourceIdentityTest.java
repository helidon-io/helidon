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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkSourceIdentityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void bundleReservationIsExclusiveAndPublishesEveryClaim() throws Exception {
        Path result = temporaryDirectory.resolve("result.json");
        Path metadata = temporaryDirectory.resolve("result.properties");
        Path resultReservation = temporaryDirectory.resolve("result.json.reservation");
        Path metadataReservation = temporaryDirectory.resolve("result.properties.reservation");
        Path stagedResult = Files.writeString(temporaryDirectory.resolve("staged-result"), "result\n");
        Path stagedMetadata = Files.writeString(temporaryDirectory.resolve("staged-metadata"), "metadata\n");

        try (var bundle = BenchmarkSourceIdentity.reserveEvidenceBundle(result, metadata)) {
            assertFalse(Files.exists(result));
            assertFalse(Files.exists(metadata));
            assertTrue(Files.exists(resultReservation));
            assertTrue(Files.exists(metadataReservation));
            assertThrows(Exception.class,
                         () -> BenchmarkSourceIdentity.reserveEvidenceBundle(result, metadata));

            bundle.publish(stagedMetadata, metadata);
            assertThrows(IllegalArgumentException.class,
                         () -> bundle.publish(stagedResult, result));
            bundle.commit(stagedResult);
        }

        assertEquals("result\n", Files.readString(result));
        assertEquals("metadata\n", Files.readString(metadata));
        assertFalse(Files.exists(resultReservation));
        assertFalse(Files.exists(metadataReservation));
    }

    @Test
    void incompleteBundleRemovesOnlyItsOwnedArtifacts() throws Exception {
        Path result = temporaryDirectory.resolve("partial.json");
        Path metadata = temporaryDirectory.resolve("partial.properties");
        Path resultReservation = temporaryDirectory.resolve("partial.json.reservation");
        Path metadataReservation = temporaryDirectory.resolve("partial.properties.reservation");
        Path stagedMetadata = Files.writeString(temporaryDirectory.resolve("partial-staged"), "metadata\n");

        try (var bundle = BenchmarkSourceIdentity.reserveEvidenceBundle(result, metadata)) {
            bundle.publish(stagedMetadata, metadata);
        }

        assertFalse(Files.exists(result));
        assertFalse(Files.exists(metadata));
        assertFalse(Files.exists(resultReservation));
        assertFalse(Files.exists(metadataReservation));
    }

    @Test
    void runtimeClasspathCanonicalizesOnlyBenchmarkListOrder() throws Exception {
        Path classpath = temporaryDirectory.resolve("runtime-classpath");
        Path benchmarkList = classpath.resolve("META-INF/BenchmarkList");
        Files.createDirectories(benchmarkList.getParent());
        Files.writeString(benchmarkList, "benchmark-b\nbenchmark-a\n");
        String originalClasspath = System.getProperty("java.class.path");
        try {
            System.setProperty("java.class.path", classpath.toString());
            String first = BenchmarkSourceIdentity.runtimeClasspathSha256(temporaryDirectory);
            Files.writeString(benchmarkList, "benchmark-a\nbenchmark-b\n");
            assertEquals(first, BenchmarkSourceIdentity.runtimeClasspathSha256(temporaryDirectory));
            Files.writeString(benchmarkList, "benchmark-a\nbenchmark-c\n");
            assertNotEquals(first, BenchmarkSourceIdentity.runtimeClasspathSha256(temporaryDirectory));
        } finally {
            System.setProperty("java.class.path", originalClasspath);
        }
    }

    @Test
    void evidenceDirectoryMustBeExplicitAndAbsolute() throws Exception {
        String property = Http3QuicEvidenceScope.EVIDENCE_ROOT_PROPERTY;
        String originalDirectory = System.getProperty(property);
        Path repository = Files.createDirectory(temporaryDirectory.resolve("repository"));
        try {
            System.clearProperty(property);
            assertThrows(IllegalArgumentException.class,
                         () -> Http3QuicEvidenceScope.evidenceRoot(repository));
            System.setProperty(property, "relative-evidence");
            assertThrows(IllegalArgumentException.class,
                         () -> Http3QuicEvidenceScope.evidenceRoot(repository));

            Path evidence = temporaryDirectory.resolve("evidence");
            System.setProperty(property, evidence.toString());
            Path resolvedEvidence = Http3QuicEvidenceScope.evidenceRoot(repository);
            assertEquals(evidence.toRealPath(), resolvedEvidence);
            var paths = Http3QuicEvidenceScope.evidencePaths(repository, "campaign-1");
            assertEquals(List.of(
                                 "campaign-1.json",
                                 "campaign-1.log",
                                 "campaign-1.properties",
                                 "campaign-1.source.manifest",
                                 "campaign-1.build.manifest",
                                 "campaign-1.build.log"),
                         List.of(
                                 paths.result().getFileName().toString(),
                                 paths.output().getFileName().toString(),
                                 paths.metadata().getFileName().toString(),
                                 paths.sourceManifest().getFileName().toString(),
                                 paths.buildManifest().getFileName().toString(),
                                 paths.buildLog().getFileName().toString()));
            assertThrows(IllegalArgumentException.class,
                         () -> Http3QuicEvidenceScope.evidencePaths(repository, "../escape"));

            Path repositoryEvidence = repository.resolve("evidence");
            System.setProperty(property, repositoryEvidence.toString());
            assertThrows(IllegalArgumentException.class,
                         () -> Http3QuicEvidenceScope.evidenceRoot(repository));

            Path regularFile = Files.writeString(temporaryDirectory.resolve("not-a-directory"), "content");
            System.setProperty(property, regularFile.toString());
            assertThrows(IllegalArgumentException.class,
                         () -> Http3QuicEvidenceScope.evidenceRoot(repository));
        } finally {
            if (originalDirectory == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, originalDirectory);
            }
        }
    }

    @Test
    void buildSnapshotRejectsSymbolicLinks() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Path repository = temporaryDirectory.resolve("repository");
        Files.createDirectories(repository);
        runGit(repository, "init");
        Path target = Files.writeString(repository.resolve("target.txt"), "content");
        try {
            Files.createSymbolicLink(repository.resolve("link.txt"), target.getFileName());
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("Symbolic links are not supported: " + e);
        }
        runGit(repository, "add", "target.txt", "link.txt");

        assertThrows(IllegalStateException.class,
                     () -> BenchmarkSourceIdentity.createBuildSnapshot(repository));
    }

    @Test
    void evidenceRootRejectsSymbolicLink() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        String property = Http3QuicEvidenceScope.EVIDENCE_ROOT_PROPERTY;
        String originalDirectory = System.getProperty(property);
        Path repository = Files.createDirectory(temporaryDirectory.resolve("symlink-repository"));
        Path target = Files.createDirectory(temporaryDirectory.resolve("symlink-target"));
        Path link = temporaryDirectory.resolve("evidence-link");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("Symbolic links are not supported: " + e);
        }
        try {
            System.setProperty(property, link.toString());
            assertThrows(IllegalArgumentException.class,
                         () -> Http3QuicEvidenceScope.evidenceRoot(repository));
        } finally {
            if (originalDirectory == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, originalDirectory);
            }
        }
    }

    @Test
    void bundleReservationAndPublicationRejectNonRegularEntries() throws Exception {
        Path existingDirectory = Files.createDirectory(temporaryDirectory.resolve("existing-result"));
        assertThrows(Exception.class,
                     () -> BenchmarkSourceIdentity.reserveEvidenceBundle(existingDirectory));

        Path result = temporaryDirectory.resolve("regular-result");
        Path stagedDirectory = Files.createDirectory(temporaryDirectory.resolve("staged-directory"));
        try (var bundle = BenchmarkSourceIdentity.reserveEvidenceBundle(result)) {
            assertThrows(IllegalArgumentException.class,
                         () -> bundle.commit(stagedDirectory));
        }
    }

    @Test
    void bundleReservationRejectsDanglingSymbolicLink() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Path result = temporaryDirectory.resolve("dangling-result");
        try {
            Files.createSymbolicLink(result, Path.of("missing-target"));
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("Symbolic links are not supported: " + e);
        }
        assertThrows(Exception.class,
                     () -> BenchmarkSourceIdentity.reserveEvidenceBundle(result));
    }

    @Test
    void controlledRepositoryPublishesOnlyItsExactContentDigest() throws Exception {
        Path localRepository = Files.createDirectory(temporaryDirectory.resolve("local-repository"));
        String localPrefix;
        try (var repository = BenchmarkSourceIdentity.createControlledRepository(localRepository, "cached")) {
            Path stagingRoot = localRepository.resolve(repository.stagingLocalPrefix());
            Path artifact = stagingRoot.resolve("io/helidon/example/1/example-1.jar");
            Files.createDirectories(artifact.getParent());
            Files.writeString(artifact, "controlled");
            var identity = repository.identity();
            localPrefix = identity.localPrefix();
            repository.publish(identity);
        }
        assertEquals("controlled",
                     Files.readString(localRepository.resolve(localPrefix)
                                              .resolve("io/helidon/example/1/example-1.jar")));
    }

    @Test
    void controlledArtifactRequiresExactScmRevision() throws Exception {
        Path jar = temporaryDirectory.resolve("controlled.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Scm-Revision", "expected-head");
        try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            // The manifest is the complete fixture.
        }
        BenchmarkSourceIdentity.verifyScmRevision(jar, "expected-head");
        assertThrows(IllegalStateException.class,
                     () -> BenchmarkSourceIdentity.verifyScmRevision(jar, "different-head"));
    }

    @Test
    void controlledMavenTimeoutIsConfigurableAndBounded() {
        String property = "helidon.benchmark.evidence.mavenTimeoutSeconds";
        String original = System.getProperty(property);
        try {
            System.setProperty(property, "60");
            assertEquals(60, Http3QuicEvidenceManifestTest.parseTimeoutSeconds());
            System.setProperty(property, "59");
            assertThrows(IllegalArgumentException.class,
                         Http3QuicEvidenceManifestTest::parseTimeoutSeconds);
            System.setProperty(property, "21601");
            assertThrows(IllegalArgumentException.class,
                         Http3QuicEvidenceManifestTest::parseTimeoutSeconds);
        } finally {
            if (original == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, original);
            }
        }
    }

    @Test
    void controlledMavenProcessorCountIsConfigurableAndBounded() {
        String property = "helidon.benchmark.evidence.activeProcessorCount";
        String original = System.getProperty(property);
        try {
            System.clearProperty(property);
            assertEquals(2, Http3QuicEvidenceManifestTest.parseActiveProcessorCount());
            System.setProperty(property, "1");
            assertEquals(1, Http3QuicEvidenceManifestTest.parseActiveProcessorCount());
            System.setProperty(property, "0");
            assertThrows(IllegalArgumentException.class,
                         Http3QuicEvidenceManifestTest::parseActiveProcessorCount);
            System.setProperty(property, "65");
            assertThrows(IllegalArgumentException.class,
                         Http3QuicEvidenceManifestTest::parseActiveProcessorCount);
            System.setProperty(property, "two");
            assertThrows(IllegalArgumentException.class,
                         Http3QuicEvidenceManifestTest::parseActiveProcessorCount);
        } finally {
            if (original == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, original);
            }
        }
    }

    @Test
    void controlledMavenProcessorCountRejectsInheritedOptions() {
        ProcessBuilder processBuilder = new ProcessBuilder("java");
        processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
        processBuilder.environment().remove("JDK_JAVA_OPTIONS");
        processBuilder.environment().remove("_JAVA_OPTIONS");
        processBuilder.environment().remove("MAVEN_OPTS");
        processBuilder.environment().remove("MAVEN_ARGS");
        Http3QuicEvidenceManifestTest.configureProcessEnvironment(processBuilder, 3);
        assertEquals("-XX:ActiveProcessorCount=3",
                     processBuilder.environment().get("JAVA_TOOL_OPTIONS"));

        processBuilder.environment().put("MAVEN_OPTS", "-Xmx1g");
        assertThrows(IllegalStateException.class,
                     () -> Http3QuicEvidenceManifestTest.configureProcessEnvironment(processBuilder, 3));
    }

    @Test
    void mavenPathsAreSanitizedWithoutResolvingRelativeLauncher() {
        Path mavenHome = temporaryDirectory.resolve("apache-maven");
        Path mavenBin = mavenHome.resolve("bin");
        Path absoluteMaven = mavenBin.resolve("mvn");
        String absoluteInput = "Maven home: " + mavenHome + "\r\ncommand=" + absoluteMaven + "\r\n";
        String absoluteExpected = "Maven home: <maven-home>\ncommand=<maven-bin>"
                + absoluteMaven.getFileSystem().getSeparator()
                + absoluteMaven.getFileName()
                + "\n";
        assertEquals(absoluteExpected,
                     Http3QuicEvidenceManifestTest.sanitizeMavenPaths(absoluteInput, absoluteMaven));

        String currentDirectory = Path.of("").toAbsolutePath().normalize().toString();
        String relativeInput = "Maven home: " + currentDirectory + "\r\nworking=" + currentDirectory + "\r\n";
        String relativeExpected = "Maven home: <maven-home>\nworking=" + currentDirectory + "\n";
        assertEquals(relativeExpected,
                     Http3QuicEvidenceManifestTest.sanitizeMavenPaths(relativeInput, Path.of("mvn")));
        assertEquals(relativeExpected,
                     Http3QuicEvidenceManifestTest.sanitizeMavenPaths(relativeInput, Path.of("mvn.cmd")));
    }

    @Test
    void evidenceTextArtifactsDoNotExposeMachineLocalPaths() throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Path campaign = temporaryDirectory.resolve("campaign");
        Path localRepository = temporaryDirectory.resolve("maven-repository");
        Files.createDirectories(campaign);
        Path stagedResult = campaign.resolve(".result-random.tmp");
        Path stagedOutput = campaign.resolve(".output-random.tmp");
        Path stagedMetadata = campaign.resolve(".metadata-random.tmp");
        String localRepositoryProperty = Http3QuicEvidenceScope.MAVEN_LOCAL_REPOSITORY_PROPERTY;
        String originalLocalRepository = System.getProperty(localRepositoryProperty);
        try {
            System.setProperty(localRepositoryProperty, localRepository.toString());
            Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
            Path javaInvoker = javaHome.resolve("bin").resolve(
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
            Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
            String escapedRepository = localRepository.toString().replace("\\", "\\\\");
            Files.writeString(
                    stagedResult,
                    "[{\"jvm\":\"" + javaInvoker.toString().replace("\\", "\\\\")
                            + "\",\"localRepository\":\"" + escapedRepository
                            + "\",\"score\":12.5}]\r\n");
            Files.writeString(
                    stagedOutput,
                    "repository=" + repository + "\r\n"
                            + "campaign=" + campaign + "\r\n"
                            + "localRepository=" + localRepository + "\r\n"
                            + "javaHome=" + javaHome + "\r\n"
                            + "workingDirectory=" + workingDirectory + "\r\n"
                            + "temporaryRoot=" + temporaryRoot + "\r\n"
                            + "userHome=" + userHome + "\r\n"
                            + "Benchmark result is saved to " + stagedResult + "\r\n"
                            + "uri=" + localRepository.resolve("artifact.jar").toUri() + "\r\n");
            Files.writeString(stagedMetadata, "workingDirectory=" + workingDirectory + "\r\n");

            BenchmarkSourceIdentity.sanitizeEvidenceArtifacts(
                    repository,
                    campaign,
                    stagedResult,
                    stagedResult,
                    stagedOutput,
                    stagedMetadata);

            String result = Files.readString(stagedResult);
            assertTrue(result.contains("\"jvm\":\"<java-invoker>\""));
            assertTrue(result.contains("\"localRepository\":\"<maven-local-repository>\""));
            assertTrue(result.contains("\"score\":12.5"));
            assertFalse(result.contains("\r"));

            String output = Files.readString(stagedOutput);
            assertTrue(output.contains("repository=<live-repository>\n"));
            assertTrue(output.contains("campaign=<campaign-root>\n"));
            assertTrue(output.contains("localRepository=<maven-local-repository>\n"));
            assertTrue(output.contains("javaHome=<java-home>\n"));
            assertTrue(output.contains("workingDirectory=<working-directory>\n"));
            assertTrue(output.contains("temporaryRoot=<temporary-directory>\n"));
            assertTrue(output.contains("userHome=<user-home>\n"));
            assertTrue(output.contains("Benchmark result is saved to <staged-result>\n"));
            assertTrue(output.contains("uri=<maven-local-repository>/artifact.jar\n"));
            assertFalse(output.contains("\r"));

            assertEquals("workingDirectory=<working-directory>\n", Files.readString(stagedMetadata));
        } finally {
            if (originalLocalRepository == null) {
                System.clearProperty(localRepositoryProperty);
            } else {
                System.setProperty(localRepositoryProperty, originalLocalRepository);
            }
        }
    }

    @Test
    void pathRepresentationsCoverWindowsFileUrisAndPreserveSuffixes() {
        String drivePath = "C:\\Users\\Alice Doe\\.m2\\repository";
        URI driveUri = URI.create("file:///C:/Users/Alice%20Doe/.m2/repository/");
        String driveInput = "json=\"" + drivePath.replace("\\", "\\\\") + "\\\\artifact.jar\"\n"
                + "raw=" + drivePath + "\\artifact.jar\n"
                + "short=file:/C:/Users/Alice%20Doe/.m2/repository/artifact.jar\n"
                + "long=file:///C:/Users/Alice%20Doe/.m2/repository/artifact.jar\n";
        String driveResult = BenchmarkSourceIdentity.sanitizePathRepresentations(
                driveInput,
                drivePath,
                driveUri,
                "<path>");
        assertTrue(driveResult.contains("json=\"<path>\\\\artifact.jar\"\n"));
        assertTrue(driveResult.contains("raw=<path>\\artifact.jar\n"));
        assertTrue(driveResult.contains("short=file:<path>/artifact.jar\n"));
        assertTrue(driveResult.contains("long=<path>/artifact.jar\n"));
        assertFalse(driveResult.contains("Alice"));

        String uncPath = "\\\\build-server\\share\\Alice Doe\\repository";
        URI uncUri = URI.create("file://build-server/share/Alice%20Doe/repository/");
        String uncInput = "canonical=file://build-server/share/Alice%20Doe/repository/artifact.jar\n"
                + "four=file:////build-server/share/Alice%20Doe/repository/artifact.jar\n"
                + "raw=" + uncPath + "\\artifact.jar\n";
        String uncResult = BenchmarkSourceIdentity.sanitizePathRepresentations(
                uncInput,
                uncPath,
                uncUri,
                "<path>");
        assertTrue(uncResult.contains("canonical=<path>/artifact.jar\n"));
        assertTrue(uncResult.contains("four=<path>/artifact.jar\n"));
        assertTrue(uncResult.contains("raw=<path>\\artifact.jar\n"));
        assertFalse(uncResult.contains("build-server"));
        assertFalse(uncResult.contains("share"));
        assertFalse(uncResult.contains("Alice"));
    }

    @Test
    void relativeMavenRepositoryIsRejectedWithoutResolvingAgainstWorkingDirectory() throws Exception {
        Path artifact = Files.writeString(temporaryDirectory.resolve("artifact"), "content\n");
        String localRepositoryProperty = Http3QuicEvidenceScope.MAVEN_LOCAL_REPOSITORY_PROPERTY;
        String originalLocalRepository = System.getProperty(localRepositoryProperty);
        try {
            System.setProperty(localRepositoryProperty, "relative-repository");
            assertThrows(
                    IllegalStateException.class,
                    () -> BenchmarkSourceIdentity.sanitizeEvidenceArtifacts(
                            temporaryDirectory,
                            temporaryDirectory,
                            artifact,
                            artifact));
            assertEquals("content\n", Files.readString(artifact));
        } finally {
            if (originalLocalRepository == null) {
                System.clearProperty(localRepositoryProperty);
            } else {
                System.setProperty(localRepositoryProperty, originalLocalRepository);
            }
        }
    }

    @Test
    void controlledMavenTimeoutTerminatesCompleteProcessTree() throws Exception {
        assumeControlledProcessEnvironment();
        assumeProcessTreeInspection();
        Path pidDirectory = Files.createDirectory(temporaryDirectory.resolve("timeout-processes"));
        assertThrows(
                IllegalStateException.class,
                () -> Http3QuicEvidenceManifestTest.runMaven(
                        temporaryDirectory,
                        Http3QuicEvidenceProcessFixture.command(pidDirectory, "root"),
                        "controlled-build timeout fixture",
                        2,
                        1));
        assertProcessTreeStopped(pidDirectory);
    }

    @Test
    void controlledMavenInterruptionCannotAbortProcessTreeCleanup() throws Exception {
        assumeControlledProcessEnvironment();
        assumeProcessTreeInspection();
        Path pidDirectory = Files.createDirectory(temporaryDirectory.resolve("interrupted-processes"));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = Thread.ofPlatform()
                .name("http3-quic-evidence-interruption-test")
                .start(() -> {
                    try {
                        Http3QuicEvidenceManifestTest.runMaven(
                                temporaryDirectory,
                                Http3QuicEvidenceProcessFixture.command(pidDirectory, "root"),
                                "controlled-build interruption fixture",
                                60,
                                1);
                    } catch (Throwable caught) {
                        failure.set(caught);
                        interrupted.set(Thread.currentThread().isInterrupted());
                    }
                });
        awaitProcessTree(pidDirectory);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (worker.isAlive() && System.nanoTime() < deadline) {
            worker.interrupt();
            Thread.sleep(10);
        }
        worker.join(TimeUnit.SECONDS.toMillis(1));
        assertFalse(worker.isAlive(), "Controlled-build worker did not stop after interruption");
        assertTrue(failure.get() instanceof InterruptedException,
                   () -> "Unexpected interruption failure: " + failure.get());
        assertTrue(interrupted.get(), "Controlled-build cleanup did not restore interrupt status");
        assertProcessTreeStopped(pidDirectory);
    }

    @Test
    void controlledMavenShutdownHookTerminatesCompleteProcessTree() throws Exception {
        assumeProcessTreeInspection();
        Path pidDirectory = Files.createDirectory(temporaryDirectory.resolve("shutdown-processes"));
        Path processLog = pidDirectory.resolve("orchestrator.log");
        ProcessBuilder processBuilder =
                new ProcessBuilder(Http3QuicEvidenceProcessFixture.command(pidDirectory, "orchestrator"))
                        .directory(temporaryDirectory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(processLog.toFile());
        processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
        processBuilder.environment().remove("JDK_JAVA_OPTIONS");
        processBuilder.environment().remove("_JAVA_OPTIONS");
        processBuilder.environment().remove("MAVEN_OPTS");
        processBuilder.environment().remove("MAVEN_ARGS");
        Process orchestrator = processBuilder.start();
        try {
            awaitProcessTree(pidDirectory);
            Files.writeString(pidDirectory.resolve("exit.request"), "exit\n");
            assertTrue(orchestrator.waitFor(15, TimeUnit.SECONDS),
                       "Controlled-build shutdown fixture did not exit; inspect " + processLog);
            assertEquals(130, orchestrator.exitValue());
            assertProcessTreeStopped(pidDirectory);
        } finally {
            if (orchestrator.isAlive()) {
                orchestrator.destroyForcibly();
            }
        }
    }

    private static void assumeProcessTreeInspection() {
        try (var descendants = ProcessHandle.current().descendants()) {
            descendants.toList();
        } catch (RuntimeException e) {
            Assumptions.abort("Process-tree inspection is unavailable: " + e.getMessage());
        }
    }

    private static void assumeControlledProcessEnvironment() {
        for (String option : List.of(
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "_JAVA_OPTIONS",
                "MAVEN_OPTS")) {
            Assumptions.assumeTrue(
                    System.getenv(option) == null || System.getenv(option).isBlank(),
                    "Inherited " + option + " prevents deterministic controlled-process validation");
        }
    }

    private static void awaitProcessTree(Path pidDirectory) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(pidDirectory.resolve("root.pid"))
                    && Files.isRegularFile(pidDirectory.resolve("child.pid"))
                    && Files.isRegularFile(pidDirectory.resolve("grandchild.pid"))) {
                return;
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("Process-tree fixture did not start completely");
    }

    private static void assertProcessTreeStopped(Path pidDirectory) throws Exception {
        for (String role : List.of("root", "child", "grandchild")) {
            Path pidFile = pidDirectory.resolve(role + ".pid");
            assertTrue(Files.isRegularFile(pidFile), "Missing process-tree PID: " + role);
            long pid = Long.parseLong(Files.readString(pidFile));
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                        "Process-tree fixture survived cleanup: " + role + " PID " + pid);
        }
    }

    private static void runGit(Path repository, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repository.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Git failed: " + new String(output, StandardCharsets.UTF_8));
        }
    }
}
