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

import java.util.Collection;

import io.helidon.common.config.Config;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;

/**
 * Behavior of components to be informed of initial meter builders, supplied by {@link io.helidon.metrics.spi.MetersProvider}
 * implementations found and invoked during {@code io.helidon.metrics.api.MetricsFactoryManager} start-up.
 */
public interface InitialMetersConsumer {

    /**
     * Invoked once as the metrics factory manager starts to communicate the active configuration.
     *
     * @param config config used by the metrics factory manager
     * @param metricsConfig metrics configuration for the new metrics factory
     * @param metricsFactory metric factory being created
     * @param initialMeterBuilders builders for initial meters
     */
    void initialBuilders(Config config,
                         MetricsConfig metricsConfig,
                         MetricsFactory metricsFactory,
                         Collection<Meter.Builder<?, ?>> initialMeterBuilders);
}
