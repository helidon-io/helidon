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
package io.helidon.reactive.webserver.examples.mtls;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.Keys;
import io.helidon.common.reactive.Single;
import io.helidon.reactive.webserver.ClientAuthentication;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.SocketConfiguration;
import io.helidon.reactive.webserver.WebServer;
import io.helidon.reactive.webserver.WebServerTls;

/**
 * Setting up {@link WebServer} to support mutual TLS via builder.
 */
public class ServerBuilderMain {

    private ServerBuilderMain() {
    }

    /**
     * Start the example.
     * This will start Helidon {@link WebServer} which is configured by the {@link WebServer.Builder}.
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
        startServer(8080, 443);
    }

    static Single<WebServer> startServer(int unsecured, int secured) {
        SocketConfiguration socketConf = SocketConfiguration.builder()
                .name("secured")
                .port(secured)
                .tls(tlsConfig())
                .build();
        Single<WebServer> webServer = WebServer.builder()
                .port(unsecured)
                .routing(createPlainRouting())
                .addSocket(socketConf, createMtlsRouting())
                .build()
                .start();

        webServer.thenAccept(ws -> {
                    System.out.println("WebServer is up!");
                    System.out.println("Unsecured: http://localhost:" + ws.port() + "/");
                    System.out.println("Secured: https://localhost:" + ws.port("secured") + "/");
                    ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });
        return webServer;
    }

    private static WebServerTls tlsConfig() {
        Keys keyConfig = Keys.builder()
                .keystore(keystore -> keystore
                .trustStore(true)
                .keystore(Resource.create("server.p12"))
                .passphrase("password"))
                .build();
        return WebServerTls.builder()
                .clientAuth(ClientAuthentication.REQUIRE)
                .trust(keyConfig)
                .privateKey(keyConfig)
                .build();
    }

    private static Routing createPlainRouting() {
        return Routing.builder()
                .get("/", (req, res) -> res.send("Hello world unsecured!"))
                .build();
    }

    private static Routing createMtlsRouting() {
        return Routing.builder()
                .get("/", (req, res) -> {
                    String cn = req.headers().first(Http.Header.X_HELIDON_CN).orElse("Unknown CN");
                    res.send("Hello " + cn + "!");
                })
                .build();
    }

}
