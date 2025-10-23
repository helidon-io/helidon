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
 * OIDC authentication and security propagation provider.
 */
@Features.Name("OIDC")
@Features.Description("Security provider for Open ID Connect authentication")
@Features.Flavor({HelidonFlavor.SE})
@Features.Path({"Security", "OIDC"})
module io.helidon.security.providers.oidc {

    requires io.helidon.common.crypto;
    requires io.helidon.common;
    requires io.helidon.cors;
    requires io.helidon.webclient;
    requires io.helidon.security.abac.scope;
    requires io.helidon.security.jwt;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.providers.oidc.common;
    requires io.helidon.security.util;
    requires io.helidon.webserver.security;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;
    requires static io.helidon.webserver.cors;
    requires static io.helidon.webserver;

    requires transitive io.helidon.config;
    requires transitive io.helidon.security;

    exports io.helidon.security.providers.oidc;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.oidc.OidcProviderService;

    uses io.helidon.security.providers.oidc.common.spi.TenantConfigProvider;
    uses io.helidon.security.providers.oidc.common.spi.TenantIdProvider;

}
