/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.config;

import io.helidon.common.config.Config;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestConfig {
    @Test
    public void testConfig() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        System.setProperty("io.helidon.service.tests.config.TestConfig.port", "8888");

        Config registryConfig = registry.get(Config.class);
        Config defaultConfig = Config.create();

        assertThat("Config.create()",
                   defaultConfig.get("io.helidon.service.tests.config.TestConfig.port").asInt().get(),
                   is(8888));
        assertThat("registry.get(Config.class)",
                   registryConfig.get("io.helidon.service.tests.config.TestConfig.port").asInt().get(),
                   is(8888));

    }
}
