/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.examples.google;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.Builder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Google login example utilities.
 */
public final class GoogleUtil {
    // do not change this constant, unless you modify configuration
    // of Google application redirect URI
    static final int PORT = 8080;
    private static final int START_TIMEOUT_SECONDS = 10;

    private GoogleUtil() {
    }

    static WebServer startIt(int port, Builder<? extends Routing> routing) {
        WebServer server = WebServer.builder(routing)
                .port(port)
                .build();

        long t = System.nanoTime();

        CountDownLatch cdl = new CountDownLatch(1);

        server.start().thenAccept(webServer -> {
            long time = System.nanoTime() - t;

            System.out.printf("Server started in %d ms ms%n", TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
            System.out.printf("Started server on localhost:%d%n", webServer.port());
            System.out.printf("You can access this example at http://localhost:%d/index.html%n", webServer.port());
            System.out.println();
            System.out.println();
            System.out.println("Check application.yaml in case you are behind a proxy to configure it");
            cdl.countDown();
        });

        try {
            cdl.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to start server within defined timeout: " + START_TIMEOUT_SECONDS + " seconds");
        }

        return server;
    }
}
