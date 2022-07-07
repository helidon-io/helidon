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
package io.helidon.microprofile.metrics.api;

import java.util.function.Supplier;

import io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver;

/**
 * Specifies behavior of a provider for a metric annotation discovery observer.
 *
 * @param <T> specific type of {@link io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver}
 */
public interface MetricAnnotationDiscoveryObserverProvider<T extends MetricAnnotationDiscoveryObserver> extends Supplier<T> {

    /**
     *
     * @return the new or pre-existing instance of the observer
     */
    T get();
}
