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

package io.helidon.webserver.observe.metrics;

import java.util.Optional;

import io.helidon.webserver.http.Filter;

/**
 * Marker interface for filters which implement metrics semantic conventions (registering and updating meters for
 * measuring HTTP requests).
 */
public interface MetricsHttpSemanticConventions {

    /**
     * Provides a {@link io.helidon.webserver.http.Filter} which uses the supplied configuration to search for, register
     * if needed, and update metrics for each routing.
     *
     * @param config {@link io.helidon.webserver.observe.metrics.MetricsObserverConfig} which determines some of the filter's
     *                                                                                 behavior
     * @return the resulting filter
     */
    Optional<Filter> filter(MetricsObserverConfig config);

}
