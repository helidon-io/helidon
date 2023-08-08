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
 * Basic and digest authentication provider.
 */
@Feature(value = "HTTP Basic",
        description = "Security provider for HTTP Basic authentication and outbound",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Security", "Provider", "HttpBasic"}
)
module io.helidon.security.providers.httpauth {
    requires static io.helidon.common.features.api;

    requires io.helidon.config;
    requires io.helidon.common;
    requires io.helidon.security;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.util;

    requires static io.helidon.config.metadata;

    exports io.helidon.security.providers.httpauth;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.httpauth.HttpBasicAuthService,
                io.helidon.security.providers.httpauth.HttpDigestAuthService;

    uses io.helidon.security.providers.httpauth.spi.UserStoreService;
}
