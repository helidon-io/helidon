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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Creates and verifies a source manifest against live Git state, source files, built reactor artifacts, and the full
 * benchmark runtime class path.
 */
public final class BenchmarkSourceIdentity {
    /**
     * Expected runtime-class-path digest property passed to every controlled evidence JMH fork.
     */
    public static final String RUNTIME_CLASSPATH_SHA256_PROPERTY =
            "helidon.benchmark.evidence.runtimeClasspathSha256";
    /**
     * Root used only to normalize class-path labels while compiling an immutable source snapshot.
     */
    public static final String RUNTIME_CLASSPATH_ROOT_PROPERTY =
            "helidon.benchmark.evidence.runtimeClasspathRoot";

    private static final String FORMAT = "6";
    private static final String BUILD_FORMAT = "4";
    private static final String RESOLVER_SPLIT_PROPERTY = "aether.enhancedLocalRepository.split";
    private static final String RESOLVER_LOCAL_PREFIX_PROPERTY = "aether.enhancedLocalRepository.localPrefix";
    private static final String RESOLVER_REMOTE_PREFIX_PROPERTY = "aether.enhancedLocalRepository.remotePrefix";
    private static final String STAGING_PREFIX = "helidon-http3-quic-evidence/staging-";
    private static final String PUBLISHED_PREFIX = "helidon-http3-quic-evidence/v1/";
    private static final String SCM_REVISION = "Scm-Revision";
    private static final long PROCESS_TIMEOUT_SECONDS = 10;

    private BenchmarkSourceIdentity() {
    }

    /**
     * Writes a canonical manifest for the supplied source scope, then verifies it against the live process.
     *
     * @param repositoryRoot repository root
     * @param manifest manifest output
     * @param buildManifest pre-build input snapshot
     * @param scopePaths repository-relative files or directories
     * @param artifacts artifact names and their current-worktree build scopes
     * @return verified identity
     * @throws Exception if Git, source, artifact, class-path, or manifest validation fails
     */
    public static SourceIdentity create(Path repositoryRoot,
                                        Path manifest,
                                        Path buildManifest,
                                        List<String> scopePaths,
                                        Map<String, ArtifactScope> artifacts) throws Exception {
        return create(repositoryRoot, manifest, buildManifest, scopePaths, artifacts, null);
    }

    static SourceIdentity createStaged(Path repositoryRoot,
                                       Path manifest,
                                       Path buildManifest,
                                       List<String> scopePaths,
                                       Map<String, ArtifactScope> artifacts,
                                       String stagingRepositoryPrefix) throws Exception {
        if (stagingRepositoryPrefix == null || !stagingRepositoryPrefix.startsWith(STAGING_PREFIX)) {
            throw new IllegalArgumentException("Invalid controlled-build staging repository prefix");
        }
        return create(repositoryRoot,
                      manifest,
                      buildManifest,
                      scopePaths,
                      artifacts,
                      stagingRepositoryPrefix);
    }

    private static SourceIdentity create(Path repositoryRoot,
                                         Path manifest,
                                         Path buildManifest,
                                         List<String> scopePaths,
                                         Map<String, ArtifactScope> artifacts,
                                         String stagingRepositoryPrefix) throws Exception {
        Path root = repositoryRoot.toRealPath();
        BuildIdentity buildIdentity =
                verifyBuildManifest(root, buildManifest, artifacts, stagingRepositoryPrefix);
        Set<String> files = scopedFiles(root, scopePaths);
        List<ClasspathIdentity> classpath = classpathIdentity(root);
        Map<String, RuntimeArtifactIdentity> runtimeArtifacts =
                runtimeArtifacts(buildIdentity.repositoryRoot, buildIdentity.head);
        String classpathSha256 = classpathSha256(classpath);
        String head = gitHead(root);
        String statusSha256 = sha256(gitStatus(root));
        List<String> lines = new ArrayList<>(
                files.size() + artifacts.size() + runtimeArtifacts.size() + classpath.size() + 5);
        lines.add("format\t" + FORMAT);
        lines.add("head\t" + head);
        lines.add("statusSha256\t" + statusSha256);
        lines.add("buildManifestSha256\t" + buildIdentity.sha256);
        lines.add("classpathSha256\t" + classpathSha256);
        for (String file : files) {
            lines.add("file\t" + sha256(Files.readAllBytes(root.resolve(file))) + "\t" + file);
        }
        List<String> artifactNames = new ArrayList<>(artifacts.keySet());
        artifactNames.sort(String::compareTo);
        for (String artifactName : artifactNames) {
            ArtifactIdentity artifact = buildIdentity.artifacts.get(artifactName);
            lines.add("artifact\t" + artifact.sha256
                              + "\t" + artifactName
                              + "\t" + artifact.fileName
                              + "\t" + artifact.modulePath);
        }
        for (Map.Entry<String, RuntimeArtifactIdentity> entry : runtimeArtifacts.entrySet()) {
            RuntimeArtifactIdentity artifact = entry.getValue();
            lines.add("runtimeArtifact\t" + artifact.sha256
                              + "\t" + artifact.groupId
                              + "\t" + artifact.artifactId
                              + "\t" + artifact.version
                              + "\t" + artifact.fileName
                              + "\t" + artifact.scmRevision);
        }
        for (ClasspathIdentity entry : classpath) {
            lines.add("classpath\t" + entry.index
                              + "\t" + entry.sha256
                              + "\t" + entry.kind
                              + "\t" + entry.label);
        }
        byte[] manifestBytes = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
        writeAtomic(manifest, manifestBytes);
        return verify(root,
                      manifest,
                      buildManifest,
                      scopePaths,
                      artifacts,
                      stagingRepositoryPrefix);
    }

    /**
     * Verifies a manifest against the live Git state, complete scoped file set, current-worktree artifacts, and full
     * runtime class path.
     *
     * @param repositoryRoot repository root
     * @param manifest manifest to verify
     * @param buildManifest pre-build input snapshot
     * @param scopePaths repository-relative files or directories
     * @param artifacts artifact names and their current-worktree build scopes
     * @return verified identity
     * @throws Exception if validation fails
     */
    public static SourceIdentity verify(Path repositoryRoot,
                                        Path manifest,
                                        Path buildManifest,
                                        List<String> scopePaths,
                                        Map<String, ArtifactScope> artifacts) throws Exception {
        return verify(repositoryRoot, manifest, buildManifest, scopePaths, artifacts, null);
    }

