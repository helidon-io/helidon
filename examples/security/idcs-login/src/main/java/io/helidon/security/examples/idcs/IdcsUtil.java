/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.idcs;

import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * IDCS login example utilities.
 */
public class IdcsUtil {
    // do not change this constant, unless you modify configuration
    // of IDCS application redirect URI
    static final int PORT = 7987;
    private static final int START_TIMEOUT_SECONDS = 10;

    private IdcsUtil() {
    }

    static WebServer startIt(Supplier<? extends Routing> routing) throws UnknownHostException {
        return WebServer.builder(routing)
                .port(PORT)
                .bindAddress("localhost")
                .build();
    }

    static WebServer start(WebServer webServer) {
        long t = System.nanoTime();

        CountDownLatch cdl = new CountDownLatch(1);

        webServer.start()
                .thenAccept(it -> whenStarted(it, t))
                .thenRun(cdl::countDown);

        try {
            cdl.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to start server within defined timeout: " + START_TIMEOUT_SECONDS + " seconds", e);
        }

        return webServer;
    }

    static void whenStarted(WebServer webServer, long startNanoTime) {
            long time = System.nanoTime() - startNanoTime;

            System.out.printf("Server started in %d ms%n", TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
            System.out.printf("Started server on localhost:%d%n", webServer.port());
            System.out.printf("You can access this example at http://localhost:%d/rest/profile%n", webServer.port());
            System.out.println();
            System.out.println();
            System.out.println("Check application.yaml in case you are behind a proxy to configure it");
    }
}
