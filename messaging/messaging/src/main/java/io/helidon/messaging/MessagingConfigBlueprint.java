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

import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.messaging.spi.MessagingConnector;
import io.helidon.messaging.spi.MessagingConnectorProvider;

/**
 * Messaging configuration.
 */
@Prototype.Blueprint
@Prototype.Configured("messaging")
@Prototype.RegistrySupport
@Api.Preview
interface MessagingConfigBlueprint {
    /**
     * Messaging execution configuration.
     *
     * @return execution configuration
     */
    @Option.Configured
    MessagingExecutionConfig execution();

    /**
     * Configured messaging connectors.
     *
     * @return configured connectors
     */
    @Option.Configured
    @Option.Provider(value = MessagingConnectorProvider.class,
                     discoverServices = false,
                     configForm = Option.Provider.ConfigForm.OBJECT_OR_LIST)
    @Option.Singular
    List<MessagingConnector> connector();

    /**
     * Incoming channel configurations, keyed by channel name.
     *
     * @return incoming channel configurations
     */
    @Option.Configured
    Map<String, Config> incoming();

    /**
     * Outgoing channel configurations, keyed by channel name.
     *
     * @return outgoing channel configurations
     */
    @Option.Configured
    Map<String, Config> outgoing();
}
