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
import io.helidon.common.features.api.Preview;

/**
 * Service registry with injection support API.
 */
@Feature(value = "Injection",
         description = "Injection, interception and config bean support for Service Registry",
         path = {"Registry", "Injection"},
         since = "4.2.0")
@Preview
module io.helidon.service.inject {
    requires static io.helidon.common.features.api;

    requires io.helidon;
    requires io.helidon.logging.common;
    requires io.helidon.metrics.api;
    requires io.helidon.service.metadata;

    requires transitive io.helidon.service.inject.api;
    requires transitive io.helidon.service.registry;
    requires transitive io.helidon.common.config;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.types;
    requires transitive io.helidon.common.configurable;

    exports io.helidon.service.inject;

    provides io.helidon.service.registry.spi.ServiceRegistryManagerProvider
            with io.helidon.service.inject.InjectRegistryManagerProvider;

    provides io.helidon.spi.HelidonStartupProvider
            with io.helidon.service.inject.InjectStartupProvider;
}