/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.testing.tests.junit5;

import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;
import io.helidon.testing.TestConfig;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/*
This test validates that all possible configuration options are correctly loaded.
It also validates that weight of each annotation is handled correctly
 */
@TestConfig.Value(key = "test.first", value = "first")
@TestConfig.Value(key = "test.second", value = "second")
@TestConfig.Block("""
        test.first=wrong
        test.third=third
        test.fourth=fourth
        """)
@TestConfig.File("test-config.properties")
@Testing.Test
public class Junit5Test {
    private static Config config;

    @TestConfig.Source
    public static ConfigSource customSourceSix() {
        return ConfigSources.create(Map.of("test.fifth", "wrong",
                                           "test.sixth", "sixth")).build();
    }

    @TestConfig.Source
    public static ConfigSource customSourceSeven() {
        return ConfigSources.create(Map.of("test.seventh", "seventh")).build();
    }

    @BeforeAll
    public static void beforeAll() {
        TestConfig.set("test.eighth", "eighth");
        config = GlobalConfig.config();
    }

    @Test
    public void testValueConfig() {
        assertThat(config.get("test.first").asString().get(), is("first"));
        assertThat(config.get("test.second").asString().get(), is("second"));
    }

    @Test
    public void testValuesConfig() {
        assertThat(config.get("test.third").asString().get(), is("third"));
        assertThat(config.get("test.fourth").asString().get(), is("fourth"));
    }

    @Test
    public void testFileConfig() {
        assertThat(config.get("test.fifth").asString().get(), is("fifth"));
    }

    @Test
    public void testSourceConfigSix() {
        assertThat(config.get("test.sixth").asString().get(), is("sixth"));
    }

    @Test
    public void testSourceConfigSeven() {
        assertThat(config.get("test.seventh").asString().get(), is("seventh"));
    }

    @Test
    public void testStaticConfig() {
        assertThat(config.get("test.eighth").asString().get(), is("eighth"));
    }
}
