/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import io.helidon.reactive.media.common.spi.MediaSupportProvider;
import io.helidon.reactive.media.jsonb.JsonbProvider;
import io.helidon.reactive.media.jsonb.JsonbSupport;

/**
 * JSON-B support common classes.
 *
 * @see JsonbSupport
 */
@Feature(value = "JSON-B",
        description = "Media support for Jakarta JSON Binding",
        in = HelidonFlavor.SE,
        path = {"Media", "Jsonb"}
)
module io.helidon.reactive.media.jsonb {
    requires static io.helidon.common.features.api;

    requires io.helidon.common;
    requires io.helidon.common.http;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires io.helidon.config;
    requires io.helidon.reactive.media.common;
    requires transitive jakarta.json.bind;

    exports io.helidon.reactive.media.jsonb;

    provides MediaSupportProvider with JsonbProvider;
}
