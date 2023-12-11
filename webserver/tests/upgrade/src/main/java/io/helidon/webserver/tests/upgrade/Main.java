/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.upgrade;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.http.Method;
import io.helidon.http.PathMatchers;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.websocket.WsRouting;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static io.helidon.http.Method.PUT;

public class Main {

    public static void main(String[] args) {
        LogConfig.configureRuntime();
        startServer(true);
    }

    public static WebServer startServer(boolean ssl) {
        Keys privateKeyConfig = Keys.builder()
                .keystore(store -> store.passphrase("password")
                        .keystore(Resource.create("server.p12")))
                .build();

        return WebServer.builder()
                .update(server -> {
                    server.host("localhost");
                    if (ssl) {
                        server.tls(tls -> tls
                                .privateKey(privateKeyConfig)
                                .privateKeyCertChain(privateKeyConfig));
                    }
                })
                .routing(r -> r
                        .get("/", (req, res) -> res.send("HTTP Version " + req.prologue().protocolVersion() + "\n"))
                        .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route\n")))
                        .route(Http2Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/2.0 route\n")))
                        .route(Http1Route.route(GET, "/versionspecific1", (req, res) -> res.send("HTTP/1.1 route\n")))
                        .route(Http2Route.route(GET, "/versionspecific2", (req, res) -> res.send("HTTP/2.0 route\n")))
                        .route(Method.predicate(GET),
                               PathMatchers.create("/multi*"),
                               (req, res) -> res.send("HTTP/" + req.prologue().protocolVersion()
                                                              + " route " + req.prologue().method() + "\n"))
                        .route(Method.predicate(POST, PUT),
                               PathMatchers.create("/multi*"),
                               (req, res) ->
                               {
                                   if (req.content().hasEntity()) {
                                       // Workaround for #7427
                                       req.content().as(String.class);
                                   }
                                   res.send("HTTP/" + req.prologue().protocolVersion()
                                                    + " route " + req.prologue().method() + "\n");
                               }))
                .addRouting(WsRouting.builder()
                                    .endpoint("/ws-echo", new EchoWsListener()))
                .build()
                .start();
    }
}
