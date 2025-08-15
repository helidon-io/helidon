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
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Langchain4j main integration module and API.
 */
@Feature(value = "Langchain4j",
         description = "Langchain4j Integration",
         in = {HelidonFlavor.SE, HelidonFlavor.MP},
         path = "Langchain4j"
)
@Aot(value = false, description = "Not yet supported in native image")
@Preview
module io.helidon.integrations.langchain4j {
    requires static io.helidon.common.features.api;

    requires transitive io.helidon.config;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.service.registry;
    requires langchain4j.core;
    requires langchain4j;

    exports io.helidon.integrations.langchain4j;

    provides dev.langchain4j.spi.services.TokenStreamAdapter
            with io.helidon.integrations.langchain4j.TokenStreamToStreamAdapter;
}