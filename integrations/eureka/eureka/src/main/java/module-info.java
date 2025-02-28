/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import io.helidon.common.features.api.Preview;

import static io.helidon.common.features.api.HelidonFlavor.MP;
import static io.helidon.common.features.api.HelidonFlavor.SE;

/**
 * Provides packages related to automatic and unobtrusive <dfn>service instance registration</dfn> in <a
 * href="https://github.com/Netflix/eureka/tree/v2.0.3">Netflix Eureka servers of version 2.0.3 or later</a>.
 *
 * <p>Most users will never need to programmatically interact with any of the classes in any of the packages belonging
 * to this module.</p>
 *
 * @see io.helidon.integrations.eureka.EurekaRegistrationServerFeatureProvider#create(io.helidon.common.config.Config, String)
 */
@Feature(value = "EurekaRegistration",
         description = "Eureka Server Service Instance Registration Integration",
         in = { SE, MP },
         path = { "WebServer", "EurekaRegistration" }
)
@Preview
module io.helidon.integrations.eureka {

    requires transitive io.helidon.builder.api;
    requires io.helidon.common.config;
    requires io.helidon.service.registry;
    requires transitive io.helidon.webclient.http1;
    requires transitive io.helidon.webserver;
    requires transitive jakarta.json;

    requires static io.helidon.common.features.api;

    exports io.helidon.integrations.eureka;

    provides io.helidon.webserver.spi.ServerFeatureProvider
        with io.helidon.integrations.eureka.EurekaRegistrationServerFeatureProvider;

}
