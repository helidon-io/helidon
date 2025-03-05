/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class ProviderTest {

    private static DataRegistry data = null;
    private static DataConfig dataConfig;

    @BeforeAll
    public static void setup() {
        Config config = Config.just(ConfigSources.classpath("test.yaml"));
        dataConfig = DataConfig.create(config.get("data"));
        data = DataRegistry.create(dataConfig);
    }

    @Test
    void testConfig() {
        DataConfig repositoryConfig = data.dataConfig();
        assertThat(repositoryConfig, sameInstance(dataConfig));
        assertThat(dataConfig.name(), is("some-name"));

        ProviderConfig providerConfig = dataConfig.provider();
        assertThat(providerConfig, instanceOf(TestConfig.class));
        assertThat(providerConfig.name(), is("test"));
        assertThat(providerConfig.type(), is("test"));

        TestConfig testConfig = (TestConfig) providerConfig;

        io.helidon.common.config.Config config = testConfig.config();
        assertThat(config.get("key").asString().get(), is("value"));
        assertThat(config.get("other").asString().get(), is("another"));
    }

}
