/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se.config;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.media.type.MediaType;
import io.helidon.config.Config;
import io.helidon.config.FileConfigSource;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;

@SuppressWarnings("ALL")
class ExtensionsSnippets {

    // stub
    class MySource implements ConfigSource {

        static MySource create() {
            return null;
        }
    }

    // stub
    class MyFilter implements ConfigFilter {

        static MyFilter create() {
            return null;
        }

        @Override
        public String apply(Config.Key key, String stringValue) {
            return null;
        }
    }

    // stub
    class MyChangeWatcher implements ChangeWatcher<Path> {

        static MyChangeWatcher create() {
            return null;
        }

        @Override
        public void start(Path target, Consumer<ChangeEvent<Path>> listener) {

        }

        @Override
        public Class<Path> type() {
            return null;
        }
    }

    // stub
    class MyPollingStrategy implements PollingStrategy {

        static MyPollingStrategy create() {
            return null;
        }

        @Override
        public void start(Polled polled) {
        }
    }

    // stub
    class MyConfigParser implements ConfigParser {

        static MyConfigParser create() {
            return null;
        }

        @Override
        public Set<MediaType> supportedMediaTypes() {
            return null;
        }

        @Override
        public ConfigNode.ObjectNode parse(Content content) throws ConfigParserException {
            return null;
        }
    }

    // stub
    class MyRetryPolicy implements RetryPolicy {

        static MyRetryPolicy create() {
            return null;
        }

        @Override
        public <T> T execute(Supplier<T> call) {
            return null;
        }
    }

    // stub
    class MyOverrides implements Supplier<OverrideSource> {

        static MyOverrides create() {
            return null;
        }

        @Override
        public OverrideSource get() {
            return null;
        }
    }

    void snippet_1() {
        // tag::snippet_1[]
        Config config = Config.builder()
                .addSource(FileConfigSource.builder()
                                   .changeWatcher(MyChangeWatcher.create())
                                   .pollingStrategy(MyPollingStrategy.create())
                                   .parser(MyConfigParser.create())
                                   .retryPolicy(MyRetryPolicy.create()))
                .addSource(MySource.create())
                .addFilter(MyFilter.create())
                .overrides(MyOverrides.create())
                .build();
        // end::snippet_1[]
    }

    interface Snippet2 {
        // tag::snippet_2[]
        boolean supports(String type);

        ConfigSource create(String type, Config metaConfig);
        // end::snippet_2[]
    }

}
