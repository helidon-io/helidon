/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.common.LogConfig;
import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;
import io.helidon.tracing.TracerBuilder;

import com.oracle.bedrock.testsupport.deferred.Eventually;
import io.grpc.Channel;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.EchoService;
import zipkin2.Span;
import zipkin2.junit.ZipkinRule;

import static com.oracle.bedrock.deferred.DeferredHelper.invoking;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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

    public static final TestInterceptor interceptor = new TestInterceptor();

    // ----- test lifecycle -------------------------------------------------

    @BeforeAll
    public static void setup() throws Exception {
        LogConfig.configureRuntime();

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
    public void shouldTraceMethodNameAndHeaders() {
        // call the gRPC Echo service so that there should be tracing span sent to zipkin server
        EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());

        Eventually.assertThat(invoking(this).getSpanCount(), is(not(0)));

        List<List<Span>> listTraces = zipkin.getTraces();
        assertThat(listTraces, is(notNullValue()));

        String sTraces = listTraces.toString();

        assertThat("The traces should include method name", sTraces, containsString("grpc.method_name"));
        assertThat("The traces should include Echo method", sTraces, containsString("EchoService/Echo"));

        assertThat("Tha traces should include headers", sTraces, containsString("grpc.headers"));
        assertThat("Tha traces should include attributes", sTraces, containsString("grpc.call_attributes"));
    }

    @Test
    public void shouldAddTracerToContext() {
        EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());

        io.helidon.common.context.Context context = interceptor.context();
        assertThat(context, is(notNullValue()));
        assertThat(context.get(Tracer.class), is(notNullValue()));
    }

    @Test
    public void shouldAddSpanContextToContext() {
        EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());

        io.helidon.common.context.Context context = interceptor.context();
        assertThat(context, is(notNullValue()));
        assertThat(context.get(SpanContext.class), is(notNullValue()));
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
                                         .intercept(interceptor)
                                         .build();
        // Enable tracing
        Tracer tracer = TracerBuilder.create("Server")
                .collectorUri(URI.create(zipkin.httpUrl() + "/api/v2/spans"))
                .build();

        GrpcTracingConfig tracingConfig = GrpcTracingConfig.builder()
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

    /**
     * A {@link io.grpc.ServerInterceptor} that captures the context set when
     * the request executed.
     */
    @Priority(InterceptorPriorities.USER)
    private static class TestInterceptor
            implements ServerInterceptor {

        private io.helidon.common.context.Context context;

        private io.helidon.common.context.Context context() {
            return context;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {

            context = io.helidon.common.context.Contexts.context().orElse(null);

            return Contexts.interceptCall(Context.current(), call, headers, next);
        }
    }
}
