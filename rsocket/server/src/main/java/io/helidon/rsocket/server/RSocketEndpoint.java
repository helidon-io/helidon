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

package io.helidon.rsocket.server;

import io.rsocket.core.RSocketServer;
import io.rsocket.transport.ServerTransport.ConnectionAcceptor;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

/**
 * RSocket endpoint class.
 */
public class RSocketEndpoint extends Endpoint {

    protected static Map<String,ConnectionAcceptor> connectionAcceptorMap = new HashMap<>();
    protected final Map<String, HelidonDuplexConnection> connections = new ConcurrentHashMap<>();

    protected String path;

    /**
     * Factory method to create {@link RSocketEndpoint}
     *
     * @param routing
     * @param path
     * @return {@link RSocketEndpoint}
     */
    public static RSocketEndpoint create(RSocketRouting routing, String path){
        return new RSocketEndpoint(routing,path);
    }

    /**
     * Empty public constructor.
     */
    public RSocketEndpoint(){
        //Empty constructor required for Tyrus
    }

    /**
     * Get all connection information.
     *
     * @return
     */
    public static Map<String,ConnectionAcceptor> connectionAcceptorMap(){
        return connectionAcceptorMap;
    }

    /**
     * Private constructor.
     * @param routing
     * @param path
     */
    public RSocketEndpoint(RSocketRouting routing, String path) {
        this.path = path;

        ConnectionAcceptor connectionAcceptor = RSocketServer
                .create()
                .acceptor((connectionSetupPayload, rSocket) -> Mono.just(RoutedRSocket.builder()
                        .fireAndForgetRoutes(routing.fireAndForgetRoutes())
                        .requestChannelRoutes(routing.requestChannelRoutes())
                        .requestResponseRoutes(routing.requestResponseRoutes())
                        .requestStreamRoutes(routing.requestStreamRoutes())
                        .build()))
                .asConnectionAcceptor();

        connectionAcceptorMap.put(path,connectionAcceptor);
    }


    /**
     * Returns the created and configured RSocket Endpoint.
     */
    public ServerEndpointConfig getEndPoint() {
        return ServerEndpointConfig.Builder.create(this.getClass(), path)
                .build();
    }

    /**
     * Function called on connection open, used to organize sessions.
     * @param session
     * @param endpointConfig
     */
    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        final HelidonDuplexConnection connection = new HelidonDuplexConnection(session);
        connections.put(session.getId(), connection);
        connectionAcceptorMap.get(session.getRequestURI().getPath()).apply(connection).subscribe();
        connection.onClose().doFinally(con -> connections.remove(session.getId())).subscribe();
    }

    /**
     * Function called on connection close, used to dispose resources.
     *
     * @param session
     * @param closeReason
     */
    @Override
    public void onClose(Session session, CloseReason closeReason) {
        connections.get(session.getId()).dispose();
    }

    /**
     * Function called on Error received, cleans up the resources.
     * @param session
     * @param thr
     */
    @Override
    public void onError(Session session, Throwable thr) {
        connections.get(session.getId()).dispose();
    }
}