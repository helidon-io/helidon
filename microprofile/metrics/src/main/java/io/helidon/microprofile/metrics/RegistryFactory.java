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

import java.lang.System.Logger.Level;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Micrometer-specific implementation of a registry factory, created automatically whenever a new
 * {@link io.helidon.metrics.api.MeterRegistry} is created by a metrics provider.
 * <p>
 * Note: Formerly, an instance of this class could be updated with new configuration information after it had been initialized
 * as described in Github issue #360. This version, though, is instantiated once per new meter registry, with the intent that
 * only
 * one meter registry will every be created in a production server.
 * </p>
 * <p>The {@link #getInstance(io.helidon.metrics.api.MeterRegistry)}
 * method creates a new instance and saves it for retrieval via {@link #getInstance()}. The
 * {@link #create(io.helidon.metrics.api.MeterRegistry)}
 * method creates a new instance but does not record it internally.
 * </p>
 */
public class RegistryFactory {

    static final Collection<Class<? extends Metric>> METRIC_TYPES = Set.of(Counter.class,
                                                                           Gauge.class,
                                                                           Histogram.class,
                                                                           Timer.class);
    private static final AtomicReference<RegistryFactory> REGISTRY_FACTORY = new AtomicReference<>();
    private static final System.Logger LOGGER = System.getLogger(RegistryFactory.class.getName());
    private final MeterRegistry meterRegistry;
    private final Map<String, Registry> registries = new HashMap<>();
    private final Lock metricsSettingsAccess = new ReentrantLock(true);

    private RegistryFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry
                .onMeterAdded(this::registerMetricForExistingMeter)
                .onMeterRemoved(this::removeMetricForMeter);
    }

    static RegistryFactory create(MeterRegistry meterRegistry) {
        return new RegistryFactory(meterRegistry);
    }

    static RegistryFactory getInstance(MeterRegistry meterRegistry) {
        return REGISTRY_FACTORY.updateAndGet(rf -> rf != null && rf.meterRegistry == meterRegistry
                ? rf
                : create(meterRegistry));
    }

    /**
     * Get a singleton instance of the registry factory.
     *
     * @return registry factory singleton
     */
    public static RegistryFactory getInstance() {
        RegistryFactory result = REGISTRY_FACTORY.get();
        if (result == null) {
            LOGGER.log(Level.WARNING, "Attempt to retrieve current " + RegistryFactory.class.getName()
                    + " before it has been initialized; using default Helidon meter registry and continuing");
            result = new RegistryFactory(Metrics.globalRegistry());
            REGISTRY_FACTORY.set(result);
        }
        return result;
    }

    /**
     * Intended for use by test initializers to do a brute force clearout of each registry and
     * the factory's collection of registries.
     */
    static void closeAll() {
        RegistryFactory rf = REGISTRY_FACTORY.get();
        if (rf != null) {
            rf.close();
            REGISTRY_FACTORY.set(null);
        }
    }

    /**
     * Get a registry based on its scope.
     *
     * @param scope scope of registry
     * @return Registry for the scope requested
     */
    public MetricRegistry getRegistry(String scope) {
        return registry(scope);
    }

    Registry registry(String scope) {
        return accessMetricsSettings(() -> registries.computeIfAbsent(scope, s ->
                Registry.create(s, meterRegistry)));
    }

    void start() {
        PeriodicExecutor.start();
    }

    void close() {
        /*
            Primarily for successive tests (e.g., in the TCK) which might share the same VM, delete each metric individually
            (which will trickle down into the delegate meter registry) and also closeAll out the collection of registries.
         */
        List.copyOf(registries.values()).forEach(Registry::clear);
        registries.clear();
        PeriodicExecutor.stop();
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

    private void registerMetricForExistingMeter(Meter delegate) {
        String scope = delegate.scope().orElse(null);
        if (scope == null) {
            LOGGER.log(Level.WARNING, "Attempt to register an existing meter with no scope: " + delegate);
        }
        registry(scope).onMeterAdded(delegate);
    }

    private void removeMetricForMeter(Meter meter) {
        String scope = meter.scope().orElse(null);
        if (scope == null) {
            LOGGER.log(Level.WARNING, "Attempt to register an existing meter with no scope: " + meter);
        }
        registry(scope).onMeterRemoved(meter);
    }

}

