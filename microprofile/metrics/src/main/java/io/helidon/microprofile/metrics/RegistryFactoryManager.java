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
package io.helidon.microprofile.metrics;

import java.util.Collection;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.spi.MeterRegistryLifeCycleListener;

/**
 * Manages the creation of registry factories.
 */
public class RegistryFactoryManager implements MeterRegistryLifeCycleListener {

    private static Collection<Meter.Builder<?, ?>> builders;

    /**
     * Sets the group of initial meter builders.
     *
     * @param initialBuilders initial builders
     */
    static void initialBuilders(Collection<Meter.Builder<?, ?>> initialBuilders) {
        builders = initialBuilders;
    }

    @Override
    public void onCreate(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        RegistryFactory.getInstance(meterRegistry, metricsConfig, builders);
    }
}
