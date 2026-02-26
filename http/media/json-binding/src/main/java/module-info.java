/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
 * Helidon JSON Binding Media Support.
 * Provides JSON Binding media type support for HTTP requests and responses,
 * enabling automatic serialization and deserialization of JSON data.
 */
@Features.Name("Helidon JSON Binding")
@Features.Description("Helidon JSON Binding media support")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"Media", "JSON Binding"})
module io.helidon.http.media.json.binding {

    requires static io.helidon.common.features.api;

    requires io.helidon.builder.api;
    requires io.helidon.http.media;
    requires io.helidon.json.binding;
    requires io.helidon.common.media.type;
    requires io.helidon.http;

    requires transitive io.helidon.config;

    exports io.helidon.http.media.json.binding;

    provides io.helidon.http.media.spi.MediaSupportProvider
            with io.helidon.http.media.json.binding.JsonBindingMediaSupportProvider;
}
