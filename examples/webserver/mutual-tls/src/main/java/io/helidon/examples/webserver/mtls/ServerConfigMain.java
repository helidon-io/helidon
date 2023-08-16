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
package io.helidon.examples.webserver.mtls;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;

/**
 * Setting up {@link WebServer} to support mutual TLS via configuration.
 */
public class ServerConfigMain {

    private ServerConfigMain() {
    }

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
        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder, config.get("server"));
        WebServer server = builder.build().start();
        System.out.printf("""
                WebServer is up!
                Unsecured: http://localhost:%1$d
                Secured: https://localhost:%2$d
                """, server.port(), server.port("secured"));
    }

    static void setup(WebServerConfig.Builder server, Config config) {
        server.config(config)
                .routing(ServerConfigMain::plainRouting)
                .putSocket("secured", socket -> socket
                        .from(server.sockets().get("secured"))
                        .routing(routing -> routing
                                .register("/", new SecureService())));
    }

    private static void plainRouting(HttpRouting.Builder routing) {
        routing.get("/", (req, res) -> res.send("Hello world unsecured!"));
    }
}
