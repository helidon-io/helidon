/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.git.GitConfigSourceBuilder.GitEndpoint;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.RepositoryTestCase;

import static io.helidon.config.PollingStrategies.regular;
import io.helidon.config.test.infra.TemporaryFolderExt;
import static io.helidon.config.testing.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

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
        return "file://" + git.getRepository().getWorkTree().getAbsolutePath();
    }

    @Test
    public void testMaster() throws Exception {
        try (ConfigSource source = GitConfigSourceBuilder
                .from("application.properties")
                .uri(URI.create(fileUri()))
                .parser(ConfigParsers.properties())
                .build()) {

            Optional<ObjectNode> root = source.load();

            assertThat(root.isPresent(), is(true));
            assertThat(root.get().get("greeting"), valueNode("ahoy"));
        }
    }

    @Test
    public void testBranch() throws Exception {
        try (ConfigSource source = GitConfigSourceBuilder
                .from("application.properties")
                .uri(URI.create(fileUri()))
                .branch("test")
                .parser(ConfigParsers.properties())
                .build()) {

            Optional<ObjectNode> root = source.load();

            assertThat(root.isPresent(), is(true));
            assertThat(root.get().get("greeting"), valueNode("hello"));
        }
    }

    @Test
    public void testDirectory() throws IOException, GitAPIException, InterruptedException, Exception {
        File tempDir = folder.newFolder();

        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(tempDir)
                .call();
             ConfigSource source = GitConfigSourceBuilder
                .from("application.properties")
                .directory(tempDir.toPath())
                .parser(ConfigParsers.properties())
                .build()) {

             assertThat(tempDir.toPath().resolve("application.properties").toFile().exists(), is(true));
        }
    }

    @Test
    public void testDirectoryEmpty() throws IOException, Exception {
        Path tempDir = folder.newFolder().toPath();

        try (ConfigSource source = GitConfigSourceBuilder
                .from("application.properties")
                .uri(URI.create(fileUri()))
                .directory(tempDir)
                .parser(ConfigParsers.properties())
                .build()) {

            assertThat(tempDir.resolve("application.properties").toFile().exists(), is(true));
        }
    }

    @Test
    public void testDirNotEmpty() throws IOException {
        Path tempDir = folder.newFolder().toPath();
        final ConfigException ce = assertThrows(ConfigException.class, () -> {
            tempDir.resolve("dust").toFile().createNewFile();
        GitConfigSourceBuilder
                .from("application.properties")
                .uri(URI.create(fileUri()))
                .directory(tempDir)
                .parser(ConfigParsers.properties())
                .build();
                });

        assertTrue(ce.getMessage().startsWith(String.format("Directory '%s' is not empty and it is not a valid repository.", tempDir.toString())));
    }

    @Test
    public void testDirAndUriIsEmpty() throws IOException {
        final ConfigException ce = assertThrows(ConfigException.class, () -> {
            GitConfigSourceBuilder
                .from("application.properties")
                .parser(ConfigParsers.properties())
                .build();
        });
        assertTrue(ce.getMessage().startsWith("Directory or Uri must be set."));
    }

    @Test
    public void testPolling() throws InterruptedException, IOException, Exception {

        checkoutBranch("refs/heads/master");

        try (ConfigSource source = GitConfigSourceBuilder
                .from("application.properties")
                .uri(URI.create(fileUri()))
                .pollingStrategy(regular(Duration.ofMillis(50)))
                .parser(ConfigParsers.properties())
                .build()) {

            Optional<ObjectNode> root = source.load();

            assertThat(root.isPresent(), is(true));
            assertThat(root.get().get("greeting"), valueNode("ahoy"));

            CountDownLatch subscribeLatch = new CountDownLatch(1);
            CountDownLatch changeLatch = new CountDownLatch(1);

            CancelableSubscriber sub = new CancelableSubscriber(
                    subscribeLatch, changeLatch);

            source.changes().subscribe(sub);

            assertThat(subscribeLatch.await(100, TimeUnit.MILLISECONDS), is(true));

            commitFile("application.properties", "greeting=hi", "master");

            assertThat(changeLatch.await(1000, TimeUnit.MILLISECONDS), is(true));

            sub.cancel();
            /*
             * Even after canceling the subscription event(s) might be delivered,
             * so stall a moment before ending the test and triggering the clean-up
             * to avoid warnings if the polling strategy publishes more events
             * to its subscribers.
             */
            TimeUnit.SECONDS.sleep(1);
        }
    }

    @Test
    public void testDescriptionWithDirAndUri() throws IOException, GitAPIException, Exception {
        Path dir = folder.newFolder().toPath();

        try (Git clone = Git.cloneRepository()
                .setURI(fileUri())
                .setDirectory(dir.toFile())
                .call();
            ConfigSource source = GitConfigSourceBuilder.from("application.conf")
                .uri(URI.create(fileUri()))
                .directory(dir)
                .build()) {

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
            ConfigSource source = GitConfigSourceBuilder.from("application.conf")
                .directory(dir)
                .build())  {

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
            ConfigSource source = GitConfigSourceBuilder.from("application.conf")
                .uri(URI.create(fileUri()))
                .build()) {

            assertThat(source.description(), is(String.format("GitConfig[%s#application.conf]", fileUri())));
        }
    }

    @Test
    public void testFromConfigNothing() {
        assertThrows(MissingValueException.class, () -> {
            GitConfigSourceBuilder.from(Config.empty());
        });
    }

    @Test
    public void testFromConfigMandatory() {
        Config metaConfig = Config.withSources(ConfigSources.from(CollectionsHelper.mapOf("path", "application.properties")))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GitConfigSourceBuilder builder = GitConfigSourceBuilder.from(metaConfig);

        assertThat(builder.getTarget().getPath(), is("application.properties"));
        assertThat(builder.getTarget().getUri(), is(nullValue()));
        assertThat(builder.getTarget().getBranch(), is("master"));
        assertThat(builder.getTarget().getDirectory(), is(nullValue()));
    }

    @Test
    public void testFromConfigAll() throws IOException {
        Path directory = folder.newFolder().toPath();

        Config metaConfig = Config.withSources(ConfigSources.from(CollectionsHelper.mapOf("path", "application.properties",
                                                                         "uri", fileUri(),
                                                                         "branch", "test",
                                                                         "directory", directory.toString())))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GitConfigSourceBuilder builder = GitConfigSourceBuilder.from(metaConfig);

        assertThat(builder.getTarget().getPath(), is("application.properties"));
        assertThat(builder.getTarget().getUri(), is(URI.create(fileUri())));
        assertThat(builder.getTarget().getBranch(), is("test"));
        assertThat(builder.getTarget().getDirectory(), is(directory));
    }

    @Test
    public void testFromConfigWithCustomPollingStrategy() throws IOException {
        Path directory = folder.newFolder().toPath();

        Config metaConfig = Config.withSources(ConfigSources.from(CollectionsHelper.mapOf(
                "path", "application.properties",
                "uri", fileUri(),
                "branch", "test",
                "directory", directory.toString(),
                "polling-strategy.class", TestingGitEndpointPollingStrategy.class.getName())))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GitConfigSourceBuilder builder = GitConfigSourceBuilder.from(metaConfig);

        assertThat(builder.getTarget().getPath(), is("application.properties"));
        assertThat(builder.getTarget().getUri(), is(URI.create(fileUri())));
        assertThat(builder.getTarget().getBranch(), is("test"));
        assertThat(builder.getTarget().getDirectory(), is(directory));

        assertThat(builder.getPollingStrategyInternal(), is(instanceOf(TestingGitEndpointPollingStrategy.class)));
        GitEndpoint strategyEndpoint = ((TestingGitEndpointPollingStrategy) builder.getPollingStrategyInternal())
                .getGitEndpoint();

        assertThat(strategyEndpoint.getPath(), is("application.properties"));
        assertThat(strategyEndpoint.getUri(), is(URI.create(fileUri())));
        assertThat(strategyEndpoint.getBranch(), is("test"));
        assertThat(strategyEndpoint.getDirectory(), is(directory));
    }

    @Test
    public void testSourceFromConfigByClass() throws IOException {
        Path directory = folder.newFolder().toPath();

        Config metaConfig = Config.withSources(ConfigSources.from(ObjectNode.builder()
                                                                          .addValue("class",
                                                                                    GitConfigSourceBuilder.class.getName())
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

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(GitConfigSource.class)));

        GitConfigSource gitSource = (GitConfigSource) source;
        assertThat(gitSource.getGitEndpoint().getPath(), is("application.properties"));
        assertThat(gitSource.getGitEndpoint().getUri(), is(URI.create(fileUri())));
        assertThat(gitSource.getGitEndpoint().getBranch(), is("test"));
        assertThat(gitSource.getGitEndpoint().getDirectory(), is(directory));
    }

    @Test
    public void testSourceFromConfigByType() throws IOException {
        Path directory = folder.newFolder().toPath();

        Config metaConfig = Config.withSources(ConfigSources.from(ObjectNode.builder()
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

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(GitConfigSource.class)));

        GitConfigSource gitSource = (GitConfigSource) source;
        assertThat(gitSource.getGitEndpoint().getPath(), is("application.properties"));
        assertThat(gitSource.getGitEndpoint().getUri(), is(URI.create(fileUri())));
        assertThat(gitSource.getGitEndpoint().getBranch(), is("test"));
        assertThat(gitSource.getGitEndpoint().getDirectory(), is(directory));
    }

    public static class TestingGitEndpointPollingStrategy implements PollingStrategy {
        private final GitEndpoint gitEndpoint;

        public TestingGitEndpointPollingStrategy(GitEndpoint gitEndpoint) {
            this.gitEndpoint = gitEndpoint;

            assertThat(gitEndpoint, notNullValue());
        }

        @Override
        public Flow.Publisher<PollingEvent> ticks() {
            return Flow.Subscriber::onComplete;
        }

        public GitEndpoint getGitEndpoint() {
            return gitEndpoint;
        }
    }

    /**
     * A subscriber we can cancel at the end of a test so it will not continue
     * to try to access the config after the test is done (and the git
     * infrastructure has been cleaned up) which leads to noisy warnings in
     * the test output.
     */
    private static class CancelableSubscriber implements Flow.Subscriber<Optional<ObjectNode>> {

        private final CountDownLatch subscribeLatch;
        private final CountDownLatch changeLatch;

        private volatile Flow.Subscription subscription = null;

        CancelableSubscriber(CountDownLatch subscribeLatch,
                CountDownLatch changeLatch) {
            this.subscribeLatch = subscribeLatch;
            this.changeLatch = changeLatch;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
            subscribeLatch.countDown();
        }

        @Override
        public void onNext(Optional<ObjectNode> item) {
            if (subscription == null) {
                return;
            }
            System.out.println(item);
            if (((ConfigNode.ValueNode) item.get().get("greeting")).get().equals("hi")) {
                changeLatch.countDown();
            }
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }

        public void cancel() {
            if (subscription != null) {
                subscription.cancel();
                subscription = null;
            }
        }
    }
}
