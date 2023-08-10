/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.webserver.spi.ServerConnectionSelector;

/**
 * Connection provider candidates.
 */
public class ConnectionProviders {
    private final List<ServerConnectionSelector> connectionProviders;

    private final Set<String> supportedAppProtocols;
    private final Map<String, ServerConnectionSelector> providersByAppProtocol;

    private ConnectionProviders(List<ServerConnectionSelector> connectionProviders) {
        this.connectionProviders = connectionProviders;
        Set<String> supportedAppProtocols = new LinkedHashSet<>();
        Map<String, ServerConnectionSelector> byAppProtocol = new HashMap<>();

        for (ServerConnectionSelector provider : connectionProviders) {
            supportedAppProtocols.addAll(provider.supportedApplicationProtocols());
            for (String protocol : provider.supportedApplicationProtocols()) {
                // we need the first one that supports the protocol
                byAppProtocol.putIfAbsent(protocol, provider);
            }
        }
        this.supportedAppProtocols = Set.copyOf(supportedAppProtocols);
        this.providersByAppProtocol = Map.copyOf(byAppProtocol);
    }

    /**
     * Create a new connection providers instance.
     *
     * @param connectionProviders list of providers to use
     * @return a new instance of connection providers
     */
    public static ConnectionProviders create(List<ServerConnectionSelector> connectionProviders) {
        return new ConnectionProviders(connectionProviders);
    }

    /**
     * Get a new (mutable) list of provider candidates.
     *
     * @return provider candidates
     */
    public List<ServerConnectionSelector> providerCandidates() {
        return new ArrayList<>(connectionProviders);
    }

    /**
     * Get a set of supported application protocols (used with ALPN).
     *
     * @return protocols supported
     */
    public Set<String> supportedApplicationProtocols() {
        return supportedAppProtocols;
    }

    /**
     * Get a connection provider for the application protocol.
     *
     * @param protocol protocol id
     * @return connection provider
     */
    public ServerConnectionSelector byApplicationProtocol(String protocol) {
        return providersByAppProtocol.get(protocol);
    }
}
