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
import java.util.Set;

import io.helidon.config.changes.FileChangeWatcher;
import io.helidon.config.parsers.PropertiesConfigParser;
import io.helidon.config.sources.ClasspathConfigSource;
import io.helidon.config.sources.FileConfigSource;
import io.helidon.config.sources.MapConfigSource;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.Content;
import io.helidon.config.spi.RetryPolicy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class ConfigTest {
    @Test
    void testSourcesWithSetup() {
        ConfigSource cpSource = ClasspathConfigSource.builder()
                .resource("classpath.properties")
                .parser(new ConfigParser() {
                    @Override
                    public Set<String> supportedMediaTypes() {
                        return Set.of(PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES);
                    }

                    @Override
                    public ConfigNode.ObjectNode parse(Content.ParsableContent content) throws ConfigParserException {
                        return ConfigNode.ObjectNode.builder()
                                .addValue("value", "Parser properties")
                                .build();
                    }
                })
                .build();

        ConfigSource fileSource = FileConfigSource.builder()
                .filePath(Paths.get("not-there-at-all.properties"))
                .optional(true)
                .build();

        ConfigSource fileWithRetryAndWatching = FileConfigSource.builder()
                .filePath(Paths.get("file.properties"))
                .retryPolicy(new RetryPolicy() { })
                .changeWatcher(FileChangeWatcher.create())
                .build();

        Config config = Config.create(List.of(cpSource, fileSource, fileWithRetryAndWatching));

        assertThat(config.get("value").getValue(), is(Optional.of("Parser properties")));
        assertThat(config.get("random").getValue(), is(Optional.empty()));
    }

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

    @Test
    void testEmpty() {
        Config config = Config.create(Set.of());

        assertThat(config.getValue(), is(Optional.empty()));
        assertThat(config.get("random").getValue(), is(Optional.empty()));
    }

    @Test
    void testTree() {
        ConfigSource sysProps = MapConfigSource.create(System.getProperties());
        ConfigSource mapSource = MapConfigSource.create(Map.of("user.dir", "mydir",
                                                               "user.home", "myhome",
                                                               "ssl", "true",
                                                               "ssl.protocol", "TLS",
                                                               "ssl.protocol.version", "1.2",
                                                               "ssl.protocol.name", "TLS_v1.2"));

        Config config = Config.create(List.of(mapSource, sysProps));
        Config user = config.get("user");
        assertThat(user.get("dir").getValue(), is(Optional.of("mydir")));
        assertThat(config.get("user.dir").getValue(), is(Optional.of("mydir")));
        assertThat(user.get("home").getValue(), is(Optional.of("myhome")));
        assertThat(config.get("user.home").getValue(), is(Optional.of("myhome")));

        Config ssl = config.get("ssl");
        assertThat(ssl.getValue(), is(Optional.of("true")));
        Config sslProtocol = ssl.get("protocol");
        Config sslProtocolFromRoot = config.get("ssl.protocol");
        assertThat(sslProtocolFromRoot, sameInstance(sslProtocol));
        assertThat(sslProtocol.getValue(), is(Optional.of("TLS")));
        assertThat(sslProtocol.get("version").getValue(), is(Optional.of("1.2")));
        assertThat(sslProtocol.get("name").getValue(), is(Optional.of("TLS_v1.2")));
    }

}