/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.MetaConfig;
import io.helidon.config.git.GitConfigSourceBuilder.GitEndpoint;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.PollingStrategyProvider;
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
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
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
        try (ConfigSource source = GitConfigSource
                .builder("application.properties")
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
        try (ConfigSource source = GitConfigSource
                .builder("application.properties")
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
                ConfigSource source = GitConfigSource
                        .builder("application.properties")
                        .directory(tempDir.toPath())
                        .parser(ConfigParsers.properties())
                        .build()) {

            assertThat(tempDir.toPath().resolve("application.properties").toFile().exists(), is(true));
        }
    }

    @Test
    public void testDirectoryEmpty() throws IOException, Exception {
        Path tempDir = folder.newFolder().toPath();

        try (ConfigSource source = GitConfigSource
                .builder("application.properties")
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
            GitConfigSource
                    .builder("application.properties")
                    .uri(URI.create(fileUri()))
                    .directory(tempDir)
                    .parser(ConfigParsers.properties())
                    .build();
        });

        assertThat(ce.getMessage(),
                   startsWith(String.format("Directory '%s' is not empty and it is not a valid repository.",
                                            tempDir.toString())));
    }

    @Test
    public void testDirAndUriIsEmpty() throws IOException {
        final ConfigException ce = assertThrows(ConfigException.class, () -> {
            GitConfigSource
                    .builder("application.properties")
                    .parser(ConfigParsers.properties())
                    .build();
        });
        assertThat(ce.getMessage(), startsWith("Directory or Uri must be set."));
    }

    @Test
    public void testPolling() throws InterruptedException, IOException, Exception {

        checkoutBranch("refs/heads/master");

        try (ConfigSource source = GitConfigSource
                .builder("application.properties")
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

            assertThat("Change latch was not finished in time",
                       changeLatch.await(1000, TimeUnit.MILLISECONDS),
                       is(true));

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
                ConfigSource source = GitConfigSource.builder("application.conf")
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
                ConfigSource source = GitConfigSource.builder("application.conf")
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
                ConfigSource source = GitConfigSource.builder("application.conf")
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
    public void testFromConfigWithCustomPollingStrategy() throws IOException {
        Path directory = folder.newFolder().toPath();

        Config metaConfig = Config.builder(ConfigSources.create(Map.of(
                "path", "application.properties",
                "uri", fileUri(),
                "branch", "test",
                "directory", directory.toString(),
                "polling-strategy.type", TestingGitEndpointPollingStrategyProvider.TYPE)))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GitConfigSourceBuilder builder = GitConfigSource.builder().config(metaConfig);

        assertThat(builder.target().path(), is("application.properties"));
        assertThat(builder.target().uri(), is(URI.create(fileUri())));
        assertThat(builder.target().branch(), is("test"));
        assertThat(builder.target().directory(), is(directory));

        assertThat(builder.pollingStrategyInternal(), is(instanceOf(TestingGitEndpointPollingStrategy.class)));
        GitEndpoint strategyEndpoint = ((TestingGitEndpointPollingStrategy) builder.pollingStrategyInternal())
                .gitEndpoint();

        assertThat(strategyEndpoint.path(), is("application.properties"));
        assertThat(strategyEndpoint.uri(), is(URI.create(fileUri())));
        assertThat(strategyEndpoint.branch(), is("test"));
        assertThat(strategyEndpoint.directory(), is(directory));
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

        try (ConfigSource source = MetaConfig.configSource(metaConfig)) {
            assertThat(source, is(instanceOf(GitConfigSource.class)));

            GitConfigSource gitSource = (GitConfigSource) source;
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

        try (ConfigSource source = MetaConfig.configSource(metaConfig)) {

            assertThat(source, is(instanceOf(GitConfigSource.class)));

            GitConfigSource gitSource = (GitConfigSource) source;
            assertThat(gitSource.gitEndpoint().path(), is("application.properties"));
            assertThat(gitSource.gitEndpoint().uri(), is(URI.create(fileUri())));
            assertThat(gitSource.gitEndpoint().branch(), is("test"));
            assertThat(gitSource.gitEndpoint().directory(), is(directory));
        }
    }

    public static class TestingGitEndpointPollingStrategyProvider implements PollingStrategyProvider {
        private static final String TYPE = "git-test";

        @Override
        public boolean supports(String type) {
            return TYPE.equals(type);
        }

        @Override
        public Function<Object, PollingStrategy> create(String type, Config metaConfig) {
            return object -> {
                if (object instanceof GitEndpoint) {
                    return new TestingGitEndpointPollingStrategy((GitEndpoint) object);
                }
                throw new IllegalArgumentException("Testing polling strategy expects GitEndpoint, but got " + object);
            };
        }

        @Override
        public Set<String> supported() {
            return Set.of(TYPE);
        }
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

        public GitEndpoint gitEndpoint() {
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
