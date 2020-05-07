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
package io.helidon.messaging;

import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.config.spi.ConfigSource;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;

final class ConnectorConfigHelper {

    private ConnectorConfigHelper() {
    }

    static ConfigValue<String> getIncomingConnectorName(Config config, String channelName) {
        //Looks suspicious but incoming connector configured for outgoing channel is ok
        return config.get(ConnectorFactory.OUTGOING_PREFIX)
                .get(channelName)
                .get(ConnectorFactory.CONNECTOR_ATTRIBUTE)
                .asString();
    }

    static ConfigValue<String> getOutgoingConnectorName(Config config, String channelName) {
        //Looks suspicious but outgoing connector configured for incoming channel is ok
        return config.get(ConnectorFactory.INCOMING_PREFIX)
                .get(channelName)
                .get(ConnectorFactory.CONNECTOR_ATTRIBUTE)
                .asString();
    }

    static ConfigSource prefixedConfigSource(String prefix, Config config) {
        return ConfigSources.create(config
                .detach()
                .asMap()
                .orElse(Map.of())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> prefix + "." + e.getKey(), Map.Entry::getValue)))
                .build();
    }

    static org.eclipse.microprofile.config.Config getConnectorConfig(String channelName,
                                                                     String connectorName,
                                                                     Config rootConfig) {

        Config incomingChannelConfig = rootConfig.get("mp.messaging.incoming");
        Config outgoingChannelConfig = rootConfig.get("mp.messaging.outgoing");

        Config channelsConfig = (Config) ConnectorConfigBuilder
                .create(incomingChannelConfig)
                .putAll(outgoingChannelConfig)
                .build();

        Config channelConfig = channelsConfig
                .get(channelName);

        ConfigValue<String> configConnectorName = channelConfig
                .get("connector")
                .asString();

        if (!configConnectorName.isPresent()) {
            throw new MessagingException(String
                    .format("No connector configured for channel %s", channelName));
        }
        if (!configConnectorName.get().equals(connectorName)) {
            throw new MessagingException(String
                    .format("Connector name miss match for channel%s", channelName));
        }

        Config connectorConfig = rootConfig
                .get("mp.messaging.connector")
                .get(configConnectorName.get());

        return ConfigProviderResolver
                .instance()
                .getBuilder()
                .withSources(
                        MpConfigSources.create(ConnectorConfigBuilder
                                .create(connectorConfig)
                                .put(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, channelName)
                                .putAll(channelConfig)
                                .build()))
                .build();
    }
}
