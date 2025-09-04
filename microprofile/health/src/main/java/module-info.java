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
 * Microprofile health module.
 *
 * @see org.eclipse.microprofile.health
 */
@Features.Name("Health")
@Features.Description("MicroProfile Health spec implementation")
@Features.Flavor(HelidonFlavor.MP)
@Features.Path("Health")
module io.helidon.microprofile.health {

    requires io.helidon.common;
    requires io.helidon.config.mp;
    requires io.helidon.microprofile.server;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.json;
    requires jakarta.ws.rs;
    requires java.management;
    requires microprofile.config.api;
    requires microprofile.health.api;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.health;
    requires transitive io.helidon.microprofile.servicecommon;
    requires transitive io.helidon.webserver.observe.health;

    exports io.helidon.microprofile.health;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.health to weld.core.impl, io.helidon.microprofile.cdi;

    uses io.helidon.microprofile.health.HealthCheckProvider;
    uses io.helidon.health.spi.HealthCheckProvider;

    provides org.eclipse.microprofile.health.spi.HealthCheckResponseProvider
            with io.helidon.microprofile.health.HealthCheckResponseProviderImpl;
    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.health.HealthCdiExtension;

}
