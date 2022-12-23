/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
 * Media MultiPart support classes.
 */
@Feature(value = "Multi-part",
        description = "Media support for Multi-part entities",
        in = HelidonFlavor.SE,
        path = {"Media", "Multipart"}
)
module io.helidon.reactive.media.multipart {
    requires static io.helidon.common.features.api;

    requires io.helidon.common;
    requires io.helidon.common.http;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires io.helidon.reactive.media.common;
    requires java.logging;
    exports io.helidon.reactive.media.multipart;
}
