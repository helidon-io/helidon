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
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon WebServer SSE Support.
 */
@Feature(value = "SSE",
         description = "WebServer SSE support",
         in = HelidonFlavor.SE,
         path = {"WebServer", "SSE"}
)
module io.helidon.webserver.sse {

    requires static io.helidon.common.features.api;

    requires io.helidon.common.socket;
    requires transitive io.helidon.common;
    requires transitive io.helidon.http.sse;
    requires transitive io.helidon.webserver;

    exports io.helidon.webserver.sse;

    provides io.helidon.webserver.http.spi.SinkProvider with io.helidon.webserver.sse.SseSinkProvider;

}
