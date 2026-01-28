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

package io.helidon.webserver.observe.metrics.spi;

import io.helidon.metrics.api.MetricsConfig;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig;

/**
 * Behavior of an HTTP filter for providing automatic metrics for HTTP traffic.
 */
@Service.Contract
public interface HttpMetricsFilter extends Filter {

    /**
     * Initializes the filter with settings it should use.
     *
     * @param metricsConfig metrics autoHttpMetricsConfig for use in preparing the filter
     * @param autoHttpMetricsConfig automatic metrics settings for use in preparing the filter
     * @return the prepared {@code Filter}
     */
    Filter prepare(MetricsConfig metricsConfig, AutoHttpMetricsConfig autoHttpMetricsConfig);

}
