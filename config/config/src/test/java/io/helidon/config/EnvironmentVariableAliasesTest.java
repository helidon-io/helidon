/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.config;

import org.junit.jupiter.api.Test;

import static io.helidon.config.EnvironmentVariableAliases.aliasesOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for class {@link EnvironmentVariableAliases}.
 */
public class EnvironmentVariableAliasesTest {

    @Test
    public void testAliases() {
        assertThat(aliasesOf("app").size(), is(1));
        assertThat(aliasesOf("app"), contains("APP"));

        assertThat(aliasesOf("app.name").size(), is(2));
        assertThat(aliasesOf("app.name"), contains("app_name", "APP_NAME"));

        assertThat(aliasesOf("app/name").size(), is(2));
        assertThat(aliasesOf("app/name"), contains("app_name", "APP_NAME"));

        assertThat(aliasesOf("app/camelCase").size(), is(2));
        assertThat(aliasesOf("app/camelCase"), contains("app_camelCase", "APP_CAMELCASE"));

        assertThat(aliasesOf("app.qualified-name").size(), is(3));
        assertThat(aliasesOf("app.qualified-name"),
                   contains("app_qualified_dash_name",
                            "APP_QUALIFIED_dash_NAME",
                            "APP_QUALIFIED_DASH_NAME"));

        assertThat(aliasesOf("app/qualified-name").size(), is(3));
        assertThat(aliasesOf("app/qualified-name"),
                   contains("app_qualified_dash_name",
                            "APP_QUALIFIED_dash_NAME",
                            "APP_QUALIFIED_DASH_NAME"));

        assertThat(aliasesOf("app/qualified-camelCaseName").size(), is(3));
        assertThat(aliasesOf("app/qualified-camelCaseName"),
                   contains("app_qualified_dash_camelCaseName",
                            "APP_QUALIFIED_dash_CAMELCASENAME",
                            "APP_QUALIFIED_DASH_CAMELCASENAME"));

        assertThat(aliasesOf("app.page-size").size(), is(3));
        assertThat(aliasesOf("app.page-size"), contains("app_page_dash_size", "APP_PAGE_dash_SIZE", "APP_PAGE_DASH_SIZE"));
    }
}
