/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
package io.helidon.examples.webserver.mtls;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.TlsClientAuth;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;

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
        WebServerConfig.Builder builder = WebServer.builder()
                .port(8080)
                .putSocket("secured", socket -> socket.port(443));
        setup(builder);
        WebServer server = builder.build().start();
        System.out.printf("""
                WebServer is up!
                Unsecured: http://localhost:%1$d
                Secured: https://localhost:%2$d
                """, server.port(), server.port("secured"));
    }

    static void setup(WebServerConfig.Builder server) {
        server.routing(ServerBuilderMain::plainRouting)
                .putSocket("secured", socket -> securedSocket(server, socket));
    }

    static void plainRouting(HttpRouting.Builder routing) {
        routing.get("/", (req, res) -> res.send("Hello world unsecured!"));
    }

    private static void securedSocket(WebServerConfig.Builder server, ListenerConfig.Builder socket) {
        Keys keyConfig = Keys.builder()
                .keystore(store -> store
                        .trustStore(true)
                        .keystore(Resource.create("server.p12"))
                        .passphrase("changeit"))
                .build();

        socket.from(server.sockets().get("secured"))
                .tls(tls -> tls
                        .endpointIdentificationAlgorithm("NONE")
                        .clientAuth(TlsClientAuth.REQUIRED)
                        .trust(keyConfig)
                        .privateKey(keyConfig)
                        .privateKeyCertChain(keyConfig))
                .routing(routing -> routing
                        .register("/", new SecureService()));
    }
}
