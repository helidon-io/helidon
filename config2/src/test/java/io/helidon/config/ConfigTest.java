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

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.sources.ClasspathConfigSource;
import io.helidon.config.sources.FileConfigSource;
import io.helidon.config.spi.ConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ConfigTest {
    @Test
    void testConfig() {
        Config config = Config.create(List.of(
                lazySource(Map.of("only-lazy", "Lazy")),
                ClasspathConfigSource.create("classpath.properties"),
                FileConfigSource.create(Paths.get("file.properties"))));

        assertThat(config.get("value").getValue(), is(Optional.of("Classpath properties")));
        assertThat(config.get("only-file").getValue(), is(Optional.of("File")));
        assertThat(config.get("only-lazy").getValue(), is(Optional.of("Lazy")));
        assertThat(config.get("random").getValue(), is(Optional.empty()));
    }

    private ConfigSource lazySource(Map<String, String> properties) {
        return new ConfigSource.LazySource() {
            @Override
            public Optional<ConfigNode> node(Key key) {
                String keyString = key.toString();

                return Optional.ofNullable(properties.get(keyString))
                        .map(ValueNodeImpl::create);
            }
        };
    }
}