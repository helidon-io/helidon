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

package io.helidon.config.spi;

import java.time.Duration;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.internal.RetryPolicyImpl;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSourceConfigMapperTest.MyConfigSource;
import io.helidon.config.spi.ConfigSourceConfigMapperTest.MyConfigSourceBuilder;
import static io.helidon.config.spi.ConfigSourceConfigMapperTest.justFrom;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link RetryPolicyConfigMapper}.
 */
public class RetryPolicyConfigMapperTest {

    @Test
    public void testRepeatNotComplete() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("retry-policy", ObjectNode.builder()
                                        .addValue("type", "repeat")
                                        .build())
                                .build())
                        .build()));

        ConfigMappingException ex = assertThrows(ConfigMappingException.class, () -> {
                metaConfig.as(ConfigSource.class);
        });
        assertTrue(instanceOf(MissingValueException.class).matches(ex.getCause()));
    }

    @Test
    public void testRepeatMandatory() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("retry-policy", ObjectNode.builder()
                                        .addValue("type", "repeat")
                                        .addObject("properties", ObjectNode.builder()
                                                .addValue("retries", "3")
                                                .build())
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        assertThat(((MyConfigSource) source).getRetryPolicy(),
                   is(instanceOf(RetryPolicyImpl.class)));

        RetryPolicyImpl policy = (RetryPolicyImpl) ((MyConfigSource) source).getRetryPolicy();
        assertThat(policy.getRetries(), is(3));
        assertThat(policy.getDelay(), is(Duration.ofMillis(200)));
        assertThat(policy.getDelayFactor(), is(2.0));
        assertThat(policy.getCallTimeout(), is(Duration.ofMillis(500)));
        assertThat(policy.getOverallTimeout(), is(Duration.ofSeconds(2)));
    }

    @Test
    public void testRepeatAll() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("retry-policy", ObjectNode.builder()
                                        .addValue("type", "repeat")
                                        .addObject("properties", ObjectNode.builder()
                                                .addValue("retries", "3")
                                                .addValue("delay", "PT2S")
                                                .addValue("delay-factor", "1.5")
                                                .addValue("call-timeout", "PT0.75S")
                                                .addValue("overall-timeout", "PT7S")
                                                .build())
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        assertThat(((MyConfigSource) source).getRetryPolicy(),
                   is(instanceOf(RetryPolicyImpl.class)));

        RetryPolicyImpl policy = (RetryPolicyImpl) ((MyConfigSource) source).getRetryPolicy();
        assertThat(policy.getRetries(), is(3));
        assertThat(policy.getDelay(), is(Duration.ofSeconds(2)));
        assertThat(policy.getDelayFactor(), is(1.5));
        assertThat(policy.getCallTimeout(), is(Duration.ofMillis(750)));
        assertThat(policy.getOverallTimeout(), is(Duration.ofSeconds(7)));
    }

    @Test
    public void testCustomClass() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("retry-policy", ObjectNode.builder()
                                        .addValue("class", TestingRetryPolicy.class.getName())
                                        .addObject("properties", ObjectNode.builder()
                                                .addValue("retries", "3")
                                                .addValue("timeout", "PT2S")
                                                .build())
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        assertThat(((MyConfigSource) source).getRetryPolicy(),
                   is(instanceOf(TestingRetryPolicy.class)));

        TestingRetryPolicy policy = (TestingRetryPolicy) ((MyConfigSource) source).getRetryPolicy();
        assertThat(policy.getRetries(), is(3));
        assertThat(policy.getTimeout(), is(Duration.ofSeconds(2)));
    }

    @Test
    public void testCustomBuilder() {
        Config metaConfig = justFrom(ConfigSources.from(
                ObjectNode.builder()
                        .addValue("class", MyConfigSourceBuilder.class.getName())
                        .addObject("properties", ObjectNode.builder()
                                .addValue("myProp1", "key1")
                                .addValue("myProp2", "23")
                                .addValue("myProp3", "true")
                                .addObject("retry-policy", ObjectNode.builder()
                                        .addValue("class", TestingRetryPolicyBuilder.class.getName())
                                        .addObject("properties", ObjectNode.builder()
                                                .addValue("retries", "3")
                                                .addValue("timeout", "PT2S")
                                                .build())
                                        .build())
                                .build())
                        .build()));

        ConfigSource source = metaConfig.as(ConfigSource.class);

        assertThat(source, is(instanceOf(MyConfigSource.class)));

        assertThat(((MyConfigSource) source).getRetryPolicy(),
                   is(instanceOf(TestingRetryPolicy.class)));

        TestingRetryPolicy policy = (TestingRetryPolicy) ((MyConfigSource) source).getRetryPolicy();
        assertThat(policy.getRetries(), is(3));
        assertThat(policy.getTimeout(), is(Duration.ofSeconds(2)));
    }

    public static class TestingRetryPolicy implements RetryPolicy {
        private final int retries;
        private final Duration timeout;

        private TestingRetryPolicy(int retries, Duration timeout) {
            this.retries = retries;
            this.timeout = timeout;
        }

        public static TestingRetryPolicy from(Config metaConfig) {
            return new TestingRetryPolicy(metaConfig.get("retries").asInt(),
                                          metaConfig.get("timeout").as(Duration.class));
        }

        public int getRetries() {
            return retries;
        }

        public Duration getTimeout() {
            return timeout;
        }

        @Override
        public <T> T execute(Supplier<T> call) {
            return call.get();
        }
    }

    public static class TestingRetryPolicyBuilder {
        private final int retries;
        private final Duration timeout;

        private TestingRetryPolicyBuilder(int retries, Duration timeout) {
            this.retries = retries;
            this.timeout = timeout;
        }

        public static TestingRetryPolicyBuilder from(Config metaConfig) {
            return new TestingRetryPolicyBuilder(metaConfig.get("retries").asInt(),
                                                 metaConfig.get("timeout").as(Duration.class));
        }

        public TestingRetryPolicy build() {
            return new TestingRetryPolicy(retries, timeout);
        }
    }

}
