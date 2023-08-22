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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.config.Config;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Micrometer-specific implementation of a registry factory.
 *
// this class is not immutable, as we may need to update registries with configuration post creation
// see Github issue #360
 */
class RegistryFactory {

    private static AtomicReference<RegistryFactory> registryFactory = new AtomicReference<>();

    private final Map<String, Registry> registries = new HashMap<>();
    private final Lock metricsSettingsAccess = new ReentrantLock(true);
    private final MetricsConfig metricsConfig;
    private MetricFactory metricFactory;


    static final Collection<Class<? extends Metric>> METRIC_TYPES = Set.of(Counter.class,
                                                                           Gauge.class,
                                                                           Histogram.class,
                                                                           Timer.class);

    /**
     * Create a new instance.
     *
     * @param metricsConfig metrics setting to use in preparing the registry factory
     * @param appRegistry application registry to provide from the factory
     * @param vendorRegistry vendor registry to provide from the factory
     */
    private RegistryFactory(MetricsConfig metricsConfig, Registry appRegistry, Registry vendorRegistry) {
        this.metricsConfig = metricsConfig;
        metricFactory = MetricFactory.create(Metrics.globalRegistry());
        registries.put(Registry.APPLICATION_SCOPE, appRegistry);
        registries.put(Registry.VENDOR_SCOPE, vendorRegistry);
    }

    private RegistryFactory(MetricsConfig metricsConfig) {
        this(metricsConfig,
             Registry.create(Registry.APPLICATION_SCOPE, metricsConfig),
             Registry.create(Registry.VENDOR_SCOPE, metricsConfig));
    }

    /**
     * Create a new factory with default configuration.
     *
     * @return a new registry factory
     */
    static RegistryFactory create() {
        return RegistryFactory.create(MetricsConfig.create());
    }

    /**
     * Create a new factory with provided configuration.
     *
     * @param config configuration to use
     * @return a new registry factory
     */
    static RegistryFactory create(Config config) {
        return RegistryFactory.create(MetricsConfig.create(config.get(MetricsConfig.METRICS_CONFIG_KEY)));
    }

    static RegistryFactory create(MetricsConfig metricsConfig) {
        return new RegistryFactory(metricsConfig);
    }

    /**
     * Get a singleton instance of the registry factory.
     *
     * @return registry factory singleton
     */
    static RegistryFactory getInstance() {
        registryFactory.compareAndSet(null, create());
        return registryFactory.get();
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

