/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.LruCache;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.tls.Tls;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.Method;
import io.helidon.service.registry.Service;
import io.helidon.webclient.spi.ClientProtocolProvider;
import io.helidon.webclient.spi.ClientProtocolProviderCacheLifecycle;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.HttpClientSpiProvider;
import io.helidon.webclient.spi.Protocol;
import io.helidon.webclient.spi.ProtocolConfig;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientTransportObserverProvider;
import io.helidon.webclient.spi.WebClientTransportObserverProvider.Registration;

import static java.lang.System.Logger.Level.WARNING;

/**
 * Base class for HTTP implementations of {@link WebClient}.
 */
@SuppressWarnings("rawtypes")
@Service.PerInstance(WebClientConfigBlueprint.class)
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class LoomClient implements WebClient, WebClientTransportObserverContext {
    static final LazyValue<ExecutorService> EXECUTOR =
            LazyValue.create(() -> Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                                            .name("helidon-client-", 0)
                                                                            .factory()));
    private static final System.Logger LOGGER = System.getLogger(LoomClient.class.getName());
    private static final List<HttpClientSpiProvider> PROVIDERS =
            HelidonServiceLoader.create(ServiceLoader.load(HttpClientSpiProvider.class))
                    .asList();
    private static final Map<String, HttpClientSpiProvider> HTTP_PROVIDERS_BY_PROTOCOL;

    static {
        Map<String, HttpClientSpiProvider> providerMap = new HashMap<>();
        PROVIDERS.forEach(it -> providerMap.put(it.protocolId(), it));
        HTTP_PROVIDERS_BY_PROTOCOL = Map.copyOf(providerMap);
    }

    private final WebClientConfig config;
    // a map of protocol ids to the client SPI implementing them
    private final Map<String, ProtocolSpi> clientSpiByProtocol;
    private final Map<String, Object> clientsByProtocol = new ConcurrentHashMap<>();
    private final List<ProtocolSpi> protocols;
    private final List<ProtocolSpi> tcpProtocols;
    private final ProtocolConfigs protocolConfigs;
    private final List<String> tcpProtocolIds;
    private final WebClientCookieManager cookieManager;
    private final LruCache<EndpointKey, HttpClientSpi> clientSpiLruCache = LruCache.create();
    private final HttpTransportObserver transportObserver;
    private final Object transportObserverIdentity;
    private final List<Registration> transportObserverRegistrations;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Construct this instance from a subclass of builder.
     *
     * @param config builder the subclass is built from
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Service.Inject
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
                HttpClientSpiProvider spi = HTTP_PROVIDERS_BY_PROTOCOL.get(protocol);
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

        Map<Object, Boolean> observerIdentities = new IdentityHashMap<>();
        List<Object> activeObserverIdentities = new ArrayList<>();
        List<HttpTransportObserver> transportObservers = new ArrayList<>();
        List<Registration> observerRegistrations = new ArrayList<>();
        boolean borrowedObserver = config.services()
                .stream()
                .anyMatch(BorrowedWebClientTransportObserverProvider.class::isInstance);
        for (WebClientService service : config.services()) {
            if (!(service instanceof WebClientTransportObserverProvider provider)) {
                continue;
            }
            if (borrowedObserver && !(provider instanceof BorrowedWebClientTransportObserverProvider)) {
                continue;
            }
            Registration registration = null;
            try {
                Object identity = Objects.requireNonNull(provider.transportObserverIdentity(),
                                                         "WebClient transport observer identity");
                if (observerIdentities.containsKey(identity)) {
                    continue;
                }
                registration = Objects.requireNonNull(provider.openTransportObserver(),
                                                      "WebClient transport observer registration");
                HttpTransportObserver observer = Objects.requireNonNull(registration.observer(),
                                                                         "WebClient transport observer");
                observerIdentities.put(identity, Boolean.TRUE);
                activeObserverIdentities.add(identity);
                observerRegistrations.add(registration);
                transportObservers.add(observer);
            } catch (Throwable failure) {
                if (registration != null) {
                    try {
                        registration.close();
                    } catch (Throwable closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                LOGGER.log(WARNING, "Failed to initialize WebClient HTTP transport observation", failure);
            }
        }
        this.transportObserver = HttpTransportObserver.compose(transportObservers);
        this.transportObserverIdentity = switch (activeObserverIdentities.size()) {
            case 0 -> HttpTransportObserver.noop();
            case 1 -> activeObserverIdentities.getFirst();
            default -> new Object();
        };
        this.transportObserverRegistrations = List.copyOf(observerRegistrations);

        Map<String, ProtocolSpi> clients = new HashMap<>();
        List<ProtocolSpi> protocols = new ArrayList<>();
        List<ProtocolSpi> tcpProtocols = new ArrayList<>();
        try {
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
        } catch (RuntimeException | Error failure) {
            for (ProtocolSpi protocol : protocols) {
                try {
                    protocol.spi().releaseResource();
                } catch (Throwable closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            for (Registration registration : transportObserverRegistrations) {
                try {
                    registration.close();
                } catch (Throwable closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }

        this.clientSpiByProtocol = clients;
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
    public HttpClientRequest method(Method method) {
        ClientUri clientUri = prototype().baseUri()
                .map(ClientUri::create) // create from base config
                .orElseGet(ClientUri::create); // create as empty

        prototype().baseQuery().ifPresent(clientUri.writeableQuery()::from);
        prototype().baseFragment().ifPresent(clientUri::fragment);

        return new HttpClientRequest(this,
                                     this.prototype(),
                                     method,
                                     clientUri,
                                     clientSpiByProtocol,
                                     protocols,
                                     tcpProtocols,
                                     tcpProtocolIds,
                                     clientSpiLruCache);
    }

    @Override
    @Service.PreDestroy
    public void closeResource() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable failure = null;
        for (ProtocolSpi o : List.copyOf(clientSpiByProtocol.values())) {
            try {
                o.spi().releaseResource();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        for (Registration registration : transportObserverRegistrations) {
            try {
                registration.close();
                registration.completion().whenComplete((_, completionFailure) -> {
                    if (completionFailure != null) {
                        LOGGER.log(WARNING, "Failed to release WebClient HTTP transport observation", completionFailure);
                    }
                });
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to close WebClient resources", failure);
        }
    }

    @Override
    public HttpTransportObserver transportObserver() {
        return transportObserver;
    }

    @Override
    public Object transportObserverIdentity() {
        return transportObserverIdentity;
    }

    @Override
    public List<String> tcpProtocolIds() {
        List<String> protocolIds = tcpProtocolIds;
        if (protocolIds == null) {
            throw new IllegalStateException("TCP protocol IDs are not available during WebClient construction");
        }
        return protocolIds;
    }

    @Override
    public void responseReceived(WebClientProtocolResponse response) {
        WebClientProtocolResponse checkedResponse = Objects.requireNonNull(response, "response");
        for (ProtocolSpi protocol : protocols) {
            try {
                protocol.spi().responseReceived(checkedResponse);
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "HTTP protocol response notification failed for " + protocol.id(),
                           failure);
            }
        }
    }

    @Override
    public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol, C protocolConfig) {
        return protocol.provider().protocol(this, protocolConfig);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol) {
        ClientProtocolProvider<T, C> provider = protocol.provider();
        String protocolId = provider.protocolId();
        if (!(provider instanceof ClientProtocolProviderCacheLifecycle<?, ?> rawLifecycle)) {
            return (T) clientsByProtocol.computeIfAbsent(protocolId,
                                                         ignored -> createProtocolClient(provider, protocolId));
        }

        ClientProtocolProviderCacheLifecycle<T, C> lifecycle =
                (ClientProtocolProviderCacheLifecycle<T, C>) rawLifecycle;
        Object current = clientsByProtocol.get(protocolId);
        if (current != null && !lifecycle.cacheReplacementReady((T) current)) {
            return (T) current;
        }
        return (T) clientsByProtocol.compute(protocolId,
                                             (ignored, cached) -> cached != null
                                                     && !lifecycle.cacheReplacementReady((T) cached)
                                                     ? cached
                                                     : createProtocolClient(provider, protocolId));
    }

    private <T, C extends ProtocolConfig> T createProtocolClient(ClientProtocolProvider<T, C> provider,
                                                                 String protocolId) {
        C config = protocolConfigs.config(protocolId,
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

    record EndpointKey(String scheme, // http/https
                       String authority, // myserver:80
                       String transportKey, // optional explicit transport address, such as a UDS path (may be null)
                       Tls tlsConfig, // TLS configuration (may be disabled, never null)
                       SniSupport.State sni,
                       Proxy proxy) { // proxy, never null
    }
}
