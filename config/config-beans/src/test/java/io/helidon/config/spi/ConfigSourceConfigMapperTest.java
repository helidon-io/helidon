/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.beans.Value;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

/**
 * Tests {@link ConfigSourceConfigMapper}.
 */
public class ConfigSourceConfigMapperTest {
    @Test
    public void testCustomClass() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSource.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource::from).get();

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(23));
        assertThat(config.get("enabled").asBoolean(), is(true));
    }

    @Test
    public void testCustomClassBuilder() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource::from).get();

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(23));
        assertThat(config.get("enabled").asBoolean(), is(true));
    }

    @Test
    public void testCustomType() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "testing1")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource::from).get();

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(23));
        assertThat(config.get("enabled").asBoolean(), is(true));
    }

    @Test
    public void testCustomTypeBuilder() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("type", "testing2")
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource::from).get();

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        Config config = justFrom(source);

        assertThat(config.get("key1").asInt(), is(23));
        assertThat(config.get("enabled").asBoolean(), is(true));
    }

    static Config justFrom(ConfigSource source) {
        return Config.withSources(source).disableEnvironmentVariablesSource().disableSystemPropertiesSource().build();
    }

    /**
     * Testing implementation of config source.
     */
    public static class MyConfigSource implements ConfigSource {

        private final MyEndpoint endpoint;
        private final boolean myProp3;
        private final PollingStrategy pollingStrategy;
        private final RetryPolicy retryPolicy;

        public MyConfigSource(MyEndpoint endpoint,
                              boolean myProp3,
                              PollingStrategy pollingStrategy,
                              RetryPolicy retryPolicy) {
            this.endpoint = endpoint;
            this.myProp3 = myProp3;
            this.pollingStrategy = pollingStrategy;
            this.retryPolicy = retryPolicy;
        }

        public static MyConfigSource from(@Value(key = "myProp1") String myProp1,
                                          @Value(key = "myProp2") int myProp2,
                                          @Value(key = "myProp3") boolean myProp3) {
            return new MyConfigSource(new MyEndpoint(myProp1, myProp2), myProp3, null, null);
        }

        @Override
        public Optional<ObjectNode> load() throws ConfigException {
            return Optional.of(ObjectNode.builder()
                                       .addValue(endpoint.myProp1, Objects.toString(endpoint.myProp2))
                                       .addValue("enabled", Objects.toString(myProp3))
                                       .build());
        }

        public PollingStrategy getPollingStrategy() {
            return pollingStrategy;
        }

        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }

        @Override
        public String toString() {
            return "MyConfigSource{"
                    + "endpoint=" + endpoint
                    + ", myProp3=" + myProp3
                    + '}';
        }
    }

    /**
     * Testing implementation of config source builder.
     */
    public static class MyConfigSourceBuilder
            extends AbstractSource.Builder<MyConfigSourceBuilder, MyEndpoint, ConfigSource> {

        private final MyEndpoint endpoint;
        private boolean myProp3;

        private MyConfigSourceBuilder(MyEndpoint endpoint) {
            super(MyEndpoint.class);
            this.endpoint = endpoint;
        }

        public static MyConfigSourceBuilder from(String myProp1, int myProp2) {
            return new MyConfigSourceBuilder(new MyEndpoint(myProp1, myProp2));
        }

        public static MyConfigSourceBuilder from(Config metaConfig) throws ConfigMappingException, MissingValueException {
            return from(metaConfig.get("myProp1").asString().get(),
                        metaConfig.get("myProp2").asInt().get())
                    .init(metaConfig);
        }

        @Override
        protected MyConfigSourceBuilder init(Config metaConfig) {
            metaConfig.get("myProp3").asBoolean().ifPresent(this::myProp3);
            return super.init(metaConfig);
        }

        public MyConfigSourceBuilder myProp3(boolean myProp3) {
            this.myProp3 = myProp3;
            return this;
        }

        @Override
        protected MyEndpoint getTarget() {
            return endpoint;
        }

        public ConfigSource build() {
            return new MyConfigSource(endpoint, myProp3, getPollingStrategy(), getRetryPolicy());
        }
    }

    /**
     * Testing implementation of config source endpoint.
     */
    public static class MyEndpoint {
        private final String myProp1;
        private final int myProp2;

        public MyEndpoint(String myProp1, int myProp2) {
            this.myProp1 = myProp1;
            this.myProp2 = myProp2;
        }

        public String getMyProp1() {
            return myProp1;
        }

        public int getMyProp2() {
            return myProp2;
        }

        @Override
        public String toString() {
            return "MyEndpoint{"
                    + "myProp1='" + myProp1 + '\''
                    + ", myProp2=" + myProp2
                    + '}';
        }
    }

}
