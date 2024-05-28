/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Suite(MySqlSuite.class)
public class TestMySqlSuite {

    private final SuiteContext suiteContext;
    private final Config config;

    public TestMySqlSuite(SuiteContext suiteContext, Config config) {
        this.suiteContext = suiteContext;
        this.config = config;
    }

    @Test
    public void testInitializationOrder() {
        assertThat(suiteContext, notNullValue());
        assertThat(suiteContext.storage().get(MySqlSuite.SETUP_CONFIG_KEY, Integer.class), is(1));
        assertThat(suiteContext.storage().get(MySqlSuite.SETUP_CONTAINER_KEY, Integer.class), is(2));
        assertThat(suiteContext.storage().get(MySqlSuite.SETUP_DBCLIENT_KEY, Integer.class), is(3));
        assertThat(suiteContext.storage().get(MySqlSuite.BEFORE_KEY, Integer.class), is(4));
    }

    @Test
    public void testConfigFromConstructor() {
        assertThat(config.get("db").asNode().isPresent(), is(true));
        assertThat(config.get("id").asString().isPresent(), is(true));
        assertThat(config.get("id").asString().get(), is("TEST"));
    }

}
