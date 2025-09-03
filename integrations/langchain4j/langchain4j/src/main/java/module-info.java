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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Langchain4j main integration module and API.
 */
@Features.Name("Langchain4j")
@Features.Description("Langchain4j Integration")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path("Langchain4j")
@Features.Aot(value = false, description  = "Not yet supported in native image")
@Features.Preview
module io.helidon.integrations.langchain4j {
    requires static io.helidon.common.features.api;

    requires transitive io.helidon.config;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.service.registry;
    requires langchain4j.core;
    requires langchain4j;
    requires io.helidon.metrics.api;
    requires langchain4j.mcp;

    exports io.helidon.integrations.langchain4j;

    provides dev.langchain4j.spi.services.TokenStreamAdapter
            with io.helidon.integrations.langchain4j.TokenStreamToStreamAdapter;
}