    private static SourceIdentity verify(Path repositoryRoot,
                                         Path manifest,
                                         Path buildManifest,
                                         List<String> scopePaths,
                                         Map<String, ArtifactScope> artifacts,
                                         String stagingRepositoryPrefix) throws Exception {
        Path root = repositoryRoot.toRealPath();
        BuildIdentity buildIdentity =
                verifyBuildManifest(root, buildManifest, artifacts, stagingRepositoryPrefix);
        Path manifestPath = manifest.toAbsolutePath().normalize();
        byte[] manifestBytes = Files.readAllBytes(manifestPath);
        String manifestText = new String(manifestBytes, StandardCharsets.UTF_8);
        if (manifestText.indexOf('\r') >= 0 || !manifestText.endsWith("\n")) {
            throw new IllegalStateException("Source manifest is not canonical UTF-8 with LF line endings");
        }

        String format = null;
        String expectedHead = null;
        String expectedStatusSha256 = null;
        String expectedBuildManifestSha256 = null;
        String expectedClasspathSha256 = null;
        Map<String, String> expectedFiles = new LinkedHashMap<>();
        Map<String, ArtifactIdentity> expectedArtifacts = new LinkedHashMap<>();
        Map<String, RuntimeArtifactIdentity> expectedRuntimeArtifacts = new LinkedHashMap<>();
        List<ClasspathIdentity> expectedClasspath = new ArrayList<>();
        String[] lines = manifestText.split("\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length - 1; lineIndex++) {
            String line = lines[lineIndex];
            String[] fields = line.split("\t", -1);
            switch (fields[0]) {
            case "format" -> {
                requireFields(fields, 2, line);
                if (format != null) {
                    throw new IllegalStateException("Duplicate source-manifest format");
                }
                format = fields[1];
            }
            case "head" -> {
                requireFields(fields, 2, line);
                if (expectedHead != null) {
                    throw new IllegalStateException("Duplicate source-manifest HEAD");
                }
                expectedHead = fields[1];
            }
            case "statusSha256" -> {
                requireFields(fields, 2, line);
                if (expectedStatusSha256 != null) {
                    throw new IllegalStateException("Duplicate source-manifest status hash");
                }
                expectedStatusSha256 = fields[1];
            }
            case "classpathSha256" -> {
                requireFields(fields, 2, line);
                if (expectedClasspathSha256 != null) {
                    throw new IllegalStateException("Duplicate source-manifest class-path hash");
                }
                expectedClasspathSha256 = fields[1];
            }
            case "buildManifestSha256" -> {
                requireFields(fields, 2, line);
                if (expectedBuildManifestSha256 != null) {
                    throw new IllegalStateException("Duplicate source-manifest build-manifest hash");
                }
                expectedBuildManifestSha256 = fields[1];
            }
            case "file" -> {
                requireFields(fields, 3, line);
                if (expectedFiles.put(fields[2], fields[1]) != null) {
                    throw new IllegalStateException("Duplicate source-manifest file: " + fields[2]);
                }
            }
            case "artifact" -> {
                requireFields(fields, 5, line);
                if (expectedArtifacts.put(fields[2],
                                          new ArtifactIdentity(fields[1], fields[3], fields[4])) != null) {
                    throw new IllegalStateException("Duplicate source-manifest artifact: " + fields[2]);
                }
            }
            case "runtimeArtifact" -> {
                requireFields(fields, 7, line);
                RuntimeArtifactIdentity artifact =
                        new RuntimeArtifactIdentity(fields[2],
                                                    fields[3],
                                                    fields[4],
                                                    fields[5],
                                                    fields[1],
                                                    fields[6]);
                String key = runtimeArtifactKey(artifact);
                if (expectedRuntimeArtifacts.put(key, artifact) != null) {
                    throw new IllegalStateException("Duplicate source-manifest runtime artifact: " + key);
                }
            }
            case "classpath" -> {
                requireFields(fields, 5, line);
                int index = Integer.parseInt(fields[1]);
                if (index != expectedClasspath.size()) {
                    throw new IllegalStateException("Non-contiguous source-manifest class-path index: " + index);
                }
                expectedClasspath.add(new ClasspathIdentity(index, fields[2], fields[3], fields[4]));
            }
            default -> throw new IllegalStateException("Unknown source-manifest entry: " + line);
            }
        }
        if (!FORMAT.equals(format)) {
            throw new IllegalStateException("Unsupported source-manifest format: " + format);
        }
        if (!buildIdentity.sha256.equals(expectedBuildManifestSha256)) {
            throw new IllegalStateException("Source-manifest build snapshot does not match the controlled build");
        }

        String liveHead = gitHead(root);
        String liveStatusSha256 = sha256(gitStatus(root));
        if (!liveHead.equals(expectedHead)) {
            throw new IllegalStateException("Source-manifest HEAD does not match the live worktree");
        }
        if (!liveStatusSha256.equals(expectedStatusSha256)) {
            throw new IllegalStateException("Source-manifest status does not match the live worktree");
        }

        Set<String> liveFiles = scopedFiles(root, scopePaths);
        if (!liveFiles.equals(expectedFiles.keySet())) {
            throw new IllegalStateException("Source-manifest file inventory does not match the live scope");
        }
        for (String file : liveFiles) {
            String actual = sha256(Files.readAllBytes(root.resolve(file)));
            if (!actual.equals(expectedFiles.get(file))) {
                throw new IllegalStateException("Source-manifest hash mismatch: " + file);
            }
        }

        if (!artifacts.keySet().equals(expectedArtifacts.keySet())) {
            throw new IllegalStateException("Source-manifest artifact inventory does not match the required artifacts");
        }
        for (Map.Entry<String, ArtifactScope> entry : artifacts.entrySet()) {
            ArtifactIdentity actual = buildIdentity.artifacts.get(entry.getKey());
            if (!actual.equals(expectedArtifacts.get(entry.getKey()))) {
                throw new IllegalStateException("Source-manifest artifact mismatch: " + entry.getKey());
            }
        }

        Map<String, RuntimeArtifactIdentity> liveRuntimeArtifacts =
                runtimeArtifacts(buildIdentity.repositoryRoot, buildIdentity.head);
        if (!liveRuntimeArtifacts.equals(expectedRuntimeArtifacts)) {
            throw new IllegalStateException(
                    "Source-manifest Helidon SNAPSHOT runtime artifact inventory does not match the live benchmark");
        }

        List<ClasspathIdentity> liveClasspath = classpathIdentity(root);
        if (!liveClasspath.equals(expectedClasspath)) {
            int commonEntries = Math.min(liveClasspath.size(), expectedClasspath.size());
            for (int index = 0; index < commonEntries; index++) {
                ClasspathIdentity expected = expectedClasspath.get(index);
                ClasspathIdentity actual = liveClasspath.get(index);
                if (!expected.equals(actual)) {
                    throw new IllegalStateException("Source-manifest runtime class-path mismatch at index "
                                                            + index
                                                            + ": expected "
                                                            + expected
                                                            + ", actual "
                                                            + actual);
                }
            }
            throw new IllegalStateException("Source-manifest runtime class-path size mismatch: expected "
                                                    + expectedClasspath.size()
                                                    + ", actual "
                                                    + liveClasspath.size());
        }
        String liveClasspathSha256 = classpathSha256(liveClasspath);
        if (!liveClasspathSha256.equals(expectedClasspathSha256)) {
            throw new IllegalStateException("Source-manifest aggregate class-path hash does not match the live benchmark");
        }
        return new SourceIdentity(sha256(manifestBytes),
                                  liveHead,
                                  liveStatusSha256,
                                  liveFiles.size(),
                                  artifacts.size(),
                                  liveRuntimeArtifacts.size(),
                                  liveClasspath.size(),
                                  liveClasspathSha256,
                                  buildIdentity.sha256,
                                  manifestPath.getFileName().toString(),
                                  manifestPath,
                                  manifestText,
                                  buildIdentity.path.getFileName().toString(),
                                  buildIdentity.path,
                                  buildIdentity.text,
                                  buildIdentity.controlledRepositorySha256,
                                  buildIdentity.repositoryLocalPrefix,
                                  buildIdentity.remoteRepositoryPrefix,
                                  buildIdentity.head,
                                  buildIdentity.mavenTimeoutSeconds,
                                  buildIdentity.activeProcessorCount,
                                  buildIdentity.buildLogSha256,
                                  buildIdentity.buildLogPath.getFileName().toString(),
                                  buildIdentity.buildLogPath,
                                  buildIdentity.buildLogText);
    }

    /**
     * Copies the complete tracked and non-ignored worktree into an isolated source snapshot.
     *
     * @param repositoryRoot repository root
     * @return owned immutable-build snapshot
     * @throws Exception if Git, source enumeration, hashing, or copying fails
     */
    public static BuildSnapshot createBuildSnapshot(Path repositoryRoot) throws Exception {
        return new BuildSnapshot(repositoryRoot.toRealPath());
    }

    /**
     * Atomically claims every final artifact in an evidence bundle before its workload starts.
     *
     * @param artifactPaths final bundle artifacts, with the primary result first
     * @return owned bundle reservation
     * @throws IOException if any artifact already exists or cannot be claimed
     */
    public static EvidenceBundle reserveEvidenceBundle(Path... artifactPaths) throws IOException {
        return new EvidenceBundle(List.of(artifactPaths));
    }

