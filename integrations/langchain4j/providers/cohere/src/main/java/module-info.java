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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Provides integration with Cohere models.
 */
@Features.Name("Langchain4j Cohere")
@Features.Description("Langchain4j Cohere Provider Integration")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path({"Langchain4j", "Cohere"})
@Features.Preview
module io.helidon.integrations.langchain4j.providers.cohere {
    requires static io.helidon.common.features.api;

    requires langchain4j.cohere;
    requires langchain4j.core;

    requires transitive io.helidon.service.registry;
    requires transitive io.helidon.integrations.langchain4j;
    requires transitive io.helidon.common.config;

    exports io.helidon.integrations.langchain4j.providers.cohere;
}