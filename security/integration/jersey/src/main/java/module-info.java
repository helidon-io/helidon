/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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
 * Security integration with Jersey.
 */
@Feature(value = "Jersey",
        description = "Security integration with Jersey (JAX-RS implementation)",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Security", "Integration", "Jersey"}
)
module io.helidon.security.integration.jersey {
    requires static io.helidon.common.features.api;

    requires jakarta.annotation;

    requires transitive io.helidon.security;
    requires transitive io.helidon.security.annotations;
    requires transitive io.helidon.security.providers.common;
    requires transitive io.helidon.security.util;
    requires transitive jakarta.ws.rs;

    requires io.helidon.common.context;
    requires io.helidon.common.uri;
    requires io.helidon.jersey.common;
    requires io.helidon.jersey.server;
    requires io.helidon.jersey.client;
    requires io.helidon.security.integration.common;
    requires io.helidon.reactive.webclient.jaxrs;

    requires jakarta.inject;

    exports io.helidon.security.integration.jersey;

    // needed for injection (uses constructor injection)
    opens io.helidon.security.integration.jersey;

    uses io.helidon.security.providers.common.spi.AnnotationAnalyzer;
    uses io.helidon.security.integration.jersey.SecurityResponseMapper;
}
