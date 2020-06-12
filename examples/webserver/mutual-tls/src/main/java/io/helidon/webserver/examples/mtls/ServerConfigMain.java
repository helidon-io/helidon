/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Setting up {@link WebServer} to support mutual TLS via configuration.
 */
public class ServerConfigMain {

    /**
     * Start the example.
     * This will start Helidon {@link WebServer} which is configured by the configuration.
     * There will be two sockets running:
     * <p><ul>
     * <li>{@code 8080} - without TLS protection
     * <li>{@code 443} - with TLS protection
     * </ul><p>
     * Both of the ports mentioned above are default ports for this example and can be changed via configuration file.
     *
     * @param args start arguments are ignored
     */
    public static void main(String[] args) {
        Config config = Config.create();
        startServer(config.get("server"));
    }

    static WebServer startServer(Config config) {
        WebServer webServer = WebServer.builder(createPlainRouting())
                .config(config)
                .addNamedRouting("secured", createMtlsRouting())
                .build();
        webServer.start()
                .thenAccept(ws -> {
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
