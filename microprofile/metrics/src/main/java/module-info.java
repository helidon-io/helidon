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
import io.helidon.microprofile.metrics.MpMetricsProgrammaticSettings;

/**
 * Microprofile metrics implementation.
 *
 * @see org.eclipse.microprofile.metrics
 */
@Feature(value = "Metrics",
        description = "MicroProfile metrics spec implementation",
        in = HelidonFlavor.MP,
        path = "Metrics"
)
module io.helidon.microprofile.metrics {
    requires static io.helidon.common.features.api;

    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires static jakarta.annotation;

    requires io.helidon.microprofile.servicecommon;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.config;

    requires transitive io.helidon.metrics.api;

    requires io.helidon.nima.observe.metrics;

    requires transitive microprofile.config.api;
    requires microprofile.metrics.api;
    requires io.helidon.config.mp;
    
    requires micrometer.registry.prometheus;
    requires simpleclient.common;

    exports io.helidon.microprofile.metrics;
    exports io.helidon.microprofile.metrics.spi;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.metrics to weld.core.impl, io.helidon.microprofile.cdi;
    opens io.helidon.microprofile.metrics.spi to io.helidon.microprofile.cdi, weld.core.impl;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.metrics.MetricsCdiExtension;
    provides io.helidon.metrics.api.MetricsProgrammaticSettings with MpMetricsProgrammaticSettings;
}
