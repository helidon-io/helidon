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

package io.helidon.config.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Flow;

import io.helidon.config.ConfigException;
import io.helidon.config.OverrideSources;
import io.helidon.config.internal.FileOverrideSource.FileBuilder;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.test.infra.TemporaryFolderExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link FileOverrideSource}.
 */
public class FileOverrideSourceTest {

    private static final String RELATIVE_PATH_TO_RESOURCE = "/src/test/resources/";


    @RegisterExtension
    static TemporaryFolderExt folder = TemporaryFolderExt.build();;

    private static String getDir() {
        return Paths.get("").toAbsolutePath().toString() + RELATIVE_PATH_TO_RESOURCE;
    }

    @Test
    public void testDescriptionMandatory() {
        OverrideSource overrideSource = OverrideSources.file("overrides.properties").build();

        assertThat(overrideSource.description(), is("FileOverride[overrides.properties]"));
    }

    @Test
    public void testDescriptionOptional() {
        OverrideSource overrideSource = OverrideSources.file("overrides.properties").optional().build();

        assertThat(overrideSource.description(), is("FileOverride[overrides.properties]?"));
    }

    @Test
    public void testLoadNotExists() {
        FileOverrideSource overrideSource = (FileOverrideSource) OverrideSources.file("overrides.properties")
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        ConfigException ex = assertThrows(ConfigException.class, overrideSource::load);
        assertThat(ex.getCause(), instanceOf(ConfigException.class));
        assertThat(ex.getMessage(), startsWith("Cannot load data from mandatory source"));
    }

    @Test
    public void testLoadExists() {
        OverrideSource overrideSource = OverrideSources.file(getDir() + "io/helidon/config/overrides.properties")
                .build();

        Optional<OverrideSource.OverrideData> configNode = overrideSource.load();

        assertThat(configNode, notNullValue());
        assertThat(configNode.isPresent(), is(true));
    }

    @Test
    public void testBuilder() {
        OverrideSource overrideSource = OverrideSources.file("overrides.properties").build();

        assertThat(overrideSource, notNullValue());
    }

    @Test
    public void testDataTimestamp() throws IOException {
        final String filename = "new-file";
        FileOverrideSource fcs = FileOverrideSource.builder()
                .path(Paths.get(filename))
                .build();
        assertThat(fcs.dataStamp(), is(not(Instant.now())));
    }

    @Test
    public void testBuilderPollingStrategy() {
        FileBuilder builder = (FileBuilder) OverrideSources.file("overrides.properties")
                .pollingStrategy(TestingPathPollingStrategy::new);

        assertThat(builder.pollingStrategyInternal(), instanceOf(TestingPathPollingStrategy.class));
        assertThat(((TestingPathPollingStrategy) builder.pollingStrategyInternal()).getPath(),
                   is(Paths.get("overrides.properties")));
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
