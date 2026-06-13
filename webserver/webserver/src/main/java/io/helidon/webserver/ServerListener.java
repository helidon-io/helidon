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

package io.helidon.webserver;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.Limit.InitializationContext;
import io.helidon.common.context.Context;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.task.HelidonTaskExecutor;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.spi.PortTransportBinding;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelectorProvider;
import io.helidon.webserver.spi.TlsTransportBinding;
import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingConfig;
import io.helidon.webserver.spi.TransportBindingProvider;

import static java.lang.System.Logger.Level.DEBUG;

class ServerListener implements ListenerContext {
    private static final System.Logger LOGGER = System.getLogger(ServerListener.class.getName());
    private static final String EXPLICIT_SSL_CONTEXT_RELOAD_NOT_SUPPORTED =
            "TLS cannot be reloaded when an explicit instance of SSL context was used to create it";
    private static final long BINDING_STOP_COMPLETION_MARGIN_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

    @SuppressWarnings("rawtypes")
    private static final LazyValue<List<ServerConnectionSelectorProvider>> SELECTOR_PROVIDERS = LazyValue.create(() ->
            HelidonServiceLoader.create(ServiceLoader.load(ServerConnectionSelectorProvider.class)).asList());
    @SuppressWarnings("rawtypes")
    private static final LazyValue<List<TransportBindingProvider>> TRANSPORT_BINDING_PROVIDERS = LazyValue.create(() ->
            HelidonServiceLoader.builder(ServiceLoader.load(TransportBindingProvider.class))
                    .addService(new TcpTransportBindingProvider())
                    .build()
                    .asList());

    private final ConnectionProviders connectionProviders;
    private final String socketName;
    private final ListenerConfig listenerConfig;
    private final Router router;
    private final HelidonTaskExecutor readerExecutor;
    private final ExecutorService sharedExecutor;
    private final DirectHandlers directHandlers;
    private final Tls tls;
    private final VirtualHostRegistry virtualHosts;
    private final SocketOptions connectionOptions;
    private final SocketAddress configuredAddress;
    private final Duration gracePeriod;
    private final Timer idleConnectionTimer;
    private final FatalBindingFailureHandler fatalBindingFailureHandler;
    private final TcpTransportBindingContext transportBindingContext;
    private final Lock idleTimeoutLock = new ReentrantLock();
    private final List<TransportBinding> transportBindings;

    private final MediaContext mediaContext;
    private final ContentEncodingContext contentEncodingContext;
    private final Context context;
    private final Limit requestLimit;

    private final AtomicBoolean lifecycleStarted = new AtomicBoolean();

    private volatile IdleTimeoutHandler idleTimeoutHandler;

    ServerListener(String socketName,
                   ListenerConfig listenerConfig,
                   Router router,
                   Context serverContext,
                   Timer idleConnectionTimer,
                   MediaContext defaultMediaContext,
                   ContentEncodingContext defaultContentEncodingContext,
                   DirectHandlers defaultDirectHandlers) {
        this(socketName,
             listenerConfig,
             router,
             serverContext,
             idleConnectionTimer,
             defaultMediaContext,
             defaultContentEncodingContext,
             defaultDirectHandlers,
             (listener, binding, cause) -> listener.stop());
    }

    @SuppressWarnings("unchecked")
    ServerListener(String socketName,
                   ListenerConfig listenerConfig,
                   Router router,
                   Context serverContext,
                   Timer idleConnectionTimer,
                   MediaContext defaultMediaContext,
                   ContentEncodingContext defaultContentEncodingContext,
                   DirectHandlers defaultDirectHandlers,
                   FatalBindingFailureHandler fatalBindingFailureHandler) {

        List<ProtocolConfig> protocolConfigs = listenerConfig.protocols();
        ProtocolConfigs tcpProtocols = ProtocolConfigs.create(protocolConfigs.stream()
                                                               .filter(ServerListener::supportsTcpTransportBinding)
                                                               .toList());
        List<ServerConnectionSelector> selectors = tcpConnectionSelectors(socketName, listenerConfig, tcpProtocols);

        if (listenerConfig.maxConcurrentRequests() == -1) {
            this.requestLimit = listenerConfig.concurrencyLimit()
                    .orElseGet(FixedLimit::create); // unlimited unless configured
        } else {
            this.requestLimit = FixedLimit.builder()
                    .permits(listenerConfig.maxConcurrentRequests())
                    .build();
        }

        InitializationContext limitContext = limitContext(socketName);
        this.requestLimit.init(limitContext);

        this.connectionProviders = ConnectionProviders.create(selectors);
        this.socketName = socketName;
        this.listenerConfig = listenerConfig;
        this.tls = listenerConfig.tls().orElseGet(() -> Tls.builder().enabled(false).build());
        this.virtualHosts = VirtualHostRegistry.create(socketName, listenerConfig, tls);
        this.connectionOptions = listenerConfig.connectionOptions();
        this.directHandlers = listenerConfig.directHandlers().orElse(defaultDirectHandlers);
        this.mediaContext = listenerConfig.mediaContext().orElse(defaultMediaContext);
        this.contentEncodingContext = listenerConfig.contentEncoding().orElse(defaultContentEncodingContext);
        this.context = listenerConfig.listenerContext().orElseGet(() -> Context.builder()
                .id("listener-" + socketName)
                .parent(serverContext)
                .build());
        this.gracePeriod = listenerConfig.shutdownGracePeriod();

        // to read requests and execute tasks
        this.readerExecutor = ExecutorsFactory.newServerListenerReaderExecutor();

        // to do anything else (writers etc.)
        this.sharedExecutor = ExecutorsFactory.newServerListenerSharedExecutor();

        this.configuredAddress = listenerConfig.bindAddress()
                .orElseGet(() -> {
                    int port = listenerConfig.port();
                    if (port < 1) {
                        port = 0;
                    }
                    return new InetSocketAddress(listenerConfig.address(), port);
                });

        this.router = router;
        this.idleConnectionTimer = idleConnectionTimer;
        this.fatalBindingFailureHandler = Objects.requireNonNull(fatalBindingFailureHandler, "fatalBindingFailureHandler");
        this.transportBindingContext = new ListenerTransportBindingContext();
        this.transportBindings = planTransportBindings(protocolConfigs);
    }

