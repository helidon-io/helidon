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

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;


public class ContextIT {

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

    @BeforeAll
    public static void setup() throws Exception {
        LogManager.getLogManager().readConfiguration(TracingIT.class.getResourceAsStream("/logging.properties"));

        startGrpcServer();

        channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port())
                                       .usePlaintext()
                                       .build();
    }

    @Test
    public void shouldObtainValueFromContextForThread() {
        Context context = grpcServer.context();
        TestValue value = new TestValue("Foo");
        context.register(value);

        Echo.EchoResponse response = EchoServiceGrpc.newBlockingStub(channel)
                .echo(Echo.EchoRequest.newBuilder().setMessage("thread").build());

        assertThat(response.getMessage(), is("Foo"));
        assertThat(context.get(TestValue.class).orElse(null), is(sameInstance(value)));
    }

    @Test
    public void shouldObtainValueFromContextForRequest() {
        Context context = grpcServer.context();
        TestValue value = new TestValue("Bar");
        context.register(value);

        Echo.EchoResponse response = EchoServiceGrpc.newBlockingStub(channel)
                .echo(Echo.EchoRequest.newBuilder().setMessage("request").build());

        assertThat(response.getMessage(), is("Bar"));
        assertThat(context.get(TestValue.class).orElse(null), is(sameInstance(value)));
    }


    /**
     * Start the gRPC Server listening on an ephemeral port.
     *
     * @throws Exception in case of an error
     */
    private static void startGrpcServer() throws Exception {
        // Add the EchoService
        GrpcRouting routing = GrpcRouting.builder()
                                         .register(new ContextService())
                                         .build();

        // Run the server on port 0 so that it picks a free ephemeral port
        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder()
                .port(0)
                .build();

        grpcServer = GrpcServer.create(serverConfig, routing)
                        .start()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started gRPC server at: localhost:" + grpcServer.port());
    }


    /**
     * A test gRPC service that obtains a value from the {@link io.helidon.common.context.Context}.
     */
    public static class ContextService
            implements GrpcService {

        @Override
        public void update(ServiceDescriptor.Rules rules) {
            rules.proto(Echo.getDescriptor())
                 .name("EchoService")
                 .unary("Echo", this::echo);
        }

        public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {
            TestValue value = null;

            Optional<Context> optional;
            if ("thread".equalsIgnoreCase(request.getMessage())) {
                optional = Contexts.context();
            } else {
                optional = Optional.ofNullable(ContextKeys.HELIDON_CONTEXT.get());
            }

            if (optional.isPresent()) {
                Context context = optional.get();
                value = context.get(TestValue.class).orElse(null);

                context.register(new TestValue("Updated"));
            }

            Echo.EchoResponse response = Echo.EchoResponse.newBuilder().setMessage(String.valueOf(value)).build();
            complete(observer, response);
        }
    }


    /**
     * A test value to register with the {@link io.helidon.common.context.Context}.
     */
    private static class TestValue {
        private final String value;

        private TestValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
