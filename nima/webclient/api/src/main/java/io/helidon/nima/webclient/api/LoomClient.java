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

package io.helidon.nima.webclient.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.inject.configdriven.api.ConfigDriven;
import io.helidon.nima.webclient.spi.HttpClientSpi;
import io.helidon.nima.webclient.spi.HttpClientSpiProvider;
import io.helidon.nima.webclient.spi.Protocol;
import io.helidon.nima.webclient.spi.ProtocolConfig;
import io.helidon.nima.webclient.spi.ProtocolProvider;

import jakarta.inject.Inject;

/**
 * Base class for HTTP implementations of {@link WebClient}.
 */
@SuppressWarnings("rawtypes")
@ConfigDriven(WebClientConfigBlueprint.class)
class LoomClient implements WebClient {
    static final LazyValue<ExecutorService> EXECUTOR = LazyValue.create(() -> {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                          .name("helidon-client-")
                                                          .factory());
    });
    private static final List<HttpClientSpiProvider> PROVIDERS =
            HelidonServiceLoader.create(ServiceLoader.load(HttpClientSpiProvider.class))
                    .asList();
    private static final Map<String, HttpClientSpiProvider> PROVIDERS_BY_PROTOCOL;

    static {
        Map<String, HttpClientSpiProvider> providerMap = new HashMap<>();
        PROVIDERS.forEach(it -> providerMap.put(it.protocolId(), it));
        PROVIDERS_BY_PROTOCOL = Map.copyOf(providerMap);
    }

    private final WebClientConfig config;
    // a map of protocol ids to the client SPI implementing them
    private final Map<String, ProtocolSpi> protocolsToClients;
    private final List<ProtocolSpi> protocols;
    private final List<ProtocolSpi> tcpProtocols;
    private final ProtocolConfigs protocolConfigs;
    private final List<String> tcpProtocolIds;
    private final WebClientCookieManager cookieManager;

    /**
     * Construct this instance from a subclass of builder.
     *
     * @param config builder the subclass is built from
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject
    protected LoomClient(WebClientConfig config) {
        this.config = config;
        this.protocolConfigs = ProtocolConfigs.create(config.protocolConfigs());
        this.cookieManager = config.cookieManager().orElseGet(() -> WebClientCookieManager.builder().build());

        List<HttpClientSpiProvider> providers;
        List<String> protocolPreference = config.protocolPreference();
        if (protocolPreference.isEmpty()) {
            // use the discovered ones
            providers = new ArrayList<>(PROVIDERS);
        } else {
            providers = new ArrayList<>();
            for (String protocol : protocolPreference) {
                HttpClientSpiProvider spi = PROVIDERS_BY_PROTOCOL.get(protocol);
                if (spi == null) {
                    throw new IllegalStateException("Requested protocol \"" + protocol + "\" is not available on classpath");
                }
                providers.add(spi);
            }
        }
        if (providers.isEmpty()) {
            throw new IllegalStateException("WebClient requires at least one protocol provider to be present on classpath,"
                                                    + " or configured through protocolPreference (such as http1)");
        }

        Map<String, ProtocolSpi> clients = new HashMap<>();
        List<ProtocolSpi> protocols = new ArrayList<>();
        List<ProtocolSpi> tcpProtocols = new ArrayList<>();
        for (HttpClientSpiProvider provider : providers) {
            Object protocolConfig = protocolConfigs.config(provider.protocolId(),
                                                           provider.configType(),
                                                           () -> (ProtocolConfig) provider.defaultConfig());

            HttpClientSpi clientSpi = (HttpClientSpi) provider.protocol(this, protocolConfig);
            String protocolId = provider.protocolId();
            ProtocolSpi spi = new ProtocolSpi(protocolId, clientSpi);
            clients.putIfAbsent(protocolId, spi);
            protocols.add(spi);
            if (clientSpi.isTcp()) {
                tcpProtocols.add(spi);
            }
        }

        this.protocolsToClients = clients;
        this.protocols = protocols;
        this.tcpProtocols = tcpProtocols;
        this.tcpProtocolIds = tcpProtocols.stream()
                .map(ProtocolSpi::id)
                .toList();
    }

    @Override
    public WebClientCookieManager cookieManager() {
        return cookieManager;
    }

    @Override
    public HttpClientRequest method(Http.Method method) {
        ClientUri clientUri = prototype().baseUri()
                .map(ClientUri::create) // create from base config
                .orElseGet(ClientUri::create);// create as empty

        prototype().baseQuery().ifPresent(clientUri.writeableQuery()::from);
        prototype().baseFragment().ifPresent(clientUri::fragment);

        return new HttpClientRequest(this,
                                     this.prototype(),
                                     method,
                                     clientUri,
                                     protocolsToClients,
                                     protocols,
                                     tcpProtocols,
                                     tcpProtocolIds);
    }

    @Override
    public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol, C protocolConfig) {
        return protocol.provider().protocol(this, protocolConfig);
    }

    @Override
    public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol) {
        ProtocolProvider<T, C> provider = protocol.provider();
        C config = protocolConfigs.config(provider.protocolId(),
                                          provider.configType(),
                                          provider::defaultConfig);

        return provider.protocol(this, config);
    }

    @Override
    public WebClientConfig prototype() {
        return config;
    }

    @Override
    public ExecutorService executor() {
        return EXECUTOR.get();
    }

    record ProtocolSpi(String id, HttpClientSpi spi) {
    }
}
