/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.MetaConfig;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.rules.TestName;

import static io.helidon.config.PollingStrategies.regular;
import static io.helidon.config.testing.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link GitConfigSourceBuilder}.
 */
public class GitConfigSourceBuilderTest extends RepositoryTestCase {

    private Git git;
    @TempDir
    File tempDir;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws Exception {
        String testMethodName = testInfo.getTestMethod()
                .map(Method::getName)
                .orElse(this.getClass().getName());

        /* Hacks to let us re-use setup from jgit 7's LocalDiskRepositoryTestCase */
        super.currentTest = new TestName() {
            @Override
            public String getMethodName() {
                return testMethodName;
            }
        };
        super.testRoot.create();
        /* end of hacks */

        super.setUp();

        git = new Git(db);

        commitFile("application.properties", "greeting=ahoy", "master");
        commitFile("application.properties", "greeting=hello", "test");
    }

    @AfterEach
    public void tearDown(TestInfo testInfo) throws Exception {
        if (git != null) {
            git.close();
        }
        super.tearDown();
    }

    private String fileUri() {
        if (git == null) {
            throw new IllegalStateException("git needs to be initialized but was not");
        }
        return Paths.get(git.getRepository().getWorkTree().getAbsolutePath()).toUri().toString();
    }

    private static ConfigContext configContext() {
        return mock(ConfigContext.class);
    }

