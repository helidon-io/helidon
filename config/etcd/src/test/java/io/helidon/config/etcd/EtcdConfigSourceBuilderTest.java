/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.etcd;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.MetaConfig;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.PollingStrategyProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link EtcdConfigSourceBuilder}.
 */
public class EtcdConfigSourceBuilderTest {

    @Test
    public void testBuilderSuccessful() {
        EtcdConfigSource etcdConfigSource = EtcdConfigSource.builder()
                .uri(URI.create("http://localhost:2379"))
                .key("/registry")
                .api(EtcdApi.v2)
                .mediaType("my/media/type")
                .build();

        assertThat(etcdConfigSource, notNullValue());
    }

    @Test
    public void testBuilderWithoutUri() {
        assertThrows(IllegalArgumentException.class, () -> {
            EtcdConfigSource.builder()
                    .uri(null)
                    .key("/registry")
                    .api(EtcdApi.v2)
                    .mediaType("my/media/type")
                    .parser(ConfigParsers.properties())
                    .build();
        });
    }

    @Test
    public void testBuilderWithoutKey() {
        assertThrows(IllegalArgumentException.class, () -> {
            EtcdConfigSource.builder()
                    .uri(URI.create("http://localhost:2379"))
                    .key(null)
                    .api(EtcdApi.v2)
                    .mediaType("my/media/type")
                    .parser(ConfigParsers.properties())
                    .build();
        });
    }

    @Test
    public void testBuilderWithoutVersion() {
        assertThrows(IllegalArgumentException.class, () -> {
            EtcdConfigSource.builder()
                    .uri(URI.create("http://localhost:2379"))
                    .key("/registry")
                    .api(null)
                    .mediaType("my/media/type")
                    .parser(ConfigParsers.properties())
                    .build();
        });
    }

    @Test
    public void testEtcdConfigSourceDescription() {
        assertThat(EtcdConfigSource.builder()
                           .uri(URI.create("http://localhost:2379"))
                           .key("/registry")
                           .api(EtcdApi.v2)
                           .mediaType("my/media/type")
                           .parser(ConfigParsers.properties())
                           .build().description(),
                   is("EtcdConfig[http://localhost:2379#/registry]"));
    }

    @Test
    public void testPollingStrategy() {
        URI uri = URI.create("http://localhost:2379");
        EtcdConfigSourceBuilder builder = EtcdConfigSource.builder()
                .uri(uri)
                .key("/registry")
                .api(EtcdApi.v2)
                .pollingStrategy(new TestingEtcdEndpointPollingStrategy());
    }

    @Test
    public void testFromConfigNothing() {
        assertThrows(IllegalArgumentException.class, () -> {
            EtcdConfigSource.create(Config.empty());
        });
    }

    @Test
    public void testFromConfigAll() {
        EtcdConfigSourceBuilder builder = EtcdConfigSource.builder()
                .config(Config.create(ConfigSources.create(Map.of(
                        "uri", "http://localhost:2379",
                        "key", "/registry",
                        "api", "v3"))));

        assertThat(builder.target().uri(), is(URI.create("http://localhost:2379")));
        assertThat(builder.target().key(), is("/registry"));
        assertThat(builder.target().api(), is(EtcdApi.v3));
    }

    @Test
    public void testFromConfigWithCustomPollingStrategy() {
        EtcdConfigSourceBuilder builder = EtcdConfigSource.builder()
                .config(Config.create(ConfigSources.create(Map.of(
                        "uri", "http://localhost:2379",
                        "key", "/registry",
                        "api", "v3",
                        "polling-strategy.type", TestingEtcdPollingStrategyProvider.TYPE))));

        assertThat(builder.target().uri(), is(URI.create("http://localhost:2379")));
        assertThat(builder.target().key(), is("/registry"));
        assertThat(builder.target().api(), is(EtcdApi.v3));
    }

    @Test
    public void testFromConfigEtcdWatchPollingStrategy() {
        EtcdConfigSourceBuilder builder = EtcdConfigSource.builder()
                .config(Config.create(ConfigSources.create(Map.of(
                        "uri", "http://localhost:2379",
                        "key", "/registry",
                        "api", "v3",
                        "change-watcher.type", EtcdWatcherProvider.TYPE))));

        assertThat(builder.target().uri(), is(URI.create("http://localhost:2379")));
        assertThat(builder.target().key(), is("/registry"));
        assertThat(builder.target().api(), is(EtcdApi.v3));
    }

    @Test
    public void testSourceFromConfigByClass() {
        Config metaConfig = Config.create(ConfigSources.create(ObjectNode.builder()
                                                                       .addValue("type", "etcd")
                                                                       .addObject("properties", ObjectNode.builder()
                                                                               .addValue("uri", "http://localhost:2379")
                                                                               .addValue("key", "/registry")
                                                                               .addValue("api", "v3")
                                                                               .build())
                                                                       .build()));

        ConfigSource source = MetaConfig.configSource(metaConfig).get(0);

        assertThat(source, is(instanceOf(EtcdConfigSource.class)));

        EtcdConfigSource etcdSource = (EtcdConfigSource) source;
        assertThat(etcdSource.etcdEndpoint().uri(), is(URI.create("http://localhost:2379")));
        assertThat(etcdSource.etcdEndpoint().key(), is("/registry"));
        assertThat(etcdSource.etcdEndpoint().api(), is(EtcdApi.v3));
    }

    @Test
    public void testSourceFromConfigByType() {
        Config metaConfig = Config.create(ConfigSources.create(ObjectNode.builder()
                                                                       .addValue("type", "etcd")
                                                                       .addObject("properties", ObjectNode.builder()
                                                                               .addValue("uri", "http://localhost:2379")
                                                                               .addValue("key", "/registry")
                                                                               .addValue("api", "v3")
                                                                               .build())
                                                                       .build()));

        ConfigSource source = MetaConfig.configSource(metaConfig).get(0);

        assertThat(source.get(), is(instanceOf(EtcdConfigSource.class)));

        EtcdConfigSource etcdSource = (EtcdConfigSource) source;
        assertThat(etcdSource.etcdEndpoint().uri(), is(URI.create("http://localhost:2379")));
        assertThat(etcdSource.etcdEndpoint().key(), is("/registry"));
        assertThat(etcdSource.etcdEndpoint().api(), is(EtcdApi.v3));
    }

    public static class TestingEtcdPollingStrategyProvider implements PollingStrategyProvider {
        private static final String TYPE = "etcd-testing";

        @Override
        public boolean supports(String type) {
            return TYPE.equals(type);
        }

        @Override
        public PollingStrategy create(String type, Config metaConfig) {
            return new TestingEtcdEndpointPollingStrategy();
        }

        @Override
        public Set<String> supported() {
            return Set.of(TYPE);
        }
    }

    public static class TestingEtcdEndpointPollingStrategy implements PollingStrategy {
        TestingEtcdEndpointPollingStrategy() {
        }

        @Override
        public void start(Polled polled) {
        }
    }

}
