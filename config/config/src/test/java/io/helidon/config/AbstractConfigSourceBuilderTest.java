/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;
import io.helidon.config.spi.Source;
import io.helidon.config.spi.WatchableSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AbstractConfigSourceBuilderTest {
    @Test
    void testDefaults() {
        FullSourceBuilder builder = new FullSourceBuilder();
        assertThat(builder.isOptional(), is(false));
        assertThat(builder.mediaType(), is(Optional.empty()));
        assertThat(builder.changeWatcher(), is(Optional.empty()));
        assertThat(builder.pollingStrategy(), is(Optional.empty()));
        assertThat(builder.parser(), is(Optional.empty()));
        assertThat(builder.mediaTypeMapping(), is(Optional.empty()));
        assertThat(builder.parserMapping(), is(Optional.empty()));
        assertThat(builder.retryPolicy(), is(Optional.empty()));
    }

    @Test
    void testConfigured() {
        String mediaType = "application/json";
        FileSystemWatcher watcher = FileSystemWatcher.create();
        PollingStrategy pollingStrategy = PollingStrategies.nop();
        ConfigParser parser = ConfigParsers.properties();
        Function<Config.Key, Optional<String>> mediaTypeMapping = key -> Optional.empty();
        Function<Config.Key, Optional<ConfigParser>> parserMapping = key -> Optional.empty();
        RetryPolicy retryPolicy = RetryPolicies.justCall();

        FullSourceBuilder builder = new FullSourceBuilder()
                .optional(true)
                .mediaType(mediaType)
                .changeWatcher(watcher)
                .pollingStrategy(pollingStrategy)
                .parser(parser)
                .mediaTypeMapping(mediaTypeMapping)
                .parserMapping(parserMapping)
                .retryPolicy(retryPolicy);

        assertThat(builder.isOptional(), is(true));
        assertThat(builder.mediaType(), is(Optional.of(mediaType)));
        assertThat(builder.changeWatcher(), is(Optional.of(watcher)));
        assertThat(builder.pollingStrategy(), is(Optional.of(pollingStrategy)));
        assertThat(builder.parser(), is(Optional.of(parser)));
        assertThat(builder.mediaTypeMapping(), is(Optional.of(mediaTypeMapping)));
        assertThat(builder.parserMapping(), is(Optional.of(parserMapping)));
        assertThat(builder.retryPolicy(), is(Optional.of(retryPolicy)));
    }

    // this is simply a compilation test, to make sure the base implements all possible methods
    private static final class FullSourceBuilder extends AbstractConfigSourceBuilder<FullSourceBuilder, Path>
            implements PollableSource.Builder<FullSourceBuilder>,
                       WatchableSource.Builder<FullSourceBuilder, Path>,
                       ParsableSource.Builder<FullSourceBuilder>,
                       Source.Builder<FullSourceBuilder> {
        @Override
        public FullSourceBuilder parser(ConfigParser parser) {
            return super.parser(parser);
        }

        @Override
        public FullSourceBuilder mediaType(String mediaType) {
            return super.mediaType(mediaType);
        }

        @Override
        public FullSourceBuilder changeWatcher(ChangeWatcher<Path> changeWatcher) {
            return super.changeWatcher(changeWatcher);
        }

        @Override
        public FullSourceBuilder pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }
    }

}
