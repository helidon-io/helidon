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
package io.helidon.cors;

import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestUtil {

    private static final Logger LOGGER = Logger.getLogger(TestUtil.class.getName());

    /**
     * Starts the web server at an available port and sets up CORS using the supplied builder.
     *
     * @param builders the {@code Builder}s to set up for the server.
     * @return the {@code WebServer} set up with OpenAPI support
     */
//    public static WebServer startServer(Supplier<? extends Service>... builders) {
//        try {
//            return startServer(0, builders);
//        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
//            throw new RuntimeException("Error starting server for test", ex);
//        }
//    }

    public static WebServer startServer(int port, Routing.Builder routingBuilder) throws InterruptedException, ExecutionException, TimeoutException {
        Config config = Config.create();
        ServerConfiguration serverConfig = ServerConfiguration.builder(config)
                .port(port)
                .build();
        WebServer server  = WebServer.create(serverConfig, routingBuilder).start().toCompletableFuture().get(10, TimeUnit.SECONDS);
        return server;
    }

    static Routing.Builder prepRouting() {
        Config config = Config.create();
        CORSSupport.Builder corsSupportBuilder = CORSSupport.builder().config(config.get(CrossOriginHelper.CORS_CONFIG_KEY));
        return Routing.builder()
                .register(corsSupportBuilder);
    }

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1, the port is dynamically selected
     * @param builders Builder instances to use in starting the server
     * @return {@code WebServer} that has been started
     * @throws java.lang.InterruptedException if the start was interrupted
     * @throws java.util.concurrent.ExecutionException if the start failed
     * @throws java.util.concurrent.TimeoutException if the start timed out
     */
    public static WebServer startServer(
            int port,
            Supplier<? extends Service>... builders) throws
            InterruptedException, ExecutionException, TimeoutException {

        WebServer result = WebServer.create(ServerConfiguration.builder()
                        .port(port)
                        .build(),
                Routing.builder()
                        .register(builders)
                        .build())
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", result.port());
        return result;
    }

    /**
     * Shuts down the specified web server.
     *
     * @param ws the {@code WebServer} instance to stop
     */
    public static void shutdownServer(WebServer ws) {
        if (ws != null) {
            try {
                stopServer(ws);
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                throw new RuntimeException("Error shutting down server for test", ex);
            }
        }
    }

    /**
     * Stop the web server.
     *
     * @param server the {@code WebServer} to stop
     * @throws InterruptedException if the stop operation was interrupted
     * @throws ExecutionException if the stop operation failed as it ran
     * @throws TimeoutException if the stop operation timed out
     */
    public static void stopServer(WebServer server) throws
            InterruptedException, ExecutionException, TimeoutException {
        if (server != null) {
            server.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Returns a {@code HttpURLConnection} for the requested method and path and
     * {code @MediaType} from the specified {@link WebServer}.
     *
     * @param port port to connect to
     * @param method HTTP method to use in building the connection
     * @param path path to the resource in the web server
     * @param mediaType {@code MediaType} to be Accepted
     * @return the connection to the server and path
     * @throws Exception in case of errors creating the connection
     */
    public static HttpURLConnection getURLConnection(
            int port,
            String method,
            String path,
            MediaType mediaType) throws Exception {
        URL url = new URL("http://localhost:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        if (mediaType != null) {
            conn.setRequestProperty("Accept", mediaType.toString());
        }
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }
}
