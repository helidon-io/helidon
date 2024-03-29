/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
 * Helidon WebClient Metrics Support.
 */
@Feature(value = "Metrics",
        description = "WebClient metrics support",
        in = HelidonFlavor.SE,
        path = {"WebClient", "Metrics"}
)
module io.helidon.webclient.metrics {

    requires io.helidon.common.features.api;
    requires io.helidon.metrics.api;
    requires io.helidon.webclient;

    requires transitive io.helidon.common.config;

    provides io.helidon.webclient.spi.WebClientServiceProvider
            with io.helidon.webclient.metrics.WebClientMetricsProvider;

}
