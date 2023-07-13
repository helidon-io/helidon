/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
 * CDI implementation enhancements for Helidon MP.
 *
 * @see jakarta.enterprise.context
 */
@Feature(value = "CDI",
        description = "Jakarta CDI implementation",
        in = HelidonFlavor.MP,
        path = "CDI"
)
module io.helidon.microprofile.cdi {
    // needed for Unsafe used from Weld
    requires jdk.unsupported;
    requires java.logging;
    // weld requires java.sql.Date and we fail if not on classpath
    requires java.sql;
    requires jakarta.cdi;
    // weld requires jakarta.el.ELResolver on module path
    requires jakarta.el;

    requires io.helidon;
    requires io.helidon.common;
    requires io.helidon.logging.common;
    requires io.helidon.common.features.api;
    requires io.helidon.common.features;
    requires io.helidon.config;
    requires io.helidon.config.mp;

    requires weld.core.impl;
    requires weld.spi;
    requires weld.environment.common;
    requires transitive weld.se.core;
    requires io.helidon.common.context;
    requires jakarta.inject;
    requires microprofile.config.api;

    exports io.helidon.microprofile.cdi;

    uses jakarta.enterprise.inject.spi.Extension;

    provides io.helidon.spi.HelidonStartupProvider with io.helidon.microprofile.cdi.CdiStartupProvider;
    provides jakarta.enterprise.inject.se.SeContainerInitializer with io.helidon.microprofile.cdi.HelidonContainerInitializer;
    provides jakarta.enterprise.inject.spi.CDIProvider with io.helidon.microprofile.cdi.HelidonCdiProvider;

    provides org.jboss.weld.bootstrap.api.Service with io.helidon.microprofile.cdi.ExecutorServices;

    opens io.helidon.microprofile.cdi to weld.core.impl;
}
