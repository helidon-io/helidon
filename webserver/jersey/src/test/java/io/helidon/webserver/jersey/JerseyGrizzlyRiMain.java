/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.jersey;

import java.io.IOException;
import java.net.URI;

import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * The JerseyGrizzlyRiMain.
 */
public class JerseyGrizzlyRiMain {

    public static void main(String[] args) throws IOException, InterruptedException {

        final ResourceConfig resourceConfig = new ResourceConfig(JerseyExampleResource.class);
        final org.glassfish.grizzly.http.server.HttpServer server = GrizzlyHttpServerFactory.createHttpServer(URI.create(
                "http://localhost:8080/jersey/"), resourceConfig, false);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
        server.start();

        System.out.println("Grizzly Jersey Application started.");
    }
}
