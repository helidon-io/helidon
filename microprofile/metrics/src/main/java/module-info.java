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

    // TODO The following line(s) are temporarily commented while MP binds directly to Micrometer and provides the /metrics
    // feature itself. Later MP metrics will bind instead to the neutral Helidon metrics API and these lines or equivalent ones
    // will return then.
//    requires transitive io.helidon.metrics.serviceapi;

    requires io.helidon.nima.observe.metrics;

    requires transitive microprofile.config.api;
    requires microprofile.metrics.api;
    requires io.helidon.config.mp;
    
    // TODO - The following lines are temporary while the MP metrics implementation binds directly to Micrometer and provides the
    // full metrics implementation (including the feature supporting /metrics). Later when MP metrics binds to the neutral
    // Helidon metrics API these two lines will disappear from here.
    requires io.helidon.nima.servicecommon; // because this component temporarily provides its own /metrics feature.
    requires java.management; // Because the base registry which uses JMX beans is temporarily in this component.
    requires micrometer.core;

    // TODO end of temp lines

    exports io.helidon.microprofile.metrics;
    exports io.helidon.microprofile.metrics.spi;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.metrics to weld.core.impl, io.helidon.microprofile.cdi;
    opens io.helidon.microprofile.metrics.spi to io.helidon.microprofile.cdi, weld.core.impl;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.metrics.MetricsCdiExtension;
}
