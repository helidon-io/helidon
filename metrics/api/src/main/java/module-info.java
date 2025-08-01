/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon metrics API.
 */
@Feature(value = "Metrics",
         description = "Metrics",
         in = HelidonFlavor.SE,
         path = {"Metrics"}
)module io.helidon.metrics.api {

    requires static io.helidon.common.features.api;

    requires io.helidon.http;
    requires transitive io.helidon.common.config;

    requires io.helidon.builder.api;
    requires io.helidon.service.registry;
    requires static io.helidon.config.metadata;

    exports io.helidon.metrics.api;
    exports io.helidon.metrics.spi;

    uses io.helidon.metrics.spi.ExemplarService;
    uses io.helidon.metrics.spi.MetricsProgrammaticConfig;
    uses io.helidon.metrics.spi.MetricsFactoryProvider;
    uses io.helidon.metrics.spi.MeterRegistryFormatterProvider;
    uses io.helidon.metrics.api.MetricsFactory;

    uses io.helidon.metrics.spi.MetersProvider;

    uses io.helidon.metrics.spi.MeterRegistryLifeCycleListener;

    provides io.helidon.metrics.spi.MetricsProgrammaticConfig with io.helidon.metrics.api.SeMetricsProgrammaticConfig;
}
