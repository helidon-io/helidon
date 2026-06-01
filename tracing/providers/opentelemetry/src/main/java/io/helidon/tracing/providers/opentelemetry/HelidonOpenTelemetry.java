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
package io.helidon.tracing.providers.opentelemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Wrapper;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import static io.opentelemetry.context.Context.current;

/**
 * Open Telemetry factory methods to create wrappers for Open Telemetry types.
 */
public final class HelidonOpenTelemetry {
    static final String DEFAULT_SERVICE_NAME = "helidon-service";

    private static final String TELEMETRY_CONFIG_KEY = "telemetry";
    private static final String TRACING_CONFIG_KEY = OpenTelemetryTracerConfigBlueprint.TRACING_CONFIG_KEY;

    /**
     * OpenTelemetry property for indicating if the Java agent is present.
     */
    public static final String OTEL_AGENT_PRESENT_PROPERTY = "otel.agent.present";

    /**
     * OpenTelemetry property for the Java agent.
     */
    public static final String IO_OPENTELEMETRY_JAVAAGENT = "io.opentelemetry.javaagent";

    static final String UNSUPPORTED_OPERATION_MESSAGE = "Span listener attempted to invoke an illegal operation";
    static final String USE_EXISTING_OTEL = "io.helidon.telemetry.otel.use-existing-instance";
    private static final String GLOBAL_OPEN_TELEMETRY_ALREADY_SET = "OpenTelemetry global is already initialized. "
            + "Set the OpenTelemetry instance in the Helidon service registry and enable OpenTelemetry global publishing "
            + "before any code initializes GlobalOpenTelemetry, or configure Helidon to use the existing OpenTelemetry global.";
    private static final String OTEL_AUTO_CONFIGURE = "otel.java.global-autoconfigure.enabled";
    private static final String OTEL_AUTO_CONFIGURE_ENV = "OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED";

