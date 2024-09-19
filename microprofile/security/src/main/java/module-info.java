/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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
 * Microprofile configuration module.
 */
@Feature(value = "Security",
        description = "Security support",
        in = HelidonFlavor.MP,
        path = "Security"
)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.microprofile.security {

    requires io.helidon.config.mp;
    requires io.helidon.jersey.common;
    requires io.helidon.microprofile.cdi;
    requires io.helidon.microprofile.server;
    requires io.helidon.security.annotations;
    requires io.helidon.security.integration.common;
    requires io.helidon.security.providers.abac;
    requires io.helidon.security.providers.common;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.config;
    requires transitive io.helidon.webserver.security;
    requires transitive io.helidon.security;
    requires transitive jakarta.cdi;
    requires transitive jakarta.ws.rs;
    requires transitive jersey.common;

    exports io.helidon.microprofile.security;
    exports io.helidon.microprofile.security.spi;

    uses io.helidon.security.providers.common.spi.AnnotationAnalyzer;
    uses io.helidon.microprofile.security.spi.SecurityResponseMapper;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.security to weld.core.impl, io.helidon.microprofile.cdi, org.glassfish.hk2.locator;
    opens io.helidon.microprofile.security.spi to io.helidon.microprofile.cdi, weld.core.impl;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.security.SecurityCdiExtension;
    provides org.glassfish.jersey.internal.spi.AutoDiscoverable
            with io.helidon.microprofile.security.ClientSecurityAutoDiscoverable;

}