    /**
     * Atomically publishes a staged evidence file in the same file system.
     *
     * @param stagedPath staged file
     * @param resultPath final file
     * @throws IOException if publication fails
     */
    public static void publishReplacing(Path stagedPath, Path resultPath) throws IOException {
        if (!Files.isRegularFile(stagedPath, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(stagedPath)) {
            throw new IllegalArgumentException("Staged evidence artifact is not a regular file: " + stagedPath);
        }
        Path result = resultPath.toAbsolutePath().normalize();
        if (result.getParent() == null) {
            throw new IllegalArgumentException("Evidence publication path has no parent: " + result);
        }
        Files.createDirectories(result.getParent());
        Files.move(stagedPath,
                   result,
                   StandardCopyOption.ATOMIC_MOVE,
                   StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Normalizes staged text artifacts and replaces machine-local paths with semantic placeholders.
     *
     * @param repositoryRoot live repository root
     * @param campaignRoot persistent campaign root
     * @param stagedResult exact staged primary-result path
     * @param artifacts staged text or JSON artifacts
     * @throws IOException if an artifact or configured path cannot be read or written
     */
    public static void sanitizeEvidenceArtifacts(Path repositoryRoot,
                                                 Path campaignRoot,
                                                 Path stagedResult,
                                                 Path... artifacts) throws IOException {
        Path sourceRoot = repositoryRoot.toAbsolutePath().normalize();
        Path outputRoot = campaignRoot.toAbsolutePath().normalize();
        Path primary = stagedResult.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>(artifacts.length);
        for (Path artifact : artifacts) {
            if (artifact == null) {
                throw new IllegalArgumentException("Staged evidence artifact is null");
            }
            Path file = artifact.toAbsolutePath().normalize();
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file)) {
                throw new IllegalArgumentException("Staged evidence artifact is not a regular file: " + artifact);
            }
            files.add(file);
        }
        if (!files.contains(primary)) {
            throw new IllegalArgumentException("Staged primary result is not part of the evidence artifacts");
        }
        String localRepositoryProperty =
                System.getProperty(Http3QuicEvidenceScope.MAVEN_LOCAL_REPOSITORY_PROPERTY);
        if (localRepositoryProperty == null || localRepositoryProperty.isBlank()) {
            throw new IllegalStateException("Maven did not expose its effective local repository");
        }
        Path localRepository = Path.of(localRepositoryProperty);
        if (!localRepository.isAbsolute()) {
            throw new IllegalStateException("Maven effective local repository is not absolute: "
                                                    + localRepositoryProperty);
        }
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path javaInvoker = javaHome.resolve("bin").resolve(
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        String runningJava = ProcessHandle.current().info().command().orElse(null);
        String mavenHomeProperty = System.getProperty("maven.home");
        String mavenHome = System.getenv("MAVEN_HOME");
        String maven2Home = System.getenv("M2_HOME");

        for (Path file : files) {
            String result = Files.readString(file, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .replaceAll("(?m)^Maven home: .+$", "Maven home: <maven-home>");
            result = sanitizePath(result, primary, "<staged-result>");
            result = sanitizePath(result, outputRoot, "<campaign-root>");
            result = sanitizePath(result, sourceRoot, "<live-repository>");
            result = sanitizePath(result, localRepository, "<maven-local-repository>");
            result = sanitizePath(result, javaInvoker, "<java-invoker>");
            if (runningJava != null && !runningJava.isBlank()) {
                result = sanitizePath(result, Path.of(runningJava), "<java-invoker>");
            }
            result = sanitizePath(result, javaHome, "<java-home>");
            if (mavenHomeProperty != null && !mavenHomeProperty.isBlank()) {
                result = sanitizePath(result, Path.of(mavenHomeProperty), "<maven-home>");
            }
            if (mavenHome != null && !mavenHome.isBlank()) {
                result = sanitizePath(result, Path.of(mavenHome), "<maven-home>");
            }
            if (maven2Home != null && !maven2Home.isBlank()) {
                result = sanitizePath(result, Path.of(maven2Home), "<maven-home>");
            }
            result = sanitizePath(result, Path.of(System.getProperty("user.dir")), "<working-directory>");
            result = sanitizePath(result, Path.of(System.getProperty("java.io.tmpdir")), "<temporary-directory>");
            result = sanitizePath(result, Path.of(System.getProperty("user.home")), "<user-home>");
            Files.writeString(file,
                              result,
                              StandardCharsets.UTF_8,
                              StandardOpenOption.WRITE,
                              StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static String sanitizePath(String value, Path path, String placeholder) {
        if (!path.isAbsolute()) {
            return value;
        }
        Path normalized = path.normalize();
        if (normalized.getParent() == null) {
            return value;
        }
        List<Path> paths = new ArrayList<>();
        paths.add(normalized);
        try {
            Path real = normalized.toRealPath();
            if (!real.equals(normalized)) {
                paths.add(real);
            }
        } catch (IOException ignored) {
            // The exact configured form remains safe to sanitize when the path does not exist.
        }
        String result = value;
        for (Path candidate : paths) {
            result = sanitizePathRepresentations(result,
                                                 candidate.toString(),
                                                 candidate.toUri(),
                                                 placeholder);
        }
        return result;
    }

    static String sanitizePathRepresentations(String value,
                                              String nativePath,
                                              URI uri,
                                              String placeholder) {
        Set<String> representations = new LinkedHashSet<>();
        representations.add(nativePath);
        representations.add(nativePath.replace("\\", "\\\\"));
        String forwardPath = nativePath.replace('\\', '/');
        representations.add(forwardPath);
        representations.add("file:" + (forwardPath.startsWith("/") ? forwardPath : "/" + forwardPath));
        representations.add("file://" + (forwardPath.startsWith("/") ? forwardPath : "/" + forwardPath));
        representations.add(uri.toString());
        representations.add(uri.toASCIIString());
        representations.add(uri.getPath());
        representations.add(uri.getRawPath());
        String authority = uri.getRawAuthority();
        if (authority != null && !authority.isEmpty()) {
            String rawPath = uri.getRawPath();
            representations.add("file:/" + authority + rawPath);
            representations.add("file://" + authority + rawPath);
            representations.add("file:////" + authority + rawPath);
        }
        List<String> ordered = representations.stream()
                .filter(representation -> representation != null && !representation.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        String result = value;
        for (String representation : ordered) {
            String replacement = representation.endsWith("/") ? placeholder + "/" : placeholder;
            result = result.replace(representation, replacement);
        }
        return result;
    }

    /**
     * Canonicalizes the effective Surefire class path, removes implicit current-directory entries, and installs the
     * explicit path as {@code java.class.path}. JMH uses this property to launch its forks.
     *
     * @return installed canonical class path
     * @throws IOException if a class-path entry cannot be resolved
     */
    public static String freezeRuntimeClassPath() throws IOException {
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) {
            classpath = System.getProperty("java.class.path");
        }
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("Could not determine the benchmark runtime class path");
        }
        List<String> canonical = new ArrayList<>();
        for (String value : classpath.split(Pattern.quote(File.pathSeparator), -1)) {
            if (!value.isEmpty()) {
                canonical.add(Path.of(value).toRealPath().toString());
            }
        }
        if (canonical.isEmpty()) {
            throw new IllegalStateException("Benchmark runtime class path has no explicit entries");
        }
        String frozen = String.join(File.pathSeparator, canonical);
        System.setProperty("java.class.path", frozen);
        return frozen;
    }

    /**
     * Computes the aggregate digest of the currently installed explicit runtime class path.
     *
     * @param repositoryRoot repository root used for stable entry labels
     * @return aggregate class-path SHA-256
     * @throws IOException if a class-path entry cannot be read
     */
    public static String runtimeClasspathSha256(Path repositoryRoot) throws IOException {
        return classpathSha256(classpathIdentity(repositoryRoot.toRealPath()));
    }

    private static BuildIdentity verifyBuildManifest(Path repositoryRoot,
                                                     Path buildManifest,
                                                     Map<String, ArtifactScope> artifacts,
                                                     String stagingRepositoryPrefix) throws Exception {
        Path path = buildManifest.toAbsolutePath().normalize();
        byte[] bytes = Files.readAllBytes(path);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\r') >= 0 || !text.endsWith("\n")) {
            throw new IllegalStateException("Build manifest is not canonical UTF-8 with LF line endings");
        }
        String format = null;
        String expectedHead = null;
        String expectedStatusSha256 = null;
        String expectedRepositoryLocalPrefix = null;
        String expectedRepositoryContentSha256 = null;
        String expectedRemoteRepositoryPrefix = null;
        String expectedBuildLogSha256 = null;
        String expectedBuildLogFile = null;
        Map<String, String> expectedMetadata = new LinkedHashMap<>();
        Map<String, String> expectedSnapshot = new LinkedHashMap<>();
        Map<String, ArtifactIdentity> expectedArtifacts = new LinkedHashMap<>();
        String[] lines = text.split("\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length - 1; lineIndex++) {
            String line = lines[lineIndex];
            String[] fields = line.split("\t", -1);
            switch (fields[0]) {
            case "format" -> {
                requireFields(fields, 2, line);
                if (format != null) {
                    throw new IllegalStateException("Duplicate build-manifest format");
                }
                format = fields[1];
            }
            case "head" -> {
                requireFields(fields, 2, line);
                if (expectedHead != null) {
                    throw new IllegalStateException("Duplicate build-manifest HEAD");
                }
                expectedHead = fields[1];
            }
            case "statusSha256" -> {
                requireFields(fields, 2, line);
                if (expectedStatusSha256 != null) {
                    throw new IllegalStateException("Duplicate build-manifest status hash");
                }
                expectedStatusSha256 = fields[1];
            }
            case "repositoryLocalPrefix" -> {
                requireFields(fields, 2, line);
                if (expectedRepositoryLocalPrefix != null) {
                    throw new IllegalStateException("Duplicate build-manifest repository local prefix");
                }
                expectedRepositoryLocalPrefix = fields[1];
            }
            case "repositoryContentSha256" -> {
                requireFields(fields, 2, line);
                if (expectedRepositoryContentSha256 != null) {
                    throw new IllegalStateException("Duplicate build-manifest repository content hash");
                }
                expectedRepositoryContentSha256 = fields[1];
            }
            case "remoteRepositoryPrefix" -> {
                requireFields(fields, 2, line);
                if (expectedRemoteRepositoryPrefix != null) {
                    throw new IllegalStateException("Duplicate build-manifest remote repository prefix");
                }
                expectedRemoteRepositoryPrefix = fields[1];
            }
            case "buildLogSha256" -> {
                requireFields(fields, 2, line);
                if (expectedBuildLogSha256 != null) {
                    throw new IllegalStateException("Duplicate build-manifest log hash");
                }
                expectedBuildLogSha256 = fields[1];
            }
            case "buildLogFile" -> {
                requireFields(fields, 2, line);
                if (expectedBuildLogFile != null) {
                    throw new IllegalStateException("Duplicate build-manifest log file");
                }
                expectedBuildLogFile = fields[1];
            }
            case "metadata" -> {
                requireFields(fields, 3, line);
                if (expectedMetadata.put(fields[1], fields[2]) != null) {
                    throw new IllegalStateException("Duplicate build-manifest metadata: " + fields[1]);
                }
            }
            case "snapshot" -> {
                requireFields(fields, 3, line);
                if (expectedSnapshot.put(fields[2], fields[1]) != null) {
                    throw new IllegalStateException("Duplicate build-snapshot file: " + fields[2]);
                }
            }
            case "artifact" -> {
                requireFields(fields, 5, line);
                if (expectedArtifacts.put(fields[2],
                                          new ArtifactIdentity(fields[1], fields[3], fields[4])) != null) {
                    throw new IllegalStateException("Duplicate build-manifest artifact: " + fields[2]);
                }
            }
            default -> throw new IllegalStateException("Unknown build-manifest entry: " + line);
            }
        }
        if (!BUILD_FORMAT.equals(format)
                || expectedHead == null
                || expectedStatusSha256 == null
                || expectedRepositoryLocalPrefix == null
                || expectedRepositoryContentSha256 == null
                || expectedRemoteRepositoryPrefix == null
                || expectedBuildLogSha256 == null
                || expectedBuildLogFile == null
                || expectedSnapshot.isEmpty()) {
            throw new IllegalStateException("Unsupported or incomplete build manifest");
        }
        List<String> requiredMetadata = List.of(
                "activeProcessorCount",
                "invocationSha256",
                "javaVendor",
                "javaVersion",
                "mavenTimeoutSeconds",
                "mavenVersion");
        if (!expectedMetadata.keySet().equals(new LinkedHashSet<>(requiredMetadata))) {
            throw new IllegalStateException("Build manifest is missing controlled-build metadata");
        }
        long mavenTimeoutSeconds;
        int activeProcessorCount;
        try {
            mavenTimeoutSeconds = Long.parseLong(expectedMetadata.get("mavenTimeoutSeconds"));
            activeProcessorCount = Integer.parseInt(expectedMetadata.get("activeProcessorCount"));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Build-manifest numeric metadata is not a number", e);
        }
        if (mavenTimeoutSeconds < 60 || mavenTimeoutSeconds > 21_600) {
            throw new IllegalStateException("Build-manifest Maven timeout is outside safe bounds");
        }
        if (activeProcessorCount < 1 || activeProcessorCount > 64) {
            throw new IllegalStateException("Build-manifest active processor count is outside safe bounds");
        }
        if (!gitHead(repositoryRoot).equals(expectedHead)
                || !sha256(gitStatus(repositoryRoot)).equals(expectedStatusSha256)) {
            throw new IllegalStateException("Build-manifest Git state changed during the controlled build");
        }

        Map<String, String> liveSnapshot = fileIdentities(repositoryRoot, worktreeFiles(repositoryRoot));
        if (!liveSnapshot.equals(expectedSnapshot)) {
            throw new IllegalStateException("Live worktree does not match the immutable controlled-build snapshot");
        }

        if (!Boolean.parseBoolean(System.getProperty(RESOLVER_SPLIT_PROPERTY))) {
            throw new IllegalStateException("Controlled benchmark evidence requires split Maven local repositories");
        }
        String activeRepositoryPrefix = System.getProperty(RESOLVER_LOCAL_PREFIX_PROPERTY);
        if (activeRepositoryPrefix == null || activeRepositoryPrefix.isBlank()) {
            throw new IllegalStateException("Maven did not expose its effective local repository prefix");
        }
        if (stagingRepositoryPrefix == null) {
            if (!expectedRepositoryLocalPrefix.equals(activeRepositoryPrefix)) {
                throw new IllegalStateException("Controlled repository prefix mismatch: expected "
                                                        + expectedRepositoryLocalPrefix
                                                        + ", actual "
                                                        + activeRepositoryPrefix);
            }
        } else if (!stagingRepositoryPrefix.equals(activeRepositoryPrefix)) {
            throw new IllegalStateException("Controlled staging repository prefix mismatch: expected "
                                                    + stagingRepositoryPrefix
                                                    + ", actual "
                                                    + activeRepositoryPrefix);
        }
        String activeRemotePrefix = System.getProperty(RESOLVER_REMOTE_PREFIX_PROPERTY);
        if (!expectedRemoteRepositoryPrefix.equals(activeRemotePrefix)) {
            throw new IllegalStateException("Controlled remote repository prefix mismatch: expected "
                                                    + expectedRemoteRepositoryPrefix
                                                    + ", actual "
                                                    + activeRemotePrefix);
        }
        String localRepositoryProperty =
                System.getProperty(Http3QuicEvidenceScope.MAVEN_LOCAL_REPOSITORY_PROPERTY);
        if (localRepositoryProperty == null || localRepositoryProperty.isBlank()) {
            throw new IllegalStateException("Maven did not expose its effective local repository");
        }
        Path localRepository = Path.of(localRepositoryProperty).toRealPath();
        Path controlledRepository = localRepository.resolve(activeRepositoryPrefix).normalize();
        if (!controlledRepository.startsWith(localRepository)
                || !Files.isDirectory(controlledRepository, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(controlledRepository)) {
            throw new IllegalStateException("Controlled Maven repository prefix is missing or invalid");
        }
        controlledRepository = controlledRepository.toRealPath();
        String liveRepositorySha256 = exactDirectorySha256(controlledRepository);
        if (!expectedRepositoryContentSha256.equals(liveRepositorySha256)) {
            throw new IllegalStateException("Controlled Maven repository content hash mismatch");
        }

        Path buildLogFile = Path.of(expectedBuildLogFile);
        if (buildLogFile.isAbsolute()
                || buildLogFile.getNameCount() != 1
                || !buildLogFile.equals(buildLogFile.getFileName())) {
            throw new IllegalStateException("Build-manifest log file must be a sibling basename");
        }
        Path buildLogPath = path.resolveSibling(buildLogFile).normalize();
        if (!buildLogPath.getParent().equals(path.getParent())
                || !Files.isRegularFile(buildLogPath, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(buildLogPath)) {
            throw new IllegalStateException("Build-manifest log file is missing or invalid");
        }
        byte[] buildLogBytes = Files.readAllBytes(buildLogPath);
        if (!expectedBuildLogSha256.equals(sha256(buildLogBytes))) {
            throw new IllegalStateException("Controlled-build log hash mismatch");
        }
        String buildLogText = new String(buildLogBytes, StandardCharsets.UTF_8);
        if (buildLogText.indexOf('\r') >= 0
                || !buildLogText.endsWith("\n")
                || !buildLogText.contains(
                        "invocationSha256=" + expectedMetadata.get("invocationSha256") + "\n")) {
            throw new IllegalStateException("Controlled-build log is not canonical or has different metadata");
        }

        if (!artifacts.keySet().equals(expectedArtifacts.keySet())) {
            throw new IllegalStateException("Build-manifest artifact inventory does not match the required artifacts");
        }
        for (Map.Entry<String, ArtifactScope> entry : artifacts.entrySet()) {
            ArtifactIdentity expected = expectedArtifacts.get(entry.getKey());
            if (!entry.getValue().modulePath.equals(expected.modulePath)) {
                throw new IllegalStateException("Build-manifest module path mismatch: " + entry.getKey());
            }
            ArtifactIdentity actual = loadedArtifactIdentity(entry.getValue());
            if (!actual.equals(expected)) {
                throw new IllegalStateException("Loaded artifact does not match the immutable build: " + entry.getKey());
            }
            verifyScmRevision(artifactPath(entry.getValue().representative), expectedHead);
        }
        return new BuildIdentity(sha256(bytes),
                                 path,
                                 text,
                                 Map.copyOf(expectedArtifacts),
                                 expectedHead,
                                 expectedRepositoryLocalPrefix,
                                 expectedRepositoryContentSha256,
                                 expectedRemoteRepositoryPrefix,
                                 controlledRepository,
                                 expectedBuildLogSha256,
                                 buildLogPath,
                                 buildLogText,
                                 mavenTimeoutSeconds,
                                 activeProcessorCount);
    }

    private static ArtifactIdentity loadedArtifactIdentity(ArtifactScope scope) throws Exception {
        Path loadedArtifact = artifactPath(scope.representative);
        String fileName = loadedArtifact.getFileName().toString();
        String loadedSha256 = sha256(Files.readAllBytes(loadedArtifact));
        return new ArtifactIdentity(loadedSha256, fileName, scope.modulePath);
    }

    private static Map<String, RuntimeArtifactIdentity> runtimeArtifacts(Path controlledRepositoryRoot,
                                                                         String expectedHead) throws Exception {
        Path repositoryRoot = controlledRepositoryRoot.toRealPath();
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("Could not determine the benchmark runtime class path");
        }
        Map<String, RuntimeArtifactIdentity> artifacts = new TreeMap<>();
        for (String value : classpath.split(Pattern.quote(File.pathSeparator), -1)) {
            if (value.isEmpty()) {
                continue;
            }
            Path path = Path.of(value).toRealPath();
            if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(path.toFile())) {
                Attributes attributes = jar.getManifest() == null
                        ? null
                        : jar.getManifest().getMainAttributes();
                String scmRevision = attributes == null ? null : attributes.getValue(SCM_REVISION);
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith("META-INF/maven/")
                            || !name.endsWith("/pom.properties")
                            || entry.isDirectory()) {
                        continue;
                    }
                    Properties properties = new Properties();
                    try (InputStream input = jar.getInputStream(entry)) {
                        properties.load(input);
                    }
                    String groupId = properties.getProperty("groupId");
                    String artifactId = properties.getProperty("artifactId");
                    String version = properties.getProperty("version");
                    if (groupId == null
                            || artifactId == null
                            || version == null
                            || !(groupId.equals("io.helidon") || groupId.startsWith("io.helidon."))
                            || !version.endsWith("-SNAPSHOT")) {
                        continue;
                    }
                    if (!path.startsWith(repositoryRoot)) {
                        throw new IllegalStateException(
                                "Helidon SNAPSHOT runtime artifact is outside the controlled repository: " + path);
                    }
                    if (!expectedHead.equals(scmRevision)) {
                        throw new IllegalStateException("Helidon SNAPSHOT runtime artifact has Scm-Revision "
                                                                + scmRevision
                                                                + " instead of "
                                                                + expectedHead
                                                                + ": "
                                                                + path.getFileName());
                    }
                    RuntimeArtifactIdentity identity =
                            new RuntimeArtifactIdentity(groupId,
                                                        artifactId,
                                                        version,
                                                        path.getFileName().toString(),
                                                        sha256(Files.readAllBytes(path)),
                                                        scmRevision);
                    String key = runtimeArtifactKey(identity);
                    RuntimeArtifactIdentity previous = artifacts.put(key, identity);
                    if (previous != null && !previous.equals(identity)) {
                        throw new IllegalStateException("Conflicting Helidon SNAPSHOT runtime artifact: " + key);
                    }
                }
            }
        }
        if (artifacts.isEmpty()) {
            throw new IllegalStateException("Benchmark runtime class path has no controlled Helidon SNAPSHOT artifacts");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(artifacts));
    }

    private static String runtimeArtifactKey(RuntimeArtifactIdentity artifact) {
        return artifact.groupId + ":" + artifact.artifactId + ":" + artifact.version + ":" + artifact.fileName;
    }

    private static List<ClasspathIdentity> classpathIdentity(Path repositoryRoot) throws IOException {
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("Could not determine the benchmark runtime class path");
        }
        Path labelRoot = repositoryRoot;
        String configuredLabelRoot = System.getProperty(RUNTIME_CLASSPATH_ROOT_PROPERTY);
        if (configuredLabelRoot != null && !configuredLabelRoot.isBlank()) {
            labelRoot = Path.of(configuredLabelRoot).toRealPath();
        }

        List<ClasspathIdentity> identities = new ArrayList<>();
        for (String value : classpath.split(Pattern.quote(File.pathSeparator), -1)) {
            if (value.isEmpty()) {
                continue;
            }
            Path path = Path.of(value).toRealPath();
            String kind;
            String hash;
            if (Files.isRegularFile(path)) {
                kind = "file";
                hash = sha256(Files.readAllBytes(path));
            } else if (Files.isDirectory(path)) {
                kind = "directory";
                hash = directorySha256(path);
            } else {
                throw new IllegalStateException("Unsupported benchmark class-path entry: " + path);
            }
            String label = path.startsWith(labelRoot)
                    ? "repo/" + repositoryRelative(labelRoot, path)
                    : path.getFileName().toString();
            validateField(label, "class-path label");
            identities.add(new ClasspathIdentity(identities.size(), hash, kind, label));
        }
        if (identities.isEmpty()) {
            throw new IllegalStateException("Benchmark runtime class path has no explicit entries");
        }
        return List.copyOf(identities);
    }

    private static String classpathSha256(List<ClasspathIdentity> identities) {
        MessageDigest digest = sha256Digest();
        for (ClasspathIdentity identity : identities) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(identity.index).array());
            updateLengthPrefixed(digest, identity.sha256.getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, identity.kind.getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, identity.label.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String directorySha256(Path directory) throws IOException {
        MessageDigest digest = sha256Digest();
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> repositoryRelative(directory, path)))
                    .toList();
            for (Path file : files) {
                String relative = repositoryRelative(directory, file);
                updateLengthPrefixed(digest, relative.getBytes(StandardCharsets.UTF_8));
                byte[] content = Files.readAllBytes(file);
                if ("META-INF/BenchmarkList".equals(relative)) {
                    List<String> benchmarkEntries = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
                    benchmarkEntries.sort(String::compareTo);
                    content = (String.join("\n", benchmarkEntries) + "\n").getBytes(StandardCharsets.UTF_8);
                }
                updateLengthPrefixed(digest, content);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String exactDirectorySha256(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("Controlled repository root is not a regular directory");
        }
        MessageDigest digest = sha256Digest();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.filter(path -> !path.equals(root))
                    .sorted(Comparator.comparing(path -> repositoryRelative(root, path)))
                    .toList();
            for (Path path : paths) {
                String relative = repositoryRelative(root, path);
                updateLengthPrefixed(digest, relative.getBytes(StandardCharsets.UTF_8));
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) {
                    digest.update((byte) 'd');
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(path)) {
                    digest.update((byte) 'f');
                    updateLengthPrefixed(digest, Files.readAllBytes(path));
                } else {
                    throw new IllegalStateException(
                            "Controlled repository contains a non-regular entry: " + relative);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void verifyScmRevision(Path jarPath, String expectedHead) throws IOException {
        if (!Files.isRegularFile(jarPath, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(jarPath)) {
            throw new IllegalStateException("Controlled artifact is not a regular JAR: " + jarPath);
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Attributes attributes = jar.getManifest() == null
                    ? null
                    : jar.getManifest().getMainAttributes();
            String revision = attributes == null ? null : attributes.getValue(SCM_REVISION);
            if (!expectedHead.equals(revision)) {
                throw new IllegalStateException("Controlled artifact has Scm-Revision "
                                                        + revision
                                                        + " instead of "
                                                        + expectedHead
                                                        + ": "
                                                        + jarPath.getFileName());
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        IOException failure = null;
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Refusing to recursively delete a symbolic-link tree: " + root);
        }
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException deletionFailure) {
                if (failure == null) {
                    failure = deletionFailure;
                } else {
                    failure.addSuppressed(deletionFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static List<String> worktreeFiles(Path repositoryRoot) throws Exception {
        byte[] output = runGit(repositoryRoot,
                               "ls-files",
                               "-z",
                               "--cached",
                               "--others",
                               "--exclude-standard");
        List<String> files = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= output.length; index++) {
            if (index < output.length && output[index] != 0) {
                continue;
            }
            if (index > start) {
                String file = new String(output, start, index - start, StandardCharsets.UTF_8);
                validateField(file, "build-snapshot path");
                Path path = repositoryRoot.resolve(file).normalize();
                if (!path.startsWith(repositoryRoot)) {
                    throw new IllegalArgumentException("Build-snapshot path escapes the repository: " + file);
                }
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IllegalStateException(
                                "Build snapshot requires a regular non-symbolic file: " + file);
                    }
                    files.add(file);
                }
            }
            start = index + 1;
        }
        files.sort(String::compareTo);
        if (new LinkedHashSet<>(files).size() != files.size()) {
            throw new IllegalStateException("Duplicate build-snapshot path");
        }
        return List.copyOf(files);
    }

    private static Map<String, String> fileIdentities(Path root, List<String> files) throws IOException {
        Map<String, String> identities = new LinkedHashMap<>();
        for (String file : files) {
            Path path = root.resolve(file).normalize();
            if (!path.startsWith(root) || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Missing or invalid build-snapshot file: " + file);
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Build snapshot requires a regular non-symbolic file: " + file);
            }
            String hash = sha256(Files.readAllBytes(path));
            identities.put(file, hash);
        }
        return identities;
    }

    private static void updateLengthPrefixed(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        digest.update(bytes);
    }

    private static Set<String> scopedFiles(Path repositoryRoot, List<String> scopePaths) throws IOException {
        Set<String> files = new LinkedHashSet<>();
        List<String> sortedScope = new ArrayList<>(scopePaths);
        sortedScope.sort(String::compareTo);
        for (String scopePath : sortedScope) {
            validateField(scopePath, "source scope");
            Path path = repositoryRoot.resolve(scopePath).normalize();
            if (!path.startsWith(repositoryRoot)) {
                throw new IllegalArgumentException("Source-manifest scope escapes the repository: " + scopePath);
            }
            if (Files.isRegularFile(path)) {
                files.add(repositoryRelative(repositoryRoot, path));
            } else if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.walk(path)) {
                    stream.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(value -> repositoryRelative(repositoryRoot, value)))
                            .map(value -> repositoryRelative(repositoryRoot, value))
                            .forEach(files::add);
                }
            } else {
                throw new IllegalArgumentException("Missing source-manifest scope: " + scopePath);
            }
        }
        return files;
    }

    private static String repositoryRelative(Path repositoryRoot, Path path) {
        String value = repositoryRoot.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
        validateField(value, "path");
        return value;
    }

    private static void validateField(String value, String description) {
        if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Unsupported source-manifest " + description + ": " + value);
        }
    }

    private static String gitHead(Path repositoryRoot) throws Exception {
        return new String(runGit(repositoryRoot, "rev-parse", "HEAD"), StandardCharsets.UTF_8).trim();
    }

    private static byte[] gitStatus(Path repositoryRoot) throws Exception {
        return runGit(repositoryRoot, "status", "--porcelain=v1", "--untracked-files=all");
    }

    private static byte[] runGit(Path repositoryRoot, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(arguments.length + 3);
        command.add("git");
        command.add("-C");
        command.add(repositoryRoot.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        CompletableFuture<byte[]> outputFuture = new CompletableFuture<>();
        Thread outputThread = Thread.ofVirtual()
                .name("benchmark-source-git-output")
                .start(() -> {
                    try {
                        outputFuture.complete(process.getInputStream().readAllBytes());
                    } catch (IOException e) {
                        outputFuture.completeExceptionally(e);
                    }
                });
        try {
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                destroyAndAwait(process);
                throw new TimeoutException("Git did not complete while creating benchmark source identity");
            }
            byte[] output;
            try {
                output = outputFuture.get(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new IOException("Could not read Git output", e.getCause());
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Git failed while creating benchmark source identity: "
                                                        + new String(output, StandardCharsets.UTF_8).trim());
            }
            return output;
        } catch (InterruptedException e) {
            destroyAndAwait(process);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputThread.isAlive()) {
                outputThread.interrupt();
            }
        }
    }

