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

package io.helidon.messaging.spi;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.messaging.MessagingExecutionConfig;

/**
 * Common configuration of an incoming or outgoing messaging channel connection.
 */
@Api.Preview
@Prototype.Blueprint(decorator = MessagingChannelConfigSupport.BuilderDecorator.class,
                     builderPublic = false,
                     createEmptyPublic = false,
                     createFromConfigPublic = false)
@Prototype.Configured
interface MessagingChannelConfigBlueprint {
    /**
     * Name of the configured connector instance used by this channel connection.
     *
     * @return connector instance name
     */
    @Option.Required
    @Option.Configured
    String connector();

    /**
     * Logical messaging channel name. Configuration-based channels derive this name from their map entry.
     * Programmatic channel configurations supply the name explicitly.
     *
     * @return logical channel name
     */
    @Option.Required
    String channelName();

    /**
     * Execution overrides for this channel. Values not configured inherit the messaging defaults.
     *
     * @return channel execution overrides
     */
    @Option.Configured
    @Option.DefaultMethod("create")
    MessagingExecutionConfig execution();

    /**
     * Original channel configuration node, including connector-specific options.
     *
     * @return original configuration, if configured
     */
    Optional<Config> config();
}
