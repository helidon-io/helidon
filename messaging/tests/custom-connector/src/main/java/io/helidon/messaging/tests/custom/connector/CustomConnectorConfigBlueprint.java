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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.messaging.spi.MessagingConnectorProvider;
import io.helidon.messaging.spi.MessagingConnectorProviderConfig;

/**
 * Custom connector configuration.
 */
@Prototype.Blueprint(isPublic = false)
@Prototype.Sealed
@Prototype.Configured(value = CustomConnectorProvider.CONNECTOR_TYPE, root = false)
@Prototype.Provides(MessagingConnectorProvider.class)
interface CustomConnectorConfigBlueprint extends MessagingConnectorProviderConfig, Prototype.Factory<CustomConnector> {

    /**
     * In-memory transport endpoint.
     *
     * @return endpoint
     */
    @Option.Configured
    String endpoint();

    /**
     * Test message prefix.
     *
     * @return prefix
     */
    @Option.Configured
    @Option.Default("custom-prefix")
    String prefix();

    /**
     * In-memory broker.
     *
     * @return broker
     */
    CustomConnectorBroker broker();

    /**
     * Test observation probe.
     *
     * @return probe
     */
    CustomConnectorProbe probe();
}
