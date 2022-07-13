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

/**
 * Observer of the discovery of metric annotations which are applied to constructors and methods.
 * <p>
 *     Implementations make themselves known via the Java service loader mechanism.
 * </p>
 * <p>
 *     Observers are notified during {@code ProcessAnnotatedType}, for each metric annotation that is
 *     discovered to apply to an executable, via a
 *     {@link io.helidon.microprofile.metrics.MetricAnnotationDiscovery} event.
 * </p>
 */
public interface MetricAnnotationDiscoveryObserver {

   /**
     * Notifies the observer that a metric annotation has been discovered to apply to a constructor or method.
     *
     * @param metricAnnotationDiscovery the discovery event
     */
    void onDiscovery(MetricAnnotationDiscovery metricAnnotationDiscovery);

}
