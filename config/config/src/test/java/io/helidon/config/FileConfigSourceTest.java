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

package io.helidon.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import io.helidon.config.FileConfigSource.FileBuilder;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.test.infra.TemporaryFolderExt;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link io.helidon.config.FileConfigSource}.
 */
public class FileConfigSourceTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";
    private static final String RELATIVE_PATH_TO_RESOURCE = "/src/test/resources/";

    @RegisterExtension
    static TemporaryFolderExt folder = TemporaryFolderExt.build();
    
    
    @Test
    public void testDescriptionMandatory() {
        ConfigSource configSource = ConfigSources.file("application.conf").build();

        assertThat(configSource.description(), is("FileConfig[application.conf]"));
    }

    @Test
    public void testDescriptionOptional() {
        ConfigSource configSource = ConfigSources.file("application.conf").optional().build();

        assertThat(configSource.description(), is("FileConfig[application.conf]?"));
    }

    @Test
    public void testGetMediaTypeSet() {
        FileConfigSource configSource = (FileConfigSource) ConfigSources.file("application.conf")
                .optional()
                .mediaType(TEST_MEDIA_TYPE)
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.mediaType(), is(TEST_MEDIA_TYPE));
    }

    @Test
    public void testGetMediaTypeGuessed() {
        FileConfigSource configSource = (FileConfigSource) ConfigSources.file("application.properties")
                .optional()
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.mediaType(), Is.is("text/x-java-properties"));
    }

    @Test
    public void testGetMediaTypeUnknown() {
        FileConfigSource configSource = (FileConfigSource) ConfigSources.file("application.unknown")
                .optional()
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.mediaType(), is(nullValue()));
    }

    @Test
    public void testLoadNotExists() {
        FileConfigSource configSource = (FileConfigSource) ConfigSources.file("application.unknown")
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            configSource.init(mock(ConfigContext.class));
            configSource.load();
        });
        assertThat(ex.getCause(), instanceOf(ConfigException.class));
        assertThat(ex.getMessage(), startsWith("Cannot load data from mandatory source"));
    }

    @Test
    public void testLoadExists() {
        ConfigSource configSource = ConfigSources.file(getDir() + "io/helidon/config/application.conf")
                .mediaType("application/hocon")
                .build();

        configSource.init(content -> Optional.of(new ConfigParser() {
            @Override
            public Set<String> supportedMediaTypes() {
                return new HashSet<String>() {{
                    add("application/hocon");
                }};
            }

            @Override
            public ObjectNode parse(Content content) throws ConfigParserException {
                assertThat(content, notNullValue());
                assertThat(content.mediaType(), is("application/hocon"));
                try {
                    assertThat((char) ConfigHelper.createReader(content.asReadable()).read(), is('#'));
                } catch (IOException e) {
                    fail("Cannot read from source's reader");
                }
                return ObjectNode.empty();
            }
        }));
        Optional<ObjectNode> configNode = configSource.load();

        assertThat(configNode, notNullValue());
        assertThat(configNode.isPresent(), is(true));
    }

    @Test
    public void testBuilder() {
        ConfigSource configSource = ConfigSources.file("application.conf").build();

        assertThat(configSource, notNullValue());
    }

    @Test
    public void testBuilderWithMediaType() {
        ConfigSource configSource = ConfigSources.file("application.conf")
                .mediaType("application/hocon")
                .build();

        assertThat(configSource, notNullValue());
    }

    private static String getDir() {
        return Paths.get("").toAbsolutePath().toString() + RELATIVE_PATH_TO_RESOURCE;
    }

    @Disabled("It is been running too long, but could be add into integration tests.")
    @Test
    public void testChangesLong() throws InterruptedException {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("io/helidon/config/application.properties")
                                 .pollingStrategy(PollingStrategies.regular(Duration.ofMillis(5)))
                                 .build())
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        config.changes().subscribe(new Flow.Subscriber<Config>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
            }

            @Override
            public void onNext(Config item) {
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        config.onChange((event) -> true);

        assertThat(latch.await(120, TimeUnit.SECONDS), is(false));

    }

    @Test
    public void testDataTimestamp() throws IOException {
        final String filename = "new-file";
        File file = folder.newFile(filename);
        FileConfigSource fcs = FileConfigSource.builder().path(file.toPath()).build();
        assertThat(fcs.dataStamp().isPresent(), is(true));
        assertThat(fcs.dataStamp().get().length, is(greaterThan(0)));
    }

    @Test
    public void testBuilderPollingStrategy() {
        FileBuilder builder = (FileBuilder) ConfigSources.file("application.conf")
                .pollingStrategy(TestingPathPollingStrategy::new);

        assertThat(builder.pollingStrategyInternal(), instanceOf(TestingPathPollingStrategy.class));
        assertThat(((TestingPathPollingStrategy) builder.pollingStrategyInternal()).getPath(),
                   is(Paths.get("application.conf")));
    }

    private static class TestingPathPollingStrategy implements PollingStrategy {

        private final Path path;

        public TestingPathPollingStrategy(Path path) {
            this.path = path;

            assertThat(path, notNullValue());
        }

        @Override
        public Flow.Publisher<PollingEvent> ticks() {
            return Flow.Subscriber::onComplete;
        }

        public Path getPath() {
            return path;
        }
    }

}
