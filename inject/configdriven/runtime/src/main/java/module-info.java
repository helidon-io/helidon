/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
 * Injection Config-Driven Services Module.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Feature(value = "Config Driven",
         since = "4.0.0",
         path = {"Inject", "Config Driven"},
         description = "Config Driven Services")
@Preview
module io.helidon.inject.configdriven.runtime {
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;
    requires static jakarta.annotation;
    requires static jakarta.inject;

    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.types;
    requires transitive io.helidon.config;
    requires transitive io.helidon.inject.api;
    requires transitive io.helidon.inject.configdriven.api; // required for compilation of generated types
    requires transitive io.helidon.inject.runtime;

    exports io.helidon.inject.configdriven.runtime;

}
