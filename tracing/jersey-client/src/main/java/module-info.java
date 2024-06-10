/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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
 * Tracing integration with jersey (JAX-RS) client.
 */
@Feature(value = "Jersey Client",
        description = "Tracing integration with Jersey client",
        in = {HelidonFlavor.MP, HelidonFlavor.SE},
        path = {"Tracing", "Integration", "JerseyClient"}
)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.tracing.jersey.client {

    requires io.helidon.common.context;
    requires io.helidon.common;
    requires io.helidon.tracing.config;
    requires io.helidon.tracing;
    requires jakarta.annotation;
    requires jersey.common;

    requires static io.helidon.common.features.api;
    
    requires transitive jakarta.ws.rs;
    requires transitive jersey.client;

    exports io.helidon.tracing.jersey.client;

    // needed to propagate tracing context from server to client
    exports io.helidon.tracing.jersey.client.internal to io.helidon.tracing.jersey, io.helidon.microprofile.tracing;

    uses io.helidon.tracing.spi.TracerProvider;

    provides org.glassfish.jersey.internal.spi.AutoDiscoverable
            with io.helidon.tracing.jersey.client.ClientTracingAutoDiscoverable;

}
