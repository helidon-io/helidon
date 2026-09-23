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

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class Http3QuicCampaignIdentityTest {
    private static final String SOURCE_MANIFEST = "http3-quic-controlled-source.manifest";
    private static final String BUILD_MANIFEST = "http3-quic-controlled-build.manifest";
    private static final String BUILD_LOG = "http3-quic-controlled-build.log";
    private static final List<String> IDENTITY_FILES = List.of(SOURCE_MANIFEST, BUILD_MANIFEST, BUILD_LOG);

    private final Map<String, String> originalProperties = new LinkedHashMap<>();

    @TempDir
    Path temporaryDirectory;

    private Path campaignRoot;

    @BeforeEach
    void configureGenerator() throws Exception {
        Path repository = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Files.createFile(repository.resolve("pom.xml"));
        // A deliberately invalid Git directory prevents snapshot/build work if reservation regresses.
        Files.writeString(repository.resolve(".git"), "invalid test repository\n");
        Files.createDirectories(repository.resolve("tests/benchmark/jmh"));
        campaignRoot = Files.createDirectory(temporaryDirectory.resolve("controlled"));
        Path localRepository = Files.createDirectory(temporaryDirectory.resolve("maven-repository"));
        configure(Http3QuicEvidenceScope.LIVE_REPOSITORY_ROOT_PROPERTY, repository.toString());
        configure(Http3QuicEvidenceScope.EVIDENCE_ROOT_PROPERTY, campaignRoot.toString());
        configure(Http3QuicEvidenceScope.MAVEN_LOCAL_REPOSITORY_PROPERTY, localRepository.toString());
        configure("helidon.benchmark.evidence.generatorChild", "false");
        configure("aether.enhancedLocalRepository.split", "true");
        configure("aether.enhancedLocalRepository.remotePrefix", "remote");
    }

    @AfterEach
    void restoreProperties() {
        originalProperties.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {SOURCE_MANIFEST, BUILD_MANIFEST, BUILD_LOG, "all"})
    void generatorPreservesExistingCampaignIdentity(String existing) throws Exception {
        List<String> existingFiles = existing.equals("all") ? IDENTITY_FILES : List.of(existing);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("earlier-result.json", "earlier benchmark result\n");
        for (String file : existingFiles) {
            expected.put(file, "original " + file + "\n");
        }
        for (var entry : expected.entrySet()) {
            Files.writeString(campaignRoot.resolve(entry.getKey()), entry.getValue());
        }

        assertThrows(FileAlreadyExistsException.class, new Http3QuicEvidenceManifestTest()::generateAndVerify);

        assertCampaignContents(expected);
    }

    @Test
    void generatorRejectsConcurrentCampaignReservation() throws Exception {
        Map<String, String> claims = new LinkedHashMap<>();
        try (var _ = BenchmarkSourceIdentity.reserveEvidenceBundle(
                campaignRoot.resolve(SOURCE_MANIFEST),
                campaignRoot.resolve(BUILD_MANIFEST),
                campaignRoot.resolve(BUILD_LOG));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String file : IDENTITY_FILES) {
                String reservation = file + ".reservation";
                claims.put(reservation, Files.readString(campaignRoot.resolve(reservation)));
            }
            var generation = executor.submit(() -> {
                new Http3QuicEvidenceManifestTest().generateAndVerify();
                return null;
            });
            ExecutionException failure = assertThrows(ExecutionException.class,
                                                     () -> generation.get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(FileAlreadyExistsException.class));
            assertCampaignContents(claims);
        }
        assertCampaignContents(Map.of());
    }

    @Test
    void campaignIdentityCanBeCopiedFromAnotherFileSystem() throws Exception {
        Map<String, String> expected = Map.of(SOURCE_MANIFEST, "verified source identity\n",
                                              BUILD_MANIFEST, "verified build identity\n",
                                              BUILD_LOG, "verified build log\n");
        try (var stagingFileSystem = FileSystems.newFileSystem(temporaryDirectory.resolve("staged.zip"),
                                                               Map.of("create", "true"));
             var identity = BenchmarkSourceIdentity.reserveEvidenceBundle(
                     campaignRoot.resolve(SOURCE_MANIFEST),
                     campaignRoot.resolve(BUILD_MANIFEST),
                     campaignRoot.resolve(BUILD_LOG))) {
            Path stagedRoot = stagingFileSystem.getPath("/");
            for (var entry : expected.entrySet()) {
                Files.writeString(stagedRoot.resolve(entry.getKey()), entry.getValue());
            }

            Http3QuicEvidenceManifestTest.publishCampaignIdentity(identity, stagedRoot, campaignRoot);

            assertCampaignContents(expected);
            for (var entry : expected.entrySet()) {
                assertThat(entry.getKey(), Files.readString(stagedRoot.resolve(entry.getKey())), is(entry.getValue()));
            }
        }
        assertCampaignContents(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {BUILD_LOG, BUILD_MANIFEST, SOURCE_MANIFEST})
    void incompletePublicationRemovesOnlyOwnedCampaignArtifacts(String missing) throws Exception {
        Path stagedRoot = Files.createDirectory(temporaryDirectory.resolve("staged"));
        for (String file : IDENTITY_FILES) {
            if (!file.equals(missing)) {
                Files.writeString(stagedRoot.resolve(file), "staged " + file + "\n");
            }
        }
        Map<String, String> existing = Map.of("earlier-result.json", "earlier benchmark result\n");
        Files.writeString(campaignRoot.resolve("earlier-result.json"), existing.get("earlier-result.json"));
        try (var identity = BenchmarkSourceIdentity.reserveEvidenceBundle(
                campaignRoot.resolve(SOURCE_MANIFEST),
                campaignRoot.resolve(BUILD_MANIFEST),
                campaignRoot.resolve(BUILD_LOG))) {
            assertThrows(IllegalArgumentException.class,
                         () -> Http3QuicEvidenceManifestTest.publishCampaignIdentity(identity, stagedRoot, campaignRoot));
            assertThat("Source identity must be committed last",
                       Files.exists(campaignRoot.resolve(SOURCE_MANIFEST)),
                       is(false));
        }
        assertCampaignContents(existing);
    }

    private void configure(String key, String value) {
        originalProperties.put(key, System.getProperty(key));
        System.setProperty(key, value);
    }

    private void assertCampaignContents(Map<String, String> expected) throws Exception {
        try (var paths = Files.list(campaignRoot)) {
            assertThat(paths.map(path -> path.getFileName().toString()).toList(),
                       containsInAnyOrder(expected.keySet().toArray(String[]::new)));
        }
        for (var entry : expected.entrySet()) {
            assertThat(entry.getKey(), Files.readString(campaignRoot.resolve(entry.getKey())), is(entry.getValue()));
        }
    }
}
