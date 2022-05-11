/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.integration.webserver.upgrade;

import io.helidon.common.LogConfig;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.common.reactive.Single;
import io.helidon.webserver.Http1Route;
import io.helidon.webserver.PathMatcher;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerTls;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.websocket.WebSocketRouting;

import static io.helidon.common.http.Http.Method.GET;
import static io.helidon.common.http.Http.Method.POST;
import static io.helidon.common.http.Http.Method.PUT;

import jakarta.websocket.server.ServerEndpointConfig;


public class Main {

    public static void main(String[] args) {
        startServer(8070, true).await();
    }

    public static Single<WebServer> startServer(int port, boolean ssl) {
        LogConfig.configureRuntime();
        return WebServer.builder()
                .defaultSocket(s -> {
                    s.bindAddress("localhost")
                            .port(port);

                    if (ssl) {
                        s.tls(WebServerTls.builder()
                                .privateKey(KeyConfig.keystoreBuilder()
                                        .keystorePassphrase("password")
                                        .keystore(Resource.create("server.p12"))
                                        .build()));
                    }

                })
                .routing(r -> r
                        .get("/", (req, res) -> res.send("HTTP Version " + req.version() + "\n"))

                        .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route\n")))
                        .route(Http2Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/2.0 route\n")))

                        .route(Http1Route.route(GET, "/versionspecific1", (req, res) -> res.send("HTTP/1.1 route\n")))
                        .route(Http2Route.route(GET, "/versionspecific2", (req, res) -> res.send("HTTP/2.0 route\n")))

                        .route(Http1Route.route(
                                        PathMatcher.create("/multi*"),
                                        (req, res) -> res.send("HTTP/1.1 route " + req.method().name() + "\n"),
                                        GET, POST, PUT
                                )
                        )
                        .route(Http2Route.route(
                                        PathMatcher.create("/multi*"),
                                        (req, res) -> res.send("HTTP/2.0 route " + req.method().name() + "\n"),
                                        GET, POST, PUT
                                )
                        )
                )
                .addRouting(WebSocketRouting.builder()
                        .endpoint("/ws-conf", ServerEndpointConfig.Builder.create(ConfiguredEndpoint.class, "/echo").build())
                        .endpoint("/ws-annotated", AnnotatedEndpoint.class)// also /echo
                )
                .build()
                .start();
    }
}
