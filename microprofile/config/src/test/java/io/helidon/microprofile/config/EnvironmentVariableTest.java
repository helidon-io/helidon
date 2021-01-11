/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

final class EnvironmentVariableTest {

    private Config config;

    private EnvironmentVariableTest() {
        super();
    }

    @BeforeEach
    final void installConfig() {
        this.config = ConfigProvider.getConfig();
        assertThat(this.config, notNullValue());
    }

    @Test
    final void testEnvironmentVariableOverridesCamelCase() {
        assertThat(this.config.getValue("camelCase", String.class), is("no"));
    }

    @Test
    final void testEnvironmentVariableOverridesYamlProperty() {
        assertThat(this.config.getValue("yamlProperty", String.class), is("overridden"));
    }

    @Test
    final void testEnvironmentVariableOverridesNestedYamlProperty() {
        assertThat(this.config.getValue("nested.yamlProperty", String.class), is("overridden"));
    }

}
