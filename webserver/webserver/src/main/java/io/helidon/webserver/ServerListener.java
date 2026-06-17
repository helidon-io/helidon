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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.Limit.InitializationContext;
import io.helidon.common.context.Context;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.spi.PortTransportBinding;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingFactory;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

class ServerListener implements TransportBindingContext, ListenerContext {
    private static final System.Logger LOGGER = System.getLogger(ServerListener.class.getName());
    private static final String EXPLICIT_SSL_CONTEXT_RELOAD_NOT_SUPPORTED =
            "TLS cannot be reloaded when an explicit instance of SSL context was used to create it";
    private static final long BINDING_STOP_COMPLETION_MARGIN_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

    private final String socketName;
    private final ListenerConfig listenerConfig;
    private final Router router;
    private final ExecutorService sharedExecutor;
    private final DirectHandlers directHandlers;
    private final Tls tls;
    private final VirtualHostRegistry virtualHosts;
    private final SocketAddress configuredAddress;
    private final Duration gracePeriod;
    private final Timer idleConnectionTimer;
    private final FatalListenerFailureHandler fatalListenerFailureHandler;
    private final List<TransportBinding> transportBindings;

    private final MediaContext mediaContext;
    private final ContentEncodingContext contentEncodingContext;
    private final Context context;
    private final Limit connectionLimit;
    private final Limit requestLimit;

    private final AtomicBoolean lifecycleStarted = new AtomicBoolean();

    ServerListener(String socketName,
                   ListenerConfig listenerConfig,
                   Router router,
                   Context serverContext,
                   Timer idleConnectionTimer,
                   MediaContext defaultMediaContext,
                   ContentEncodingContext defaultContentEncodingContext,
                   DirectHandlers defaultDirectHandlers,
                   FatalListenerFailureHandler fatalListenerFailureHandler) {

        List<ProtocolConfig> protocolConfigs = listenerConfig.protocols();
        if (listenerConfig.maxConcurrentRequests() == -1) {
            this.requestLimit = listenerConfig.concurrencyLimit()
                    .orElseGet(FixedLimit::create); // unlimited unless configured
        } else {
            this.requestLimit = FixedLimit.builder()
                    .permits(listenerConfig.maxConcurrentRequests())
                    .build();
        }

        InitializationContext limitContext = limitContext(socketName);
        if (listenerConfig.maxConnections() == -1 || listenerConfig.maxConnections() == 0) {
            this.connectionLimit = FixedLimit.create();
        } else {
            this.connectionLimit = FixedLimit.builder()
                    .queueLength(Math.max(1, listenerConfig.bindings().size()))
                    .queueTimeout(Duration.ofMinutes(5))
                    .permits(listenerConfig.maxConnections())
                    .build();
        }
        this.connectionLimit.init(limitContext);
        this.requestLimit.init(limitContext);

        this.socketName = socketName;
        this.listenerConfig = listenerConfig;
        this.tls = listenerConfig.tls().orElseGet(() -> Tls.builder().enabled(false).build());
        this.virtualHosts = VirtualHostRegistry.create(socketName, listenerConfig, tls);
        this.directHandlers = listenerConfig.directHandlers().orElse(defaultDirectHandlers);
        this.mediaContext = listenerConfig.mediaContext().orElse(defaultMediaContext);
        this.contentEncodingContext = listenerConfig.contentEncoding().orElse(defaultContentEncodingContext);
        this.context = listenerConfig.listenerContext().orElseGet(() -> Context.builder()
                .id("listener-" + socketName)
                .parent(serverContext)
                .build());
        this.gracePeriod = listenerConfig.shutdownGracePeriod();

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
        this.fatalListenerFailureHandler = Objects.requireNonNull(fatalListenerFailureHandler, "fatalListenerFailureHandler");
        this.transportBindings = planTransportBindings(protocolConfigs);
        int maxConnections = listenerConfig.maxConnections();
        int bindingCount = transportBindings.size();
        if (maxConnections > 0 && bindingCount > maxConnections) {
            LOGGER.log(WARNING, "Listener " + socketName + " has " + bindingCount
                    + " active transport bindings, but maxConnections is " + maxConnections
                    + ". Connection permits are shared across transport bindings, so at least one binding may never "
                    + "accept a connection. Configure maxConnections to at least the number of active transport bindings "
                    + "or leave it unlimited.");
        }
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
    public ListenerContext listenerContext() {
        return this;
    }

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
    public Limit connectionLimit() {
        return connectionLimit;
    }

    @Override
    public ListenerTlsContext listenerTls() {
        return virtualHosts;
    }

    @Override
    public String toString() {
        return socketName + " (" + configuredAddress + ")";
    }

    int port() {
        return boundPort().orElse(-1);
    }

    @Override
    public OptionalInt boundPort() {
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

    SocketAddress configuredAddress() {
        return configuredAddress;
    }

    @Override
    public void fatalBindingFailure(TransportBinding binding, Throwable cause) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(cause, "cause");
        fatalListenerFailureHandler.handle(this,
                                           new IllegalStateException("Fatal failure in transport binding " + binding.name()
                                                                             + " of type \"" + binding.type() + "\" at "
                                                                             + binding.configuredEndpoint(),
                                                                     cause));
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
        if (!tls.enabled()) {
            throw new IllegalArgumentException("TLS is not enabled on the socket " + socketName
                                                       + " and therefore cannot be reloaded");
        }
        tls.reload(material);
    }

    void reloadVirtualHostTls(TlsMaterial material, String host) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(host, "host");
        if (!tls.enabled()) {
            throw new IllegalArgumentException("TLS is not enabled on the socket " + socketName
                                                       + " and therefore cannot be reloaded");
        }
        virtualHosts.reloadTls(material, host);
    }

