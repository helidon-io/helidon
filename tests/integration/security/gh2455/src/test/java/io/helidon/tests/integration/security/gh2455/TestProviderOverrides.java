/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.gh2455;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.Security;

import org.junit.jupiter.api.Test;

class TestProviderOverrides {
    @Test
    void testOverride() {
        Map<String, String> map = Map.of(
                "security.providers.1.type", "header-atn",
                "security.providers.1.header-atn.authenticate", "false"
        );
        Config config = Config.builder()
                .addSource(ConfigSources.create(map))
                .addSource(ConfigSources.classpath("application.yaml"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Security security = Security.create(config.get("security"));
    }
}
