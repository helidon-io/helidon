/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.examples.mtls;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.Keys;
import io.helidon.nima.common.tls.TlsClientAuth;
import io.helidon.nima.webserver.ListenerConfig;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * Setting up {@link WebServer} to support mutual TLS via builder.
 */
public class ServerBuilderMain {

    private ServerBuilderMain() {
    }

    /**
     * Start the example.
     * This will start Helidon {@link WebServer} which is configured by the {@link WebServerConfig.Builder}.
     * There will be two sockets running:
     * <p><ul>
     * <li>{@code 8080} - without TLS protection
     * <li>{@code 443} - with TLS protection
     * </ul><p>
     * Both of the ports mentioned above are default ports for this example and can be changed by updating
     * values in this method.
     *
     * @param args start arguments are ignored
     */
    public static void main(String[] args) {
        WebServerConfig.Builder builder = WebServer.builder().port(8080);
        setup(builder);
        WebServer server = builder.build().start();
        System.out.printf("""
                WebServer is up!
                Unsecured: http://localhost:%1$d
                Secured: https://localhost:%2$d
                """, server.port(), server.port("secured"));
    }

    static void setup(WebServerConfig.Builder server) {
        server.port(8080)
                .routing(ServerBuilderMain::plainRouting)
                .putSocket("secured", ServerBuilderMain::securedSocket);
    }

    static void plainRouting(HttpRouting.Builder routing) {
        routing.get("/", (req, res) -> res.send("Hello world unsecured!"));
    }

    private static void securedSocket(ListenerConfig.Builder socket) {
        Keys keyConfig = Keys.builder()
                .keystore(store -> store
                        .trustStore(true)
                        .keystore(Resource.create("server.p12"))
                        .passphrase("password"))
                .build();

        socket.port(443)
                .tls(tls -> tls
                        .clientAuth(TlsClientAuth.REQUIRED)
                        .trust(keyConfig)
                        .privateKey(keyConfig))
                .routing(routing -> routing
                        .get("/", (req, res) -> {
                            String cn = req.headers().first(Http.Header.X_HELIDON_CN).orElse("Unknown CN");
                            res.send("Hello " + cn + "!");
                        }));
    }
}
