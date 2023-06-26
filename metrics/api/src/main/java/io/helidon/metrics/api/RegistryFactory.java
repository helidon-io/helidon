/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.metrics.api;

import java.util.Set;

import io.helidon.common.media.type.MediaType;
import io.helidon.config.Config;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Behavior of a {@code RegistryFactory}, capable of providing metrics registries of various types (application, base, vendor)
 * plus static methods for:
 * <ul>
 *     <li>creating "free-standing" registry factories according to {@link MetricsSettings} or {@link Config} (see the {@code
 *     create} methods),</li>
 *     <li>retrieving the singleton registry factory or updating it according to {@code MetricsSettings} or {@code Config} (see
 *     the {@code getInstance} methods), and</li>
 *     <li>retrieving the appropriate registry factory for a metrics-capable component, based on the component's own metrics
 *     settings combined with the overall metrics settings and whether full-featured metrics are available on the path (see the
 *     {@link #getInstance} method).</li>
 * </ul>
 */
public interface RegistryFactory {

    /**
     * Set of specific types of metrics.
     */
    Set<Class<? extends Metric>> METRIC_TYPES = Set.of(Counter.class, Gauge.class, Histogram.class, Timer.class);

    /**
     * Returns a {@code RegistryFactory} according to the default metrics settings.
     *
     * @return new {@code RegistryFactory}
     */
    static RegistryFactory create() {
        return RegistryFactoryManager.create();
    }

    /**
     * Returns a {@code RegistryFactory} according to the specified {@code MetricsSettings}.
     *
     * @param metricsSettings settings for overall metrics
     * @return new {@code RegistryFactory}
     */
    static RegistryFactory create(MetricsSettings metricsSettings) {
        return RegistryFactoryManager.create(metricsSettings);
    }

    /**
     * Returns a {@code RegistryFactory} according to the specified overall metrics {@code Config}.
     * <p>Equivalent to {@code RegistryFactory.create(MetricsSettings.create(config))}.</p>
     *
     * @param config {@code Config} node for the overall metrics config section
     * @return new {@code RegistryFactory}
     * @deprecated Use {@link #create(MetricsSettings)} instead
     */
    @Deprecated
    static RegistryFactory create(Config config) {
        return RegistryFactoryManager.create(config);
    }

    /**
     * Returns the singleton instance of the {@code RegistryFactory}, either the initial default value or the one resulting from
     * the most recent prior use of {@link #getInstance(MetricsSettings)} or {@link #getInstance(Config)}.
     *
     * @return {@code RegistryFactory}
     */
    static RegistryFactory getInstance() {
        return RegistryFactoryManager.getInstance();
    }

    /**
     * Returns a {@code RegistryFactory} created according to the specified {@code Config}, while also setting the singleton
     * instance {@code RegistryFactory} which {@link #getInstance()} will return.
     *
     * @param config the {@code Config} to use in setting up the {@code RegistryFactory}
     * @return the new {@code RegistryFactory} created according to the specified config
     * @deprecated Use {@link #getInstance(MetricsSettings)} instead
     */
    @Deprecated
    static RegistryFactory getInstance(Config config) {
        return RegistryFactoryManager.getInstance(config);
    }

    /**
     * Returns a {@code RegistryFactory} according to the {@link MetricsSettings} provided and makes the instance the new value
     * of the singleton.
     *
     * @param metricSettings metrics settings to be used in preparing the {@code RegistryFactory}
     * @return the new {@code RegistryFactory}
     */
    static RegistryFactory getInstance(MetricsSettings metricSettings) {
        return RegistryFactoryManager.getInstance(metricSettings);
    }

    /**
     * Returns a {@code RegistryFactory} according to the {@link ComponentMetricsSettings} provided and the underlying overall
     * metrics settings.
     *
     * @param componentMetricsSettings metrics settings for the component requesting the registry factory
     * @return either the active {@code RegistryFactory} or a no-op one (if the component's metrics settings indicate)
     */
    static RegistryFactory getInstance(ComponentMetricsSettings componentMetricsSettings) {
        return RegistryFactoryManager.getInstance(componentMetricsSettings);
    }

    /**
     * Returns a {@link MetricRegistry} instance of the requested scope.
     *
     * @param scope scope of the registry to be returned
     * @return the {@code MetricRegistry} of the requested scope
     */
    Registry getRegistry(String scope);

    /**
     * Updates the metrics settings for the {@code RegistryFactory}.
     * <p>Reserved for use by internal Helidon code.</p>
     *
     * @param metricsSettings metrics settings to use in updating the factory
     */
    default void update(MetricsSettings metricsSettings) {
    }

    /**
     * Is this factory enabled (backed by a real implementation).
     *
     * @return whether this factory is enabled, disabled factory may only do no-ops (and produce no-op metrics)
     */
    boolean enabled();

    /**
     * Exposes the contents of the implementation registry according to the requested media type,
     * using the specified tag name used to add each metric's scope to its identity, and limiting by the provided scope
     * selection and meter name selection.
     *
     * @param mediaType {@link io.helidon.common.media.type.MediaType} to control the output format
     * @param scopeSelection {@link java.lang.Iterable} of individual scope names to include in the output
     * @param meterNameSelection {@link java.lang.Iterable} of individual meter names to include in the output
     * @return {@link String} meter exposition as governed by the parameters
     * @throws java.lang.IllegalArgumentException if the implementation cannot handle the requested media type
     * @throws java.lang.UnsupportedOperationException if the implementation cannot expose its metrics
     */
    Object scrape(MediaType mediaType, Iterable<String> scopeSelection, Iterable<String> meterNameSelection);

    /**
     * Returns the current scopes represented by registries created by the factory.
     *
     * @return scopes
     */
    Iterable<String> scopes();

    /**
     * Called to start required background tasks of a factory (if any).
     */
    default void start() {
    }

    /**
     * Called to stop required background tasks of a factory (if any).
     */
    default void stop() {
    }
}
