/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Consumer;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.jersey.ClientSecurityFeature;
import io.helidon.security.provider.httpauth.HttpBasicAuthProvider;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

/**
 * Creates two services. First service invokes the second with outbound security. There are two endpoints - one that
 * does simple identity propagation and one that uses an explicit username and password.
 *
 * Uses basic authentication both to authenticate users and to propagate identity.
 */
public final class OutboundOverrideExample {
    private static volatile int clientPort;
    private static volatile int servingPort;
    private static Client client;

    private OutboundOverrideExample() {
    }

    /**
     * Example that propagates identity and on one endpoint explicitly sets the username and password.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        CompletionStage<Void> first = startClientService();
        CompletionStage<Void> second = startServingService();

        first.toCompletableFuture().join();
        second.toCompletableFuture().join();

        client = ClientBuilder.newBuilder()
                .register(new ClientSecurityFeature())
                .build();

        System.out.println("Started services. Main endpoints:");
        System.out.println("http://localhost:" + clientPort + "/propagate");
        System.out.println("http://localhost:" + clientPort + "/override");
        System.out.println();
        System.out.println("Backend service started on:");
        System.out.println("http://localhost:" + servingPort + "/hello");
    }

    private static CompletionStage<Void> startServingService() {
        Config config = createConfig("serving-service");

        return startServer(Routing
                                   .builder()
                                   .register(WebSecurity.from(config))
                                   .get("/hello", (req, res) -> {
                                       res.send(req.context().get(SecurityContext.class).flatMap(SecurityContext::getUser).map(
                                               Subject::getPrincipal).map(Principal::getName).orElse("Anonymous"));
                                   }),
                           server -> servingPort = server.port());
    }

    private static CompletionStage<Void> startClientService() {
        Config config = createConfig("client-service");

        return startServer(Routing
                                   .builder()
                                   .register(WebSecurity.from(config))
                                   .get("/override", OutboundOverrideExample::override)
                                   .get("/propagate", OutboundOverrideExample::propagate),
                           server -> clientPort = server.port());

    }

    private static void override(ServerRequest req, ServerResponse res) {
        SecurityContext context = getContext(req);

        String result = webTarget().request()
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jill")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "anotherPassword")
                .property(ClientSecurityFeature.PROPERTY_CONTEXT, context)
                .get(String.class);

        res.send("You are: " + context.getUserName() + ", backend service returned: " + result);
    }

    private static void propagate(ServerRequest req, ServerResponse res) {
        SecurityContext context = getContext(req);
        String result = webTarget().request()
                .property(ClientSecurityFeature.PROPERTY_CONTEXT, context)
                .get(String.class);

        res.send("You are: " + context.getUserName() + ", backend service returned: " + result);
    }

    private static WebTarget webTarget() {
        return client.target("http://localhost:" + servingPort + "/hello");
    }

    private static SecurityContext getContext(ServerRequest req) {
        return req.context().get(SecurityContext.class)
                .orElseThrow(() -> new RuntimeException("Failed to get security context from request, security not configured"));
    }

    private static CompletionStage<Void> startServer(Routing.Builder builder, Consumer<WebServer> callback) {
        return WebServer.create(builder)
                .start()
                .thenAccept(callback);
    }

    private static Config createConfig(String fileName) {
        return Config.builder()
                .sources(ConfigSources.classpath(fileName + ".yaml"))
                .build();
    }
}
