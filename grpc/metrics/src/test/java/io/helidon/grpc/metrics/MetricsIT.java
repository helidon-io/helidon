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

package io.helidon.grpc.metrics;

import java.io.StringReader;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.EchoService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Integration tests for gRPC server with metrics.
 */
public class MetricsIT {

    // ----- data members ---------------------------------------------------

    /**
     * The Helidon {@link WebServer} to use for testing.
     */
    private static WebServer webServer;

    /**
     * The JAX-RS {@link Client} to use to make http requests to the {@link WebServer}.
     */
    private static Client client;

    /**
     * The {@link Logger} to use for logging.
     */
    private static final Logger LOGGER = Logger.getLogger(MetricsIT.class.getName());

    /**
     * The Helidon {@link io.helidon.grpc.server.GrpcServer} being tested.
     */
    private static GrpcServer grpcServer;

    /**
     * A gRPC {@link Channel} to connect to the test gRPC server
     */
    private static Channel channel;

    // ----- test lifecycle -------------------------------------------------

    @BeforeAll
    public static void setup() throws Exception {
        LogManager.getLogManager().readConfiguration(MetricsIT.class.getResourceAsStream("/logging.properties"));

        // start the server at a free port
        startWebServer();

        client = ClientBuilder.newBuilder()
                .register(new LoggingFeature(LOGGER, Level.WARNING, LoggingFeature.Verbosity.PAYLOAD_ANY, 500))
                .property(ClientProperties.FOLLOW_REDIRECTS, true)
                .build();

        startGrpcServer();

        channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port())
                                       .usePlaintext()
                                       .build();
    }

    @AfterAll
    public static void cleanup() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                     .toCompletableFuture()
                     .get(10, TimeUnit.SECONDS);
        }
    }

    // ----- test methods ---------------------------------------------------

    @Test
    public void shouldPublishMetrics() {
        // call the gRPC Echo service so that there should be some metrics
        EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());

        // request the application metrics in json format from the web server
        String metrics = client.target("http://localhost:" + webServer.port())
                .path("metrics/application")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get(String.class);

        // verify that the json response
        JsonStructure json = Json.createReader(new StringReader(metrics)).read();
        JsonValue     value = json.getValue("/EchoService.Echo");

        assertThat(value, is(notNullValue()));
    }

    // ----- helper methods -------------------------------------------------

    /**
     * Start the gRPC Server listening on an ephemeral port.
     *
     * @throws Exception in case of an error
     */
    private static void startGrpcServer() throws Exception {
        // Add the EchoService and enable GrpcMetrics
        GrpcRouting routing = GrpcRouting.builder()
                                         .intercept(GrpcMetrics.timed())
                                         .register(new EchoService(), rules -> rules.intercept(GrpcMetrics.metered())
                                                                                    .intercept("Echo",
                                                                                               GrpcMetrics.counted()))
                                         .build();

        // Run the server on port 0 so that it picks a free ephemeral port
        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(0).build();

        grpcServer = GrpcServer.create(serverConfig, routing)
                        .start()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

       LOGGER.info("Started gRPC server at: localhost:" + grpcServer.port());
    }

    /**
     * Start the Web Server listening on an ephemeral port.
     *
     * @throws Exception in case of an error
     */
    private static void startWebServer() throws Exception {
        // Add metrics to the web server routing
        Routing routing = Routing.builder()
                .register(MetricsSupport.create())
                .build();

        // Run the web server on port 0 so that it picks a free ephemeral port
        ServerConfiguration webServerConfig = ServerConfiguration.builder().port(0).build();

        webServer = WebServer.create(webServerConfig, routing)
                     .start()
                     .toCompletableFuture()
                     .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started web server at: http://localhost:" + webServer.port());
    }
}
