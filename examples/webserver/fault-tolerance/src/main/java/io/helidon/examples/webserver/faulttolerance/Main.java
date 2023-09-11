/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.faulttolerance;

import io.helidon.faulttolerance.BulkheadException;
import io.helidon.faulttolerance.CircuitBreakerOpenException;
import io.helidon.faulttolerance.TimeoutException;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;

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

        WebServerConfig.Builder builder = WebServer.builder().port(8079);
        setup(builder);
        WebServer server = builder.build().start();

        System.out.println("Server started on " + "http://localhost:" + server.port());
    }

    static void setup(WebServerConfig.Builder server) {
        server.routing(Main::routing);
    }

    static void routing(HttpRouting.Builder routing) {
        routing.register("/ft", new FtService())
               .error(BulkheadException.class,
                       (req, res, ex) -> res.status(Status.SERVICE_UNAVAILABLE_503).send("bulkhead"))
               .error(CircuitBreakerOpenException.class,
                       (req, res, ex) -> res.status(Status.SERVICE_UNAVAILABLE_503).send("circuit breaker"))
               .error(TimeoutException.class,
                       (req, res, ex) -> res.status(Status.REQUEST_TIMEOUT_408).send("timeout"))
               .error(Throwable.class,
                       (req, res, ex) -> res.status(Status.INTERNAL_SERVER_ERROR_500)
                                            .send(ex.getClass().getName() + ": " + ex.getMessage()));
    }
}
