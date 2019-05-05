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

package io.helidon.grpc.server;

import com.oracle.bedrock.testsupport.deferred.Eventually;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;
import io.helidon.tracing.TracerBuilder;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.opentracing.Tracer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.EchoService;
import zipkin2.Span;
import zipkin2.junit.ZipkinRule;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static com.oracle.bedrock.deferred.DeferredHelper.invoking;

/**
 * Tests for gRPC server with tracing.
 */
public class TracingIT {

    // ----- data members ---------------------------------------------------

    /**
     * The {@link java.util.logging.Logger} to use for logging.
     */
    private static final Logger LOGGER = Logger.getLogger(TracingIT.class.getName());

    /**
     * The Helidon {@link io.helidon.grpc.server.GrpcServer} being tested.
     */
    private static GrpcServer grpcServer;

    /**
     * A gRPC {@link io.grpc.Channel} to connect to the test gRPC server
     */
    private static Channel channel;

    /**
     * ZipkinRule to start a Zipkin server
     */
    public static ZipkinRule zipkin = new ZipkinRule();


    // ----- test lifecycle -------------------------------------------------

    @BeforeAll
    public static void setup() throws Exception {
        LogManager.getLogManager().readConfiguration(TracingIT.class.getResourceAsStream("/logging.properties"));

        //start zipkin server on an ephemeral port
        zipkin.start(0);

        startGrpcServer();

        channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port())
                                       .usePlaintext()
                                       .build();
    }

    @AfterAll
    public static void cleanup() throws Exception {
        zipkin.shutdown();
        }

    // ----- test methods ---------------------------------------------------

    @Test
    public void shouldTraceMethodNameAndHeaders() throws Exception {
        // call the gRPC Echo service so that there should be tracing span sent to zipkin server
        EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());

        Eventually.assertThat(invoking(this).getSpanCount(), is(1));

        List<List<Span>> listTraces = zipkin.getTraces();
        assertThat(listTraces, is(notNullValue()));

        String sTraces = listTraces.toString();

        assertThat("The traces should include method name", sTraces.contains("grpc.method_name"));
        assertThat("The traces should include Echo method", sTraces.contains("EchoService/Echo"));

        assertThat("Tha traces should include headers", sTraces.contains("grpc.headers"));
        assertThat("Tha traces should include attributes", sTraces.contains("grpc.call_attributes"));
    }

    // ----- helper methods -------------------------------------------------

    /**
     * Start the gRPC Server listening on an ephemeral port.
     *
     * @throws Exception in case of an error
     */
    private static void startGrpcServer() throws Exception {
        // Add the EchoService
        GrpcRouting routing = GrpcRouting.builder()
                                         .register(new EchoService())
                                         .build();
        // Enable tracing
        Tracer tracer = (Tracer) TracerBuilder.create("Server")
                .collectorUri(URI.create(zipkin.httpUrl() + "/api/v2/spans"))
                .build();

        TracingConfiguration tracingConfig = new TracingConfiguration.Builder()
                .withStreaming()
                .withVerbosity()
                .withTracedAttributes(ServerRequestAttribute.CALL_ATTRIBUTES,
                     ServerRequestAttribute.HEADERS,
                     ServerRequestAttribute.METHOD_NAME)
                .build();

        // Run the server on port 0 so that it picks a free ephemeral port
        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(0).tracer(tracer).tracingConfig(tracingConfig).build();

        grpcServer = GrpcServer.create(serverConfig, routing)
                        .start()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

       LOGGER.info("Started gRPC server at: localhost:" + grpcServer.port());
    }

    /**
     * Return the span count collect.
     */
    public int getSpanCount() {
        return zipkin.collectorMetrics().spans();
    }
}
