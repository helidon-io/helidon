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
 * OIDC authentication and security propagation provider.
 */
@Feature(value = "OIDC",
        description = "Security provider for Open ID Connect authentication",
        in = {HelidonFlavor.NIMA},
        path = {"Security", "OIDC"}
)
module io.helidon.security.providers.oidc {
    requires static io.helidon.common.features.api;

    requires io.helidon.config;
    requires io.helidon.common;
    requires io.helidon.common.crypto;
    requires io.helidon.security;

    requires io.helidon.security.providers.oidc.common;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.util;
    requires io.helidon.security.abac.scope;
    requires io.helidon.security.jwt;
    requires io.helidon.reactive.webclient;

    requires io.helidon.cors;
    requires static io.helidon.nima.webserver;
    requires static io.helidon.nima.webserver.cors;
    requires static io.helidon.security.integration.nima;

    requires static io.helidon.config.metadata;

    exports io.helidon.security.providers.oidc;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.oidc.OidcProviderService;

    uses io.helidon.security.providers.oidc.common.spi.TenantConfigProvider;
    uses io.helidon.security.providers.oidc.common.spi.TenantIdProvider;
}