    @Override
    public MediaContext mediaContext() {
        return mediaContext;
    }

    @Override
    public ContentEncodingContext contentEncodingContext() {
        return contentEncodingContext;
    }

    @Override
    public DirectHandlers directHandlers() {
        return directHandlers;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public ListenerConfig config() {
        return listenerConfig;
    }

    @Override
    public ExecutorService executor() {
        return sharedExecutor;
    }

    @Override
    public String toString() {
        return socketName + " (" + configuredAddress + ")";
    }

    int port() {
        return boundPort().orElse(-1);
    }

    private OptionalInt boundPort() {
        List<TransportBinding> localTransportBindings = transportBindings;
        if (localTransportBindings == null) {
            return OptionalInt.empty();
        }
        for (TransportBinding binding : localTransportBindings) {
            if (binding instanceof PortTransportBinding portBinding) {
                int port = portBinding.port();
                if (port != -1) {
                    return OptionalInt.of(port);
                }
            }
        }
        return OptionalInt.empty();
    }

    Router router() {
        return router;
    }

    SocketAddress configuredAddress() {
        return configuredAddress;
    }

    void stop() {
        if (!lifecycleStarted.compareAndSet(true, false)) {
            return;
        }
        Throwable failure = stopResources();
        try {
            router.afterStop();
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to stop listener " + socketName);
    }

    void start() {
        start(() -> false);
    }

    void start(BooleanSupplier cancelled) {
        boolean beforeStartSucceeded = false;
        List<TransportBinding> startAttemptedBindings = new ArrayList<>();
        try {
            checkCancelledStartup(cancelled);
            router.beforeStart();
            beforeStartSucceeded = true;
            lifecycleStarted.set(true);
            checkCancelledStartup(cancelled);
            startIt(cancelled, startAttemptedBindings);
        } catch (RuntimeException | Error e) {
            rollbackFailedStart(e, beforeStartSucceeded, startAttemptedBindings);
            throw e;
        }
    }

    boolean hasTls() {
        for (TransportBinding binding : transportBindings) {
            if (binding.security() == TransportBinding.Security.TLS) {
                return true;
            }
        }
        return false;
    }

    // Intended for tests that need listener state without stack walking.
    Thread.State serverThreadState() {
        for (TransportBinding binding : transportBindings) {
            if (binding instanceof TcpTransportBinding tcpBinding) {
                return tcpBinding.serverThreadState();
            }
        }
        return null;
    }

    @Deprecated(forRemoval = true, since = "27.0.0")
    void reloadTls(Tls tls) {
        Objects.requireNonNull(tls, "tls");
        if (!this.tls.enabled()) {
            throw new IllegalArgumentException("TLS is not enabled on the socket " + socketName
                                                       + " and therefore cannot be reloaded");
        }
        if (!tls.enabled()) {
            throw new UnsupportedOperationException("TLS cannot be disabled by reloading on the socket " + socketName);
        }
        reloadTls(tlsMaterial(tls));
    }

    void reloadTls(TlsMaterial material) {
        Objects.requireNonNull(material, "material");
        boolean handled = false;
        Throwable failure = null;
        for (TransportBinding binding : transportBindings) {
            if (binding.security() == TransportBinding.Security.TLS
                    && binding instanceof TlsTransportBinding tlsBinding) {
                handled = true;
                try {
                    tlsBinding.reloadTls(material);
                } catch (RuntimeException | Error e) {
                    failure = LifecycleFailures.add(failure, bindingFailure("reload TLS for", binding, e));
                }
            }
        }
        if (!handled) {
            throw new IllegalArgumentException("TLS is not enabled on the socket " + socketName
                                                       + " and therefore cannot be reloaded");
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to reload TLS on listener " + socketName);
    }

    void reloadVirtualHostTls(TlsMaterial material, String host) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(host, "host");
        boolean handled = false;
        Throwable failure = null;
        for (TransportBinding binding : transportBindings) {
            if (binding.security() == TransportBinding.Security.TLS
                    && binding instanceof TlsTransportBinding tlsBinding) {
                handled = true;
                try {
                    tlsBinding.reloadVirtualHostTls(material, host);
                } catch (RuntimeException | Error e) {
                    failure = LifecycleFailures.add(failure, bindingFailure("reload virtual host TLS for", binding, e));
                }
            }
        }
        if (!handled) {
            throw new IllegalArgumentException("TLS is not enabled on the socket " + socketName
                                                       + " and therefore cannot be reloaded");
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to reload virtual host TLS on listener " + socketName);
    }

    void suspend() {
        for (TransportBinding binding : transportBindings) {
            binding.suspend();
        }
        suspendForCheckpoint();
        for (TransportBinding binding : transportBindings) {
            if (binding instanceof TcpTransportBinding tcpBinding) {
                tcpBinding.clearAfterSuspend();
            }
        }
    }

    void resume() {
        for (TransportBinding binding : transportBindings) {
            binding.resume();
        }
    }

    private static InitializationContext limitContext(String socketName) {
        if (WebServer.DEFAULT_SOCKET_NAME.equals(socketName)) {
            return InitializationContext.create(socketName);
        }
        return InitializationContext.create(socketName, List.of(Tag.create("socketName", socketName)));
    }

    private TcpTransportBinding createTcpTransportBinding(TransportBindingContext context, TcpTransportConfig config) {
        Objects.requireNonNull(config, "config");
        return new TcpTransportBinding(context,
                                       config.name(),
                                       listenerConfig,
                                       configuredAddress,
                                       connectionOptions,
                                       connectionProviders,
                                       tls,
                                       virtualHosts,
                                       readerExecutor,
                                       requestLimit,
                                       this::startIdleTimeoutHandler);
    }

    private static TlsMaterial tlsMaterial(Tls tls) {
        var tlsConfig = tls.prototype();
        if (tlsConfig.sslContext().isPresent()) {
            throw new UnsupportedOperationException(EXPLICIT_SSL_CONTEXT_RELOAD_NOT_SUPPORTED);
        }

        TlsMaterial.Builder builder = TlsMaterial.builder()
                .trustAll(tlsConfig.trustAll());
        tlsConfig.privateKey().ifPresent(builder::privateKey);
        if (!tlsConfig.privateKeyCertChain().isEmpty()) {
            builder.privateKeyCertChain(tlsConfig.privateKeyCertChain());
        }
        if (!tlsConfig.trust().isEmpty()) {
            builder.trust(tlsConfig.trust());
        }
        tlsConfig.secureRandom().ifPresent(builder::secureRandom);
        tlsConfig.secureRandomAlgorithm().ifPresent(builder::secureRandomAlgorithm);
        tlsConfig.secureRandomProvider().ifPresent(builder::secureRandomProvider);
        tlsConfig.keyManagerFactoryAlgorithm().ifPresent(builder::keyManagerFactoryAlgorithm);
        tlsConfig.keyManagerFactoryProvider().ifPresent(builder::keyManagerFactoryProvider);
        tlsConfig.trustManagerFactoryAlgorithm().ifPresent(builder::trustManagerFactoryAlgorithm);
        tlsConfig.trustManagerFactoryProvider().ifPresent(builder::trustManagerFactoryProvider);
        tlsConfig.internalKeystoreType().ifPresent(builder::internalKeystoreType);
        tlsConfig.internalKeystoreProvider().ifPresent(builder::internalKeystoreProvider);
        tlsConfig.revocation().ifPresent(builder::revocation);

        return builder.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<ServerConnectionSelector> tcpConnectionSelectors(String socketName,
                                                                         ListenerConfig listenerConfig,
                                                                         ProtocolConfigs protocols) {
        List<ServerConnectionSelector> selectors = new ArrayList<>(listenerConfig.connectionSelectors());

        // for each discovered selector provider, add a selector for each configuration of that provider
        SELECTOR_PROVIDERS.get()
                .forEach(provider -> {
                    List<ProtocolConfig> configurations = protocols.config(provider.protocolType(),
                                                                           provider.protocolConfigType());
                    for (ProtocolConfig configuration : configurations) {
                        selectors.add(provider.create(socketName, configuration, protocols));
                    }
                });
        return selectors;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<TransportBinding> planTransportBindings(List<ProtocolConfig> protocolConfigs) {
        Map<String, TransportBindingProvider> providers = transportBindingProviders();
        BindingPlanContext planContext = new ListenerBindingPlanContext(socketName,
                                                                        Optional.of(configuredAddress),
                                                                        bindingPlanHost(configuredAddress, listenerConfig),
                                                                        bindingPlanPort(configuredAddress, listenerConfig));
        List<TransportBinding> activeBindings = new ArrayList<>();
        Set<BindingConfigId> bindingConfigs = new LinkedHashSet<>();
        Set<String> bindingNames = new LinkedHashSet<>();
        boolean hasExplicitTcpBinding = hasExplicitTcpBinding(listenerConfig.bindings());
        boolean tcpBindingPlanned = false;

        for (TransportBindingConfig config : orderedBindingConfigs(listenerConfig.bindings())) {
            if (!config.enabled()) {
                continue;
            }
            if (isDiscoveredDefaultTcpBinding(config) && (hasExplicitTcpBinding || tcpBindingPlanned)) {
                continue;
            }
            if (!bindingConfigs.add(new BindingConfigId(config.type(), config.name()))) {
                throw new IllegalArgumentException("Duplicate transport binding config \"" + config.name()
                                                           + "\" of type \"" + config.type()
                                                           + "\" on listener " + socketName);
            }
            if (TcpTransportBinding.TYPE.equals(config.type())) {
                if (tcpBindingPlanned) {
                    throw new IllegalArgumentException("Listener " + socketName
                                                               + " can only plan one TCP transport binding because TCP "
                                                               + "does not define a per-binding endpoint");
                }
                tcpBindingPlanned = true;
            }
            TransportBindingProvider provider = providers.get(config.type());
            if (provider == null) {
                throw new IllegalArgumentException("Listener " + socketName
                                                           + " has configured transport binding type \"" + config.type()
                                                           + "\", but only the following providers are supported: "
                                                           + providers.keySet());
            }
            if (!provider.configType().isInstance(config)) {
                throw new IllegalArgumentException("Listener " + socketName
                                                           + " transport binding " + config.name()
                                                           + " of type \"" + config.type()
                                                           + "\" uses config type " + config.getClass().getName()
                                                           + ", but provider expects " + provider.configType().getName());
            }
            if (!provider.canBind(planContext, config)) {
                if (config.required()) {
                    throw new IllegalArgumentException("Listener " + socketName
                                                               + " requires transport binding " + config.name()
                                                               + " of type \"" + config.type()
                                                               + "\", but this binding cannot bind with the listener "
                                                               + "endpoint configuration");
                }
                continue;
            }

            TransportBinding binding = Objects.requireNonNull(provider.create(transportBindingContext, config),
                                                              "Transport binding provider returned null");
            if (!bindingNames.add(binding.name())) {
                throw new IllegalArgumentException("Duplicate transport binding name \"" + binding.name()
                                                           + "\" on listener " + socketName);
            }
            activeBindings.add(binding);
        }

        if (activeBindings.isEmpty()) {
            throw new IllegalArgumentException("Listener " + socketName + " has no active transport bindings");
        }

        validateBindingCapabilities(activeBindings);
        validateProtocolTransportBindings(protocolConfigs, activeBindings, providers.keySet());

        return List.copyOf(activeBindings);
    }

    private static boolean hasExplicitTcpBinding(List<TransportBindingConfig> configs) {
        for (TransportBindingConfig config : configs) {
            if (config.enabled()
                    && TcpTransportBinding.TYPE.equals(config.type())
                    && !isDiscoveredDefaultTcpBinding(config)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDefaultTcpBinding(TransportBindingConfig config) {
        return TcpTransportBinding.TYPE.equals(config.type()) && TcpTransportBinding.TYPE.equals(config.name());
    }

    private static List<TransportBindingConfig> orderedBindingConfigs(List<TransportBindingConfig> configs) {
        TransportBindingConfig discoveredDefaultTcpConfig = null;
        List<TransportBindingConfig> orderedConfigs = new ArrayList<>(configs.size());
        for (TransportBindingConfig config : configs) {
            if (discoveredDefaultTcpConfig == null && isDiscoveredDefaultTcpBinding(config)) {
                discoveredDefaultTcpConfig = config;
            } else {
                orderedConfigs.add(config);
            }
        }

        if (discoveredDefaultTcpConfig != null) {
            orderedConfigs.add(0, discoveredDefaultTcpConfig);
            return orderedConfigs;
        }

        if (hasTcpBinding(configs)) {
            return configs;
        }

        List<TransportBindingConfig> result = new ArrayList<>(configs.size() + 1);
        result.add(TcpTransportConfig.create());
        result.addAll(configs);
        return result;
    }

    private static boolean isDiscoveredDefaultTcpBinding(TransportBindingConfig config) {
        return config instanceof TcpTransportConfig tcpConfig && TcpTransportBindingProvider.isDiscoveredDefault(tcpConfig);
    }

    private static boolean hasTcpBinding(List<TransportBindingConfig> configs) {
        for (TransportBindingConfig config : configs) {
            if (TcpTransportBinding.TYPE.equals(config.type())) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsTcpTransportBinding(ProtocolConfig config) {
        Set<String> transportBindingTypes = config.transportBindingTypes();
        return transportBindingTypes.isEmpty() || transportBindingTypes.contains(TcpTransportBinding.TYPE);
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, TransportBindingProvider> transportBindingProviders() {
        Map<String, TransportBindingProvider> providers = new LinkedHashMap<>();
        for (TransportBindingProvider provider : TRANSPORT_BINDING_PROVIDERS.get()) {
            TransportBindingProvider previous = providers.putIfAbsent(provider.configKey(), provider);
            if (previous != null) {
                throw new IllegalStateException("Multiple transport binding providers configured for type \""
                                                        + provider.configKey() + "\": "
                                                        + previous.getClass().getName() + ", "
                                                        + provider.getClass().getName());
            }
        }
        return providers;
    }

    private void validateBindingCapabilities(List<TransportBinding> bindings) {
        for (TransportBinding binding : bindings) {
            TransportBinding.Security security = Objects.requireNonNull(binding.security(),
                                                                        "Transport binding returned null security");
            if (tls.enabled() && security == TransportBinding.Security.UNPROTECTED) {
                throw new IllegalArgumentException("Listener " + socketName
                                                           + " has TLS enabled, but transport binding " + binding.name()
                                                           + " of type \"" + binding.type()
                                                           + "\" is unprotected");
            }
            if (!tls.enabled() && security == TransportBinding.Security.TLS) {
                throw new IllegalArgumentException("Listener " + socketName
                                                           + " does not have TLS enabled, but transport binding "
                                                           + binding.name()
                                                           + " of type \"" + binding.type()
                                                           + "\" requires listener TLS");
            }
            if (security == TransportBinding.Security.TLS && !(binding instanceof TlsTransportBinding)) {
                throw new IllegalArgumentException("Transport binding " + binding.name()
                                                           + " of type \"" + binding.type()
                                                           + "\" declares listener TLS protection but does not support "
                                                           + "listener TLS operations");
            }
            if (virtualHosts.enabled()
                    && (security != TransportBinding.Security.TLS
                    || !(binding instanceof TlsTransportBinding tlsBinding)
                    || !tlsBinding.supportsListenerVirtualHosts())) {
                throw new IllegalArgumentException("Listener " + socketName
                                                           + " has TLS virtual hosts configured, but transport binding "
                                                           + binding.name()
                                                           + " of type \"" + binding.type()
                                                           + "\" does not support listener virtual hosts");
            }
        }
    }

    private void validateProtocolTransportBindings(List<ProtocolConfig> protocolConfigs,
                                                   List<TransportBinding> bindings,
                                                   Set<String> providerTypes) {
        Set<String> activeTypes = new LinkedHashSet<>();
        for (TransportBinding binding : bindings) {
            activeTypes.add(binding.type());
        }
        for (ProtocolConfig protocolConfig : protocolConfigs) {
            Set<String> requiredTypes = protocolConfig.transportBindingTypes();
            if (requiredTypes.isEmpty()) {
                continue;
            }
            if (activeTypes.stream().noneMatch(requiredTypes::contains)) {
                throw new IllegalArgumentException("Listener " + socketName
                                                           + " protocol " + protocolConfig.type()
                                                           + "/" + protocolConfig.name()
                                                           + " requires transport binding type(s) " + requiredTypes
                                                           + ", active binding type(s) " + activeTypes
                                                           + ", supported provider type(s) " + providerTypes);
            }
        }
    }

    private Throwable stopResources() {
        return stopResources(transportBindings);
    }

    private Throwable stopResources(List<TransportBinding> bindings) {
        Throwable failure = null;
        List<BindingStop> bindingStops = new ArrayList<>();
        long stopAtNanos = stopAtNanos(gracePeriod);

        // Stop listening for connections
        for (TransportBinding binding : bindings) {
            try {
                Future<TransportBinding.ShutdownResult> stopFuture = sharedExecutor.submit(() -> stopBinding(binding));
                bindingStops.add(new BindingStop(binding, stopFuture));
            } catch (RuntimeException e) {
                failure = LifecycleFailures.add(failure, stopBindingInline(binding));
            } catch (Error e) {
                failure = LifecycleFailures.add(failure, stopBindingInline(binding));
                failure = LifecycleFailures.add(failure, bindingFailure("stop", binding, e));
            }
        }
        IdleTimeoutHandler cancelledIdleTimeoutHandler = null;

        try {
            cancelledIdleTimeoutHandler = cancelIdleTimeoutHandler();
            purgeCancelledIdleTimeoutHandler(cancelledIdleTimeoutHandler);
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }

        BindingStopResult bindingStopResult = awaitBindingStops(bindingStops, gracePeriod, stopAtNanos);
        failure = LifecycleFailures.add(failure, bindingStopResult.failure());

        if (bindingStopResult.forceSharedExecutorShutdown()) {
            try {
                forceShutdownSharedExecutor();
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }

        try {
            // Shutdown reader executor
            shutdownReaderExecutor(remainingNanos(stopAtNanos));
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }

        if (!bindingStopResult.forceSharedExecutorShutdown()) {
            try {
                // Shutdown shared executor
                shutdownSharedExecutor(remainingNanos(stopAtNanos));
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }

        try {
            awaitIdleTimeoutHandler(cancelledIdleTimeoutHandler);
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }

        return failure;
    }

    private TransportBinding.ShutdownResult stopBinding(TransportBinding binding) {
        return Objects.requireNonNull(binding.stop(gracePeriod), "Transport binding stop result must not be null");
    }

    private Throwable stopBindingInline(TransportBinding binding) {
        try {
            stopBinding(binding);
            return null;
        } catch (RuntimeException | Error e) {
            return bindingFailure("stop", binding, e);
        }
    }

    private void suspendForCheckpoint() {
        Throwable failure = null;
        List<TcpTransportBinding> tcpBindings = tcpBindings();
        IdleTimeoutHandler cancelledIdleTimeoutHandler = null;
        try {
            cancelledIdleTimeoutHandler = cancelIdleTimeoutHandler();
            purgeCancelledIdleTimeoutHandler(cancelledIdleTimeoutHandler);
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        // Stop handling any new requests on all accepted and active connections
        for (TcpTransportBinding tcpBinding : tcpBindings) {
            failure = LifecycleFailures.add(failure, tcpBinding.closeOpenConnections(false));
        }
        // Interrupt and close any accepted and active connections
        for (TcpTransportBinding tcpBinding : tcpBindings) {
            failure = LifecycleFailures.add(failure, tcpBinding.closeOpenConnections(true));
        }
        try {
            awaitIdleTimeoutHandler(cancelledIdleTimeoutHandler);
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }

        for (TcpTransportBinding tcpBinding : tcpBindings) {
            failure = LifecycleFailures.add(failure, tcpBinding.awaitClose());
        }
        throwIfCheckpointSuspendFailed(failure);
    }

    private void startIt(BooleanSupplier cancelled, List<TransportBinding> startAttemptedBindings) {
        for (TransportBinding binding : transportBindings) {
            checkCancelledStartup(cancelled);
            startAttemptedBindings.add(binding);
            binding.start();
        }
    }

    private void checkCancelledStartup(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new IllegalStateException("Listener startup cancelled " + socketName);
        }
    }

    private void rollbackFailedStart(Throwable startupFailure,
                                     boolean beforeStartSucceeded,
                                     List<TransportBinding> startAttemptedBindings) {
        suppressCleanupFailure(startupFailure, () ->
                LifecycleFailures.throwIfFailed(stopResources(startAttemptedBindings),
                                                "Failed to roll back listener " + socketName));
        suppressCleanupFailure(startupFailure, this::cancelAndAwaitIdleTimeoutHandler);
        if (beforeStartSucceeded && lifecycleStarted.compareAndSet(true, false)) {
            suppressCleanupFailure(startupFailure, router::afterStop);
        }
    }

    private static void suppressCleanupFailure(Throwable startupFailure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error e) {
            LifecycleFailures.add(startupFailure, e);
        }
    }

    private void shutdownReaderExecutor(long timeoutNanos) {
        Throwable failure = null;
        readerExecutor.terminate(timeoutNanos, TimeUnit.NANOSECONDS);
        if (Thread.currentThread().isInterrupted()) {
            failure = LifecycleFailures.add(failure,
                                            new IllegalStateException("Interrupted while shutting down listener reader executor "
                                                                              + "for " + socketName));
        }
        if (!readerExecutor.isTerminated()) {
            LOGGER.log(DEBUG, "Some tasks in reader executor did not terminate gracefully");
            try {
                readerExecutor.forceTerminate();
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to shut down listener reader executor for " + socketName);
    }

    private void shutdownSharedExecutor(long timeoutNanos) {
        Throwable failure = null;
        sharedExecutor.shutdown();
        try {
            boolean done = sharedExecutor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS);
            if (!done) {
                forceShutdownSharedExecutor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IllegalStateException interrupted =
                    new IllegalStateException("Interrupted while shutting down listener executor for " + socketName, e);
            try {
                forceShutdownSharedExecutor();
            } catch (RuntimeException | Error shutdownFailure) {
                interrupted.addSuppressed(shutdownFailure);
            }
            failure = LifecycleFailures.add(failure, interrupted);
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to shut down listener executor for " + socketName);
    }

    private void forceShutdownSharedExecutor() {
        List<Runnable> running = sharedExecutor.shutdownNow();
        if (!running.isEmpty()) {
            LOGGER.log(DEBUG, running.size() + " tasks in shared executor did not terminate gracefully");
        }
    }

    private void startIdleTimeoutHandler() {
        idleTimeoutLock.lock();
        try {
            if (idleTimeoutHandler != null) {
                return;
            }
            IdleTimeoutHandler handler = new IdleTimeoutHandler(idleConnectionTimer,
                                                                listenerConfig,
                                                                this::connectionHandlers);
            handler.start();
            idleTimeoutHandler = handler;
        } finally {
            idleTimeoutLock.unlock();
        }
    }

    private IdleTimeoutHandler cancelIdleTimeoutHandler() {
        idleTimeoutLock.lock();
        try {
            IdleTimeoutHandler handler = idleTimeoutHandler;
            if (handler == null) {
                return null;
            }
            idleTimeoutHandler = null;
            handler.cancelOnly();
            return handler;
        } finally {
            idleTimeoutLock.unlock();
        }
    }

    private void cancelAndAwaitIdleTimeoutHandler() {
        IdleTimeoutHandler handler = cancelIdleTimeoutHandler();
        Throwable failure = null;
        try {
            purgeCancelledIdleTimeoutHandler(handler);
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        try {
            awaitIdleTimeoutHandler(handler);
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to cancel listener idle timeout task for " + socketName);
    }

    private void purgeCancelledIdleTimeoutHandler(IdleTimeoutHandler handler) {
        if (handler != null) {
            idleConnectionTimer.purge();
        }
    }

    private static void awaitIdleTimeoutHandler(IdleTimeoutHandler handler) {
        if (handler != null) {
            handler.awaitFinished();
        }
    }

    private static void throwIfCheckpointSuspendFailed(Throwable failure) {
        if (failure == null) {
            return;
        }
        Throwable unwrapped = LifecycleFailures.unwrap(failure);
        if (unwrapped instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (unwrapped instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Failed to suspend listener for checkpoint", unwrapped);
    }

    private List<TcpTransportBinding> tcpBindings() {
        return tcpBindings(transportBindings);
    }

    private static List<TcpTransportBinding> tcpBindings(List<TransportBinding> bindings) {
        List<TcpTransportBinding> result = new ArrayList<>();
        for (TransportBinding binding : bindings) {
            if (binding instanceof TcpTransportBinding tcpBinding) {
                result.add(tcpBinding);
            }
        }
        return result;
    }

    private List<ConnectionHandler> connectionHandlers() {
        List<ConnectionHandler> result = new ArrayList<>();
        for (TcpTransportBinding tcpBinding : tcpBindings()) {
            tcpBinding.addConnectionHandlersTo(result);
        }
        return result;
    }

    private static String bindingPlanHost(SocketAddress configuredAddress, ListenerConfig listenerConfig) {
        if (configuredAddress instanceof InetSocketAddress inetSocketAddress) {
            var address = inetSocketAddress.getAddress();
            if (address != null) {
                return address.getHostAddress();
            }
            return inetSocketAddress.getHostString();
        }
        return listenerConfig.host();
    }

    private static int bindingPlanPort(SocketAddress configuredAddress, ListenerConfig listenerConfig) {
        if (configuredAddress instanceof InetSocketAddress inetSocketAddress) {
            return inetSocketAddress.getPort();
        }
        return Math.max(listenerConfig.port(), 0);
    }

    private static BindingStopResult awaitBindingStops(List<BindingStop> bindingStops,
                                                       Duration gracefulPeriod,
                                                       long stopAtNanos) {
        if (bindingStops.isEmpty()) {
            return new BindingStopResult(null, false);
        }
        Throwable failure = null;
        boolean forceSharedExecutorShutdown = false;
        boolean interrupted = false;

        for (BindingStop bindingStop : bindingStops) {
            boolean done = false;
            while (!done) {
                try {
                    if (bindingStop.future().isDone()) {
                        forceSharedExecutorShutdown |= forceSharedExecutorShutdown(bindingStop.future().get());
                        done = true;
                        continue;
                    }
                    long remainingNanos = stopAtNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        bindingStop.future().cancel(true);
                        forceSharedExecutorShutdown = true;
                        failure = LifecycleFailures.add(failure,
                                                        bindingFailure("stop", bindingStop.binding(),
                                                                       timedOutBindingStop(bindingStop.binding(),
                                                                                           gracefulPeriod,
                                                                                           new TimeoutException())));
                        done = true;
                        continue;
                    }
                    forceSharedExecutorShutdown |= forceSharedExecutorShutdown(
                            bindingStop.future().get(remainingNanos, TimeUnit.NANOSECONDS));
                    done = true;
                } catch (InterruptedException e) {
                    interrupted = true;
                    failure = LifecycleFailures.add(failure,
                                                    bindingFailure("stop", bindingStop.binding(),
                                                                   interruptedBindingStop(bindingStop.binding(), e)));
                } catch (TimeoutException e) {
                    bindingStop.future().cancel(true);
                    forceSharedExecutorShutdown = true;
                    failure = LifecycleFailures.add(failure,
                                                    bindingFailure("stop", bindingStop.binding(),
                                                                   timedOutBindingStop(bindingStop.binding(),
                                                                                       gracefulPeriod,
                                                                                       e)));
                    done = true;
                } catch (CancellationException e) {
                    failure = LifecycleFailures.add(failure, bindingFailure("stop", bindingStop.binding(), e));
                    done = true;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    failure = LifecycleFailures.add(failure, bindingFailure("stop", bindingStop.binding(), cause));
                    done = true;
                } catch (RuntimeException | Error e) {
                    failure = LifecycleFailures.add(failure, bindingFailure("stop", bindingStop.binding(), e));
                    done = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return new BindingStopResult(failure, forceSharedExecutorShutdown);
    }

    private static boolean forceSharedExecutorShutdown(TransportBinding.ShutdownResult shutdownResult) {
        return shutdownResult == TransportBinding.ShutdownResult.FORCED;
    }

    private static long remainingNanos(long stopAtNanos) {
        if (stopAtNanos == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, stopAtNanos - System.nanoTime());
    }

    private static Throwable bindingFailure(String action, TransportBinding binding, Throwable cause) {
        if (cause instanceof Error) {
            return cause;
        }
        return new IllegalStateException("Failed to " + action + " transport binding " + binding.name()
                                                 + " of type \"" + binding.type() + "\" at "
                                                 + binding.configuredEndpoint(),
                                         cause);
    }

    private static IllegalStateException interruptedBindingStop(TransportBinding binding, InterruptedException cause) {
        return new IllegalStateException("Interrupted while waiting for transport binding " + binding.name()
                                                 + " to stop",
                                         cause);
    }

    private static IllegalStateException timedOutBindingStop(TransportBinding binding,
                                                            Duration gracefulPeriod,
                                                            TimeoutException cause) {
        return new IllegalStateException("Timed out waiting for transport binding " + binding.name()
                                                 + " to stop after " + gracefulPeriod,
                                         cause);
    }

    private static long stopAtNanos(Duration gracefulPeriod) {
        long timeoutNanos = bindingStopTimeoutNanos(gracefulPeriod);
        long now = System.nanoTime();
        long stopAtNanos = now + timeoutNanos;
        return stopAtNanos < now ? Long.MAX_VALUE : stopAtNanos;
    }

    private static long bindingStopTimeoutNanos(Duration gracefulPeriod) {
        long timeoutNanos = timeoutNanos(gracefulPeriod);
        if (timeoutNanos > Long.MAX_VALUE - BINDING_STOP_COMPLETION_MARGIN_NANOS) {
            return Long.MAX_VALUE;
        }
        return timeoutNanos + BINDING_STOP_COMPLETION_MARGIN_NANOS;
    }

    private static long timeoutNanos(Duration gracefulPeriod) {
        if (gracefulPeriod.isNegative()) {
            return 0;
        }
        try {
            return gracefulPeriod.toNanos();
        } catch (ArithmeticException _) {
            return Long.MAX_VALUE;
        }
    }

    private record ListenerBindingPlanContext(String name,
                                              Optional<SocketAddress> bindAddress,
                                              String host,
                                              int port) implements BindingPlanContext {
    }

    private final class ListenerTransportBindingContext implements TcpTransportBindingContext {
        @Override
        public String name() {
            return socketName;
        }

        @Override
        public Router router() {
            return router;
        }

        @Override
        public Timer timer() {
            return idleConnectionTimer;
        }

        @Override
        public Limit requestLimit() {
            return requestLimit;
        }

        @Override
        public OptionalInt boundPort() {
            return ServerListener.this.boundPort();
        }

        @Override
        public void fatalBindingFailure(TransportBinding binding, Throwable cause) {
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(cause, "cause");
            fatalBindingFailureHandler.handle(ServerListener.this, binding, cause);
        }

        @Override
        public TcpTransportBinding createTcpTransportBinding(TcpTransportConfig config) {
            return ServerListener.this.createTcpTransportBinding(this, config);
        }

        @Override
        public MediaContext mediaContext() {
            return ServerListener.this.mediaContext();
        }

        @Override
        public ContentEncodingContext contentEncodingContext() {
            return ServerListener.this.contentEncodingContext();
        }

        @Override
        public DirectHandlers directHandlers() {
            return ServerListener.this.directHandlers();
        }

        @Override
        public Context context() {
            return ServerListener.this.context();
        }

        @Override
        public ListenerConfig config() {
            return ServerListener.this.config();
        }

        @Override
        public ExecutorService executor() {
            return ServerListener.this.executor();
        }
    }

    private record BindingStop(TransportBinding binding, Future<TransportBinding.ShutdownResult> future) {
    }

    private record BindingStopResult(Throwable failure, boolean forceSharedExecutorShutdown) {
    }

    private record BindingConfigId(String type, String name) {
    }

    @FunctionalInterface
    interface FatalBindingFailureHandler {
        void handle(ServerListener listener, TransportBinding binding, Throwable cause);
    }

}
