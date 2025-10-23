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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Provides a Eureka-based implementation of <dfn>discovery</dfn> support for Helidon.
 */
@Features.Name("Discovery")
@Features.Description("Eureka provider for Discovery")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"Discovery", "Eureka"})
@Features.Preview
@Features.Since("4.3.0")
module io.helidon.discovery.providers.eureka {

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common;
    requires transitive io.helidon.config;
    requires transitive io.helidon.webclient.http1;

    requires io.helidon.common.media.type;
    requires io.helidon.discovery;
    requires io.helidon.http;
    requires io.helidon.http.media;
    requires io.helidon.service.registry;

    requires jakarta.json;

    exports io.helidon.discovery.providers.eureka;

}
