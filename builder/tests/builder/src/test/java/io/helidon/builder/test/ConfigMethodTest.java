/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.ConfigMethod;
import io.helidon.builder.test.testsubjects.ConfigOptionalMethod;
import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigMethodTest {
    private static Config config;

    @BeforeAll
    static void beforeAll() {
        config = Config.just(ConfigSources.classpath("config-method-test.yaml"));
    }

    @Test
    void testConfig() {
        ConfigMethod configMethod = ConfigMethod.create(config);
        assertThat(configMethod.config(), is(config));
        assertThat(configMethod.key(), is("value"));
    }
    @Test
    void testOptionalConfig() {
        ConfigOptionalMethod configMethod = ConfigOptionalMethod.create(config);
        assertThat(configMethod.config(), optionalValue(is(config)));
        assertThat(configMethod.key(), is("value"));
    }

    @Test
    void testOptionalConfigMissing() {
        ConfigOptionalMethod configMethod = ConfigOptionalMethod.create();
        assertThat(configMethod.config(), optionalEmpty());
        assertThat(configMethod.key(), is("default-value"));
    }

    @Test
    void testConfigMissing() {
        Errors.ErrorMessagesException fail = assertThrows(Errors.ErrorMessagesException.class,
                                                                            () -> ConfigMethod.builder().build());
        assertThat(fail.getMessages(), hasSize(1));
        assertThat(fail.getMessage(), containsString("\"config\" must not be null"));
    }
}
