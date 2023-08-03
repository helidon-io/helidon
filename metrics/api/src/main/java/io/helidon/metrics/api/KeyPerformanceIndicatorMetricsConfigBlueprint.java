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

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.inject.configdriven.api.ConfigBean;

/**
 * Config bean for KPI metrics configuration.
 */
@ConfigBean()
@Configured(prefix = MetricsConfigBlueprint.METRICS_CONFIG_KEY
        + "."
        + KeyPerformanceIndicatorMetricsConfigBlueprint.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY)
@Prototype.Blueprint()
interface KeyPerformanceIndicatorMetricsConfigBlueprint {

    /**
     * Config key for extended key performance indicator metrics settings.
     */
    String KEY_PERFORMANCE_INDICATORS_CONFIG_KEY = "key-performance-indicators";

    /**
     * Config key for {@code enabled} setting of the extended KPI metrics.
     */
    String KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY = "extended";

    /**
     * Default enabled setting for extended KPI metrics.
     */
    String KEY_PERFORMANCE_INDICATORS_EXTENDED_DEFAULT = "false";

    /**
     * Config key for long-running requests threshold setting (in milliseconds).
     */
    String LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY = "threshold-ms";

    /**
     * Config key for long-running requests settings.
     */
    String LONG_RUNNING_REQUESTS_CONFIG_KEY = "long-running-requests";

    /**
     * Default long-running requests threshold.
     */
    String LONG_RUNNING_REQUESTS_THRESHOLD_MS_DEFAULT = "10000"; // 10 seconds

    /**
     * Configuration key for long-running requests extended configuration.
     */
    String QUALIFIED_LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY =
            LONG_RUNNING_REQUESTS_CONFIG_KEY + "." + LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY;

    /**
     * Whether KPI extended metrics are enabled.
     *
     * @return true if KPI extended metrics are enabled; false otherwise
     */
    @ConfiguredOption(key = KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY,
                      value = KEY_PERFORMANCE_INDICATORS_EXTENDED_DEFAULT)
    boolean isExtended();

    /**
     * Threshold in ms that characterizes whether a request is long running.
     *
     * @return threshold in ms indicating a long-running request
     */
    @ConfiguredOption(key = QUALIFIED_LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY,
                      value = LONG_RUNNING_REQUESTS_THRESHOLD_MS_DEFAULT)
    long longRunningRequestThresholdMs();

}
