/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh2171;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigurationTest {
    private static Configuration configuration;

    @BeforeAll
    static void createInstance() {
        configuration = Configuration.create();
    }

    @Test
    void testSameKey() {
        assertThat(configuration.value("value"), optionalValue(is("yaml")));
    }

    @Test
    void testYamlPresent() {
        assertThat(configuration.value("yaml"), optionalValue(is("yaml")));
    }

    @Test
    void testYmlNotPresent() {
        assertThat(configuration.value("yml"), optionalEmpty());
    }

}