    private static void createSymbolicLink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "Symbolic links are not available: " + e.getMessage());
        } catch (FileSystemException e) {
            String reason = e.getReason();
            if (reason != null
                    && (reason.contains("not supported")
                            || reason.contains("Operation not permitted")
                            || reason.contains("privilege"))) {
                assumeTrue(false, "Symbolic links are not available: " + e.getMessage());
            }
            throw e;
        }
    }

    @Test
    public void testInitRejectsNullContext() throws Exception {
        try (GitConfigSource source = GitConfigSource
                .builder()
                .path("application.properties")
                .uri(URI.create(fileUri()))
                .parser(ConfigParsers.properties())
                .build()) {

            NullPointerException npe = assertThrows(NullPointerException.class, () -> source.init(null));
            assertThat(npe.getMessage(), is("context"));
        }
    }

    @Test
    public void testMaster() throws Exception {
        try (GitConfigSource source = GitConfigSource
                .builder()
                .path("application.properties")
                .uri(URI.create(fileUri()))
                .build()) {

            source.init(configContext());

            ObjectNode root = ConfigParsers.properties().parse(source.load().get());

            assertThat(root.get("greeting"), valueNode("ahoy"));
        }
    }

    @Test
    public void testBranch() throws Exception {
        try (GitConfigSource source = GitConfigSource
                .builder()
                .path("application.properties")
                .uri(URI.create(fileUri()))
                .branch("test")
                .build()) {

            source.init(configContext());

            ObjectNode root = ConfigParsers.properties().parse(source.load().get());

            assertThat(root.get("greeting"), valueNode("hello"));
        }
    }

    @Test
    public void testDirectory() throws IOException, GitAPIException, InterruptedException, Exception {
        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(tempDir)
                .call();
                GitConfigSource source = GitConfigSource
                        .builder()
                        .path("application.properties")
                        .directory(tempDir.toPath())
                        .parser(ConfigParsers.properties())
                        .build()) {

            source.init(configContext());

            assertThat(tempDir.toPath().resolve("application.properties").toFile().exists(), is(true));
        }
    }

    @Test
    public void testDirectoryEmpty() throws IOException, Exception {
        try (GitConfigSource source = GitConfigSource
                .builder()
                .path("application.properties")
                .uri(URI.create(fileUri()))
                .directory(tempDir.toPath())
                .parser(ConfigParsers.properties())
                .build()) {

            source.init(configContext());

            assertThat(tempDir.toPath().resolve("application.properties").toFile().exists(), is(true));
        }
    }

    @Test
    public void testDirectoryTraversalRejected() throws IOException {
        Path cloneDir = Files.createDirectory(tempDir.toPath().resolve("clone"));
        Path escapedDir = Files.createDirectory(tempDir.toPath().resolve("escaped"));
        Path escapedFile = escapedDir.resolve("outside.properties");

        Files.writeString(escapedFile, "proof=outside\n", StandardCharsets.UTF_8);

        ConfigException ce = assertThrows(ConfigException.class, () -> {
            try (GitConfigSource source = GitConfigSource
                    .builder()
                    .path("../escaped/outside.properties")
                    .uri(URI.create(fileUri()))
                    .directory(cloneDir)
                    .parser(ConfigParsers.properties())
                    .build()) {
                source.init(configContext());
                source.load();
            }
        });
        assertThat(ce.getMessage(), startsWith("Git configuration path must stay inside the repository:"));
    }

    @Test
    public void testDirectoryAbsolutePathRejected() throws IOException {
        Path cloneDir = Files.createDirectory(tempDir.toPath().resolve("clone"));
        Path escapedFile = Files.writeString(tempDir.toPath().resolve("outside.properties"),
                                            "proof=outside\n",
                                            StandardCharsets.UTF_8);

        ConfigException ce = assertThrows(ConfigException.class, () -> {
            try (GitConfigSource source = GitConfigSource
                    .builder()
                    .path(escapedFile.toString())
                    .uri(URI.create(fileUri()))
                    .directory(cloneDir)
                    .parser(ConfigParsers.properties())
                    .build()) {
                source.init(configContext());
                source.load();
            }
        });
        assertThat(ce.getMessage(), startsWith("Git configuration path must be relative:"));
    }

    @Test
    public void testDirectorySymlinkInsideRepository() throws Exception {
        Path cloneDir = Files.createDirectory(tempDir.toPath().resolve("clone"));

        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(cloneDir.toFile())
                .call()) {
            Files.writeString(cloneDir.resolve("linked-target.properties"), "proof=inside\n", StandardCharsets.UTF_8);
            createSymbolicLink(cloneDir.resolve("linked.properties"), Path.of("linked-target.properties"));

            try (GitConfigSource source = GitConfigSource
                    .builder()
                    .path("linked.properties")
                    .directory(cloneDir)
                    .parser(ConfigParsers.properties())
                    .build()) {
                source.init(configContext());

                ObjectNode root = ConfigParsers.properties().parse(source.load().get());
                assertThat(root.get("proof"), valueNode("inside"));
            }
        }
    }

    @Test
    public void testDirectorySymlinkTraversalRejected() throws Exception {
        Path cloneDir = Files.createDirectory(tempDir.toPath().resolve("clone"));
        Path escapedFile = Files.writeString(tempDir.toPath().resolve("outside.properties"),
                                            "proof=outside\n",
                                            StandardCharsets.UTF_8);

        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(cloneDir.toFile())
                .call()) {
            Path link = cloneDir.resolve("linked.properties");
            createSymbolicLink(link, escapedFile);

            try (GitConfigSource source = GitConfigSource
                    .builder()
                    .path("linked.properties")
                    .directory(cloneDir)
                    .parser(ConfigParsers.properties())
                    .build()) {
                source.init(configContext());

                ConfigException ce = assertThrows(ConfigException.class, source::load);
                assertThat(ce.getMessage(), startsWith("Git configuration path must stay inside the repository:"));
            }
        }
    }

    @Test
    public void testRelativeTraversalRejected() throws Exception {
        Path cloneDir = Files.createDirectory(tempDir.toPath().resolve("clone"));
        Path escapedDir = Files.createDirectory(tempDir.toPath().resolve("escaped"));

        Files.writeString(escapedDir.resolve("outside.properties"), "proof=outside\n", StandardCharsets.UTF_8);

        try (GitConfigSource source = GitConfigSource
                .builder()
                .path("application.properties")
                .uri(URI.create(fileUri()))
                .directory(cloneDir)
                .parser(ConfigParsers.properties())
                .build()) {
            source.init(configContext());

            ConfigException ce = assertThrows(ConfigException.class,
                                              () -> source.relativeResolver().apply("../escaped/outside.properties"));
            assertThat(ce.getMessage(), startsWith("Git configuration path must stay inside the repository:"));
        }
    }

    @Test
    public void testRelativeSymlinkTraversalRejected() throws Exception {
        Path cloneDir = Files.createDirectory(tempDir.toPath().resolve("clone"));
        Path escapedFile = Files.writeString(tempDir.toPath().resolve("outside.properties"),
                                            "proof=outside\n",
                                            StandardCharsets.UTF_8);

        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(cloneDir.toFile())
                .call()) {
            createSymbolicLink(cloneDir.resolve("linked.properties"), escapedFile);

            try (GitConfigSource source = GitConfigSource
                    .builder()
                    .path("application.properties")
                    .directory(cloneDir)
                    .parser(ConfigParsers.properties())
                    .build()) {
                source.init(configContext());

                ConfigException ce = assertThrows(ConfigException.class,
                                                  () -> source.relativeResolver().apply("linked.properties"));
                assertThat(ce.getMessage(), startsWith("Git configuration path must stay inside the repository:"));
            }
        }
    }

    @Test
    public void testDirNotEmpty() throws IOException {
        final ConfigException ce = assertThrows(ConfigException.class, () -> {
            tempDir.toPath().resolve("dust").toFile().createNewFile();
            GitConfigSource
                    .builder()
                    .path("application.properties")
                    .uri(URI.create(fileUri()))
                    .directory(tempDir.toPath())
                    .parser(ConfigParsers.properties())
                    .build()
                    .init(configContext());
        });

        assertThat(ce.getMessage(),
                   startsWith(String.format("Directory '%s' is not empty and it is not a valid repository.",
                                            tempDir.toString())));
    }

    @Test
    public void testDirAndUriIsEmpty() throws IOException {
        final ConfigException ce = assertThrows(ConfigException.class, () -> {
            GitConfigSource
                    .builder()
                    .path("application.properties")
                    .parser(ConfigParsers.properties())
                    .build();
        });
        assertThat(ce.getMessage(), startsWith("Directory or Uri must be set."));
    }

    @Test
    public void testPolling() throws InterruptedException, IOException, Exception {

        checkoutBranch("refs/heads/master");

        try (GitConfigSource source = GitConfigSource
                .builder()
                .path("application.properties")
                .uri(URI.create(fileUri()))
                .pollingStrategy(regular(Duration.ofMillis(50)).build())
                .build()) {

            source.init(configContext());

            Optional<ObjectNode> root = source.load().map(ConfigParsers.properties()::parse);

            assertThat(root.isPresent(), is(true));
            assertThat(root.get().get("greeting"), valueNode("ahoy"));
        }
    }

    @Test
    public void testDescriptionWithDirAndUri() throws IOException, GitAPIException, Exception {
        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(tempDir)
                .call();
                GitConfigSource source = GitConfigSource.builder()
                        .path("application.conf")
                        .uri(URI.create(fileUri()))
                        .directory(tempDir.toPath())
                        .build()) {

            source.init(configContext());

            assertThat(source.description(), is(String.format("GitConfig[%s|%s#application.conf]", tempDir, fileUri())));
        }
    }

    @Test
    public void testDescriptionWithDir() throws IOException, GitAPIException, Exception {
        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(tempDir)
                .call();
                GitConfigSource source = GitConfigSource.builder()
                        .path("application.conf")
                        .directory(tempDir.toPath())
                        .build()) {

            assertThat(source.description(), is(String.format("GitConfig[%s#application.conf]", tempDir)));
        }
    }

    @Test
    public void testDescriptionWithUri() throws IOException, GitAPIException, Exception {
        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(tempDir)
                .call();
                GitConfigSource source = GitConfigSource.builder()
                        .path("application.conf")
                        .uri(URI.create(fileUri()))
                        .build()) {

            assertThat(source.description(), is(String.format("GitConfig[%s#application.conf]", fileUri())));
        }
    }

    @Test
    public void testFromConfigNothing() {
        assertThrows(IllegalArgumentException.class, () -> {
            GitConfigSource.create(Config.empty());
        });
    }

    @Test
    public void testFromConfigMandatory() {
        Config metaConfig = Config.builder(ConfigSources.create(Map.of("path", "application.properties")))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GitConfigSourceBuilder builder = GitConfigSource.builder().config(metaConfig);

        assertThat(builder.target().path(), is("application.properties"));
        assertThat(builder.target().uri(), is(nullValue()));
        assertThat(builder.target().branch(), is("master"));
        assertThat(builder.target().directory(), is(nullValue()));
    }

    @Test
    public void testFromConfigAll() throws IOException {
        Config metaConfig = Config.builder(ConfigSources.create(Map.of("path", "application.properties",
                                                                       "uri", fileUri(),
                                                                       "branch", "test",
                                                                       "directory", tempDir.toPath().toString())))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GitConfigSourceBuilder builder = GitConfigSource.builder().config(metaConfig);

        assertThat(builder.target().path(), is("application.properties"));
        assertThat(builder.target().uri(), is(URI.create(fileUri())));
        assertThat(builder.target().branch(), is("test"));
        assertThat(builder.target().directory(), is(tempDir.toPath()));
    }

    @Test
    public void testSourceFromConfigByClass() throws Exception {
        Config metaConfig = Config.builder(ConfigSources.create(ObjectNode.builder()
                                                                        .addValue("type",
                                                                                  GitConfigSourceProvider.TYPE)
                                                                        .addObject("properties", ObjectNode.builder()
                                                                                .addValue("path", "application.properties")
                                                                                .addValue("uri", fileUri())
                                                                                .addValue("branch", "test")
                                                                                .addValue("directory", tempDir.toPath().toString())
                                                                                .build())
                                                                        .build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        try (GitConfigSource gitSource = (GitConfigSource) MetaConfig.configSource(metaConfig).get(0)) {
            assertThat(gitSource.gitEndpoint().path(), is("application.properties"));
            assertThat(gitSource.gitEndpoint().uri(), is(URI.create(fileUri())));
            assertThat(gitSource.gitEndpoint().branch(), is("test"));
            assertThat(gitSource.gitEndpoint().directory(), is(tempDir.toPath()));
        }
    }

    @Test
    public void testSourceFromConfigByType() throws Exception {
        Config metaConfig = Config.builder(ConfigSources.create(ObjectNode.builder()
                                                                        .addValue("type", "git")
                                                                        .addObject("properties", ObjectNode.builder()
                                                                                .addValue("path", "application.properties")
                                                                                .addValue("uri", fileUri())
                                                                                .addValue("branch", "test")
                                                                                .addValue("directory", tempDir.toPath().toString())
                                                                                .build())
                                                                        .build()))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        try (GitConfigSource gitSource = (GitConfigSource) MetaConfig.configSource(metaConfig).get(0)) {
            assertThat(gitSource.gitEndpoint().path(), is("application.properties"));
            assertThat(gitSource.gitEndpoint().uri(), is(URI.create(fileUri())));
            assertThat(gitSource.gitEndpoint().branch(), is("test"));
            assertThat(gitSource.gitEndpoint().directory(), is(tempDir.toPath()));
        }
    }
}
