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

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.config.NamedService;

/**
 * A configured messaging connector capable of creating channel-specific connector instances.
 * <p>
 * An implementation must support at least one direction by overriding {@link #incoming(Config)},
 * {@link #outgoing(Config)}, or both. Each successful invocation must return a fresh, unstarted channel connector.
 */
@Api.Preview
public interface MessagingConnector extends NamedService {
    /**
     * Configuration used to create this connector.
     *
     * @return connector configuration
     */
    MessagingConnectorProviderConfig prototype();

    @Override
    default String name() {
        return prototype().name();
    }

    /**
     * Create an incoming connector for one channel.
     *
     * @param channelConfig channel-specific configuration
     * @return incoming connector, or empty if this connector does not support incoming channels
     */
    default Optional<IncomingConnector> incoming(Config channelConfig) {
        Objects.requireNonNull(channelConfig);
        return Optional.empty();
    }

    /**
     * Create an outgoing connector for one channel.
     *
     * @param channelConfig channel-specific configuration
     * @return outgoing connector, or empty if this connector does not support outgoing channels
     */
    default Optional<OutgoingConnector> outgoing(Config channelConfig) {
        Objects.requireNonNull(channelConfig);
        return Optional.empty();
    }
}
