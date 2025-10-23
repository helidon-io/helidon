/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * GraphQL microprofile server module.
 */
@Features.Name("GraphQL")
@Features.Description("MicroProfile GraphQL spec implementation")
@Features.Flavor(HelidonFlavor.MP)
@Features.Path("GraphQL")
@Features.Aot(description = "Experimental support, tested on limited use cases")
module io.helidon.microprofile.graphql.server {

    requires com.graphqljava.extendedscalars;
    requires com.graphqljava;
    requires io.helidon.config;
    requires io.helidon.graphql.server;
    requires io.helidon.microprofile.cdi;
    requires io.helidon.microprofile.server;
    requires io.helidon.webserver.graphql;
    requires jakarta.annotation;
    requires jakarta.json.bind;
    requires java.desktop;
    requires microprofile.config.api;
    requires microprofile.graphql.api;
    requires org.eclipse.yasson;
    requires org.jboss.jandex;

    requires static io.helidon.common.features.api;

    requires transitive jakarta.cdi;

    exports io.helidon.microprofile.graphql.server;

    provides jakarta.enterprise.inject.spi.Extension with
            io.helidon.microprofile.graphql.server.GraphQlCdiExtension;

    opens io.helidon.microprofile.graphql.server to weld.core.impl;
	
}
