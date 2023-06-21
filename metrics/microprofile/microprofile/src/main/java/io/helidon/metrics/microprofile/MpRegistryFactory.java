/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.microprofile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * Factory class for finding existing or creating new metric registries.
 */
public class MpRegistryFactory {

    /**
     * Scope name for the application metric registry.
     */
    public static final String APPLICATION_SCOPE = "application";

    /**
     * Scope name for the base metric registry.
     */
    public static final String BASE_SCOPE = "base";

    /**
     * Scope name for the vendor metric registry.
     */
    public static final String VENDOR_SCOPE = "vendor";

    /**
     * Config key suffix for distribution summary (histogram) precision.
     */
    public static final String HISTOGRAM_PRECISION_CONFIG_KEY_SUFFIX = "helidon.distribution-summary.precision";

    /**
     * Config key suffix for timer precision.
     */
    public static final String TIMER_PRECISION_CONFIG_KEY_SUFFIX = "helidon.timer.precision";

    private static final int HISTOGRAM_PRECISION_DEFAULT = 3;
    private static final int TIMER_PRECISION_DEFAULT = 3;

    private static MpRegistryFactory instance;

    private static final ReentrantLock LOCK = new ReentrantLock();
    private final Exception creation;

    private Config mpConfig;
    private final Map<String, MpMetricRegistry> registries = new HashMap<>();
    private final PrometheusMeterRegistry prometheusMeterRegistry;
    private final int distributionSummaryPrecision;
    private final int timerPrecision;

    /**
     * Creates a new registry factory using the specified MicroProfile configuration.
     *
     * @param mpConfig the MicroProfile config to use in preparing the registry factory
     * @return registry factory
     */
    public static MpRegistryFactory create(Config mpConfig) {
        LOCK.lock();
        try {
            if (instance == null) {
                instance = new MpRegistryFactory(mpConfig);
                return instance;
            } else {
                throw new IllegalStateException(
                        "Attempt to set up MpRegistryFactory multiple times; previous invocation follows: ",
                        instance.creation);
            }
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Returns the singleton registry factory, creating it if needed.
     *
     * @return the registry factory
     */
    public static MpRegistryFactory get() {
        LOCK.lock();
        try {
            if (instance == null) {
                instance = new MpRegistryFactory(ConfigProvider.getConfig());
            }
            return instance;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Returns the {@link org.eclipse.microprofile.metrics.MetricRegistry} for the specified scope.
     *
     * @param scope scope for the meter registry to get or create
     * @return previously-existing or newly-created {@code MeterRegistry} for the specified scope
     */
    public MetricRegistry registry(String scope) {
        return registries.computeIfAbsent(scope,
                                                   s -> MpMetricRegistry.create(s, Metrics.globalRegistry));
    }

    /**
     * Returns the Prometheus meter registry known to the registry factory.
     *
     * @return Prometheus meter registry
     */
    public PrometheusMeterRegistry prometheusMeterRegistry() {
        return prometheusMeterRegistry;
    }

    /**
     * Returns the precision for use with distribution summaries.
     *
     * @return distribution summary precision
     */
    int distributionSummaryPrecision() {
        return distributionSummaryPrecision;
    }

    /**
     * Returns the precision for use with timers.
     *
     * @return timer precision
     */
    int timerPrecision() {
        return timerPrecision;
    }

    private MpRegistryFactory(Config mpConfig) {
        creation = new Exception("Initial creation of " + MpRegistryFactory.class.getSimpleName());
        this.mpConfig = mpConfig;
        prometheusMeterRegistry = findOrAddPrometheusRegistry();
        distributionSummaryPrecision = mpConfig.getOptionalValue("mp.metrics." + HISTOGRAM_PRECISION_CONFIG_KEY_SUFFIX,
                                                                 Integer.class).orElse(HISTOGRAM_PRECISION_DEFAULT);
        timerPrecision = mpConfig.getOptionalValue("mp.metrics." + TIMER_PRECISION_CONFIG_KEY_SUFFIX,
                                                   Integer.class).orElse(TIMER_PRECISION_DEFAULT);
        registries.put(APPLICATION_SCOPE, MpMetricRegistry.create(APPLICATION_SCOPE, Metrics.globalRegistry));
        registries.put(BASE_SCOPE, BaseRegistry.create(Metrics.globalRegistry));
        registries.put(VENDOR_SCOPE, MpMetricRegistry.create(VENDOR_SCOPE, Metrics.globalRegistry));

    }

    private PrometheusMeterRegistry findOrAddPrometheusRegistry() {
        return Metrics.globalRegistry.getRegistries().stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .map(PrometheusMeterRegistry.class::cast)
                .findFirst()
                .orElseGet(() -> {
                    PrometheusMeterRegistry result = new PrometheusMeterRegistry(
                            s -> mpConfig.getOptionalValue("mp.metrics." + s, String.class)
                                    .orElse(null));
                    Metrics.addRegistry(result);
                    return result;
                });
    }
}