    private static final System.Logger LOGGER = System.getLogger(HelidonOpenTelemetry.class.getName());
    private static final LazyValue<List<SpanListener>> SPAN_LISTENERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(SpanListener.class)).asList());
    private static final ReentrantLock APPLICATION_TELEMETRY_LOCK = new ReentrantLock();
    private static final Map<ServiceRegistry, ApplicationTelemetry> APPLICATION_TELEMETRY =
            new WeakHashMap<>();


    private HelidonOpenTelemetry() {
    }

    /**
     * Selects and initializes the application-wide OpenTelemetry instance using Helidon configuration and ownership
     * strategies.
     *
     * @param config root configuration
     * @param strategies ownership strategies
     * @return application-wide OpenTelemetry instance
     */
    static OpenTelemetry applicationOpenTelemetry(Config config, List<OpenTelemetryOwnershipStrategy> strategies) {
        return selectApplicationTelemetry(config, strategies, null).openTelemetry();
    }

    static OpenTelemetry applicationOpenTelemetry(ServiceRegistry registry,
                                                  Config config,
                                                  List<OpenTelemetryOwnershipStrategy> strategies) {
        return applicationTelemetry(registry, config, strategies, () -> null).openTelemetry();
    }

    static OpenTelemetry applicationOpenTelemetry(ServiceRegistry registry,
                                                  Config config,
                                                  List<OpenTelemetryOwnershipStrategy> strategies,
                                                  OpenTelemetry registryOpenTelemetry) {
        return applicationTelemetry(registry, config, strategies, () -> registryOpenTelemetry).openTelemetry();
    }

    static OpenTelemetry applicationOpenTelemetry(ServiceRegistry registry,
                                                  Config config,
                                                  List<OpenTelemetryOwnershipStrategy> strategies,
                                                  Supplier<OpenTelemetry> registryOpenTelemetry) {
        return applicationTelemetry(registry, config, strategies, registryOpenTelemetry).openTelemetry();
    }

    /**
     * Wrap an open telemetry tracer.
     *
     * @param telemetry open telemetry instance
     * @param tracer    tracer
     * @param tags      tracer tags
     * @return Helidon {@link io.helidon.tracing.Tracer}
     */
    public static io.helidon.tracing.Tracer create(OpenTelemetry telemetry, Tracer tracer, Map<String, String> tags) {
        return OpenTelemetryTracerBuilder.create()
                .serviceName(DEFAULT_SERVICE_NAME)
                .openTelemetry(telemetry)
                .delegate(tracer)
                .registerGlobal(false)
                .tracerTags(tags)
                .build();
    }

    /**
     * Wrap an open telemetry span.
     *
     * @param span open telemetry span
     * @return Helidon {@link io.helidon.tracing.Span}
     */
    public static io.helidon.tracing.Span create(Span span) {
        return new OpenTelemetrySpan(span, SPAN_LISTENERS.get());
    }

    /**
     * Wrap an open telemetry span.
     *
     * @param span open telemetry span
     * @param baggage open telemetry baggage
     * @return Helidon {@link io.helidon.tracing.Span}
     */
    public static io.helidon.tracing.Span create(Span span, Baggage baggage) {
        return new OpenTelemetrySpan(span, baggage, SPAN_LISTENERS.get());
    }

    /**
     * Wrap an open telemetry span builder.
     *
     * @param spanBuilder open telemetry span builder
     * @param helidonTracer Helidon {@link io.helidon.tracing.Tracer} to use in creating the wrapping span builder
     * @return Helidon {@link io.helidon.tracing.Span.Builder}
     */
    public static io.helidon.tracing.Span.Builder<?> create(SpanBuilder spanBuilder,
                                                            io.helidon.tracing.Tracer helidonTracer) {

        return new OpenTelemetrySpanBuilder(spanBuilder, helidonTracer.unwrap(OpenTelemetryTracer.class).spanListeners());
    }

    /**
     * Wrap an Open Telemetry context.
     *
     * @param context Open Telemetry context
     * @return Helidon {@link io.helidon.tracing.SpanContext}
     */
    public static io.helidon.tracing.SpanContext create(Context context) {
        return new OpenTelemetrySpanContext(context);
    }

    /**
     * Returns an OpenTelemetry {@link Tracer} implementation which provides {@link SpanBuilder} and
     * {@link io.opentelemetry.api.trace.Span} instances capable of notifying registered {@link io.helidon.tracing.SpanListener}
     * objects.
     * <p>
     * The returned callback-enabled tracer is prepared with any service-loaded {@code SpanListener} objects. If the calling
     * code wants Helidon to notify other listeners it must register them explicitly as shown in the example (which
     * shows fully-qualified types for clarity).
     * {@snippet :
     *
     * io.opentelemetry.api.trace.Tracer nativeOtelTracer; // previously-assigned
     * io.helidon.tracing.api.SpanListener mySpanListener; // previously-assigned
     *
     * io.opentelemetry.api.trace.Tracer callbackEnabledOtelTracer = HelidonOpenTelemetry.callbackEnabledFrom(nativeOtelTracer);
     * callbackEnabledOtelTracer.unwrap(io.helidon.tracing.api.Tracer.class).register(mySpanListener);
     *}
     * Code which has a Helidon {@code Tracer} should instead invoke {@link #callbackEnabledFrom(io.helidon.tracing.Tracer)},
     * passing the Helidon tracer. Then Helidon will automatically notify all listeners already registered with the
     * Helidon tracer.
     *
     * @param otelTracer the native OpenTelemetry {@code Tracer} to expose as a separate callback-enabled OpenTelemetry
     *                   {@code Tracer}
     * @param <T>        specific type of the tracer to return
     * @return an OpenTelemetry {@code Tracer} and {@link io.helidon.tracing.Wrapper} able to notify span lifecycle listeners
     */
    public static <T extends Tracer & Wrapper> T callbackEnabledFrom(Tracer otelTracer) {
        return callbackEnabledFrom(OpenTelemetryTracerBuilder.create()
                                           .serviceName("callback-enabled-otel-tracer")
                                           .openTelemetry(GlobalOpenTelemetry.getOrNoop())
                                           .delegate(otelTracer)
                                           .registerGlobal(false)
                                           .build());
    }

    /**
     * Returns an OpenTelemetry {@link io.opentelemetry.api.trace.Tracer} implementation which provides
     * {@link io.opentelemetry.api.trace.SpanBuilder} and {@link io.opentelemetry.api.trace.Span} instances capable of
     * notifying {@link io.helidon.tracing.SpanListener} objects registered with the supplied Helidon tracer.
     *
     * @param helidonTracer the Helidon {@code Tracer} to expose as a callback-enabled OpenTelemetry {@code Tracer}
     * @param <T>           specific type of the tracer to return
     * @return an OpenTelemetry {@code Tracer} and {@link io.helidon.tracing.Wrapper} able to notify span lifecycle listeners
     */
    public static <T extends Tracer & Wrapper> T callbackEnabledFrom(io.helidon.tracing.Tracer helidonTracer) {
        return (T) WrappedTracer.create(helidonTracer);
    }

    /**
     * Returns an OpenTelemetry {@link io.opentelemetry.api.trace.Span} implementation which delegates to the {@code Span}
     * managed by the supplied Helidon {@link io.helidon.tracing.Span} and which also provides
     * {@link io.opentelemetry.context.Scope} instances capable of notifying registered
     * {@link io.helidon.tracing.SpanListener} objects.
     * <p>
     * This method internally creates a new Helidon {@link io.helidon.tracing.Span} to perform the notifications.
     * Code which already has a Helidon {@code Span} from which it unwrapped the OpenTelemetry span should instead invoke
     * {@link #callbackEnabledFrom(io.helidon.tracing.Span)}, passing the Helidon tracer.
     *
     * @param otelSpan the native OpenTelemetry {@code Span} to expose as a callback-enabled OpenTelemetry {@code Span}
     * @param <T>      specific type of the Span to return
     * @return an OpenTelemetry {@code Span} and {@link io.helidon.tracing.Wrapper} which also performs lifecycle callbacks
     */
    public static <T extends Span & Wrapper> T callbackEnabledFrom(Span otelSpan) {
        return (T) WrappedSpan.create(create(otelSpan));
    }

    /**
     * Returns a {@link io.opentelemetry.api.trace.Span} implementation which delegates to the provided Helidon
     * {@link io.helidon.tracing.Span}, thereby notifying registered {@link io.helidon.tracing.SpanListener}
     * objects of span lifecycle events.
     *
     * @param <T> specific type of the {@code Span} to return
     * @param helidonSpan the Helidon {@code Span} to expose as a callback-enabled OpenTelemetry {@code Span}
     * @return an OpenTelemetry {@code Span} and {@link io.helidon.tracing.Wrapper} which also performs lifecycle callbacks
     **/
    public static <T extends Span & Wrapper> T callbackEnabledFrom(io.helidon.tracing.Span helidonSpan) {
        return (T) WrappedSpan.create(helidonSpan);
    }

    /**
     * Check if OpenTelemetry is present by indirect properties.
     * This class does best explicit check if OTEL_AGENT_PRESENT_PROPERTY config property is set and uses its
     * value to set the behaviour of OpenTelemetry producer.
     *
     * If the value is not explicitly set, the detector does best effort to estimate indirect means if the agent is present.
     * This detector may stop working if OTEL changes the indirect indicators.
     */
    public static final class AgentDetector {

        //Private constructor for a utility class.
        private AgentDetector() {
        }

        /**
         * Check if the OTEL Agent is present.
         *
         * @param config Configuration
         * @return boolean
         */
        public static boolean isAgentPresent(Config config) {

            //Explicitly check if agent property is set
            if (config != null) {
                Optional<Boolean> agentPresent = config.get(OTEL_AGENT_PRESENT_PROPERTY).asBoolean().asOptional();
                if (agentPresent.isPresent()) {
                    return agentPresent.get();
                }
            }

            if (checkContext() || checkSystemProperties()) {
                if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
                    LOGGER.log(System.Logger.Level.INFO, "OpenTelemetry Agent detected");
                }
                return true;
            }
            return false;
        }

        /**
         * Return whether the user has requested that Helidon use an existing global OpenTelemetry instance rather than
         * creating one itself; specifying that the OpenTelemetry agent is present automatically implies using the agent's
         * existing instance.
         *
         * @param config configuration potentially containing the setting
         * @return true if Helidon is configured to use an existing global OpenTelemetry instance; false otherwise
         */
        public static boolean useExistingGlobalOpenTelemetry(Config config) {
            return isAgentPresent(config) || config.get(USE_EXISTING_OTEL).asBoolean().orElse(false);
        }

        private static boolean checkSystemProperties() {
            return System.getProperties().stringPropertyNames()
                    .stream()
                    .anyMatch(property -> property.contains(IO_OPENTELEMETRY_JAVAAGENT));
        }

        private static boolean checkContext() {
            return current().getClass().getName().contains("agent");
        }

    }

    /**
     * Invokes listeners known to the specified Helidon span using the provided operation; intended for Helidon internal use
     * only.
     *
     * @param helidonSpan Helidon {@link io.helidon.tracing.Span} whose listeners are to be invoked
     * @param logger      logger for reporting exceptions during listener invocations
     * @param operation   operation to invoke on each listener
     */
    public static void invokeListeners(io.helidon.tracing.Span helidonSpan,
                                       System.Logger logger,
                                       Consumer<SpanListener> operation) {
        invokeListeners(helidonSpan.unwrap(OpenTelemetrySpan.class).spanListeners(), logger, operation);
    }

    static void invokeListeners(List<SpanListener> spanListeners, System.Logger logger, Consumer<SpanListener> operation) {
        if (spanListeners.isEmpty()) {
            return;
        }
        List<Throwable> throwables = new ArrayList<>();
        for (SpanListener listener : spanListeners) {
            try {
                operation.accept(listener);
            } catch (Throwable t) {
                throwables.add(t);
            }
        }

        Throwable throwableToLog = null;
        if (throwables.size() == 1) {
            // If only one exception is present, propagate that one in the log record.
            throwableToLog = throwables.getFirst();
        } else if (!throwables.isEmpty()) {
            // Propagate a RuntimeException with multiple suppressed exceptions in the log record.
            throwableToLog = new RuntimeException();
            throwables.forEach(throwableToLog::addSuppressed);
        }
        if (throwableToLog != null) {
            logger.log(System.Logger.Level.WARNING, "Error(s) from listener(s)", throwableToLog);
        }
    }

    static io.helidon.tracing.Tracer applicationTracer(ServiceRegistry registry,
                                                       Config config,
                                                       List<OpenTelemetryOwnershipStrategy> strategies,
                                                       OpenTelemetry openTelemetry) {
        OpenTelemetry required = Objects.requireNonNull(openTelemetry);
        return applicationTelemetry(registry, config, strategies, () -> required).tracer();
    }

    private static ApplicationTelemetry applicationTelemetry(ServiceRegistry registry,
                                                            Config config,
                                                            List<OpenTelemetryOwnershipStrategy> strategies,
                                                            Supplier<OpenTelemetry> registryOpenTelemetry) {
        Objects.requireNonNull(registry);
        Objects.requireNonNull(config);
        Objects.requireNonNull(strategies);
        Objects.requireNonNull(registryOpenTelemetry);

        APPLICATION_TELEMETRY_LOCK.lock();
        try {
            ApplicationTelemetry existing = APPLICATION_TELEMETRY.get(registry);
            if (existing != null) {
                return existing;
            }
        } finally {
            APPLICATION_TELEMETRY_LOCK.unlock();
        }

        OpenTelemetry openTelemetry = registryOpenTelemetry.get();

        APPLICATION_TELEMETRY_LOCK.lock();
        try {
            ApplicationTelemetry existing = APPLICATION_TELEMETRY.get(registry);
            if (existing != null) {
                return existing;
            }

            ApplicationTelemetry selected = selectApplicationTelemetry(config, strategies, openTelemetry);
            APPLICATION_TELEMETRY.put(registry, selected);
            return selected;
        } finally {
            APPLICATION_TELEMETRY_LOCK.unlock();
        }
    }

    private static ApplicationTelemetry selectApplicationTelemetry(Config rootConfig,
                                                                  List<OpenTelemetryOwnershipStrategy> strategies,
                                                                  OpenTelemetry registryOpenTelemetry) {
        List<OpenTelemetryOwnershipStrategy> activeStrategies = activeStrategies(rootConfig, strategies);
        OpenTelemetryOwnershipStrategy selectedStrategy = activeStrategies.isEmpty() ? null : activeStrategies.getFirst();
        String serviceName = serviceName(rootConfig, selectedStrategy);
        boolean publishGlobalOpenTelemetry = selectedStrategy != null
                && selectedStrategy.globalOpenTelemetry(rootConfig);

        if (registryOpenTelemetry != null) {
            if (publishGlobalOpenTelemetry) {
                publishGlobalOpenTelemetry(registryOpenTelemetry);
            }
            return applicationTelemetry(rootConfig, selectedStrategy, registryOpenTelemetry, serviceName, null);
        }

        if (selectedStrategy == null && telemetryDisabled(rootConfig)) {
            return applicationTelemetry(rootConfig, null, OpenTelemetry.noop(), serviceName, null);
        }

        if (AgentDetector.useExistingGlobalOpenTelemetry(rootConfig)
                || (selectedStrategy == null && useNoOwnerGlobalOpenTelemetry())) {
            return applicationTelemetry(rootConfig, selectedStrategy, GlobalOpenTelemetry.get(), serviceName, null);
        }

        if (selectedStrategy != null) {
            if (publishGlobalOpenTelemetry) {
                OpenTelemetrySelection selection = createAndPublishGlobalOpenTelemetrySelection(rootConfig, selectedStrategy);
                return applicationTelemetry(rootConfig,
                                            selectedStrategy,
                                            selection.openTelemetry(),
                                            serviceName,
                                            selection.closeable());
            }
            OpenTelemetry openTelemetry = selectedStrategy.create(rootConfig);
            return applicationTelemetry(rootConfig,
                                        selectedStrategy,
                                        openTelemetry,
                                        serviceName,
                                        closeable(openTelemetry));
        }

        OpenTelemetry openTelemetry = OpenTelemetry.noop();
        return applicationTelemetry(rootConfig, null, openTelemetry, serviceName, null);
    }

    private static OpenTelemetrySelection createAndPublishGlobalOpenTelemetrySelection(Config rootConfig,
                                                                                      OpenTelemetryOwnershipStrategy strategy) {
        if (GlobalOpenTelemetry.isSet()) {
            throw new IllegalStateException(GLOBAL_OPEN_TELEMETRY_ALREADY_SET);
        }

        AtomicReference<RuntimeException> creationFailure = new AtomicReference<>();
        AtomicReference<OpenTelemetry> createdOpenTelemetry = new AtomicReference<>();
        try {
            GlobalOpenTelemetry.set(() -> {
                try {
                    OpenTelemetry openTelemetry = strategy.create(rootConfig);
                    createdOpenTelemetry.set(openTelemetry);
                    return openTelemetry;
                } catch (RuntimeException e) {
                    creationFailure.set(e);
                    throw e;
                }
            });
        } catch (IllegalStateException e) {
            if (e == creationFailure.get()) {
                throw e;
            }
            throw new IllegalStateException(GLOBAL_OPEN_TELEMETRY_ALREADY_SET, e);
        }
        return new OpenTelemetrySelection(GlobalOpenTelemetry.get(), closeable(createdOpenTelemetry.get()));
    }

    private static void publishGlobalOpenTelemetry(OpenTelemetry openTelemetry) {
        if (GlobalOpenTelemetry.isSet()) {
            throw new IllegalStateException(GLOBAL_OPEN_TELEMETRY_ALREADY_SET);
        }

        try {
            GlobalOpenTelemetry.set(openTelemetry);
        } catch (IllegalStateException e) {
            throw new IllegalStateException(GLOBAL_OPEN_TELEMETRY_ALREADY_SET, e);
        }
    }

    private static List<OpenTelemetryOwnershipStrategy> activeStrategies(Config rootConfig,
                                                                         List<OpenTelemetryOwnershipStrategy> strategies) {
        return strategies.stream()
                .map(Objects::requireNonNull)
                .filter(strategy -> strategy.active(rootConfig))
                .toList();
    }

    private static ApplicationTelemetry applicationTelemetry(Config rootConfig,
                                                            OpenTelemetryOwnershipStrategy strategy,
                                                            OpenTelemetry openTelemetry,
                                                            String serviceName,
                                                            AutoCloseable closeable) {
        io.helidon.tracing.Tracer tracer;
        if (tracingDisabled(rootConfig) || (strategy == null && telemetryDisabled(rootConfig))) {
            tracer = io.helidon.tracing.Tracer.noOp();
        } else if (strategy == null) {
            tracer = create(openTelemetry, openTelemetry.getTracer(serviceName), Map.of());
        } else {
            tracer = strategy.createTracer(rootConfig, openTelemetry);
        }
        if (tracer.enabled()) {
            OpenTelemetryTracerProvider.applicationOpenTelemetrySelected();
        }
        if (strategy != null) {
            strategy.selected(rootConfig, openTelemetry);
        }
        return new ApplicationTelemetry(openTelemetry, tracer, closeable);
    }

    private static AutoCloseable closeable(OpenTelemetry openTelemetry) {
        return openTelemetry instanceof AutoCloseable closeable ? closeable : null;
    }

    static void clearApplicationTelemetry(ServiceRegistry registry) {
        ApplicationTelemetry telemetry;
        APPLICATION_TELEMETRY_LOCK.lock();
        try {
            telemetry = APPLICATION_TELEMETRY.remove(registry);
        } finally {
            APPLICATION_TELEMETRY_LOCK.unlock();
        }
        if (telemetry != null) {
            telemetry.close();
        }
    }

    static boolean applicationTelemetryCached(ServiceRegistry registry) {
        APPLICATION_TELEMETRY_LOCK.lock();
        try {
            return APPLICATION_TELEMETRY.containsKey(registry);
        } finally {
            APPLICATION_TELEMETRY_LOCK.unlock();
        }
    }

    private static boolean tracingDisabled(Config rootConfig) {
        Config tracingConfig = rootConfig.get(TRACING_CONFIG_KEY);
        return tracingConfig.exists()
                && !tracingConfig.get("enabled").asBoolean().orElse(true);
    }

    private static boolean telemetryDisabled(Config rootConfig) {
        Config telemetryConfig = rootConfig.get(TELEMETRY_CONFIG_KEY);
        return telemetryConfig.exists()
                && !telemetryConfig.get("enabled").asBoolean().orElse(true);
    }

    private static boolean useNoOwnerGlobalOpenTelemetry() {
        return GlobalOpenTelemetry.isSet()
                || autoConfigureGlobalOpenTelemetry();
    }

    static boolean autoConfigureGlobalOpenTelemetry(String propertyValue, String envValue) {
        if (propertyValue != null) {
            return Boolean.parseBoolean(propertyValue);
        }
        return Boolean.parseBoolean(envValue);
    }

    private static boolean autoConfigureGlobalOpenTelemetry() {
        return autoConfigureGlobalOpenTelemetry(System.getProperty(OTEL_AUTO_CONFIGURE),
                                                System.getenv(OTEL_AUTO_CONFIGURE_ENV));
    }

    private static String serviceName(Config rootConfig, OpenTelemetryOwnershipStrategy strategy) {
        if (strategy != null) {
            return strategy.serviceName(rootConfig);
        }
        Optional<String> telemetryServiceName = serviceNameIfConfigured(rootConfig.get(TELEMETRY_CONFIG_KEY));
        if (telemetryServiceName.isPresent()) {
            return telemetryServiceName.get();
        }

        Optional<String> tracingServiceName = serviceNameIfConfigured(rootConfig.get(TRACING_CONFIG_KEY));
        if (tracingServiceName.isPresent()) {
            return tracingServiceName.get();
        }

        return DEFAULT_SERVICE_NAME;
    }

    private static Optional<String> serviceNameIfConfigured(Config config) {
        if (!config.exists()) {
            return Optional.empty();
        }
        return config.get("service").asString().asOptional();
    }

    private static final class ApplicationTelemetry {
        private final OpenTelemetry openTelemetry;
        private final io.helidon.tracing.Tracer tracer;
        private final AutoCloseable closeable;

        private ApplicationTelemetry(OpenTelemetry openTelemetry,
                                     io.helidon.tracing.Tracer tracer,
                                     AutoCloseable closeable) {
            this.openTelemetry = openTelemetry;
            this.tracer = tracer;
            this.closeable = closeable;
        }

        private OpenTelemetry openTelemetry() {
            return openTelemetry;
        }

        private io.helidon.tracing.Tracer tracer() {
            return tracer;
        }

        private void close() {
            if (closeable == null) {
                return;
            }

            try {
                closeable.close();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to close application OpenTelemetry", e);
            }
        }
    }

    private record OpenTelemetrySelection(OpenTelemetry openTelemetry, AutoCloseable closeable) {
    }
}
