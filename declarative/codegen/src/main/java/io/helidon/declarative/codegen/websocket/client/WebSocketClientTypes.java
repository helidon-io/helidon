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

package io.helidon.declarative.codegen.websocket.client;

import io.helidon.common.types.TypeName;

final class WebSocketClientTypes {
    static final TypeName ANNOTATION_ENDPOINT = TypeName.create("io.helidon.webclient.websocket.WebSocketClient.Endpoint");
    static final TypeName CLIENT_ENDPOINT_FACTORY = TypeName.create("io.helidon.webclient.websocket.WsClientEndpointFactoryBase");
    static final TypeName WS_CLIENT = TypeName.create("io.helidon.webclient.websocket.WsClient");

    private WebSocketClientTypes() {
    }
}
