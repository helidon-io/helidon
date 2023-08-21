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
package io.helidon.metrics.api;

import java.time.Duration;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Config bean for KPI metrics configuration.
 */
@Configured
@Prototype.Blueprint()
interface KeyPerformanceIndicatorMetricsConfigBlueprint {

    /**
     * Default long-running requests threshold.
     */
    String LONG_RUNNING_REQUESTS_THRESHOLD_DEFAULT = "PT10S"; // Duration.ofSeconds(10).toString();

    /**
     * Whether KPI extended metrics are enabled.
     *
     * @return true if KPI extended metrics are enabled; false otherwise
     */
    @ConfiguredOption(value = "false")
    boolean extended();

    /**
     * Threshold in ms that characterizes whether a request is long running.
     *
     * @return threshold in ms indicating a long-running request
     */
    @ConfiguredOption(key = "long-running-requests.threshold",
                      value = LONG_RUNNING_REQUESTS_THRESHOLD_DEFAULT)
    Duration longRunningRequestThreshold();

}
