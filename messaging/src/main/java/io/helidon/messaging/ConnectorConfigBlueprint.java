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

package io.helidon.messaging;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Common connector configuration for one channel direction.
 */
@Api.Preview
@Prototype.Blueprint
@Prototype.Configured
interface ConnectorConfigBlueprint {
    /**
     * Channel name attribute.
     */
    String CHANNEL_NAME_ATTRIBUTE = "channel-name";

    /**
     * Connector name attribute.
     */
    String CONNECTOR_ATTRIBUTE = "connector";

    /**
     * Incoming channel config prefix.
     */
    String INCOMING_PREFIX = "helidon.messaging.incoming.";

    /**
     * Outgoing channel config prefix.
     */
    String OUTGOING_PREFIX = "helidon.messaging.outgoing.";

    /**
     * Connector-level defaults config prefix.
     */
    String CONNECTOR_PREFIX = "helidon.messaging.connector.";

    /**
     * Channel direction.
     *
     * @return direction
     */
    @Option.Configured("direction")
    ConnectorDirection direction();

    /**
     * Channel name.
     *
     * @return channel name
     */
    @Option.Configured(CHANNEL_NAME_ATTRIBUTE)
    String channel();

    /**
     * Connector name.
     *
     * @return connector name
     */
    @Option.Configured(CONNECTOR_ATTRIBUTE)
    String connector();

}
