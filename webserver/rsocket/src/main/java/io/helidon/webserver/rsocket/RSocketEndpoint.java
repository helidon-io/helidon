/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.rsocket;

import io.helidon.webserver.rsocket.server.RSocketRouting;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.ServerTransport.ConnectionAcceptor;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

public class RSocketEndpoint extends Endpoint {

    private static ConnectionAcceptor connectionAcceptor;
    final Map<String, HelidonDuplexConnection> connections = new ConcurrentHashMap<>();

    private RSocketRouting routing;
    private String path;

    public static RSocketEndpoint create(RSocketRouting routing, String path){
        return new RSocketEndpoint(routing,path);
    }

    public RSocketEndpoint(){
    }

    private RSocketEndpoint(RSocketRouting routing, String path) {
        this.routing = routing;
        this.path = path;

        connectionAcceptor = RSocketServer
                .create()
                .acceptor((connectionSetupPayload, rSocket) -> Mono.just(RoutedRSocket.builder()
                        .fireAndForgetRoutes(routing.getFireAndForgetRoutes())
                        .requestChannelRoutes(routing.getRequestChannelRoutes())
                        .requestResponseRoutes(routing.getRequestResponseRoutes())
                        .requestStreamRoutes(routing.getRequestStreamRoutes())
                        .build()))
                .asConnectionAcceptor();
    }

    public ServerEndpointConfig getEndPoint() {
        return ServerEndpointConfig.Builder.create(this.getClass(), path)
                .build();
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        final HelidonDuplexConnection connection = new HelidonDuplexConnection(session);
        connections.put(session.getId(), connection);
        connectionAcceptor.apply(connection).subscribe();
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        connections.get(session.getId()).onCloseSink.tryEmitEmpty();
    }
}