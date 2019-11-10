/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging.connector;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.microprofile.messaging.AdHocConfigBuilder;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;

import javax.enterprise.inject.spi.DeploymentException;

public interface ConfigurableConnector {

    String getConnectorName();

    Config getRootConfig();

    Config getChannelsConfig();

    default org.eclipse.microprofile.config.Config getConnectorConfig(String channelName) {
        Config channelConfig = getChannelsConfig()
                .get(channelName);
        ConfigValue<String> connectorName = channelConfig
                .get("connector")
                .asString();

        if (!connectorName.isPresent()) {
            throw new DeploymentException("No connector configured for channel " + channelName);
        }
        if (!connectorName.get().equals(getConnectorName())) {
            throw new DeploymentException("Connector name miss match for channel" + channelName);
        }

        Config connectorConfig = getRootConfig()
                .get("mp.messaging.connector")
                .get(connectorName.get());

        return AdHocConfigBuilder
                .from(channelConfig)
                //It seams useless but its required by the spec
                .put(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, channelName)
                .putAll(connectorConfig)
                .build();
    }
}
