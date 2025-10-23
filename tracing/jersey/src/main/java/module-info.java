/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates.
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
 * Tracing integration with Jersey.
 */
@Features.Name("Jersey Server")
@Features.Description("Tracing integration with Jersey server")
@Features.Flavor({HelidonFlavor.MP, HelidonFlavor.SE})
@Features.Path({"Tracing", "Integration", "Jersey"})
module io.helidon.tracing.jersey {

    requires io.helidon.common.context;
    requires io.helidon.common;
    requires io.helidon.jersey.common;
    requires io.helidon.tracing.config;
    requires jakarta.annotation;
    requires jersey.server;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.tracing.jersey.client;
    requires transitive io.helidon.tracing;
    requires transitive jakarta.ws.rs;

    exports io.helidon.tracing.jersey;
	
}
