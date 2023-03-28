/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.common.config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmptyConfigTest {
    private final Config emptyConfig = Config.empty();

    @Test
    void testEmptyInstance() {
        assertThat(emptyConfig, notNullValue());
        assertThat(emptyConfig.exists(), is(false));
        assertThat(emptyConfig.isLeaf(), is(false));
        assertThat(emptyConfig.isList(), is(false));
        assertThat(emptyConfig.isObject(), is(false));
        assertThat(emptyConfig.hasValue(), is(false));

        Config.Key key = emptyConfig.key();
        assertThat("Key should be root", key.isRoot(), is(true));
        assertThat(key.name(), is(""));
        assertThat(key.toString(), is(""));

        Config config = emptyConfig.get("some.path");
        assertThat(config.exists(), is(false));
        assertThat(config.isLeaf(), is(false));
        assertThat(config.isList(), is(false));
        assertThat(config.isObject(), is(false));
        assertThat(config.hasValue(), is(false));

        key = config.key();
        assertThat("Key should not be root", key.isRoot(), is(false));
        assertThat(key.name(), is("path"));
        assertThat(key.toString(), is("some.path"));

        key = key.parent();
        assertThat("Key should not be root", key.isRoot(), is(false));
        assertThat(key.name(), is("some"));
        assertThat(key.toString(), is("some"));

        key = key.parent();
        assertThat("Key should be root", key.isRoot(), is(true));
        assertThat(key.name(), is(""));
        assertThat(key.toString(), is(""));

        assertThrows(IllegalStateException.class, key::parent);
    }

    @Test
    void testEmptyInstanceValues() {
        testValue("Boolean", "", emptyConfig.asBoolean());
        testValue("String", "", emptyConfig.asString());
        testValue("EmptyConfigTest", "", emptyConfig.as(EmptyConfigTest.class));

        Config config = emptyConfig.get("some.path");
        testValue("Boolean", "some.path", config.asBoolean());
        testValue("String", "some.path", config.asString());
        testValue("EmptyConfigTest", "some.path", config.as(EmptyConfigTest.class));
    }

    private static void testValue(String type, String path, ConfigValue<?> value) {
        assertThat("Path for type: " + type, value.key().toString(), is(path));
        assertThat("Is present for type: " + type, value.isPresent(), is(false));
        assertThat("Is present for type: " + type, value.orElse(null), nullValue());
    }
}
