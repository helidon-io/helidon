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
 *
 */
package io.helidon.metrics;

import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MicrometerTestUtil {

    private static final Logger LOGGER = Logger.getLogger(MicrometerTestUtil.class.getName());

    /**
     * Starts the web server at an available port and sets up OpenAPI using the
     * supplied builder.
     *
     * @param builder the {@code OpenAPISupport.Builder} to set up for the
     * server.
     * @return the {@code WebServer} set up with OpenAPI support
     */
    public static WebServer startServer(MicrometerSupport.Builder builder) {
        try {
            return startServer(0, builder);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            throw new RuntimeException("Error starting server for test", ex);
        }
    }

    public static WebServer startServer(
            int port,
            MicrometerSupport.Builder... builders) throws
            InterruptedException, ExecutionException, TimeoutException {
        WebServer result = WebServer.builder(Routing.builder()
                .register(builders)
                .build())
                .port(port)
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", result.port());
        return result;
    }
}
