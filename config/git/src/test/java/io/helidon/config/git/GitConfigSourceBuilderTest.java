/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
import java.net.URI;
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
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.test.infra.TemporaryFolderExt;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.helidon.config.PollingStrategies.regular;
import static io.helidon.config.testing.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link GitConfigSourceBuilder}.
 */
public class GitConfigSourceBuilderTest extends RepositoryTestCase {

    private Git git;

    @RegisterExtension
    static TemporaryFolderExt folder = TemporaryFolderExt.build();

    @BeforeEach
    public void setUp(TestInfo testInfo) throws Exception {
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

    @Test
    public void testMaster() throws Exception {
        try (GitConfigSource source = GitConfigSource
                .builder()
                .path("application.properties")
                .uri(URI.create(fileUri()))
                .build()) {

            source.init(null);

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

            source.init(null);

            ObjectNode root = ConfigParsers.properties().parse(source.load().get());

            assertThat(root.get("greeting"), valueNode("hello"));
        }
    }

    @Test
    public void testDirectory() throws IOException, GitAPIException, InterruptedException, Exception {
        File tempDir = folder.newFolder();

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

            source.init(null);

            assertThat(tempDir.toPath().resolve("application.properties").toFile().exists(), is(true));
        }
    }

    @Test
    public void testDirectoryEmpty() throws IOException, Exception {
        Path tempDir = folder.newFolder().toPath();

        try (GitConfigSource source = GitConfigSource
                .builder()
                .path("application.properties")
                .uri(URI.create(fileUri()))
                .directory(tempDir)
                .parser(ConfigParsers.properties())
                .build()) {

            source.init(null);

            assertThat(tempDir.resolve("application.properties").toFile().exists(), is(true));
        }
    }

    @Test
    public void testDirNotEmpty() throws IOException {
        Path tempDir = folder.newFolder().toPath();
        final ConfigException ce = assertThrows(ConfigException.class, () -> {
            tempDir.resolve("dust").toFile().createNewFile();
            GitConfigSource
                    .builder()
                    .path("application.properties")
                    .uri(URI.create(fileUri()))
                    .directory(tempDir)
                    .parser(ConfigParsers.properties())
                    .build()
                    .init(null);
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

            source.init(null);

            Optional<ObjectNode> root = source.load().map(ConfigParsers.properties()::parse);

            assertThat(root.isPresent(), is(true));
            assertThat(root.get().get("greeting"), valueNode("ahoy"));
        }
    }

    @Test
    public void testDescriptionWithDirAndUri() throws IOException, GitAPIException, Exception {
        Path dir = folder.newFolder().toPath();

        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(dir.toFile())
                .call();
                GitConfigSource source = GitConfigSource.builder()
                        .path("application.conf")
                        .uri(URI.create(fileUri()))
                        .directory(dir)
                        .build()) {

            source.init(null);

            assertThat(source.description(), is(String.format("GitConfig[%s|%s#application.conf]", dir, fileUri())));
        }
    }

    @Test
    public void testDescriptionWithDir() throws IOException, GitAPIException, Exception {
        Path dir = folder.newFolder().toPath();

        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(dir.toFile())
                .call();
                GitConfigSource source = GitConfigSource.builder()
                        .path("application.conf")
                        .directory(dir)
                        .build()) {

            assertThat(source.description(), is(String.format("GitConfig[%s#application.conf]", dir)));
        }
    }

    @Test
    public void testDescriptionWithUri() throws IOException, GitAPIException, Exception {
        Path dir = folder.newFolder().toPath();

        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(dir.toFile())
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
        Path directory = folder.newFolder().toPath();

        Config metaConfig = Config.builder(ConfigSources.create(Map.of("path", "application.properties",
                                                                       "uri", fileUri(),
                                                                       "branch", "test",
                                                                       "directory", directory.toString())))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GitConfigSourceBuilder builder = GitConfigSource.builder().config(metaConfig);

        assertThat(builder.target().path(), is("application.properties"));
        assertThat(builder.target().uri(), is(URI.create(fileUri())));
        assertThat(builder.target().branch(), is("test"));
        assertThat(builder.target().directory(), is(directory));
    }

    @Test
    public void testSourceFromConfigByClass() throws Exception {
        Path directory = folder.newFolder().toPath();

        Config metaConfig = Config.builder(ConfigSources.create(ObjectNode.builder()
                                                                        .addValue("type",
                                                                                  GitConfigSourceProvider.TYPE)
                                                                        .addObject("properties", ObjectNode.builder()
                                                                                .addValue("path", "application.properties")
                                                                                .addValue("uri", fileUri())
                                                                                .addValue("branch", "test")
                                                                                .addValue("directory", directory.toString())
                                                                                .build())
                                                                        .build()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        try (GitConfigSource gitSource = (GitConfigSource) MetaConfig.configSource(metaConfig).get(0)) {
            assertThat(gitSource.gitEndpoint().path(), is("application.properties"));
            assertThat(gitSource.gitEndpoint().uri(), is(URI.create(fileUri())));
            assertThat(gitSource.gitEndpoint().branch(), is("test"));
            assertThat(gitSource.gitEndpoint().directory(), is(directory));
        }
    }

    @Test
    public void testSourceFromConfigByType() throws Exception {
        Path directory = folder.newFolder().toPath();

        Config metaConfig = Config.builder(ConfigSources.create(ObjectNode.builder()
                                                                        .addValue("type", "git")
                                                                        .addObject("properties", ObjectNode.builder()
                                                                                .addValue("path", "application.properties")
                                                                                .addValue("uri", fileUri())
                                                                                .addValue("branch", "test")
                                                                                .addValue("directory", directory.toString())
                                                                                .build())
                                                                        .build()))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        try (GitConfigSource gitSource = (GitConfigSource) MetaConfig.configSource(metaConfig).get(0)) {
            assertThat(gitSource.gitEndpoint().path(), is("application.properties"));
            assertThat(gitSource.gitEndpoint().uri(), is(URI.create(fileUri())));
            assertThat(gitSource.gitEndpoint().branch(), is("test"));
            assertThat(gitSource.gitEndpoint().directory(), is(directory));
        }
    }
}