    private static void destroyAndAwait(Process process) throws InterruptedException {
        process.destroyForcibly();
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Could not terminate Git while creating benchmark source identity");
        }
    }

    private static Path artifactPath(Class<?> type) throws URISyntaxException, IOException {
        if (type.getProtectionDomain().getCodeSource() == null) {
            throw new IllegalStateException("Loaded artifact has no code source: " + type.getName());
        }
        Path path = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Loaded artifact is not a frozen file: " + type.getName());
        }
        return path;
    }

    private static void writeAtomic(Path output, byte[] bytes) throws IOException {
        Path target = output.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Source-manifest output has no parent: " + output);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + "-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary,
                       target,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void requireFields(String[] fields, int expected, String line) {
        if (fields.length != expected) {
            throw new IllegalStateException("Malformed source-manifest entry: " + line);
        }
    }

    /**
     * Creates an owned, isolated Maven Resolver prefix for the controlled build.
     *
     * @param localRepository effective Maven local repository
     * @param remotePrefix shared Resolver remote-artifact prefix
     * @return controlled repository
     * @throws IOException if the staging prefix cannot be reserved
     */
    public static ControlledRepository createControlledRepository(Path localRepository,
                                                                  String remotePrefix) throws IOException {
        return new ControlledRepository(localRepository, remotePrefix);
    }

    /**
     * Owned Maven Resolver staging prefix that can be published once under its exact content digest.
     */
    public static final class ControlledRepository implements AutoCloseable {
        private final Path localRepository;
        private final String remotePrefix;
        private final String stagingLocalPrefix;
        private final Path stagingRoot;
        private final Path stagingMarker;
        private final byte[] stagingClaim;
        private boolean published;
        private boolean retained;

        private ControlledRepository(Path localRepository, String remotePrefix) throws IOException {
            this.localRepository = localRepository.toRealPath();
            if (!Files.isDirectory(this.localRepository)
                    || remotePrefix == null
                    || remotePrefix.isBlank()) {
                throw new IllegalArgumentException("Invalid controlled Maven repository configuration");
            }
            validateField(remotePrefix, "remote repository prefix");
            this.remotePrefix = remotePrefix;
            String reservation = UUID.randomUUID().toString();
            stagingLocalPrefix = STAGING_PREFIX + reservation;
            stagingRoot = this.localRepository.resolve(stagingLocalPrefix).normalize();
            if (!stagingRoot.startsWith(this.localRepository)
                    || Files.exists(stagingRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Controlled Maven staging prefix already exists");
            }
            Files.createDirectories(stagingRoot.getParent());
            stagingMarker = stagingRoot.resolveSibling(stagingRoot.getFileName() + ".reservation");
            stagingClaim = ("helidon-controlled-repository-v1\t" + reservation + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            Files.write(stagingMarker,
                        stagingClaim,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
        }

        /**
         * Maven arguments that isolate locally installed artifacts while retaining the shared remote cache.
         *
         * @return immutable Maven arguments
         */
        public List<String> mavenArguments() {
            return List.of(
                    "-Dmaven.repo.local=" + localRepository,
                    "-D" + RESOLVER_SPLIT_PROPERTY + "=true",
                    "-D" + RESOLVER_LOCAL_PREFIX_PROPERTY + "=" + stagingLocalPrefix,
                    "-D" + RESOLVER_REMOTE_PREFIX_PROPERTY + "=" + remotePrefix);
        }

        /**
         * Resolver local prefix used only while the controlled build is staged.
         *
         * @return staging local prefix
         */
        public String stagingLocalPrefix() {
            return stagingLocalPrefix;
        }

        /**
         * Freezes and hashes the completed staging prefix.
         *
         * @return immutable repository identity
         * @throws IOException if the tree cannot be validated
         */
        public RepositoryIdentity identity() throws IOException {
            verifyStagingOwnership();
            if (!Files.isDirectory(stagingRoot, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(stagingRoot)) {
                throw new IllegalStateException("Controlled Maven build did not create its staging repository");
            }
            String contentSha256 = exactDirectorySha256(stagingRoot);
            return new RepositoryIdentity(
                    PUBLISHED_PREFIX + contentSha256,
                    stagingLocalPrefix,
                    stagingRoot,
                    contentSha256,
                    remotePrefix);
        }

        /**
         * Atomically publishes an unchanged staging tree under its content-addressed prefix.
         *
         * @param identity sealed staging identity
         * @throws IOException if publication or ownership validation fails
         */
        public void publish(RepositoryIdentity identity) throws IOException {
            if (published || retained) {
                throw new IllegalStateException("Controlled Maven repository is no longer publishable");
            }
            verifyStagingOwnership();
            if (!stagingLocalPrefix.equals(identity.stagingLocalPrefix)
                    || !stagingRoot.equals(identity.stagingRoot)
                    || !remotePrefix.equals(identity.remotePrefix)
                    || !identity.contentSha256.equals(exactDirectorySha256(stagingRoot))) {
                throw new IllegalStateException("Controlled Maven staging repository changed before publication");
            }
            Path finalRoot = localRepository.resolve(identity.localPrefix).normalize();
            if (!finalRoot.startsWith(localRepository)) {
                throw new IllegalArgumentException("Controlled Maven publication prefix escapes the repository");
            }
            Files.createDirectories(finalRoot.getParent());
            Path publicationMarker = finalRoot.resolveSibling(finalRoot.getFileName() + ".reservation");
            byte[] publicationClaim = ("helidon-controlled-repository-publication-v1\t"
                                               + UUID.randomUUID()
                                               + "\n").getBytes(StandardCharsets.UTF_8);
            Files.write(publicationMarker,
                        publicationClaim,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            try {
                if (Files.exists(finalRoot, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isDirectory(finalRoot, LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(finalRoot)
                            || !identity.contentSha256.equals(exactDirectorySha256(finalRoot))) {
                        throw new IllegalStateException(
                                "Controlled Maven content-addressed publication target has different content");
                    }
                    deleteTree(stagingRoot);
                } else {
                    Files.move(stagingRoot, finalRoot, StandardCopyOption.ATOMIC_MOVE);
                    if (!identity.contentSha256.equals(exactDirectorySha256(finalRoot))) {
                        throw new IllegalStateException(
                                "Controlled Maven repository changed during atomic publication");
                    }
                }
                published = true;
                verifyClaim(publicationMarker, publicationClaim, "publication");
                Files.delete(publicationMarker);
                verifyStagingOwnership();
                Files.delete(stagingMarker);
            } finally {
                if (Files.exists(publicationMarker, LinkOption.NOFOLLOW_LINKS)) {
                    verifyClaim(publicationMarker, publicationClaim, "publication");
                    Files.delete(publicationMarker);
                }
            }
        }

        /**
         * Retains a failed staging tree when a child process could still be using it.
         */
        public void retain() {
            retained = true;
        }

        @Override
        public void close() throws IOException {
            if (published || retained) {
                return;
            }
            verifyStagingOwnership();
            deleteTree(stagingRoot);
            Files.delete(stagingMarker);
        }

        private void verifyStagingOwnership() throws IOException {
            verifyClaim(stagingMarker, stagingClaim, "staging");
        }

        private static void verifyClaim(Path marker, byte[] claim, String description) throws IOException {
            if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(marker)
                    || !MessageDigest.isEqual(claim, Files.readAllBytes(marker))) {
                throw new IOException("Controlled Maven " + description + " ownership changed: " + marker);
            }
        }
    }

    /**
     * Immutable identity of a complete Maven Resolver staging tree.
     *
     * @param localPrefix final content-addressed local prefix
     * @param stagingLocalPrefix owned staging local prefix
     * @param stagingRoot owned staging root
     * @param contentSha256 exact staging-tree SHA-256
     * @param remotePrefix shared remote prefix
     */
    public record RepositoryIdentity(String localPrefix,
                                     String stagingLocalPrefix,
                                     Path stagingRoot,
                                     String contentSha256,
                                     String remotePrefix) {
    }

    /**
     * Sanitized metadata for the controlled Maven invocation.
     *
     * @param invocationSha256 canonical invocation SHA-256
     * @param mavenVersion Maven version
     * @param javaVersion Java version
     * @param javaVendor Java vendor
     * @param mavenTimeoutSeconds configured Maven timeout
     * @param activeProcessorCount configured processor count exposed to nested build JVMs
     */
    public record BuildMetadata(String invocationSha256,
                                String mavenVersion,
                                String javaVersion,
                                String javaVendor,
                                long mavenTimeoutSeconds,
                                int activeProcessorCount) {
    }

    /**
     * Complete, isolated worktree snapshot used as the only input to the controlled artifact and benchmark builds.
     */
    public static final class BuildSnapshot implements AutoCloseable {
        private final Path repositoryRoot;
        private final Path root;
        private final String head;
        private final String statusSha256;
        private final List<String> files;
        private final Map<String, String> identities;
        private boolean retained;

        private BuildSnapshot(Path repositoryRoot) throws Exception {
            this.repositoryRoot = repositoryRoot;
            root = Files.createTempDirectory("helidon-http3-quic-evidence-build-").toRealPath();
            boolean complete = false;
            try {
                files = worktreeFiles(repositoryRoot);
                for (String file : files) {
                    Path source = repositoryRoot.resolve(file);
                    Path target = root.resolve(file);
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                }
                head = gitHead(repositoryRoot);
                statusSha256 = sha256(gitStatus(repositoryRoot));
                identities = Map.copyOf(fileIdentities(root, files));
                complete = true;
            } finally {
                if (!complete) {
                    deleteTree(root);
                }
            }
        }

        /**
         * Root of the copied Maven reactor.
         *
         * @return snapshot root
         */
        public Path root() {
            return root;
        }

        /**
         * Captured Git HEAD used as the deterministic SCM revision for the Git-less snapshot build.
         *
         * @return captured HEAD
         */
        public String head() {
            return head;
        }

        /**
         * Verifies that the immutable snapshot and live worktree still match the captured input.
         *
         * @throws Exception if either input changed
         */
        public void verifyUnchanged() throws Exception {
            if (!identities.equals(fileIdentities(root, files))) {
                throw new IllegalStateException("Controlled Maven build modified its immutable source snapshot");
            }
            if (!head.equals(gitHead(repositoryRoot))
                    || !statusSha256.equals(sha256(gitStatus(repositoryRoot)))
                    || !identities.equals(fileIdentities(repositoryRoot, worktreeFiles(repositoryRoot)))) {
                throw new IllegalStateException("Live worktree changed while creating the controlled build");
            }
        }

        /**
         * Verifies the clean snapshot build and writes its complete input, artifact, repository, invocation, and log
         * identity. A subsequent clean benchmark build verifies its loaded artifacts against this identity.
         *
         * @param buildManifest staged build-manifest output
         * @param buildLog staged controlled-build log
         * @param artifacts required runtime artifacts
         * @param repository controlled Maven repository identity
         * @param metadata controlled invocation metadata
         * @param buildLogText canonical controlled-build log
         * @throws Exception if snapshot, live source, built artifact, or publication validation fails
         */
        public void writeBuildEvidence(Path buildManifest,
                                       Path buildLog,
                                       Map<String, ArtifactScope> artifacts,
                                       RepositoryIdentity repository,
                                       BuildMetadata metadata,
                                       String buildLogText) throws Exception {
            verifyUnchanged();
            if (!repository.contentSha256.equals(exactDirectorySha256(repository.stagingRoot))) {
                throw new IllegalStateException("Controlled Maven repository changed before manifest creation");
            }
            if (metadata.mavenTimeoutSeconds < 60 || metadata.mavenTimeoutSeconds > 21_600) {
                throw new IllegalArgumentException("Controlled Maven timeout is outside safe bounds");
            }
            if (metadata.activeProcessorCount < 1 || metadata.activeProcessorCount > 64) {
                throw new IllegalArgumentException("Controlled Maven active processor count is outside safe bounds");
            }
            validateField(metadata.invocationSha256, "invocation hash");
            validateField(metadata.mavenVersion, "Maven version");
            validateField(metadata.javaVersion, "Java version");
            validateField(metadata.javaVendor, "Java vendor");
            if (buildLogText.indexOf('\r') >= 0
                    || !buildLogText.endsWith("\n")
                    || !buildLogText.contains("invocationSha256=" + metadata.invocationSha256 + "\n")) {
                throw new IllegalArgumentException("Controlled-build log is not canonical or has different metadata");
            }
            Path manifestPath = buildManifest.toAbsolutePath().normalize();
            Path logPath = buildLog.toAbsolutePath().normalize();
            if (manifestPath.getParent() == null
                    || !manifestPath.getParent().equals(logPath.getParent())
                    || !logPath.getFileName().equals(Path.of("http3-quic-controlled-build.log"))) {
                throw new IllegalArgumentException("Controlled-build manifest and log must use their staged siblings");
            }
            byte[] buildLogBytes = buildLogText.getBytes(StandardCharsets.UTF_8);

            List<String> lines = new ArrayList<>(identities.size() + artifacts.size() + 14);
            lines.add("format\t" + BUILD_FORMAT);
            lines.add("head\t" + head);
            lines.add("statusSha256\t" + statusSha256);
            lines.add("repositoryLocalPrefix\t" + repository.localPrefix);
            lines.add("repositoryContentSha256\t" + repository.contentSha256);
            lines.add("remoteRepositoryPrefix\t" + repository.remotePrefix);
            lines.add("buildLogSha256\t" + sha256(buildLogBytes));
            lines.add("buildLogFile\t" + logPath.getFileName());
            lines.add("metadata\tactiveProcessorCount\t" + metadata.activeProcessorCount);
            lines.add("metadata\tinvocationSha256\t" + metadata.invocationSha256);
            lines.add("metadata\tjavaVendor\t" + metadata.javaVendor);
            lines.add("metadata\tjavaVersion\t" + metadata.javaVersion);
            lines.add("metadata\tmavenTimeoutSeconds\t" + metadata.mavenTimeoutSeconds);
            lines.add("metadata\tmavenVersion\t" + metadata.mavenVersion);
            for (String file : files) {
                lines.add("snapshot\t" + identities.get(file) + "\t" + file);
            }
            List<String> artifactNames = new ArrayList<>(artifacts.keySet());
            artifactNames.sort(String::compareTo);
            for (String artifactName : artifactNames) {
                ArtifactScope scope = artifacts.get(artifactName);
                Path loadedArtifact = artifactPath(scope.representative);
                String fileName = loadedArtifact.getFileName().toString();
                Path module = root.resolve(scope.modulePath).normalize();
                if (!module.startsWith(root) || !Files.isDirectory(module)) {
                    throw new IllegalArgumentException("Invalid artifact module path: " + scope.modulePath);
                }
                Path builtArtifact = module.resolve("target").resolve(fileName);
                if (!Files.isRegularFile(builtArtifact)) {
                    throw new IllegalStateException("Missing immutable-snapshot artifact for "
                                                            + artifactName + ": " + builtArtifact);
                }
                verifyScmRevision(builtArtifact, head);
                String artifactSha256 = sha256(Files.readAllBytes(builtArtifact));
                lines.add("artifact\t" + artifactSha256
                                  + "\t" + artifactName
                                  + "\t" + fileName
                                  + "\t" + scope.modulePath);
            }
            byte[] bytes = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
            writeAtomic(logPath, buildLogBytes);
            writeAtomic(manifestPath, bytes);
        }

        /**
         * Retains the source snapshot after a child process could not be proved terminated.
         */
        public void retain() {
            retained = true;
        }

        @Override
        public void close() throws IOException {
            if (!retained) {
                deleteTree(root);
            }
        }
    }

    /**
     * Exclusive ownership of all final files in one evidence bundle.
     */
    public static final class EvidenceBundle implements AutoCloseable {
        private final Map<Path, Claim> claims = new LinkedHashMap<>();
        private final Map<Path, String> ownedContentSha256 = new LinkedHashMap<>();
        private final Set<Path> published = new LinkedHashSet<>();
        private final Path primaryArtifact;
        private boolean committed;

        private EvidenceBundle(List<Path> artifactPaths) throws IOException {
            if (artifactPaths.isEmpty()) {
                throw new IllegalArgumentException("Evidence bundle must contain at least one artifact");
            }
            String reservation = UUID.randomUUID().toString();
            primaryArtifact = artifactPaths.getFirst().toAbsolutePath().normalize();
            try {
                for (int index = 0; index < artifactPaths.size(); index++) {
                    Path artifact = artifactPaths.get(index).toAbsolutePath().normalize();
                    if (claims.containsKey(artifact)) {
                        throw new IllegalArgumentException("Duplicate evidence-bundle artifact: " + artifact);
                    }
                    Path parent = artifact.getParent();
                    if (parent == null) {
                        throw new IllegalArgumentException("Evidence-bundle artifact has no parent: " + artifact);
                    }
                    Files.createDirectories(parent);
                    if (Files.exists(artifact, LinkOption.NOFOLLOW_LINKS)) {
                        throw new FileAlreadyExistsException(artifact.toString());
                    }
                    Path marker = artifact.resolveSibling(artifact.getFileName() + ".reservation");
                    byte[] claim = ("helidon-evidence-reservation-v1\t"
                                            + reservation
                                            + "\t"
                                            + index
                                            + "\n").getBytes(StandardCharsets.UTF_8);
                    Files.write(marker,
                                claim,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE);
                    if (Files.exists(artifact, LinkOption.NOFOLLOW_LINKS)) {
                        Files.delete(marker);
                        throw new FileAlreadyExistsException(artifact.toString());
                    }
                    claims.put(artifact, new Claim(marker, claim));
                }
            } catch (IOException | RuntimeException | Error failure) {
                try {
                    cleanupOwnedArtifacts();
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        /**
         * Replaces this reservation's claim with a staged artifact through an atomic same-directory move.
         *
         * @param stagedPath staged artifact
         * @param artifactPath claimed final artifact
         * @throws IOException if ownership changed or publication fails
         */
        public void publish(Path stagedPath, Path artifactPath) throws IOException {
            if (committed) {
                throw new IllegalStateException("Evidence bundle is already committed");
            }
            Path artifact = artifactPath.toAbsolutePath().normalize();
            Claim claim = claims.get(artifact);
            if (claim == null || published.contains(artifact)) {
                throw new IllegalArgumentException("Evidence artifact is not an unpublished bundle claim: " + artifact);
            }
            if (artifact.equals(primaryArtifact)) {
                throw new IllegalArgumentException(
                        "The primary evidence artifact must be published by commit");
            }
            if (!Files.isRegularFile(stagedPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Staged evidence artifact is not a regular file: " + stagedPath);
            }
            if (!Files.isRegularFile(claim.marker, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Evidence-bundle reservation is not a regular file: " + claim.marker);
            }
            byte[] liveClaim = Files.readAllBytes(claim.marker);
            if (!MessageDigest.isEqual(claim.content, liveClaim)) {
                throw new IllegalStateException("Evidence-bundle ownership changed before publication: " + artifact);
            }
            if (Files.exists(artifact, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(artifact.toString());
            }
            String contentSha256 = sha256(Files.readAllBytes(stagedPath));
            Files.move(stagedPath,
                       artifact,
                       StandardCopyOption.ATOMIC_MOVE);
            ownedContentSha256.put(artifact, contentSha256);
            published.add(artifact);
            Files.delete(claim.marker);
        }

        /**
         * Publishes the exact manifest byte snapshot retained by source verification.
         *
         * @param identity verified source identity
         * @param artifactPath claimed final manifest path
         * @throws IOException if staging or publication fails
         */
        public void publishManifest(SourceIdentity identity, Path artifactPath) throws IOException {
            publishTextSnapshot(identity.manifestText, identity.manifestSha256, artifactPath);
        }

        /**
         * Publishes the exact pre-build input snapshot retained by source verification.
         *
         * @param identity verified source identity
         * @param artifactPath claimed final build-manifest path
         * @throws IOException if staging or publication fails
         */
        public void publishBuildManifest(SourceIdentity identity, Path artifactPath) throws IOException {
            publishTextSnapshot(identity.buildManifestText, identity.buildManifestSha256, artifactPath);
        }

        /**
         * Publishes the exact controlled-build log retained by source verification.
         *
         * @param identity verified source identity
         * @param artifactPath claimed final build-log path
         * @throws IOException if staging or publication fails
         */
        public void publishBuildLog(SourceIdentity identity, Path artifactPath) throws IOException {
            publishTextSnapshot(identity.buildLogText, identity.buildLogSha256, artifactPath);
        }

        private void publishTextSnapshot(String text, String expectedSha256, Path artifactPath) throws IOException {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (!sha256(bytes).equals(expectedSha256)) {
                throw new IllegalStateException("Retained manifest snapshot does not match its verified digest");
            }
            Path artifact = artifactPath.toAbsolutePath().normalize();
            Path temporary = Files.createTempFile(
                    artifact.getParent(),
                    "." + artifact.getFileName() + "-",
                    ".tmp");
            try {
                Files.write(temporary, bytes);
                publish(temporary, artifact);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        /**
         * Validates all supporting artifacts and atomically publishes the staged primary result as the final commit
         * action. The primary result is the first path supplied when the bundle was reserved.
         *
         * @param stagedPrimaryArtifact staged primary result in the final directory
         * @throws IOException if ownership, validation, or atomic publication fails
         */
        public void commit(Path stagedPrimaryArtifact) throws IOException {
            if (committed) {
                throw new IllegalStateException("Evidence bundle is already committed");
            }
            if (published.size() != claims.size() - 1 || published.contains(primaryArtifact)) {
                throw new IllegalStateException("Evidence bundle supporting artifacts are incomplete");
            }
            for (Map.Entry<Path, String> entry : ownedContentSha256.entrySet()) {
                if (!entry.getValue().equals(sha256(Files.readAllBytes(entry.getKey())))) {
                    throw new IllegalStateException("Published evidence changed before bundle commit: "
                                                            + entry.getKey());
                }
            }
            Path staged = stagedPrimaryArtifact.toAbsolutePath().normalize();
            if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(staged)
                    || !primaryArtifact.getParent().equals(staged.getParent())) {
                throw new IllegalArgumentException(
                        "Staged primary evidence artifact must be a regular sibling of its final path");
            }
            Claim claim = claims.get(primaryArtifact);
            if (!Files.isRegularFile(claim.marker, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(claim.marker)
                    || !MessageDigest.isEqual(claim.content, Files.readAllBytes(claim.marker))) {
                throw new IllegalStateException(
                        "Primary evidence-bundle reservation changed before commit: " + primaryArtifact);
            }
            if (Files.exists(primaryArtifact, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(primaryArtifact.toString());
            }
            Files.delete(claim.marker);
            Files.move(staged,
                       primaryArtifact,
                       StandardCopyOption.ATOMIC_MOVE);
            committed = true;
        }

        @Override
        public void close() throws IOException {
            if (!committed) {
                cleanupOwnedArtifacts();
            }
        }

        private void cleanupOwnedArtifacts() throws IOException {
            IOException failure = null;
            List<Path> artifacts = new ArrayList<>(ownedContentSha256.keySet());
            for (int index = artifacts.size() - 1; index >= 0; index--) {
                Path artifact = artifacts.get(index);
                try {
                    if (Files.exists(artifact, LinkOption.NOFOLLOW_LINKS)) {
                        if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException(
                                    "Refusing to delete a non-regular evidence artifact: " + artifact);
                        }
                        String liveSha256 = sha256(Files.readAllBytes(artifact));
                        if (!liveSha256.equals(ownedContentSha256.get(artifact))) {
                            throw new IOException("Refusing to delete an evidence artifact whose ownership changed: "
                                                          + artifact);
                        }
                        Files.delete(artifact);
                    }
                } catch (IOException cleanupFailure) {
                    if (failure == null) {
                        failure = cleanupFailure;
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            List<Claim> reservationClaims = new ArrayList<>(claims.values());
            for (int index = reservationClaims.size() - 1; index >= 0; index--) {
                Claim claim = reservationClaims.get(index);
                try {
                    if (Files.exists(claim.marker, LinkOption.NOFOLLOW_LINKS)) {
                        if (!Files.isRegularFile(claim.marker, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException(
                                    "Refusing to delete a non-regular evidence reservation: " + claim.marker);
                        }
                        byte[] liveClaim = Files.readAllBytes(claim.marker);
                        if (!MessageDigest.isEqual(claim.content, liveClaim)) {
                            throw new IOException("Refusing to delete an evidence reservation whose ownership changed: "
                                                          + claim.marker);
                        }
                        Files.delete(claim.marker);
                    }
                } catch (IOException cleanupFailure) {
                    if (failure == null) {
                        failure = cleanupFailure;
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private record Claim(Path marker, byte[] content) {
        }
    }

    /**
     * Describes a frozen Helidon artifact produced by the immutable controlled build.
     *
     * @param representative representative class loaded from the artifact
     * @param modulePath repository-relative module path
     */
    public record ArtifactScope(Class<?> representative,
                                String modulePath) {
    }

    /**
     * Verified source, build-artifact, and runtime identity.
     *
     * @param manifestSha256 manifest SHA-256
     * @param head Git HEAD
     * @param statusSha256 complete porcelain-status SHA-256
     * @param fileCount number of source files
     * @param artifactCount number of immutable-build artifacts
     * @param runtimeArtifactCount number of controlled Helidon SNAPSHOT runtime artifacts
     * @param classpathEntryCount number of frozen runtime class-path entries
     * @param classpathSha256 aggregate runtime class-path SHA-256
     * @param buildManifestSha256 controlled-build input-manifest SHA-256
     * @param manifestFile sanitized manifest file name
     * @param manifestPath absolute verified manifest path
     * @param manifestText exact verified canonical manifest snapshot
     * @param buildManifestFile sanitized build-manifest file name
     * @param buildManifestPath absolute verified build-manifest path
     * @param buildManifestText exact verified pre-build input snapshot
     * @param controlledRepositorySha256 exact controlled Maven prefix digest
     * @param repositoryLocalPrefix content-addressed Resolver local prefix
     * @param remoteRepositoryPrefix shared Resolver remote prefix
     * @param scmRevision exact controlled artifact SCM revision
     * @param mavenTimeoutSeconds controlled Maven timeout
     * @param activeProcessorCount processor count exposed to the controlled build JVMs
     * @param buildLogSha256 controlled-build log SHA-256
     * @param buildLogFile sanitized build-log file name
     * @param buildLogPath absolute verified build-log path
     * @param buildLogText exact verified controlled-build log snapshot
     */
    public record SourceIdentity(String manifestSha256,
                                 String head,
                                 String statusSha256,
                                 int fileCount,
                                 int artifactCount,
                                 int runtimeArtifactCount,
                                 int classpathEntryCount,
                                 String classpathSha256,
                                 String buildManifestSha256,
                                 String manifestFile,
                                 Path manifestPath,
                                 String manifestText,
                                 String buildManifestFile,
                                 Path buildManifestPath,
                                 String buildManifestText,
                                 String controlledRepositorySha256,
                                 String repositoryLocalPrefix,
                                 String remoteRepositoryPrefix,
                                 String scmRevision,
                                 long mavenTimeoutSeconds,
                                 int activeProcessorCount,
                                 String buildLogSha256,
                                 String buildLogFile,
                                 Path buildLogPath,
                                 String buildLogText) {
    }

    private record ArtifactIdentity(String sha256,
                                    String fileName,
                                    String modulePath) {
    }

    private record ClasspathIdentity(int index,
                                     String sha256,
                                     String kind,
                                     String label) {
    }

    private record RuntimeArtifactIdentity(String groupId,
                                           String artifactId,
                                           String version,
                                           String fileName,
                                           String sha256,
                                           String scmRevision) {
    }

    private record BuildIdentity(String sha256,
                                 Path path,
                                 String text,
                                 Map<String, ArtifactIdentity> artifacts,
                                 String head,
                                 String repositoryLocalPrefix,
                                 String controlledRepositorySha256,
                                 String remoteRepositoryPrefix,
                                 Path repositoryRoot,
                                 String buildLogSha256,
                                 Path buildLogPath,
                                 String buildLogText,
                                 long mavenTimeoutSeconds,
                                 int activeProcessorCount) {
    }
}
