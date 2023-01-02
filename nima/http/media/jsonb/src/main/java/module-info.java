/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import io.helidon.nima.http.media.jsonb.JsonbMediaSupportProvider;
import io.helidon.nima.http.media.spi.MediaSupportProvider;

/**
 * JSON-B media support.
 */
@Feature(value = "JSONB",
        description = "JSON-B media support",
        in = HelidonFlavor.NIMA,
        invalidIn = HelidonFlavor.SE,
        path = {"Media", "JSON-B"}
)
module io.helidon.nima.http.media.jsonb {
    requires static io.helidon.common.features.api;

    requires io.helidon.nima.http.media;
    requires jakarta.json;
    requires jakarta.json.bind;

    exports io.helidon.nima.http.media.jsonb;

    provides MediaSupportProvider with JsonbMediaSupportProvider;
}