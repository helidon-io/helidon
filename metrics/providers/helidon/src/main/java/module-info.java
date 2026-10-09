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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon metrics provider.
 */
@Features.Name("Metrics")
@Features.Description("Helidon provider for metrics")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"Metrics", "Helidon"})
module io.helidon.metrics.providers.helidon {

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.builder.api;
    requires io.helidon.common;
    requires transitive io.helidon.common.media.type;
    requires io.helidon.config;
    requires transitive io.helidon.metrics.api;
    requires io.helidon.service.registry;

    exports io.helidon.metrics.providers.helidon;

    provides io.helidon.metrics.spi.MetricsFactoryProvider
            with io.helidon.metrics.providers.helidon.HelidonMetricsFactoryProvider;
    provides io.helidon.metrics.spi.MeterRegistryFormatterProvider
            with io.helidon.metrics.providers.helidon.HelidonPrometheusFormatterProvider;

}
