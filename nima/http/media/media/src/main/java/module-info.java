/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * Common classes for Media Support (reading/writing entity as a specific type).
 */
@Feature(value = "Media",
         description = "Media Support",
         in = HelidonFlavor.NIMA,
         invalidIn = HelidonFlavor.SE,
         path = "Media"
)
module io.helidon.nima.http.media {
    requires static io.helidon.common.features.api;
    requires static io.helidon.builder.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.common;
    requires transitive io.helidon.common.http;
    requires transitive io.helidon.common.media.type;
    requires io.helidon.common.buffers;
    requires io.helidon.common.uri;

    exports io.helidon.nima.http.media;
    exports io.helidon.nima.http.media.spi;

    uses io.helidon.nima.http.media.spi.MediaSupportProvider;
}
