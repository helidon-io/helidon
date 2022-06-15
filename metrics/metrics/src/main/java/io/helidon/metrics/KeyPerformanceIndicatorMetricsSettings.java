/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import io.helidon.config.Config;

/**
 * Settings for KPI metrics (for compatibility).
 *
 * @deprecated Use {@link io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings} instead.
 */
@Deprecated(since = "2.4.0", forRemoval = true)
public interface KeyPerformanceIndicatorMetricsSettings extends io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings {

    /**
     * Creates a new builder for the settings.
     *
     * @return new {@link Builder}.
     */
    static Builder builder() {
        return KeyPerformanceIndicatorMetricsSettingsCompatibility.builder();
    }

    /**
     * Builder for KPI settings.
     */
    interface Builder extends io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings.Builder {

        @Override
        Builder extended(boolean value);

        @Override
        Builder longRunningRequestThresholdMs(long value);

        @Override
        Builder config(Config kpiConfig);
    }
}
