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

import io.helidon.common.config.Config;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.InitialMetersConsumer;

/**
 * Saves initial meter builders for use by new registry factories.
 */
public class InitialBuildersStore implements InitialMetersConsumer {


    @Override
    public void initialBuilders(Config config,
                                MetricsConfig metricsConfig,
                                MetricsFactory metricsFactory,
                                Collection<Meter.Builder<?, ?>> initialMeterBuilders) {

        RegistryFactoryManager.initialBuilders(initialMeterBuilders);
    }
}
