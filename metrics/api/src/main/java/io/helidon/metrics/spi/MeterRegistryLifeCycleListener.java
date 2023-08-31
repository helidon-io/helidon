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
package io.helidon.metrics.spi;

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;

/**
 * Receives notifications of events related to meter registries.
 */
public interface MeterRegistryLifeCycleListener {

    /**
     * Receives notification when a new {@link io.helidon.metrics.api.MeterRegistry} is created.
     *
     * @param meterRegistry the new meter registry
     * @param metricsConfig the metrics config
     */
    void onCreate(MeterRegistry meterRegistry, MetricsConfig metricsConfig);
}
