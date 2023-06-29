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

package io.helidon.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.MetricsProgrammaticSettings;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.spi.MetricFactory;

import io.micrometer.core.instrument.Metrics;

/**
 * Access point to all registries.
 *
 * There are two options to use the factory:
 * <ol>
 *     <li>A singleton instance, obtained through {@link #getInstance()} or {@link #getInstance(io.helidon.config.Config)}.
 *     This instance is lazily initialized - the latest call that provides a config instance before a
 *     {@link org.eclipse.microprofile.metrics.MetricRegistry#BASE_SCOPE} registry is obtained would be used to configure
 *     the base registry (as that is the only configurable registry in current implementation)
 *     </li>
 *     <li>A custom instance, obtained through {@link #create(Config)} or {@link #create()}. This would create a
 *     new instance of a registry factory (in case multiple instances are desired), independent on the singleton instance
 *     and on other instances provided by these methods.</li>
 * </ol>
 */
// this class is not immutable, as we may need to update registries with configuration post creation
// see Github issue #360
public class RegistryFactory implements io.helidon.metrics.api.RegistryFactory {

    private final Map<String, Registry> registries = new HashMap<>();
    private final Lock metricsSettingsAccess = new ReentrantLock(true);
    private final HelidonPrometheusConfig prometheusConfig;
    private MetricsSettings metricsSettings;
    private MetricFactory metricFactory;

    /**
     * Create a new instance.
     *
     * @param metricsSettings metrics setting to use in preparing the registry factory
     * @param appRegistry application registry to provide from the factory
     * @param vendorRegistry vendor registry to provide from the factory
     */
    protected RegistryFactory(MetricsSettings metricsSettings, Registry appRegistry, Registry vendorRegistry) {
        this.metricsSettings = metricsSettings;
        prometheusConfig = new HelidonPrometheusConfig(metricsSettings);
        metricFactory = HelidonMetricFactory.create(Metrics.globalRegistry);
        registries.put(Registry.APPLICATION_SCOPE, appRegistry);
        registries.put(Registry.VENDOR_SCOPE, vendorRegistry);
    }

    private RegistryFactory(MetricsSettings metricsSettings) {
        this(metricsSettings,
             Registry.create(Registry.APPLICATION_SCOPE, metricsSettings.registrySettings(Registry.APPLICATION_SCOPE)),
             Registry.create(Registry.VENDOR_SCOPE, metricsSettings.registrySettings(Registry.VENDOR_SCOPE)));
    }

    private void accessMetricsSettings(Runnable operation) {
        metricsSettingsAccess.lock();
        try {
            operation.run();
        } finally {
            metricsSettingsAccess.unlock();
        }
    }


    /**
     * Create a new factory with default configuration, with pre-filled
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#VENDOR} and
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} metrics.
     *
     * @return a new registry factory
     * @deprecated Use {@link io.helidon.metrics.api.RegistryFactory#create()}
     */
    @Deprecated(since = "2.4.0", forRemoval = true)
    public static RegistryFactory create() {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.create());
    }

    /**
     * Create a new factory with provided configuration, with pre filled
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#VENDOR} and
     * {@link org.eclipse.microprofile.metrics.MetricRegistry.Type#BASE} metrics.
     *
     * @param config configuration to use
     * @return a new registry factory
     * @deprecated Use {@link io.helidon.metrics.api.RegistryFactory#create(Config)}
     */
    @Deprecated(since = "2.4.0", forRemoval = true)
    public static RegistryFactory create(Config config) {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.create(config));
    }

    static RegistryFactory create(MetricsSettings metricsSettings) {
        return new RegistryFactory(metricsSettings);
    }

    /**
     * Get a singleton instance of the registry factory.
     *
     * @return registry factory singleton
     * @deprecated Use {@link io.helidon.metrics.api.RegistryFactory#getInstance()}
     */
    @Deprecated(since = "2.4.0", forRemoval = true)
    public static RegistryFactory getInstance() {
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
    @Deprecated(since = "2.4.0", forRemoval = true)
    public static RegistryFactory getInstance(Config config) {
        return RegistryFactory.class.cast(io.helidon.metrics.api.RegistryFactory.getInstance(config));
    }

    Registry getARegistry(String scope) {
        if (Registry.BASE_SCOPE.equals(scope)) {
            ensureBase();
        }
        return registries.get(scope);
    }

    /**
     * Get a registry based on its scope.
     *
     * @param scope scope of registry
     * @return Registry for the scope requested
     */
    @Override
    public io.helidon.metrics.api.Registry getRegistry(String scope) {
        if (Registry.BASE_SCOPE.equals(scope)) {
            ensureBase();
        }
        return registries.computeIfAbsent(scope, s -> Registry.create(s, metricsSettings.registrySettings(s)));
    }

    @Override
    public void update(MetricsSettings metricsSettings) {
        accessMetricsSettings(() -> {
            this.metricsSettings = metricsSettings;
            prometheusConfig.update(metricsSettings);
            registries.forEach((key, value) -> value.update(metricsSettings.registrySettings(key)));
        });
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Optional<?> scrape(MediaType mediaType,
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

            return Optional.ofNullable(formatter.filteredOutput());
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

    @Override
    public Iterable<String> scopes() {
        return registries.keySet();
    }

    @Override
    public void start() {
        PeriodicExecutor.start();
    }

    @Override
    public void stop() {
        PeriodicExecutor.stop();
    }

    private void ensureBase() {
        if (null == registries.get(Registry.BASE_SCOPE)) {
            accessMetricsSettings(() -> {
                Registry registry = BaseRegistry.create(metricsSettings);
                registries.put(Registry.BASE_SCOPE, registry);
            });
        }
    }
}

