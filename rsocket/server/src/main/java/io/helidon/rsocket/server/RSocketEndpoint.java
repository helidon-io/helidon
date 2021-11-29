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

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;


import io.helidon.common.serviceloader.HelidonServiceLoader;

import io.rsocket.DuplexConnection;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketServer;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.transport.ServerTransport.ConnectionAcceptor;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;
import reactor.core.publisher.Mono;



/**
 * RSocket endpoint class.
 */
public class RSocketEndpoint extends Endpoint {

    private static Map<String, ConnectionAcceptor> connectionAcceptorMap = new HashMap<>();
    private final Map<String, DuplexConnection> connections = new ConcurrentHashMap<>();

    private HelidonServiceLoader<DuplexConnectionInterceptor> connectionInterceptors = HelidonServiceLoader
            .builder(ServiceLoader.load(DuplexConnectionInterceptor.class))
            .build();

    private HelidonServiceLoader<RSocketInterceptor> rsocketInterceptors = HelidonServiceLoader
            .builder(ServiceLoader.load(RSocketInterceptor.class))
            .build();

    private String path;

    /**
     * Factory method to create {@link RSocketEndpoint}.
     *
     * @param routing RSocketRouting
     * @param path String
     * @return {@link RSocketEndpoint}
     */
    public static RSocketEndpoint create(RSocketRouting routing, String path){
        return new RSocketEndpoint(routing, path);
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
     * @return Map with acceptors
     */
    public static Map<String, ConnectionAcceptor> connectionAcceptorMap(){
        return connectionAcceptorMap;
    }

    /**
     * Private constructor.
     * @param routing RSocketRouting
     * @param path String
     */
    public RSocketEndpoint(RSocketRouting routing, String path) {
        this.path = path;

        RSocket rSocket = RoutedRSocket.builder()
                .fireAndForgetRoutes(routing.fireAndForgetRoutes())
                .requestChannelRoutes(routing.requestChannelRoutes())
                .requestResponseRoutes(routing.requestResponseRoutes())
                .requestStreamRoutes(routing.requestStreamRoutes())
                .build();

        //Apply interceptors for metrics
        for (RSocketInterceptor interceptor : rsocketInterceptors) {
            rSocket = interceptor.apply(rSocket);
        }

        RSocket finalRSocket = rSocket;

        ConnectionAcceptor connectionAcceptor = RSocketServer
                .create()
                .acceptor((connectionSetupPayload, rs) -> Mono.just(finalRSocket))
                .asConnectionAcceptor();

        connectionAcceptorMap.put(path, connectionAcceptor);
    }


    /**
     * Returns the created and configured RSocket Endpoint.
     * @return Server Endpoint Config
     */
    public ServerEndpointConfig getEndPoint() {
        return ServerEndpointConfig.Builder.create(this.getClass(), path)
                .build();
    }

    /**
     * Function called on connection open, used to organize sessions.
     * @param session Session
     * @param endpointConfig EndpointConfig
     */
    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        DuplexConnection connection = new HelidonDuplexConnection(session);
        //Apply interceptors for metrics
        for (DuplexConnectionInterceptor interceptor : connectionInterceptors) {
            connection = interceptor.apply(DuplexConnectionInterceptor.Type.SERVER, connection);
        }
        connections.put(session.getId(), connection);
        connectionAcceptorMap.get(session.getRequestURI().getPath()).apply(connection).subscribe();
        connection.onClose().doFinally(con -> connections.remove(session.getId())).subscribe();
    }

    /**
     * Function called on connection close, used to dispose resources.
     *
     * @param session Session
     * @param closeReason CloseReason
     */
    @Override
    public void onClose(Session session, CloseReason closeReason) {
        connections.get(session.getId()).dispose();
    }

    /**
     * Function called on Error received, cleans up the resources.
     * @param session Session
     * @param thr Throwable
     */
    @Override
    public void onError(Session session, Throwable thr) {
        connections.get(session.getId()).dispose();
    }
}
