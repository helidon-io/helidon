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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;

import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigException;
import io.helidon.config.OverrideSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.internal.ClasspathOverrideSource.ClasspathBuilder;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollingStrategy;

import org.hamcrest.core.Is;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ClasspathOverrideSource}.
 */
public class ClasspathOverrideSourceTest {

    @Test
    public void testDescriptionMandatory() {
        OverrideSource overrideSource = OverrideSources.classpath("overrides.properties").build();

        assertThat(overrideSource.description(), is("ClasspathOverride[overrides.properties]"));
    }

    @Test
    public void testDescriptionOptional() {
        OverrideSource overrideSource = OverrideSources.classpath("overrides.properties").optional().build();

        assertThat(overrideSource.description(), is("ClasspathOverride[overrides.properties]?"));
    }

    @Test
    public void testLoadNotExists() {
        ClasspathOverrideSource overrideSource = (ClasspathOverrideSource) OverrideSources.classpath("application.unknown")
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            overrideSource.load();
        });
        assertTrue(instanceOf(ConfigException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("Cannot load data from mandatory source"));
    }

    @Test
    public void testLoadExists() {
        OverrideSource overrideSource = OverrideSources.classpath("io/helidon/config/overrides.properties")
                .build();

        Optional<OverrideSource.OverrideData> objectNode = overrideSource.load();

        assertThat(objectNode, notNullValue());
        assertThat(objectNode.isPresent(), is(true));
    }

    @Test
    public void testBuilder() {
        OverrideSource overrideSource = OverrideSources.classpath("overrides.properties").build();

        assertThat(overrideSource, notNullValue());
    }

    @Test
    public void testBuilderWithMediaType() {
        OverrideSource overrideSource = OverrideSources.classpath("io/helidon/config/overrides.properties")
                .build();

        assertThat(overrideSource, notNullValue());
    }

    @Test
    public void testBuilderPollingStrategyNotExistingResource() {
        ClasspathBuilder builder = (ClasspathBuilder) OverrideSources.classpath("not-exists")
                .pollingStrategy(TestingPathPollingStrategy::new);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            assertThat(builder.getPollingStrategyInternal(), Is.is(PollingStrategies.nop()));
        });
        assertTrue(ex.getMessage().startsWith("Could not find a filesystem path for resource 'not-exists'"));
    }

    @Test
    public void testBuilderPollingStrategyExistingResource() throws URISyntaxException {
        ClasspathBuilder builder = (ClasspathBuilder) OverrideSources.classpath("io/helidon/config/overrides.properties")
                .pollingStrategy(TestingPathPollingStrategy::new);

        assertThat(builder.getPollingStrategyInternal(), instanceOf(TestingPathPollingStrategy.class));
        assertThat(((TestingPathPollingStrategy) builder.getPollingStrategyInternal()).getPath(),
                   is(ClasspathSourceHelper.resourcePath("io/helidon/config/overrides.properties")));
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
