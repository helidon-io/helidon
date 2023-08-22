/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.config.Config;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsProgrammaticSettings;

/**
 * Micrometer-specific implementation of a registry factory.
 *
// this class is not immutable, as we may need to update registries with configuration post creation
// see Github issue #360
 */
class RegistryFactory {

    private final Map<String, Registry> registries = new HashMap<>();
    private final Lock metricsSettingsAccess = new ReentrantLock(true);
    private final HelidonPrometheusConfig prometheusConfig;
    private MetricsConfig metricsConfig;
    private MetricFactory metricFactory;

    /**
     * Create a new instance.
     *
     * @param metricsConfig metrics setting to use in preparing the registry factory
     * @param appRegistry application registry to provide from the factory
     * @param vendorRegistry vendor registry to provide from the factory
     */
    private RegistryFactory(MetricsConfig metricsConfig, Registry appRegistry, Registry vendorRegistry) {
        this.metricsConfig = metricsConfig;
        prometheusConfig = new HelidonPrometheusConfig(metricsConfig);
        metricFactory = HelidonMicrometerMetricFactory.create(Metrics.globalRegistry);
        registries.put(Registry.APPLICATION_SCOPE, appRegistry);
        registries.put(Registry.VENDOR_SCOPE, vendorRegistry);
    }

    private RegistryFactory(MetricsConfig metricsConfig) {
        this(metricsConfig,
             Registry.create(Registry.APPLICATION_SCOPE, metricsConfig.scoping().scopes().get(Registry.APPLICATION_SCOPE)),
             Registry.create(Registry.VENDOR_SCOPE, metricsConfig.scoping().scopes().get(Registry.VENDOR_SCOPE)));
    }

    /**
     * Create a new factory with default configuration, with pre-filled
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#VENDOR} and
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} metrics.
     *
     * @return a new registry factory
     */
    static RegistryFactory create() {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.create());
    }

    /**
     * Create a new factory with provided configuration, with pre filled
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#VENDOR} and
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} metrics.
     *
     * @param config configuration to use
     * @return a new registry factory
     */
    static RegistryFactory create(Config config) {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.create(config));
    }

    static RegistryFactory create(MetricsConfig metricsSettings) {
        return new RegistryFactory(metricsSettings);
    }

    /**
     * Get a singleton instance of the registry factory.
     *
     * @return registry factory singleton
     */
    static RegistryFactory getInstance() {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.getInstance());
    }

    /**
     * Get a singleton instance of the registry factory for and update it with provided configuration.
     * Note that the config is used only if nobody access the base registry.
     *
     * @param config configuration of the registry factory used to update behavior of the instance returned
     * @return registry factory singleton
     * @deprecated Use {@link io.helidon.metrics.api.RegistryFactory#getInstance(MetricsSettings)}
     */
    static RegistryFactory getInstance(Config config) {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.getInstance(config));
    }

    Registry getARegistry(String scope) {
        return registries.get(scope);
    }

    /**
     * Get a registry based on its scope.
     *
     * @param scope scope of registry
     * @return Registry for the scope requested
     */
    Registry getRegistry(String scope) {
        return accessMetricsSettings(() -> registries.computeIfAbsent(scope, s ->
                Registry.create(s, metricsConfig)));
    }

    void update(MetricsConfig metricsSettings) {
        accessMetricsSettings(() -> {
            this.metricsConfig = metricsSettings;
            prometheusConfig.update(metricsSettings);
            registries.forEach((key, value) -> value.update(metricsSettings.scoping().scopes().get(key)));
        });
    }

    boolean enabled() {
        return true;
    }

    Optional<?> scrape(MediaType mediaType,
                                   Iterable<String> scopeSelection,
                                   Iterable<String> meterNameSelection) {
        if (mediaType.equals(MediaTypes.TEXT_PLAIN) || mediaType.equals(MediaTypes.APPLICATION_OPENMETRICS_TEXT)) {
            var formatter =
                    MicrometerPrometheusFormatter
                            .builder()
                            .resultMediaType(mediaType)
                            .scopeTagName(MetricsProgrammaticSettings.instance().scopeTagName())
                            .scopeSelection(scopeSelection)
                            .meterNameSelection(meterNameSelection)
                            .build();

            return formatter.filteredOutput();
        } else if (mediaType.equals(MediaTypes.APPLICATION_JSON)) {
            var formatter = JsonFormatter.builder()
                    .scopeTagName(MetricsProgrammaticSettings.instance().scopeTagName())
                    .scopeSelection(scopeSelection)
                    .meterNameSelection(meterNameSelection)
                    .build();
            return formatter.data(true);
        }
        throw new UnsupportedOperationException();
    }

    Iterable<String> scopes() {
        return registries.keySet();
    }

    void start() {
        PeriodicExecutor.start();
    }

    void stop() {
        /*
            Primarily for successive tests (e.g., in the TCK) which might share the same VM, delete each metric individually
            (which will trickle down into the delegate meter registry) and also clear out the collection of registries.
         */
        registries.values()
                .forEach(r -> r.getMetrics()
                        .forEach((id, m) -> r.remove(id)));
        registries.clear();
        PeriodicExecutor.stop();
    }

    private void accessMetricsSettings(Runnable operation) {
        metricsSettingsAccess.lock();
        try {
            operation.run();
        } finally {
            metricsSettingsAccess.unlock();
        }
    }

    private <T> T accessMetricsSettings(Callable<T> callable) {
        metricsSettingsAccess.lock();
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            metricsSettingsAccess.unlock();
        }
    }
}

