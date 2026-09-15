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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.quic.QuicClient;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http3.Http3Config;
import io.helidon.webserver.quic.QuicTransportBindingProvider;

/**
 * Defines the complete HTTP/3 and QUIC evidence source and loaded-artifact identity scope.
 */
public final class Http3QuicEvidenceScope {
    /**
     * Absolute directory used for manifests and completed non-smoke evidence bundles.
     */
    public static final String EVIDENCE_ROOT_PROPERTY = "helidon.benchmark.evidence.root";
    /**
     * Live worktree root used while the benchmark harness is clean-compiled in an isolated snapshot.
     */
    static final String LIVE_REPOSITORY_ROOT_PROPERTY = "helidon.benchmark.evidence.liveRepositoryRoot";
    /**
     * Effective Maven local repository passed by Surefire.
     */
    public static final String MAVEN_LOCAL_REPOSITORY_PROPERTY =
            "helidon.benchmark.evidence.mavenLocalRepository";
    private static final Pattern SAFE_STEM = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private Http3QuicEvidenceScope() {
    }

    /**
     * Locates the Helidon repository root from the Maven module working directory.
     *
     * @return repository root
     */
    public static Path repositoryRoot() {
        String configured = System.getProperty(LIVE_REPOSITORY_ROOT_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            try {
                Path root = Path.of(configured).toRealPath();
                if (Files.exists(root.resolve("pom.xml"))
                        && Files.exists(root.resolve(".git"))
                        && Files.isDirectory(root.resolve("tests/benchmark/jmh"))) {
                    return root;
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not resolve the configured live Helidon repository root", e);
            }
            throw new IllegalArgumentException("Configured live Helidon repository root is invalid: " + configured);
        }
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("pom.xml"))
                    && Files.exists(candidate.resolve(".git"))
                    && Files.isDirectory(candidate.resolve("tests/benchmark/jmh"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the Helidon repository root");
    }

    /**
     * Resolves the source manifest under the configured evidence directory.
     *
     * @return manifest path
     */
    public static Path manifest(Path repositoryRoot) {
        return evidenceRoot(repositoryRoot).resolve("http3-quic-controlled-source.manifest");
    }

    /**
     * Resolves the controlled-build input manifest under the configured evidence directory.
     *
     * @return build-manifest path
     */
    public static Path buildManifest(Path repositoryRoot) {
        return evidenceRoot(repositoryRoot).resolve("http3-quic-controlled-build.manifest");
    }

    /**
     * Resolves the controlled-build log under the configured evidence directory.
     *
     * @return build-log path
     */
    public static Path buildLog(Path repositoryRoot) {
        return evidenceRoot(repositoryRoot).resolve("http3-quic-controlled-build.log");
    }

    /**
     * Resolves the required absolute directory for persistent evidence bundles.
     *
     * @return evidence directory
     */
    public static Path evidenceRoot(Path repositoryRoot) {
        String configured = System.getProperty(EVIDENCE_ROOT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("HTTP/3 and QUIC evidence requires -D"
                                                       + EVIDENCE_ROOT_PROPERTY
                                                       + "=<absolute-directory>");
        }
        Path path = Path.of(configured);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("HTTP/3 and QUIC evidence directory must be absolute");
        }
        path = path.normalize();
        try {
            if (path.getParent() == null) {
                throw new IllegalArgumentException("HTTP/3 and QUIC evidence directory has no directory parent");
            }
            if (Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException("HTTP/3 and QUIC evidence root must not be a symbolic link");
            }
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("HTTP/3 and QUIC evidence root is not a directory");
            }
            Files.createDirectories(path);
            Path directory = path.toRealPath();
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("HTTP/3 and QUIC evidence directory is not a directory");
            }
            Path liveRepository = repositoryRoot.toRealPath();
            if (directory.startsWith(liveRepository)) {
                throw new IllegalArgumentException(
                        "HTTP/3 and QUIC evidence root must remain outside the Helidon checkout");
            }
            return directory;
        } catch (IOException e) {
            throw new IllegalStateException("Could not validate the HTTP/3 and QUIC evidence directory", e);
        }
    }

    /**
     * Creates the complete output-path set for one persistent evidence run.
     *
     * @param repositoryRoot repository root
     * @param stem safe file-name stem
     * @return complete evidence paths
     */
    public static EvidencePaths evidencePaths(Path repositoryRoot, String stem) {
        if (!SAFE_STEM.matcher(stem).matches()) {
            throw new IllegalArgumentException("Invalid HTTP/3 and QUIC evidence file-name stem: " + stem);
        }
        Path root = evidenceRoot(repositoryRoot);
        return new EvidencePaths(
                root.resolve(stem + ".json"),
                root.resolve(stem + ".log"),
                root.resolve(stem + ".properties"),
                root.resolve(stem + ".source.manifest"),
                root.resolve(stem + ".build.manifest"),
                root.resolve(stem + ".build.log"));
    }

    /**
     * Repository-relative source and configuration roots covered by the manifest.
     *
     * @return immutable source scope
     */
    public static List<String> sourcePaths() {
        return List.of(
                "pom.xml",
                "common/pom.xml",
                "common/buffers/pom.xml",
                "common/buffers/src/main",
                "http/pom.xml",
                "http/http/pom.xml",
                "http/http/src/main",
                "http/http3/pom.xml",
                "http/http3/src/main",
                "quic/pom.xml",
                "quic/quic/pom.xml",
                "quic/quic/src/main",
                "tests/pom.xml",
                "tests/benchmark/pom.xml",
                "tests/benchmark/jmh/pom.xml",
                "tests/benchmark/jmh/README.md",
                "tests/benchmark/jmh/src/main/java/io/helidon/quic",
                "tests/benchmark/jmh/src/main/java/io/helidon/webclient/http3",
                "tests/benchmark/jmh/src/main/resources",
                "tests/benchmark/jmh/src/test/java/io/helidon/quic",
                "tests/benchmark/jmh/src/test/java/io/helidon/tests/benchmark",
                "tests/benchmark/jmh/src/test/java/io/helidon/webclient/http3",
                "webclient/pom.xml",
                "webclient/api/pom.xml",
                "webclient/api/src/main",
                "webclient/http3/pom.xml",
                "webclient/http3/src/main",
                "webclient/http1/pom.xml",
                "webclient/http1/src/main",
                "webclient/http2/pom.xml",
                "webclient/http2/src/main",
                "webserver/pom.xml",
                "webserver/webserver/pom.xml",
                "webserver/webserver/src/main",
                "webserver/quic/pom.xml",
                "webserver/quic/src/main",
                "webserver/http3/pom.xml",
                "webserver/http3/src/main");
    }

    /**
     * Representative classes whose loaded JARs must match the manifest.
     *
     * @return ordered artifact map
     */
    public static Map<String, BenchmarkSourceIdentity.ArtifactScope> artifacts() {
        Map<String, BenchmarkSourceIdentity.ArtifactScope> artifacts = new LinkedHashMap<>();
        artifacts.put("helidon-common-buffers",
                      artifact(BufferData.class, "common/buffers"));
        artifacts.put("helidon-http",
                      artifact(HttpTransportObserver.class, "http/http"));
        artifacts.put("helidon-http-http3",
                      artifact(Http3ErrorCode.class, "http/http3"));
        artifacts.put("helidon-quic",
                      artifact(QuicClient.class, "quic/quic"));
        artifacts.put("helidon-webclient-api",
                      artifact(Proxy.class, "webclient/api"));
        artifacts.put("helidon-webclient-http1",
                      artifact(Http1Client.class, "webclient/http1"));
        artifacts.put("helidon-webclient-http2",
                      artifact(Http2Client.class, "webclient/http2"));
        artifacts.put("helidon-webclient-http3",
                      artifact(Http3Client.class, "webclient/http3"));
        artifacts.put("helidon-webserver",
                      artifact(WebServer.class, "webserver/webserver"));
        artifacts.put("helidon-webserver-quic",
                      artifact(QuicTransportBindingProvider.class, "webserver/quic"));
        artifacts.put("helidon-webserver-http3",
                      artifact(Http3Config.class, "webserver/http3"));
        return artifacts;
    }

    private static BenchmarkSourceIdentity.ArtifactScope artifact(Class<?> representative,
                                                                  String modulePath) {
        return new BenchmarkSourceIdentity.ArtifactScope(representative, modulePath);
    }

    /**
     * Complete, self-contained output paths for one evidence run.
     *
     * @param result primary JSON result
     * @param output readable workload log
     * @param metadata effective configuration
     * @param sourceManifest verified source manifest snapshot
     * @param buildManifest verified controlled-build manifest snapshot
     * @param buildLog verified controlled-build log snapshot
     */
    public record EvidencePaths(Path result,
                                Path output,
                                Path metadata,
                                Path sourceManifest,
                                Path buildManifest,
                                Path buildLog) {
    }
}
