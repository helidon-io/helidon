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

package io.helidon.examples.rsocket.server;

import io.helidon.rsocket.server.HelidonTcpRSocketServer;
import io.helidon.rsocket.server.RSocketRouting;
import io.helidon.rsocket.server.RoutedRSocket;
import io.helidon.webserver.WebServer;

import java.util.concurrent.CompletableFuture;


/**
 * Application demonstrates combination of websocket and REST.
 */
public class MainTCP {

    private MainTCP() {
    }

    static RSocketRouting rSocketRouting() {

        MyRSocketService myRSocketService = new MyRSocketService();
        RSocketRouting rSocketRouting = RSocketRouting.builder()
                .register(myRSocketService)
                .build();
        return rSocketRouting;
    }


    static WebServer startWebServer() {

// 1 the easiest option to start with tcp
//        RSocketServer.create()
//            .acceptor((payload, rsocket) -> Mono.just(RoutedRSocket.builder().build()))
//            .bindNow(TcpServerTransport.create(9090));


//        ServerTransport.ConnectionAcceptor connectionAcceptor = RSocketServer.create()
//                .asConnectionAcceptor();
//
//
//
//        TcpServer server = TcpServer.builder()
//                .config1
//                .config1
//                .config1
//                .build(connection -> {
//                    connectionAcceptor.apply(new TcpHelidonDuplexConnection()).subscribe();
//                });

        HelidonTcpRSocketServer rSocketServer = HelidonTcpRSocketServer.builder()
                .port(9090)
                .rsocket(RoutedRSocket
                        .builder()
                        .rSocketRouting(rSocketRouting())
                        .build())
                .build();

        rSocketServer.start();

        WebServer server = WebServer.builder()
                .port(8080)
                .build();

        // Start webserver
        CompletableFuture<Void> started = new CompletableFuture<>();
        server.start().thenAccept(ws -> {
            System.out.println("WEB server is up! http://localhost:" + ws.port());
            started.complete(null);
        });

        // Wait for webserver to start before returning
        try {
            started.toCompletableFuture().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return server;
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        WebServer server = startWebServer();

        // Server threads are not demon. NO need to block. Just react.
        server.whenShutdown()
                .thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));

    }
}
