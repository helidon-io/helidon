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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon JSON-RPC client support. See
 * <a href="https://www.jsonrpc.org/specification">JSON-RPC 2.0 Specification</a>.
 */
@Features.Name("JSON-RPC")
@Features.Description("Support for the JSON-RPC protocol in Webclient")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"WebClient", "JSON-RPC"})
@Features.Preview
@Features.Since("4.3.0")
module io.helidon.webclient.jsonrpc {

    requires jakarta.json;
    requires jakarta.json.bind;
    requires io.helidon.jsonrpc.core;
    requires io.helidon.builder.api;
    requires io.helidon.webclient;
    requires io.helidon.common.features.api;

    exports io.helidon.webclient.jsonrpc;
}
