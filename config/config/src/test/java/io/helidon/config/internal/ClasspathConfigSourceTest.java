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

package io.helidon.config.internal;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.internal.ClasspathConfigSource.ClasspathBuilder;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

import org.hamcrest.core.Is;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link ClasspathConfigSource}.
 */
public class ClasspathConfigSourceTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";

    @Test
    public void testDescriptionMandatory() {
        ConfigSource configSource = ConfigSources.classpath("application.conf").build();

        assertThat(configSource.description(), is("ClasspathConfig[application.conf]"));
    }

    @Test
    public void testDescriptionOptional() {
        ConfigSource configSource = ConfigSources.classpath("application.conf").optional().build();

        assertThat(configSource.description(), is("ClasspathConfig[application.conf]?"));
    }

    @Test
    public void testGetMediaTypeSet() {
        ClasspathConfigSource configSource = (ClasspathConfigSource) ConfigSources.classpath("application.conf")
                .optional()
                .mediaType(TEST_MEDIA_TYPE)
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.getMediaType(), is(TEST_MEDIA_TYPE));
    }

    @Test
    public void testGetMediaTypeGuessed() {
        ClasspathConfigSource configSource = (ClasspathConfigSource) ConfigSources.classpath("application.properties")
                .optional()
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.getMediaType(), Is.is("text/x-java-properties"));
    }

    @Test
    public void testGetMediaTypeUnknown() {
        ClasspathConfigSource configSource = (ClasspathConfigSource) ConfigSources.classpath("application.unknown")
                .optional()
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.getMediaType(), is(nullValue()));
    }

    @Test
    public void testLoadNotExists() {
        ClasspathConfigSource configSource = (ClasspathConfigSource) ConfigSources.classpath("application.unknown")
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            configSource.init(mock(ConfigContext.class));
            configSource.load();
        });
        
        assertTrue(instanceOf(ConfigException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("Cannot load data from mandatory source"));
    }

    @Test
    public void testLoadExists() {
        ConfigSource configSource = ConfigSources.classpath("io/helidon/config/application.conf")
                .mediaType("application/hocon")
                .build();

        configSource.init(content -> Optional.of(new ConfigParser() {
            @Override
            public Set<String> getSupportedMediaTypes() {
                return new HashSet<String>() {{
                    add("application/hocon");
                }};
            }

            @Override
            public ObjectNode parse(Content content) throws ConfigParserException {
                assertThat(content, notNullValue());
                assertThat(content.getMediaType(), is("application/hocon"));
                try {
                    assertThat((char) ConfigHelper.createReader(content.asReadable()).read(), is('#'));
                } catch (IOException e) {
                    fail("Cannot read from source's reader");
                }
                return ObjectNode.empty();
            }
        }));
        Optional<ObjectNode> objectNode = configSource.load();

        assertThat(objectNode, notNullValue());
        assertThat(objectNode.isPresent(), is(true));
    }

    @Test
    public void testBuilder() {
        ConfigSource configSource = ConfigSources.classpath("application.conf").build();

        assertThat(configSource, notNullValue());
    }

    @Test
    public void testBuilderWithMediaType() {
        ConfigSource configSource = ConfigSources.classpath("io/helidon/config/application.conf")
                .mediaType("application/hocon")
                .build();

        assertThat(configSource, notNullValue());
    }

    @Test
    public void testBuilderPollingStrategyNotExistingResource() {
        ClasspathBuilder builder = (ClasspathBuilder) ConfigSources.classpath("not-exists")
                .pollingStrategy(TestingPathPollingStrategy::new);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            assertThat(builder.getPollingStrategyInternal(), is(PollingStrategies.nop()));
        });
        assertTrue(ex.getMessage().startsWith("Could not find a filesystem path for resource 'not-exists'"));
        
    }

    @Test
    public void testBuilderPollingStrategyExistingResource() throws URISyntaxException {
        ClasspathBuilder builder = (ClasspathBuilder) ConfigSources.classpath("io/helidon/config/application.conf")
                .pollingStrategy(TestingPathPollingStrategy::new);

        assertThat(builder.getPollingStrategyInternal(), instanceOf(TestingPathPollingStrategy.class));
        assertThat(((TestingPathPollingStrategy) builder.getPollingStrategyInternal()).getPath(),
                   is(ClasspathSourceHelper.resourcePath("io/helidon/config/application.conf")));
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
