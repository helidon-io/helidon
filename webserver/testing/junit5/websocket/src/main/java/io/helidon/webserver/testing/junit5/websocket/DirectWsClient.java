/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.websocket;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webclient.websocket.WsClientConfig;
import io.helidon.webserver.websocket.WsRoute;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsListener;

/**
 * A client for WebSocket, that directly invokes routing (and bypasses network).
 */
public class DirectWsClient implements WsClient {
    private final List<DirectWsConnection> connections = new ArrayList<>();
    private final WsRouting routing;

    private DirectWsClient(WsRouting routing) {
        this.routing = routing;

        routing.beforeStart();
    }

    /**
     * Create a new client based on the provided routing.
     *
     * @param routing used to discover route to handle a new connection
     * @return a new instance for the provided routing
     */
    public static DirectWsClient create(WsRouting routing) {
        return new DirectWsClient(routing);
    }

    @Override
    public void connect(URI uri, WsListener clientListener) {
        HttpPrologue prologue = HttpPrologue.create("ws", "ws", "13", Method.GET, uri.getRawPath(), false);
        WsRoute route = routing.findRoute(prologue);
        DirectWsConnection directWsConnection = DirectWsConnection.create(prologue, clientListener, route);
        directWsConnection.start();
        connections.add(directWsConnection);
    }

    @Override
    public void connect(String path, WsListener listener) {
        try {
            connect(new URI("ws", null, "helidon-unit", 65000, path, null, null), listener);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot create URI from provided path", e);
        }
    }

    @Override
    public WsClientConfig prototype() {
        return WsClientConfig.create();
    }

    void close() {
        connections.forEach(DirectWsConnection::stop);
        this.routing.afterStop();
    }
}
