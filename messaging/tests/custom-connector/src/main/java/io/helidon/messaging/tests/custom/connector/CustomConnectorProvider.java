/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging.tests.custom.connector;

import io.helidon.config.Config;
import io.helidon.messaging.ConnectorDirection;
import io.helidon.messaging.spi.IncomingConnector;
import io.helidon.messaging.spi.IncomingConnectorProvider;
import io.helidon.messaging.spi.OutgoingConnector;
import io.helidon.messaging.spi.OutgoingConnectorProvider;
import io.helidon.service.registry.Service;

@Service.Singleton
final class CustomConnectorProvider implements IncomingConnectorProvider,
                                               OutgoingConnectorProvider {
    static final String CONNECTOR_TYPE = "test-custom";

    private final CustomConnectorBroker broker;
    private final CustomConnectorProbe probe;

    @Service.Inject
    CustomConnectorProvider(CustomConnectorBroker broker, CustomConnectorProbe probe) {
        this.broker = broker;
        this.probe = probe;
    }

    @Override
    public String connectorType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public IncomingConnector createIncomingConnector(Config config) {
        return createConnector(config).incoming(config).orElseThrow();
    }

    @Override
    public OutgoingConnector createOutgoingConnector(Config config) {
        return createConnector(config).outgoing(config).orElseThrow();
    }

    private static ConnectorDirection direction(Config config) {
        return ConnectorDirection.valueOf(config.get("direction").asString().orElseThrow());
    }

    private CustomConnector createConnector(Config config) {
        ConnectorDirection direction = direction(config);
        String channel = config.get("channel-name").asString().orElseThrow();
        String connectorType = config.get("connector").asString().orElseThrow();
        CustomConnector connector = CustomConnector.builder()
                .name(connectorType)
                .broker(broker)
                .probe(probe)
                .config(config)
                .build();
        probe.configured(direction, channel, connectorType, connector.endpoint(), connector.prefix());
        return connector;
    }
}
