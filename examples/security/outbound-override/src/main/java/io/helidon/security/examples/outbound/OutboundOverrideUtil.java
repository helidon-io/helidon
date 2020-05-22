/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.SecurityContext;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

/**
 * Example utilities.
 */
public final class OutboundOverrideUtil {
    private static final WebClient CLIENT = WebClient.create();

    private OutboundOverrideUtil() {
    }

    static WebClientRequestBuilder webTarget(int port) {
        return CLIENT.get()
                .uri("http://localhost:" + port + "/hello");
    }

    static Void sendError(Throwable throwable, ServerResponse res) {
        res.status(Http.Status.INTERNAL_SERVER_ERROR_500);
        res.send("Error: " + throwable.getClass().getName() + ": " + throwable.getMessage());
        return null;
    }

    static Config createConfig(String fileName) {
        return Config.builder()
                .sources(ConfigSources.classpath(fileName + ".yaml"))
                .build();
    }

    static SecurityContext getSecurityContext(ServerRequest req) {
        return req.context().get(SecurityContext.class)
                .orElseThrow(() -> new RuntimeException("Failed to get security context from request, security not configured"));
    }

    static CompletionStage<Void> startServer(Routing routing, int port, Consumer<WebServer> callback) {
        return WebServer.builder(routing)
                .port(port)
                .build()
                .start()
                .thenAccept(callback);
    }
}