    void suspend() {
        Throwable failure = null;
        for (TransportBinding binding : transportBindings) {
            try {
                binding.suspend();
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }
        throwIfCheckpointSuspendFailed(failure);
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

    private List<TransportBinding> planTransportBindings(List<ProtocolConfig> protocolConfigs) {
        BindingPlanContext planContext = new ListenerBindingPlanContext();
        List<TransportBinding> activeBindings = new ArrayList<>();
        Set<BindingConfigId> bindingConfigs = new LinkedHashSet<>();
        Set<String> bindingNames = new LinkedHashSet<>();

        for (TransportBindingFactory factory : listenerConfig.bindings()) {
            if (!factory.enabled()) {
                continue;
            }
            if (!bindingConfigs.add(new BindingConfigId(factory.type(), factory.name()))) {
                throw new IllegalArgumentException("Duplicate transport binding factory \"" + factory.name()
                                                           + "\" of type \"" + factory.type()
                                                           + "\" on listener " + socketName);
            }
            if (!factory.canBind(planContext)) {
                if (factory.required()) {
                    throw new IllegalArgumentException("Listener " + socketName
                                                               + " requires transport binding " + factory.name()
                                                               + " of type \"" + factory.type()
                                                               + "\", but this binding cannot bind with the listener "
                                                               + "configuration");
                }
                continue;
            }

            TransportBinding binding = Objects.requireNonNull(factory.create(this),
                                                              "Transport binding factory returned null");
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
        validateProtocolTransportBindings(protocolConfigs, activeBindings);

        return List.copyOf(activeBindings);
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
            if (virtualHosts.virtualHostsEnabled() && security != TransportBinding.Security.TLS) {
                throw new IllegalArgumentException("Listener " + socketName
                                                           + " has TLS virtual hosts configured, but transport binding "
                                                           + binding.name()
                                                           + " of type \"" + binding.type()
                                                           + "\" does not use listener TLS");
            }
        }
    }

    private void validateProtocolTransportBindings(List<ProtocolConfig> protocolConfigs,
                                                   List<TransportBinding> bindings) {
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
                                                           + ", active binding type(s) " + activeTypes);
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
        BindingStopResult bindingStopResult = awaitBindingStops(bindingStops, gracePeriod, stopAtNanos);
        failure = LifecycleFailures.add(failure, bindingStopResult.failure());

        if (bindingStopResult.forceSharedExecutorShutdown()) {
            try {
                forceShutdownSharedExecutor();
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }

        if (!bindingStopResult.forceSharedExecutorShutdown()) {
            try {
                // Shutdown shared executor
                shutdownSharedExecutor(remainingNanos(stopAtNanos));
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
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

    private final class ListenerBindingPlanContext implements BindingPlanContext {
        @Override
        public ListenerConfig listenerConfig() {
            return listenerConfig;
        }
    }

    private record BindingStop(TransportBinding binding, Future<TransportBinding.ShutdownResult> future) {
    }

    private record BindingStopResult(Throwable failure, boolean forceSharedExecutorShutdown) {
    }

    private record BindingConfigId(String type, String name) {
    }

    @FunctionalInterface
    interface FatalListenerFailureHandler {
        void handle(ServerListener listener, Throwable cause);
    }

}
