/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Make sure you never call configure or configureRuntime, as that modifies the shape of
 * current JRE without possibility to reverse it.
 */
class SerializationConfigTest {
    @Test
    void testDefaults() throws Exception {
        SerializationConfig serializationConfig = SerializationConfig.builder().build();

        SerializationConfig.ConfigOptions options = serializationConfig.options();
        assertThat(options.traceSerialization(), is(SerializationConfig.TraceOption.NONE));
        // TODO this will change in 3.0.0
        assertThat(options.onNoConfig(), is(SerializationConfig.Action.WARN));
        // TODO this will change in 3.0.0
        assertThat(options.onWrongConfig(), is(SerializationConfig.Action.WARN));
        assertThat(options.filterPattern(), is("!*"));
    }

    @Test
    void testBuilder() {
        SerializationConfig serializationConfig = SerializationConfig.builder()
                .ignoreFiles(true)
                .filterPattern(SerializationConfigTest.class.getName())
                .onNoConfig(SerializationConfig.Action.IGNORE)
                .onWrongConfig(SerializationConfig.Action.CONFIGURE)
                .traceSerialization(SerializationConfig.TraceOption.FULL)
                .build();

        SerializationConfig.ConfigOptions options = serializationConfig.options();
        assertThat(options.traceSerialization(), is(SerializationConfig.TraceOption.FULL));
        assertThat(options.onNoConfig(), is(SerializationConfig.Action.IGNORE));
        assertThat(options.onWrongConfig(), is(SerializationConfig.Action.CONFIGURE));
        assertThat(options.filterPattern(), is(SerializationConfigTest.class.getName() + ";!*"));
    }

    @Test
    void testSysProps() {
        try {
            System.setProperty(SerializationConfig.PROP_PATTERN, SerializationConfigTest.class.getName());
            System.setProperty(SerializationConfig.PROP_NO_CONFIG_ACTION, "ignore");
            System.setProperty(SerializationConfig.PROP_WRONG_CONFIG_ACTION, "configure");
            System.setProperty(SerializationConfig.PROP_TRACE, "full");
            System.setProperty(SerializationConfig.PROP_IGNORE_FILES, "true");

            SerializationConfig serializationConfig = SerializationConfig.builder()
                    .ignoreFiles(true)
                    .filterPattern(SerializationConfigTest.class.getName())
                    .onNoConfig(SerializationConfig.Action.IGNORE)
                    .onWrongConfig(SerializationConfig.Action.CONFIGURE)
                    .traceSerialization(SerializationConfig.TraceOption.FULL)
                    .build();

            SerializationConfig.ConfigOptions options = serializationConfig.options();
            assertThat(options.traceSerialization(), is(SerializationConfig.TraceOption.FULL));
            assertThat(options.onNoConfig(), is(SerializationConfig.Action.IGNORE));
            assertThat(options.onWrongConfig(), is(SerializationConfig.Action.CONFIGURE));
            assertThat(options.filterPattern(), is(SerializationConfigTest.class.getName() + ";!*"));
        } finally {
            Properties properties = System.getProperties();

            properties.remove(SerializationConfig.PROP_PATTERN);
            properties.remove(SerializationConfig.PROP_NO_CONFIG_ACTION);
            properties.remove(SerializationConfig.PROP_WRONG_CONFIG_ACTION);
            properties.remove(SerializationConfig.PROP_TRACE);
            properties.remove(SerializationConfig.PROP_IGNORE_FILES);
        }
    }
}