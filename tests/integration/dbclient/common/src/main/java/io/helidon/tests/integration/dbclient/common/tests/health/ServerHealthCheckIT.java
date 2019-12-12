/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.common.tests.health;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.health.HealthSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.CONFIG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;

/**
 * Verify health check in web server environment.
 */
public class ServerHealthCheckIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(HealthCheckIT.class.getName());

    private static WebServer SERVER;
    private static String URL;

    private static Routing createRouting() {
        final HealthSupport health = HealthSupport.builder()
                .addLiveness(DbClientHealthCheck.create(DB_CLIENT))
                .build();
        return Routing.builder()
                .register(health)                   // Health at "/health"
                .build();
    }

    /**
     * Start Helidon Web Server with DB Client health check support.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException {
        final ServerConfiguration serverConfig = ServerConfiguration.builder(CONFIG.get("server"))
                .build();
        final WebServer server = WebServer.create(serverConfig, createRouting());
        final CompletionStage<WebServer> serverFuture = server.start();
        serverFuture.thenAccept(srv -> {
            LOGGER.info(() -> String.format("WEB server is running at http://%s:%d", srv.configuration().bindAddress(), srv.port()));
            URL = String.format("http://localhost:%d", srv.port());
        });
        SERVER = serverFuture.toCompletableFuture().get();
    }

    /**
     * Stop Helidon Web Server with DB Client health check support.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @AfterAll
    public static void shutdown() throws InterruptedException, ExecutionException {
        SERVER.shutdown().toCompletableFuture().get();
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Read and check DB Client health status from Helidon Web Server.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testHttpHealth() throws IOException, InterruptedException {
        String response = get(URL + "/health");
        LOGGER.info(response);
    }


}
