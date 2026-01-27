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

package io.helidon.declarative.tests.websocket;

import io.helidon.http.Headers;
import io.helidon.http.Http;
import io.helidon.http.HttpPrologue;
import io.helidon.service.registry.Service;
import io.helidon.webserver.websocket.WebSocketServer;
import io.helidon.websocket.WebSocket;

@SuppressWarnings("deprecation")
@WebSocketServer.Endpoint
@Http.Path("/websocket/upgrade/3")
@Service.Singleton
class TestEndpointUpgrade3 {
    @WebSocket.OnHttpUpgrade
    void onUpgrade(HttpPrologue prologue, Headers headers) {
    }
}
