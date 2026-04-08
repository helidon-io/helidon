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
 * JSON Smile Media Support.
 * Provides Smile media type support for HTTP requests and responses,
 * enabling automatic serialization and deserialization of Smile data.
 */
@Features.Name("Smile")
@Features.Description("Smile media support")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"Media", "Smile"})
module io.helidon.http.media.json.smile {

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires io.helidon.builder.api;
    requires io.helidon.http.media;
    requires io.helidon.json;
    requires io.helidon.json.smile;
    requires io.helidon.json.binding;

    requires transitive io.helidon.config;

    exports io.helidon.http.media.json.smile;

    provides io.helidon.http.media.spi.MediaSupportProvider
            with io.helidon.http.media.json.smile.SmileMediaSupportProvider;
}
