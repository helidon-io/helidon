/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
 * Metrics endpoint for Níma WebServer.
 */
@Feature(value = "Metrics",
         description = "Metrics support",
         in = HelidonFlavor.SE)
module io.helidon.nima.observe.metrics {
    uses io.helidon.metrics.spi.MeterRegistryFormatterProvider;
    requires transitive io.helidon.nima.observe;
    requires io.helidon.nima.webserver;
    requires io.helidon.nima.http.media.jsonp;
    requires io.helidon.nima.servicecommon;
    requires static io.helidon.config.metadata;
    requires io.helidon.metrics.api;
    requires io.helidon.metrics.serviceapi;
    requires io.helidon.common.context;
    requires io.helidon.common.features.api;

    requires static micrometer.core;
    requires static micrometer.registry.prometheus;
    requires static simpleclient.common;

    exports io.helidon.nima.observe.metrics;

    provides io.helidon.nima.observe.spi.ObserveProvider with io.helidon.nima.observe.metrics.MetricsObserveProvider;
    provides io.helidon.metrics.spi.MeterRegistryFormatterProvider
            with io.helidon.nima.observe.metrics.JsonMeterRegistryFormatterProvider;
}