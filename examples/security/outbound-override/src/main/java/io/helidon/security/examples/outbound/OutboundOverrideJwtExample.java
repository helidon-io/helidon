/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletionStage;

import io.helidon.config.Config;
import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.jwt.JwtProvider;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.security.examples.outbound.OutboundOverrideUtil.createConfig;
import static io.helidon.security.examples.outbound.OutboundOverrideUtil.getSecurityContext;
import static io.helidon.security.examples.outbound.OutboundOverrideUtil.sendError;
import static io.helidon.security.examples.outbound.OutboundOverrideUtil.startServer;
import static io.helidon.security.examples.outbound.OutboundOverrideUtil.webTarget;

/**
 * Creates two services. First service invokes the second with outbound security. There are two endpoints - one that
 * does simple identity propagation and one that uses an explicit username.
 *
 * Uses basic authentication to authenticate users and JWT to propagate identity.
 *
 * The difference between this example and basic authentication example:
 * <ul>
 * <li>Configuration files (this example uses ones with -jwt.yaml suffix)</li>
 * <li>Property name used in {@link #override(ServerRequest, ServerResponse)} method to override username</li>
 * </ul>
 */
public final class OutboundOverrideJwtExample {
    private static volatile int clientPort;
    private static volatile int servingPort;

    private OutboundOverrideJwtExample() {
    }

    /**
     * Example that propagates identity and on one endpoint explicitly sets the username and password.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        CompletionStage<Void> first = startClientService(8080);
        CompletionStage<Void> second = startServingService(9080);

        first.toCompletableFuture().join();
        second.toCompletableFuture().join();

        System.out.println("Started services. Main endpoints:");
        System.out.println("http://localhost:" + clientPort + "/propagate");
        System.out.println("http://localhost:" + clientPort + "/override");
        System.out.println();
        System.out.println("Backend service started on:");
        System.out.println("http://localhost:" + servingPort + "/hello");
    }

    static CompletionStage<Void> startServingService(int port) {
        Config config = createConfig("serving-service-jwt");

        Routing routing = Routing.builder()
                        .register(WebSecurity.create(config.get("security")))
                        .get("/hello", (req, res) -> {
                            // This is the token. It should be bearer <signed JWT base64 encoded>
                            req.headers().first("Authorization")
                                    .ifPresent(System.out::println);
                            res.send(req.context().get(SecurityContext.class).flatMap(SecurityContext::user).map(
                                    Subject::principal).map(Principal::getName).orElse("Anonymous"));
                        }).build();
        return startServer(routing, port, server -> servingPort = server.port());
    }

    static CompletionStage<Void> startClientService(int port) {
        Config config = createConfig("client-service-jwt");

        Routing routing = Routing.builder()
                .register(WebSecurity.create(config.get("security")))
                .get("/override", OutboundOverrideJwtExample::override)
                .get("/propagate", OutboundOverrideJwtExample::propagate)
                .build();
        return startServer(routing, port, server -> clientPort = server.port());
    }

    private static void override(ServerRequest req, ServerResponse res) {
        SecurityContext context = getSecurityContext(req);

        webTarget(servingPort)
                .property(JwtProvider.EP_PROPERTY_OUTBOUND_USER, "jill")
                .request(String.class)
                .thenAccept(result -> res.send("You are: " + context.userName() + ", backend service returned: " + result))
                .exceptionally(throwable -> sendError(throwable, res));
    }

    private static void propagate(ServerRequest req, ServerResponse res) {
        SecurityContext context = getSecurityContext(req);

        webTarget(servingPort)
                .request(String.class)
                .thenAccept(result -> res.send("You are: " + context.userName() + ", backend service returned: " + result))
                .exceptionally(throwable -> sendError(throwable, res));
    }

    static int clientPort() {
        return clientPort;
    }
}
