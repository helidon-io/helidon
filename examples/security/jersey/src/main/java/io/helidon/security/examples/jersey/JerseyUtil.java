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

package io.helidon.security.examples.jersey;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Utility for this example.
 */
class JerseyUtil {
    private static final int START_TIMEOUT_SECONDS = 10;

    private JerseyUtil() {
    }

    static WebServer startIt(Supplier<? extends Routing> routing, int port) {
        WebServer server = WebServer.builder(routing)
                .port(port)
                .build();

        long t = System.nanoTime();

        CountDownLatch cdl = new CountDownLatch(1);

        AtomicReference<Throwable> throwableRef = new AtomicReference<>();

        server.start().whenComplete((webServer, throwable) -> {
            if (null != throwable) {
                System.err.println("Failed to start server");
                throwableRef.set(throwable);
            } else {
                long time = System.nanoTime() - t;

                System.out.printf("Server started in %d ms%n", TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
                System.out.printf("Started server on localhost:%d%n", webServer.port());
                System.out.println();
                System.out.println("Users:");
                System.out.println("jack/password in roles: user, admin");
                System.out.println("jill/password in roles: user");
                System.out.println("john/password in no roles");
                System.out.println();
                System.out.println("***********************");
                System.out.println("** Endpoints:        **");
                System.out.println("***********************");
                System.out.println("Unprotected:");
                System.out.printf("  http://localhost:%1$d/rest%n", server.port());
                System.out.println("Protected:");
                System.out.printf("  http://localhost:%1$d/rest/protected%n", server.port());
                System.out.println("Identity propagation:");
                System.out.printf("  http://localhost:%1$d/rest/outbound%n", server.port());
            }
            cdl.countDown();
        });

        try {
            if (cdl.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Throwable thrown = throwableRef.get();
                if (null != thrown) {
                    throw new RuntimeException("Failed to start server", thrown);
                }
            } else {
                throw new RuntimeException("Failed to start server, timed out");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to start server within defined timeout: " + START_TIMEOUT_SECONDS + " seconds");
        }

        return server;
    }
}
