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

package io.helidon.telemetry.opentelemetry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.telemetry.opentelemetry.spi.OpenTelemetryOwnershipStrategy;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

import static io.opentelemetry.context.Context.current;

@Api.Preview
public final class ApplicationOpenTelemetry {
    static final String OTEL_AGENT_PRESENT_PROPERTY = "otel.agent.present";
    static final String IO_OPENTELEMETRY_JAVAAGENT = "io.opentelemetry.javaagent";
    static final String USE_EXISTING_OTEL = "io.helidon.telemetry.otel.use-existing-instance";

    private static final String TELEMETRY_CONFIG_KEY = "telemetry";
    private static final String GLOBAL_OPEN_TELEMETRY_ALREADY_SET = "OpenTelemetry global is already initialized. "
            + "Set the OpenTelemetry instance in the Helidon service registry and enable OpenTelemetry global publishing "
            + "before any code initializes GlobalOpenTelemetry, or configure Helidon to use the existing OpenTelemetry global.";
    private static final String OTEL_AUTO_CONFIGURE = "otel.java.global-autoconfigure.enabled";
    private static final String OTEL_AUTO_CONFIGURE_ENV = "OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED";

    private static final System.Logger LOGGER = System.getLogger(ApplicationOpenTelemetry.class.getName());
    private static final ReentrantLock APPLICATION_TELEMETRY_LOCK = new ReentrantLock();
    private static final Map<ServiceRegistry, ApplicationTelemetry> APPLICATION_TELEMETRY =
            new WeakHashMap<>();

    private ApplicationOpenTelemetry() {
    }

    public static OpenTelemetry applicationOpenTelemetry(Config config, List<OpenTelemetryOwnershipStrategy> strategies) {
        return selectApplicationTelemetry(config, strategies, null).openTelemetry();
    }

    public static OpenTelemetry applicationOpenTelemetry(ServiceRegistry registry,
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
                return existing.openTelemetry();
            }
        } finally {
            APPLICATION_TELEMETRY_LOCK.unlock();
        }

        OpenTelemetry openTelemetry = registryOpenTelemetry.get();

        APPLICATION_TELEMETRY_LOCK.lock();
        try {
            ApplicationTelemetry existing = APPLICATION_TELEMETRY.get(registry);
            if (existing != null) {
                return existing.openTelemetry();
            }

            ApplicationTelemetry selected = selectApplicationTelemetry(config, strategies, openTelemetry);
            APPLICATION_TELEMETRY.put(registry, selected);
            return selected.openTelemetry();
        } finally {
            APPLICATION_TELEMETRY_LOCK.unlock();
        }
    }

    public static void clearApplicationTelemetry(ServiceRegistry registry) {
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

    public static boolean applicationTelemetryCached(ServiceRegistry registry) {
        APPLICATION_TELEMETRY_LOCK.lock();
        try {
            return APPLICATION_TELEMETRY.containsKey(registry);
        } finally {
            APPLICATION_TELEMETRY_LOCK.unlock();
        }
    }

    static boolean autoConfigureGlobalOpenTelemetry(String propertyValue, String envValue) {
        if (propertyValue != null) {
            return Boolean.parseBoolean(propertyValue);
        }
        return Boolean.parseBoolean(envValue);
    }

    private static ApplicationTelemetry selectApplicationTelemetry(Config rootConfig,
                                                                  List<OpenTelemetryOwnershipStrategy> strategies,
                                                                  OpenTelemetry registryOpenTelemetry) {
        List<OpenTelemetryOwnershipStrategy> activeStrategies = activeStrategies(rootConfig, strategies);
        OpenTelemetryOwnershipStrategy selectedStrategy = activeStrategies.isEmpty() ? null : activeStrategies.getFirst();
        boolean publishGlobalOpenTelemetry = selectedStrategy != null
                && selectedStrategy.global(rootConfig);

        if (registryOpenTelemetry != null) {
            if (publishGlobalOpenTelemetry) {
                publishGlobalOpenTelemetry(registryOpenTelemetry);
            }
            return applicationTelemetry(rootConfig, selectedStrategy, registryOpenTelemetry, null);
        }

        if (selectedStrategy == null && telemetryDisabled(rootConfig)) {
            return applicationTelemetry(rootConfig, null, OpenTelemetry.noop(), null);
        }

        if (AgentDetector.useExistingGlobalOpenTelemetry(rootConfig)
                || (selectedStrategy == null && useNoOwnerGlobalOpenTelemetry())) {
            return applicationTelemetry(rootConfig, selectedStrategy, GlobalOpenTelemetry.get(), null);
        }

        if (selectedStrategy != null) {
            if (publishGlobalOpenTelemetry) {
                OpenTelemetrySelection selection = createAndPublishGlobalOpenTelemetrySelection(rootConfig, selectedStrategy);
                return applicationTelemetry(rootConfig, selectedStrategy, selection.openTelemetry(), selection.closeable());
            }
            OpenTelemetry openTelemetry = selectedStrategy.create(rootConfig);
            return applicationTelemetry(rootConfig, selectedStrategy, openTelemetry, closeable(openTelemetry));
        }

        return applicationTelemetry(rootConfig, null, OpenTelemetry.noop(), null);
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
                                                            AutoCloseable closeable) {
        if (strategy != null) {
            strategy.selected(rootConfig, openTelemetry);
        }
        return new ApplicationTelemetry(openTelemetry, closeable);
    }

    private static AutoCloseable closeable(OpenTelemetry openTelemetry) {
        return openTelemetry instanceof AutoCloseable closeable ? closeable : null;
    }

    private static boolean telemetryDisabled(Config rootConfig) {
        Config telemetryConfig = rootConfig.get(TELEMETRY_CONFIG_KEY);
        return telemetryConfig.exists()
                && !telemetryConfig.get("enabled").asBoolean().orElse(true);
    }

    private static boolean useNoOwnerGlobalOpenTelemetry() {
        return GlobalOpenTelemetry.isSet()
                || autoConfigureGlobalOpenTelemetry(System.getProperty(OTEL_AUTO_CONFIGURE),
                                                    System.getenv(OTEL_AUTO_CONFIGURE_ENV));
    }

    static final class AgentDetector {

        private AgentDetector() {
        }

        static boolean isAgentPresent(Config config) {
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

        static boolean useExistingGlobalOpenTelemetry(Config config) {
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

    private static final class ApplicationTelemetry {
        private final OpenTelemetry openTelemetry;
        private final AutoCloseable closeable;

        private ApplicationTelemetry(OpenTelemetry openTelemetry, AutoCloseable closeable) {
            this.openTelemetry = openTelemetry;
            this.closeable = closeable;
        }

        private OpenTelemetry openTelemetry() {
            return openTelemetry;
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
