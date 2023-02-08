/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
 * Static content support for Helidon WebServer.
 * Supports both classpath and file system based static content.
 */
@Feature(value = "Static Content",
        description = "Static content support for webserver",
        in = HelidonFlavor.SE,
        path = {"WebServer", "Static Content"}
)
module io.helidon.reactive.webserver.staticcontent {
    requires static io.helidon.common.features.api;

    requires io.helidon.common.http;
    requires io.helidon.common.media.type;
    requires io.helidon.common.reactive;
    requires io.helidon.reactive.media.common;
    requires io.helidon.reactive.webserver;

    exports io.helidon.reactive.webserver.staticcontent;
}