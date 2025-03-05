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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Incubating;

/**
 * Helidon Data Repository Runtime.
 */
@Feature(value = "Data",
         since = "4.3.0",
         path = "Data",
         description = "Helidon Data - repository pattern")
@Incubating
module io.helidon.data {

    requires static io.helidon.common.features.api;

    requires io.helidon.service.registry;

    requires transitive io.helidon.transaction;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.config;

    exports io.helidon.data;
    exports io.helidon.data.spi;

    uses io.helidon.data.spi.DataProvider;
    uses io.helidon.data.spi.ProviderConfigProvider;

    provides io.helidon.data.spi.DataProvider
            with io.helidon.data.HelidonDataProvider;
}
