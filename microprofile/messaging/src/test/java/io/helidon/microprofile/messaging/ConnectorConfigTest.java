/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConnectorConfigTest {
    private static final String TEST_CHANNEL_VALUE = "test-channel-value";
    private static final String TEST_CONNECTOR_VALUE = "test-connector-value";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_CHANNEL_NAME = "test-channel";
    private static final String TEST_CONNECTOR_NAME = "test-connector";

    Map<String, String> propMap = Map.of(
            "mp.messaging.outgoing." + TEST_CHANNEL_NAME + ".connector", TEST_CONNECTOR_NAME,
            "mp.messaging.outgoing." + TEST_CHANNEL_NAME + "." + TEST_KEY, TEST_CHANNEL_VALUE,
            "mp.messaging.connector." + TEST_CONNECTOR_NAME + "." + TEST_KEY, TEST_CONNECTOR_VALUE
    );

    @Test
    void testChannelPropsPrecedent() {
        Config rootConfig = Config.builder()
                .sources(ConfigSources.create(propMap))
                .build();

        ConfigurableConnector connector = new ConfigurableConnector() {
            @Override
            public String getConnectorName() {
                return TEST_CONNECTOR_NAME;
            }

            @Override
            public Config getRootConfig() {
                return rootConfig;
            }

            @Override
            public Config getChannelsConfig() {
                return rootConfig.get("mp.messaging.outgoing");
            }
        };

        org.eclipse.microprofile.config.Config connectorConfig = connector.getConnectorConfig(TEST_CHANNEL_NAME);
        assertEquals(TEST_CHANNEL_VALUE, connectorConfig.getValue(TEST_KEY, String.class));
    }
}
