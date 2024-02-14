/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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
package io.helidon.security.examples.outbound;

import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.security.SecurityHttpFeature;

/**
 * Creates two services.
 * <p>
 * First service invokes the second with outbound security.
 * There are two endpoints:
 * <ul>
 *     <li>one that does simple identity propagation and one that uses an explicit username and password</li>
 *     <li>one that uses basic authentication both to authenticate users and to propagate identity.</li>
 * </ul>
 */
public final class OutboundOverrideExample {

    private OutboundOverrideExample() {
    }

    /**
     * Example that propagates identity and on one endpoint explicitly sets the username and password.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build();

        long t = System.nanoTime();
        server.start();
        long time = System.nanoTime() - t;

        server.context().register(server);

        System.out.printf("""
                Server started in %3d ms

                ***********************
                ** Endpoints:        **
                ***********************

                http://localhost:%1d/propagate
                http://localhost:%1d/override

                Backend service started on: http://localhost:%2d/hello

                """, TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS),
                server.port(), server.port(), server.port("backend"));
    }

    static void setup(WebServerConfig.Builder server) {
        Config clientConfig = Config.create(ConfigSources.classpath("client-service.yaml"));
        Config backendConfig = Config.create(ConfigSources.classpath("backend-service.yaml"));

        server.config(clientConfig.get("security"))
                .routing(routing -> routing
                        .addFeature(SecurityHttpFeature.create(clientConfig.get("security.web-server")))
                        .register(new OverrideService()))

                // backend that prints the current user
                .putSocket("backend", socket -> socket
                        .routing(routing -> routing
                                .addFeature(SecurityHttpFeature.create(backendConfig.get("security.web-server")))
                                .get("/hello", (req, res) -> {
                                    String username = req.context()
                                            .get(SecurityContext.class)
                                            .flatMap(SecurityContext::user)
                                            .map(Subject::principal)
                                            .map(Principal::getName)
                                            .orElse("Anonymous");
                                    res.send(username);
                                })));
    }
}
