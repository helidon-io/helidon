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

package io.helidon.config.etcd;

import java.net.URI;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.config.Config;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdApi;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

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
        EtcdConfigSource etcdConfigSource = EtcdConfigSourceBuilder
                .create(URI.create("http://localhost:2379"), "/registry", EtcdApi.v2)
                .mediaType("my/media/type")
                .build();

        assertThat(etcdConfigSource, notNullValue());
    }

    @Test
    public void testBuilderWithoutUri() {
        assertThrows(NullPointerException.class, () -> {
        EtcdConfigSourceBuilder
                .create(null, "/registry", EtcdApi.v2)
                .mediaType("my/media/type")
                .parser(ConfigParsers.properties())
                .build();
        });
    }

    @Test    public void testBuilderWithoutKey() {
        assertThrows(NullPointerException.class, () -> {
        EtcdConfigSourceBuilder
                .create(URI.create("http://localhost:2379"), null, EtcdApi.v2)
                .mediaType("my/media/type")
                .parser(ConfigParsers.properties())
                .build();
        });
    }

    @Test
    public void testBuilderWithoutVersion() {
        assertThrows(NullPointerException.class, () -> {
        EtcdConfigSourceBuilder
                .create(URI.create("http://localhost:2379"), "/registry", null)
                .mediaType("my/media/type")
                .parser(ConfigParsers.properties())
                .build();
        });
    }

    @Test
    public void testEtcdConfigSourceDescription() {
        assertThat(EtcdConfigSourceBuilder
                           .create(URI.create("http://localhost:2379"), "/registry", EtcdApi.v2)
                           .mediaType("my/media/type")
                           .parser(ConfigParsers.properties())
                           .build().description(),
                   is("EtcdConfig[http://localhost:2379#/registry]"));
    }

    @Test
    public void testPollingStrategy() {
        URI uri = URI.create("http://localhost:2379");
        EtcdConfigSourceBuilder builder = EtcdConfigSourceBuilder
                .create(uri, "/registry", EtcdApi.v2)
                .pollingStrategy(TestingEtcdEndpointPollingStrategy::new);

        assertThat(builder.pollingStrategyInternal(), is(instanceOf(TestingEtcdEndpointPollingStrategy.class)));
        EtcdEndpoint strategyEndpoint = ((TestingEtcdEndpointPollingStrategy) builder.pollingStrategyInternal())
                .etcdEndpoint();

        assertThat(strategyEndpoint.uri(), is(uri));
        assertThat(strategyEndpoint.key(), is("/registry"));
        assertThat(strategyEndpoint.api(), is(EtcdApi.v2));
    }

    @Test
    public void testFromConfigNothing() {
        assertThrows(MissingValueException.class, () -> {
            EtcdConfigSourceBuilder.create(Config.empty());
        });
    }

    @Test
    public void testFromConfigAll() {
        EtcdConfigSourceBuilder builder = EtcdConfigSourceBuilder.create(Config.create(ConfigSources.create(CollectionsHelper.mapOf(
                "uri", "http://localhost:2379",
                "key", "/registry",
                "api", "v3"))));

        assertThat(builder.target().uri(), is(URI.create("http://localhost:2379")));
        assertThat(builder.target().key(), is("/registry"));
        assertThat(builder.target().api(), is(EtcdApi.v3));
    }

    @Test
    public void testFromConfigWithCustomPollingStrategy() {
        EtcdConfigSourceBuilder builder = EtcdConfigSourceBuilder.create(Config.create(ConfigSources.create(CollectionsHelper.mapOf(
                "uri", "http://localhost:2379",
                "key", "/registry",
                "api", "v3",
                "polling-strategy.class", TestingEtcdEndpointPollingStrategy.class.getName()))));

        assertThat(builder.target().uri(), is(URI.create("http://localhost:2379")));
        assertThat(builder.target().key(), is("/registry"));
        assertThat(builder.target().api(), is(EtcdApi.v3));

        assertThat(builder.pollingStrategyInternal(), is(instanceOf(TestingEtcdEndpointPollingStrategy.class)));
        EtcdEndpoint strategyEndpoint = ((TestingEtcdEndpointPollingStrategy) builder.pollingStrategyInternal())
                .etcdEndpoint();

        assertThat(strategyEndpoint.uri(), is(URI.create("http://localhost:2379")));
        assertThat(strategyEndpoint.key(), is("/registry"));
        assertThat(strategyEndpoint.api(), is(EtcdApi.v3));
    }

    @Test
    public void testFromConfigEtcdWatchPollingStrategy() {
        EtcdConfigSourceBuilder builder = EtcdConfigSourceBuilder.create(Config.create(ConfigSources.create(CollectionsHelper.mapOf(
                "uri", "http://localhost:2379",
                "key", "/registry",
                "api", "v3",
                "polling-strategy.class", EtcdWatchPollingStrategy.class.getName()))));

        assertThat(builder.target().uri(), is(URI.create("http://localhost:2379")));
        assertThat(builder.target().key(), is("/registry"));
        assertThat(builder.target().api(), is(EtcdApi.v3));

        assertThat(builder.pollingStrategyInternal(), is(instanceOf(EtcdWatchPollingStrategy.class)));
        EtcdEndpoint strategyEndpoint = ((EtcdWatchPollingStrategy) builder.pollingStrategyInternal())
                .etcdEndpoint();

        assertThat(strategyEndpoint.uri(), is(URI.create("http://localhost:2379")));
        assertThat(strategyEndpoint.key(), is("/registry"));
        assertThat(strategyEndpoint.api(), is(EtcdApi.v3));
    }

    @Test
    public void testSourceFromConfigByClass() {
        Config metaConfig = Config.create(ConfigSources.create(ObjectNode.builder()
                                                                   .addValue("class", EtcdConfigSource.class.getName())
                                                                   .addObject("properties", ObjectNode.builder()
                                                                           .addValue("uri", "http://localhost:2379")
                                                                           .addValue("key", "/registry")
                                                                           .addValue("api", "v3")
                                                                           .build())
                                                                   .build()));

        ConfigSource source = metaConfig.as(ConfigSource::create).get();

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

        ConfigSource source = metaConfig.as(ConfigSource::create).get();

        assertThat(source.get(), is(instanceOf(EtcdConfigSource.class)));

        EtcdConfigSource etcdSource = (EtcdConfigSource) source;
        assertThat(etcdSource.etcdEndpoint().uri(), is(URI.create("http://localhost:2379")));
        assertThat(etcdSource.etcdEndpoint().key(), is("/registry"));
        assertThat(etcdSource.etcdEndpoint().api(), is(EtcdApi.v3));
    }

    public static class TestingEtcdEndpointPollingStrategy implements PollingStrategy {
        private final EtcdEndpoint etcdEndpoint;

        public TestingEtcdEndpointPollingStrategy(EtcdEndpoint etcdEndpoint) {
            this.etcdEndpoint = etcdEndpoint;

            assertThat(etcdEndpoint, notNullValue());
        }

        @Override
        public Flow.Publisher<PollingEvent> ticks() {
            return Flow.Subscriber::onComplete;
        }

        public EtcdEndpoint etcdEndpoint() {
            return etcdEndpoint;
        }
    }

}
