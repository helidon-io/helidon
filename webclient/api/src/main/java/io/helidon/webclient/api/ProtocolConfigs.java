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

package io.helidon.webclient.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.webclient.spi.ProtocolConfig;

/**
 * Protocol configuration to obtain explicitly configured details for the current socket.
 */
class ProtocolConfigs {
    private final List<ProtocolConfig> protocolConfigs;

    private ProtocolConfigs(List<ProtocolConfig> protocolConfigs) {
        this.protocolConfigs = List.copyOf(protocolConfigs);
    }

    /**
     * Create new protocol configuration handler.
     *
     * @param protocolConfigs all available protocol configurations.
     * @return protocol configuration handler
     */
    public static ProtocolConfigs create(List<ProtocolConfig> protocolConfigs) {
        Objects.requireNonNull(protocolConfigs);
        return new ProtocolConfigs(protocolConfigs);
    }

    /**
     * Get a protocol configuration if defined.
     *
     * @param protocolType       type of the protocol, to distinguish between protocols
     *                           (two protocols may use the same configuration class)
     * @param protocolConfigType type of the expected configuration (same protocol type may use different configuration
     *                           class)
     * @param <T>                type of the expected protocol configuration
     * @return protocol configuration(s) to use, if empty, this protocol should not be used
     */
    public <T extends ProtocolConfig> T config(String protocolType,
                                               Class<T> protocolConfigType,
                                               Supplier<T> defaultConfigSupplier) {
        Objects.requireNonNull(protocolType);
        Objects.requireNonNull(protocolConfigType);

        return protocolConfigs.stream()
                .filter(it -> protocolType.equals(it.type()))
                .filter(it -> protocolConfigType.isAssignableFrom(it.getClass()))
                .map(protocolConfigType::cast)
                .findFirst()
                .orElseGet(defaultConfigSupplier);
    }
}
