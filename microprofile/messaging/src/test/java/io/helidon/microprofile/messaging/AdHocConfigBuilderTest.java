/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class AdHocConfigBuilderTest {

    private static final String TEST_TOPIC_CONFIG = "TEST_TOPIC_CONFIG";
    private static final String TEST_TOPIC_CUSTOM = "TEST_TOPIC_CUSTOM";
    private static final String TEST_KEY = "TEST_KEY";
    private static final String ADDITION_ATTR_1 = "addition-attr1";
    private static final String ADDITION_ATTR_2 = "addition-attr2";
    private static final String ADDITION_ATTR_1_VALUE = "addition-attr1-value";
    private static final String ADDITION_ATTR_2_VALUE = "addition-attr2-value";
    private static final String TEST_CONNECTOR = "test-connector";

    @Test
    void currentContext() {
        Map<String, String> propMap = Map.of(
                "mp.messaging.outcoming.test-channel.key.serializer", AdHocConfigBuilderTest.class.getName()
        );

        Config config = Config.builder()
                .sources(ConfigSources.create(propMap))
                .build();

        org.eclipse.microprofile.config.Config c = AdHocConfigBuilder
                .from(config.get("mp.messaging.outcoming.test-channel"))
                .put(TEST_KEY, TEST_TOPIC_CUSTOM)
                .build();

        assertThat(c.getValue(TEST_KEY, String.class), is(TEST_TOPIC_CUSTOM));
        assertThat(c.getValue("key.serializer", String.class), is(AdHocConfigBuilderTest.class.getName()));
    }

    @Test
    void customValueOverride() {
        Map<String, String> propMap = Map.of(
                "mp.messaging.outcoming.test-channel." + TEST_KEY, TEST_TOPIC_CONFIG,
                "mp.messaging.outcoming.test-channel.key.serializer", AdHocConfigBuilderTest.class.getName()
        );

        Config config = Config.builder()
                .sources(ConfigSources.create(propMap))
                .build();

        org.eclipse.microprofile.config.Config c = AdHocConfigBuilder
                .from(config.get("mp.messaging.outcoming.test-channel"))
                .put(TEST_KEY, TEST_TOPIC_CUSTOM)
                .build();

        assertThat(c.getValue(TEST_KEY, String.class), is(TEST_TOPIC_CUSTOM));
    }

    @Test
    void putAllTest() {
        Map<String, String> propMap = Map.of(
                "mp.messaging.outcoming.test-channel." + TEST_KEY, TEST_TOPIC_CONFIG
        );

        Map<String, String> propMap2 = Map.of(
                "mp.messaging.connector." + TEST_CONNECTOR + "." + ADDITION_ATTR_1, ADDITION_ATTR_1_VALUE,
                "mp.messaging.connector." + TEST_CONNECTOR + "." + ADDITION_ATTR_2, ADDITION_ATTR_2_VALUE
        );

        Config config = Config.builder(ConfigSources.create(propMap))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        Config config2 = Config.builder(ConfigSources.create(propMap2))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        org.eclipse.microprofile.config.Config c = AdHocConfigBuilder
                .from(config.get("mp.messaging.outcoming.test-channel"))
                .putAll(config2.get("mp.messaging.connector." + TEST_CONNECTOR))
                .build();

        assertThat(c.getValue(ADDITION_ATTR_1, String.class), is(ADDITION_ATTR_1_VALUE));
        assertThat(c.getValue(ADDITION_ATTR_2, String.class), is(ADDITION_ATTR_2_VALUE));
    }
}