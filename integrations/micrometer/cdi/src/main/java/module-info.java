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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Support for Micrometer in Helidon MP.
 */
@Features.Preview
@Features.Name("Micrometer")
@Features.Description("Micrometer integration")
@Features.Flavor(HelidonFlavor.MP)
@Features.Path("Micrometer")
module io.helidon.integrations.micrometer.cdi {

    requires io.helidon.common;
    requires io.helidon.config.mp;
    requires io.helidon.config;
    requires jakarta.annotation;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires micrometer.core;
    requires simpleclient;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.integrations.micrometer;
    requires transitive io.helidon.microprofile.server;
    requires transitive io.helidon.microprofile.servicecommon;

    exports io.helidon.integrations.micrometer.cdi;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.integrations.micrometer.cdi to weld.core.impl, io.helidon.microprofile.cdi;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.integrations.micrometer.cdi.MicrometerCdiExtension;

}
