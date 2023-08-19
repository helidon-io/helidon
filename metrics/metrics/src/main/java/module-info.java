/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
 * Helidon Metrics implementation.
 */
@Feature(value = "Metrics",
        description = "Metrics for gRPC services",
        in = HelidonFlavor.SE,
        path = {"grpc", "Metrics"}
)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.metrics {

    requires io.helidon.common.media.type;
    requires io.helidon.common;
    requires jakarta.json;
    requires java.logging;
    requires java.management;
    requires micrometer.core;
    requires micrometer.registry.prometheus;
    requires simpleclient.common; // for Prometheus formatting

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.common.configurable;
    requires transitive io.helidon.metrics.api;
    requires transitive microprofile.metrics.api;

    exports io.helidon.metrics;

    uses io.helidon.metrics.api.spi.ExemplarService;

    provides io.helidon.common.configurable.spi.ExecutorServiceSupplierObserver
            with io.helidon.metrics.ExecutorServiceMetricsObserver;
    provides io.helidon.metrics.spi.MetersProvider
            with io.helidon.metrics.BaseMetricsProvider;

}
