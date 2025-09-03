/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
 * Microprofile metrics implementation.
 *
 * @see org.eclipse.microprofile.metrics
 */
@Features.Name("Metrics")
@Features.Description("MicroProfile metrics spec implementation")
@Features.Flavor(HelidonFlavor.MP)
@Features.Path("Metrics")
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.microprofile.metrics {

    requires io.helidon.config.mp;
    requires io.helidon.metrics.api;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;
    requires jakarta.annotation;
    requires jakarta.inject;
    requires microprofile.metrics.api;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.microprofile.servicecommon;
    requires transitive io.helidon.webserver.observe.metrics;
    requires transitive jakarta.cdi;
    requires transitive microprofile.config.api;

    exports io.helidon.microprofile.metrics;
    exports io.helidon.microprofile.metrics.spi;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.metrics to weld.core.impl, io.helidon.microprofile.cdi;
    opens io.helidon.microprofile.metrics.spi to io.helidon.microprofile.cdi, weld.core.impl;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.metrics.MetricsCdiExtension;
    provides io.helidon.metrics.spi.MetricsProgrammaticConfig
            with io.helidon.microprofile.metrics.MpMetricsProgrammaticConfig;
    provides io.helidon.metrics.spi.MeterRegistryLifeCycleListener
            with io.helidon.microprofile.metrics.RegistryFactoryManager;

    uses io.helidon.metrics.spi.ExemplarService;
}
