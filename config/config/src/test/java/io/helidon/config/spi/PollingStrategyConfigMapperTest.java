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

package io.helidon.config.spi;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

import io.helidon.common.reactive.Flow;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.internal.FilesystemWatchPollingStrategy;
import io.helidon.config.internal.ScheduledPollingStrategy;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSourceConfigMapperTest.MyConfigSource;
import io.helidon.config.spi.ConfigSourceConfigMapperTest.MyConfigSourceBuilder;
import io.helidon.config.spi.ConfigSourceConfigMapperTest.MyEndpoint;
import static io.helidon.config.spi.ConfigSourceConfigMapperTest.justFrom;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PollingStrategyConfigMapper}.
 */
public class PollingStrategyConfigMapperTest {

    @Test
    public void testRegular() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("polling-strategy", ObjectNode.builder()
                                        .addValue("type", "regular")
                                        .addValue("properties.interval", "PT15S")
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        assertThat(((MyConfigSource) source).getPollingStrategy(), is(instanceOf(ScheduledPollingStrategy.class)));

        ScheduledPollingStrategy strategy = (ScheduledPollingStrategy) ((MyConfigSource) source).getPollingStrategy();
        assertThat(strategy.getRecurringPolicy().interval(), is(Duration.ofSeconds(15)));
    }

    @Test
    public void testWatch() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", PathConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("path", "application.properties")
                                .addObject("polling-strategy", ObjectNode.builder()
                                        .addValue("type", "watch")
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(PathConfigSource.class)));

        assertThat(((PathConfigSource) source).getPollingStrategy(), is(instanceOf(FilesystemWatchPollingStrategy.class)));

        FilesystemWatchPollingStrategy strategy = (FilesystemWatchPollingStrategy) ((PathConfigSource) source)
                .getPollingStrategy();
        assertThat(strategy.getPath(), is(Paths.get("application.properties")));
    }

    @Test
    public void testCustomClassWithEndpoint() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("polling-strategy", ObjectNode.builder()
                                        .addValue("class", MyEndpointPollingStrategy.class.getName())
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        assertThat(((MyConfigSource) source).getPollingStrategy(), is(instanceOf(MyEndpointPollingStrategy.class)));

        MyEndpointPollingStrategy strategy = (MyEndpointPollingStrategy) ((MyConfigSource) source).getPollingStrategy();
        assertThat(strategy.endpoint.getMyProp1(), is("key1"));
        assertThat(strategy.endpoint.getMyProp2(), is(23));
    }

    @Test
    public void testCustomClassFromConfig() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("polling-strategy", ObjectNode.builder()
                                        .addValue("class", TestingPollingStrategy.class.getName())
                                        .addObject("properties", ObjectNode.builder()
                                                .addValue("retries", "42")
                                                .addValue("interval", "PT5S")
                                                .build())
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        assertThat(((MyConfigSource) source).getPollingStrategy(), is(instanceOf(TestingPollingStrategy.class)));

        TestingPollingStrategy strategy = (TestingPollingStrategy) ((MyConfigSource) source).getPollingStrategy();
        assertThat(strategy.getRetries(), is(42));
        assertThat(strategy.getInterval(), is(Duration.ofSeconds(5)));
    }

    @Test
    public void testCustomBuilderFromConfig() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("polling-strategy", ObjectNode.builder()
                                        .addValue("class", TestingPollingStrategyBuilder.class.getName())
                                        .addObject("properties", ObjectNode.builder()
                                                .addValue("retries", "42")
                                                .addValue("interval", "PT5S")
                                                .build())
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        assertThat(((MyConfigSource) source).getPollingStrategy(), is(instanceOf(TestingPollingStrategy.class)));

        TestingPollingStrategy strategy = (TestingPollingStrategy) ((MyConfigSource) source).getPollingStrategy();
        assertThat(strategy.getRetries(), is(42));
        assertThat(strategy.getInterval(), is(Duration.ofSeconds(5)));
    }

    public static class MyEndpointPollingStrategy implements PollingStrategy {

        private final MyEndpoint endpoint;

        public MyEndpointPollingStrategy(MyEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public Flow.Publisher<PollingEvent> ticks() {
            return subscriber -> {
            };
        }

        @Override
        public String toString() {
            return "MyEndpointPollingStrategy{"
                    + "endpoint=" + endpoint
                    + '}';
        }
    }

    public static class PathConfigSourceBuilder
            extends AbstractSource.Builder<PathConfigSourceBuilder, Path, ConfigSource> {
        private Path path;

        private PathConfigSourceBuilder(Path path) {
            super(Path.class);
            this.path = path;
        }

        public static PathConfigSourceBuilder from(Config metaConfig) {
            return new PathConfigSourceBuilder(metaConfig.get("path").as(Path.class))
                    .init(metaConfig);
        }

        @Override
        protected Path getTarget() {
            return path;
        }

        @Override
        public ConfigSource build() {
            return new PathConfigSource(getPollingStrategy());
        }
    }

    public static class PathConfigSource implements ConfigSource {
        private PollingStrategy pollingStrategy;

        public PathConfigSource(PollingStrategy pollingStrategy) {
            this.pollingStrategy = pollingStrategy;
        }

        @Override
        public Optional<ObjectNode> load() throws ConfigException {
            return Optional.empty();
        }

        public PollingStrategy getPollingStrategy() {
            return pollingStrategy;
        }
    }

    public static class TestingPollingStrategy implements PollingStrategy {
        private final int retries;
        private final Duration interval;

        private TestingPollingStrategy(int retries, Duration interval) {
            this.retries = retries;
            this.interval = interval;
        }

        public static TestingPollingStrategy from(Config metaConfig) {
            return new TestingPollingStrategy(metaConfig.get("retries").asInt(),
                                              metaConfig.get("interval").as(Duration.class));
        }

        public int getRetries() {
            return retries;
        }

        public Duration getInterval() {
            return interval;
        }

        @Override
        public Flow.Publisher<PollingEvent> ticks() {
            return Flow.Subscriber::onComplete;
        }
    }

    public static class TestingPollingStrategyBuilder {
        private final int retries;
        private final Duration interval;

        private TestingPollingStrategyBuilder(int retries, Duration interval) {
            this.retries = retries;
            this.interval = interval;
        }

        public static TestingPollingStrategyBuilder from(Config metaConfig) {
            return new TestingPollingStrategyBuilder(metaConfig.get("retries").asInt(),
                                                     metaConfig.get("interval").as(Duration.class));
        }

        public TestingPollingStrategy build() {
            return new TestingPollingStrategy(retries, interval);
        }
    }

}
