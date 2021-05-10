/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import io.helidon.common.LazyValue;

/**
 * Factory for creating the correct {@link KeyPerformanceIndicatorMetricsService} instance.
 */
public interface KeyPerformanceIndicatorMetricsServiceFactory {

    /**
     * The factory instance to use during this application run.
     */
    LazyValue<KeyPerformanceIndicatorMetricsServiceFactory> KPI_METRICS_SERVICE_FACTORY =
            LazyValue.create(KeyPerformanceIndicatorMetricsServiceFactoryLoader::load);

    /**
     * Returns a suitable instance of {@link KeyPerformanceIndicatorMetricsService}.
     *
     * @param metricsPrefix prefix for the names of metrics created by the returned instance
     * @param kpiMetricsConfig KPI metrics configuration
     * @return service instance
     */
    KeyPerformanceIndicatorMetricsService create(String metricsPrefix, KeyPerformanceIndicatorMetricsConfig kpiMetricsConfig);
}
