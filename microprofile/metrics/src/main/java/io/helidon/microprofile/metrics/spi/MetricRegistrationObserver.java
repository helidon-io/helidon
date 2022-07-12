/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics.spi;

import io.helidon.microprofile.metrics.MetricAnnotationDiscovery;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * Observer of the registration of metrics due to metric annotations applied to executables--constructors and methods.
 * <p>
 *     Implementations make themselves known via the Java service loader mechanism.
 * </p>
 * <p>
 *     Once registered, the observer is notified each time a metric required by a metric annotation is registered.
 * </p>
 */
public interface MetricRegistrationObserver {

    /**
     * Notifies the observer that a metric has been registered due to a metric annotation that applies to an executable.
     *
     * @param discovery the {@link io.helidon.microprofile.metrics.MetricAnnotationDiscovery}
     *                  triggering this metric registration
     * @param metadata the metrics {@link org.eclipse.microprofile.metrics.Metadata} for the indicated metric
     * @param metricId the {@link org.eclipse.microprofile.metrics.MetricID} for the indicated metric
     * @param metric the metric associated with the discovery index
     */
    void onRegistration(MetricAnnotationDiscovery discovery, Metadata metadata, MetricID metricId, Metric metric);
}
