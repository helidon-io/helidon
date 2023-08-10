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

package io.helidon.webserver.websocket;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.webserver.ProtocolConfigs;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class WsUpgradeProviderConfigTest {

    // Verify that WsUpgrader is properly configured from config file
    @Test
    void testConnectionConfig()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {

        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();

        WsProtocolConfigProvider provider = new WsProtocolConfigProvider();
        WsConfig wsConfig = provider.create(config.get(provider.configKey()), "@default");

        WsUpgrader upgrader = (WsUpgrader) new WsUpgradeProvider().create(wsConfig, ProtocolConfigs.create(List.of()));

        Set<String> origins = upgrader.origins();
        assertThat(origins, containsInAnyOrder("origin1", "origin2", "origin3"));

    }

    // Verify that WsUpgrader is properly configured from builder
    @Test
    void testUpgraderConfigBuilder() {
        WsUpgrader upgrader = WsUpgrader.create(
                WsConfig.builder()
                        .name("@default")
                        .addOrigin("bOrigin1")
                        .addOrigin("bOrigin2")
                        .build());

        Set<String> origins = upgrader.origins();
        assertThat(origins, containsInAnyOrder("bOrigin1", "bOrigin2"));
    }
}
