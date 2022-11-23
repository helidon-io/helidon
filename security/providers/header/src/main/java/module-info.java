/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
 * Header based authentication provider.
 */
@Feature(value = "Header",
        description = "Security provider for header based authentication",
        in = {HelidonFlavor.SE, HelidonFlavor.MP, HelidonFlavor.NIMA},
        path = {"Security", "Provider", "Header"}
)
module io.helidon.security.providers.header {
    requires static io.helidon.common.features.api;

    requires io.helidon.config;
    requires io.helidon.common;
    requires io.helidon.security;
    requires io.helidon.security.util;
    requires io.helidon.security.providers.common;

    requires static io.helidon.config.metadata;

    exports io.helidon.security.providers.header;

    provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.header.HeaderAtnService;
}
