/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import io.helidon.http.HeaderNames;
import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.security.SecurityHttpFeature;

/**
 * Creates two services.
 * First service invokes the second with outbound security.
 * There are two endpoints:
 * <ul>
 *     <li>One that does simple identity propagation and one that uses an explicit username</li>
 *     <li>One that uses basic authentication to authenticate users and JWT to propagate identity</li>
 * </ul>- one that does simple identity propagation and one that uses an explicit username.
 * <p>
 * The difference between this example and basic authentication example:
 * <ul>
 *     <li>Configuration files (this example uses ones with -jwt.yaml suffix)</li>
 *     <li>Client property used to override username</li>
 * </ul>
 */
public final class OutboundOverrideJwtExample {

    private OutboundOverrideJwtExample() {
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
                        Server started in %3$d ms

                        ***********************
                        ** Endpoints:        **
                        ***********************

                        http://localhost:%1$d/propagate
                        http://localhost:%1$d/override

                        Backend service started on: http://localhost:%2$d/hello

                        """,
                server.port(),
                server.port("backend"),
                TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
    }

    static void setup(WebServerConfig.Builder server) {
        Config clientConfig = Config.create(ConfigSources.classpath("client-service-jwt.yaml"));
        Config backendConfig = Config.create(ConfigSources.classpath("backend-service-jwt.yaml"));

        server.routing(routing -> routing
                        .addFeature(SecurityHttpFeature.create(clientConfig.get("security.web-server")))
                        .register(new JwtOverrideService()))

                // backend that prints the current user
                .putSocket("backend", socket -> socket
                        .routing(routing -> routing
                                .addFeature(SecurityHttpFeature.create(backendConfig.get("security.web-server")))
                                .get("/hello", (req, res) -> {

                                    // This is the token. It should be bearer <signed JWT base64 encoded>
                                    req.headers().first(HeaderNames.AUTHORIZATION)
                                            .ifPresent(System.out::println);

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
