/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.micrometer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.webserver.WebServer;

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
    public static WebServer startServer(MicrometerFeature.Builder builder) {
        try {
            return startServer(0, builder);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            throw new RuntimeException("Error starting server for test", ex);
        }
    }

    public static WebServer startServer(
            int port,
            MicrometerFeature.Builder builder) throws
            InterruptedException, ExecutionException, TimeoutException {
        WebServer result = WebServer.builder()
                .port(port)
                .routing(router -> router.addFeature(() -> builder.build()))
                .build()
                .start();
        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", result.port());
        return result;
    }
}
