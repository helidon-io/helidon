/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.faulttolerance;

import java.util.concurrent.TimeoutException;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.faulttolerance.BulkheadException;
import io.helidon.faulttolerance.CircuitBreakerOpenException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Main class of Fault tolerance example.
 */
public final class Main {
    // utility class
    private Main() {
    }

    /**
     * Start the example.
     *
     * @param args start arguments are ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        startServer(8079).thenRun(() -> {});
    }

    static Single<WebServer> startServer(int port) {
        return WebServer.builder()
                .routing(routing())
                .port(port)
                .build()
                .start()
                .peek(server -> {
                    String url = "http://localhost:" + server.port();
                    System.out.println("Server started on " + url);
                });
    }

    private static Routing routing() {
        return Routing.builder()
                .register("/ft", new FtService())
                .error(BulkheadException.class,
                       (req, res, ex) -> res.status(Http.Status.SERVICE_UNAVAILABLE_503).send("bulkhead"))
                .error(CircuitBreakerOpenException.class,
                       (req, res, ex) -> res.status(Http.Status.SERVICE_UNAVAILABLE_503).send("circuit breaker"))
                .error(TimeoutException.class,
                       (req, res, ex) -> res.status(Http.Status.REQUEST_TIMEOUT_408).send("timeout"))
                .error(Throwable.class,
                       (req, res, ex) -> res.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                               .send(ex.getClass().getName() + ": " + ex.getMessage()))
                .build();
    }
}
