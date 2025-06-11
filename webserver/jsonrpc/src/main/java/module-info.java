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

import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.Preview;

/**
 * Helidon WebServer JSON-RPC Support.
 */
@Preview
@Feature(value = "JSON-RPC",
         description = "WebServer JSON-RPC Support",
         in = HelidonFlavor.SE,
         path = {"WebServer", "JSON-RPC"}
)
@SuppressWarnings({ "requires-automatic"})
module io.helidon.webserver.jsonrpc {

    requires io.helidon.builder.api;
    requires io.helidon.common.config;

    requires static io.helidon.common.features.api;
    requires io.helidon.webserver;
    requires io.helidon.jsonrpc.core;
    requires jakarta.json;
    requires jakarta.json.bind;

    exports io.helidon.webserver.jsonrpc;
}
