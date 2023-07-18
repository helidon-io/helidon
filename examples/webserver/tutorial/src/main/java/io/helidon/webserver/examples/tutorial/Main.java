/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.tutorial;

import io.helidon.common.http.HttpMediaType;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * Application java main class.
 *
 * <p>The TUTORIAL application demonstrates various WebServer use cases together and in its complexity.
 * <p>It also serves web server tutorial articles composed of live examples.
 */
public final class Main {

    private Main() {
    }

    /**
     * Set up the routing.
     *
     * @param routing routing builder
     */
    static void routing(HttpRouting.Builder routing) {
        routing.any(new UserFilter())
                .register("/article", new CommentService())
                .post("/mgmt/shutdown", (req, res) -> {
                    res.headers().contentType(HttpMediaType.PLAINTEXT_UTF_8);
                    res.send("Shutting down TUTORIAL server. Good bye!\n");
                    req.context()
                            .get(WebServer.class)
                            .orElseThrow()
                            .stop();
                });
    }

    /**
     * Set up the server.
     *
     * @param server server builder
     */
    static void setup(WebServerConfig.Builder server) {
        server.routing(Main::routing)
                .contentEncoding(encoding -> encoding.addContentEncoding(new UpperXEncodingProvider()))
                .mediaContext(MediaContext.builder()
                        .addMediaSupport(new CommentSupport())
                        .build());
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        // Create a web server instance
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                port = 0;
            }
        }

        WebServerConfig.Builder builder = WebServer.builder().port(port);
        setup(builder);
        WebServer server = builder.build().start();
        server.context().register(server);

        System.out.printf("""
                TUTORIAL server is up! http://localhost:%1$d"
                Call POST on 'http://localhost:%1$d/mgmt/shutdown' to STOP the server!
                """, server.port());
    }
}
