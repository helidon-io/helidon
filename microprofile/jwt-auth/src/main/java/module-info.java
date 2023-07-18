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
 * Microprofile jwt module.
 *
 * @see org.eclipse.microprofile.jwt
 * @see org.eclipse.microprofile.auth
 */
@Feature(value = "JWT Auth",
        description = "MicroProfile JWT Auth spec implementation",
        in = HelidonFlavor.MP,
        path = {"Security", "JWTAuth"}
)
module io.helidon.microprofile.jwt.auth {
    requires static io.helidon.common.features.api;

    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.ws.rs;
    requires microprofile.config.api;
    requires transitive microprofile.jwt.auth.api;

    requires io.helidon.common;
    requires io.helidon.common.pki;
    requires io.helidon.config;
    requires transitive io.helidon.security;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.security;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.util;
    requires transitive io.helidon.security.jwt;
    requires jakarta.annotation;

    exports io.helidon.microprofile.jwt.auth;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.jwt.auth to weld.core.impl, io.helidon.microprofile.cdi;

    provides io.helidon.security.providers.common.spi.AnnotationAnalyzer with io.helidon.microprofile.jwt.auth.JwtAuthAnnotationAnalyzer;
    provides io.helidon.security.spi.SecurityProviderService with io.helidon.microprofile.jwt.auth.JwtAuthProviderService;
    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.jwt.auth.JwtAuthCdiExtension;
}
