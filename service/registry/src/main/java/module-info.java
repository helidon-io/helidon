/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import io.helidon.common.features.api.Preview;

/**
 * Core service registry, supporting {@link io.helidon.service.registry.Service.Provider}.
 */
@Feature(value = "Registry",
         description = "Service Registry",
         in = HelidonFlavor.SE,
         path = "Registry"
)
@Preview
module io.helidon.service.registry {
    requires static io.helidon.common.features.api;

    requires io.helidon.service.metadata;
    requires io.helidon.metadata.hson;

    requires transitive io.helidon.common.config;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.types;

    exports io.helidon.service.registry;
    exports io.helidon.service.registry.spi;

    uses io.helidon.service.registry.spi.ServiceRegistryManagerProvider;
}