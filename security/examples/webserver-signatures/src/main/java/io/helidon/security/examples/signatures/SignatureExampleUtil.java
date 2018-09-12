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

package io.helidon.security.examples.signatures;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import io.helidon.common.OptionalHelper;
import io.helidon.common.http.MediaType;
import io.helidon.security.SecurityContext;
import io.helidon.security.jersey.ClientSecurityFeature;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

/**
 * Common code for both examples (builder and config based).
 */
final class SignatureExampleUtil {
    private static final Client CLIENT = ClientBuilder.newBuilder()
            .register(new ClientSecurityFeature())
            .build();

    private static final int START_TIMEOUT_SECONDS = 10;

    private SignatureExampleUtil() {
    }

    /**
     * Start a web server.
     *
     * @param routing routing to configre
     * @return started web server instance
     */
    public static WebServer startServer(Routing routing) {
        WebServer server = WebServer.create(routing);
        long t = System.nanoTime();

        CountDownLatch cdl = new CountDownLatch(1);

        server.start().thenAccept(webServer -> {
            long time = System.nanoTime() - t;

            System.out.printf("Server started in %d ms ms%n", TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
            System.out.printf("Started server on localhost:%d%n", webServer.port());
            System.out.println();
            cdl.countDown();
        }).exceptionally(throwable -> {
            throw new RuntimeException("Failed to start server", throwable);
        });

        try {
            cdl.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to start server within defined timeout: " + START_TIMEOUT_SECONDS + " seconds");
        }
        return server;
    }

    static void processService1Request(ServerRequest req, ServerResponse res, String path, int svc2port) {
        Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);

        res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));

        OptionalHelper.from(securityContext).ifPresentOrElse(context -> {
            Response response = CLIENT
                    .target("http://localhost:" + svc2port + path)
                    .request()
                    .property(ClientSecurityFeature.PROPERTY_CONTEXT, context)
                    .get();

            if (response.getStatus() == 200) {
                res.send(response.readEntity(String.class));
            } else {
                res.send("Request failed, status: " + response.getStatus());
            }
        }, () -> res.send("Security context is null"));
    }
}
