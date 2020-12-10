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

package io.helidon.microprofile.grpc.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.common.LogConfig;
import io.helidon.grpc.core.ResponseHelper;
import io.helidon.grpc.server.CollectingObserver;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.microprofile.grpc.core.Bidirectional;
import io.helidon.microprofile.grpc.core.ClientStreaming;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.ServerStreaming;
import io.helidon.microprofile.grpc.core.Unary;
import io.helidon.microprofile.grpc.server.test.BidiServiceGrpc;
import io.helidon.microprofile.grpc.server.test.ClientStreamingServiceGrpc;
import io.helidon.microprofile.grpc.server.test.ServerStreamingServiceGrpc;
import io.helidon.microprofile.grpc.server.test.Services;
import io.helidon.microprofile.grpc.server.test.Services.TestRequest;
import io.helidon.microprofile.grpc.server.test.Services.TestResponse;
import io.helidon.microprofile.grpc.server.test.UnaryServiceGrpc;

import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Functional tests to verify the various server side call handlers.
 */
public class AnnotatedServiceTest {

    private static GrpcServer grpcServer;

    private static Channel channel;

    @BeforeAll
    public static void startServer() throws Exception {
        LogConfig.configureRuntime();

        GrpcRouting routing = GrpcRouting.builder()
                                         // register the service class
                                         // the service definition will be created from the annotations
                                         // on the class (and/or implemented interface)
                                         .register(descriptor(UnaryService.class))
                                         .register(descriptor(ServerStreamingService.class))
                                         .register(descriptor(ClientStreamingService.class))
                                         .register(descriptor(BidiService.class))
                                         .build();

        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(0).build();

        grpcServer = GrpcServer.create(serverConfig, routing)
                        .start()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

        channel = InProcessChannelBuilder.forName(grpcServer.configuration().name()).build();
    }

    @AfterAll
    public static void cleanup() {
        grpcServer.shutdown();
    }

    // ----- unary ----------------------------------------------------------

    @Test
    public void shouldCallRequestResponse() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.TestResponse response = stub.requestResponse(Services.TestRequest.newBuilder().setMessage("Foo").build());

        assertThat(response.getMessage(), is("Foo"));
    }

    @Test
    public void shouldCallResponseOnly() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.TestResponse response = stub.responseOnly(Services.Empty.getDefaultInstance());

        assertThat(response.getMessage(), is("Foo"));
    }

    @Test
    public void shouldCallRequestNoResponse() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.Empty response = stub.requestNoResponse(TestRequest.newBuilder().setMessage("Foo").build());

        assertThat(response, is(notNullValue()));
    }

    @Test
    public void shouldCallNoRequestNoResponse() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.Empty response = stub.noRequestNoResponse(Services.Empty.getDefaultInstance());

        assertThat(response, is(notNullValue()));
    }

    @Test
    public void shouldCallFutureRequestResponse() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.TestResponse response = stub.futureResponse(TestRequest.newBuilder().setMessage("Foo").build());

        assertThat(response.getMessage(), is("Foo"));
    }

    @Test
    public void shouldCallFutureResponseNoRequest() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.TestResponse response = stub.futureResponseNoRequest(Services.Empty.getDefaultInstance());

        assertThat(response.getMessage(), is("Foo"));
    }

    @Test
    public void shouldCallStandardUnary() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.TestResponse response = stub.unary(TestRequest.newBuilder().setMessage("Foo").build());

        assertThat(response.getMessage(), is("Foo"));
    }

    @Test
    public void shouldCallStandardUnaryNoRequest() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.TestResponse response = stub.unaryNoRequest(Services.Empty.getDefaultInstance());

        assertThat(response.getMessage(), is("Foo"));
    }


    @Test
    public void shouldCallUnaryFuture() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.TestResponse response = stub.unaryFuture(TestRequest.newBuilder().setMessage("Foo").build());

        assertThat(response.getMessage(), is("Foo"));
    }

    @Test
    public void shouldCallFutureUnaryNoRequest() {
        UnaryServiceGrpc.UnaryServiceBlockingStub  stub = UnaryServiceGrpc.newBlockingStub(channel);
        Services.TestResponse response = stub.unaryFutureNoRequest(Services.Empty.getDefaultInstance());

        assertThat(response.getMessage(), is("Foo"));
    }

    // ----- server streaming  -----------------------------------------------

    @Test
    public void shouldCallServerStreaming() {
        ServerStreamingServiceGrpc.ServerStreamingServiceBlockingStub stub = ServerStreamingServiceGrpc.newBlockingStub(channel);
        Iterator<TestResponse> iterator = stub.streaming(TestRequest.newBuilder().setMessage("A B C D").build());

        assertThat(toList(iterator), contains("A", "B", "C", "D"));
    }

    @Test
    public void shouldCallServerStreamingNoRequest() {
        ServerStreamingServiceGrpc.ServerStreamingServiceBlockingStub stub = ServerStreamingServiceGrpc.newBlockingStub(channel);
        Iterator<TestResponse> iterator = stub.streamingNoRequest(Services.Empty.getDefaultInstance());

        assertThat(toList(iterator), contains("A", "B", "C", "D"));
    }


    @Test
    public void shouldCallServerStreamingWithStreamResponse() {
        ServerStreamingServiceGrpc.ServerStreamingServiceBlockingStub stub = ServerStreamingServiceGrpc.newBlockingStub(channel);
        Iterator<TestResponse> iterator = stub.stream(TestRequest.newBuilder().setMessage("A B C D").build());

        assertThat(toList(iterator), contains("A", "B", "C", "D"));
    }

    @Test
    public void shouldCallServerStreamWithStreamResponseNoRequest() {
        ServerStreamingServiceGrpc.ServerStreamingServiceBlockingStub stub = ServerStreamingServiceGrpc.newBlockingStub(channel);
        Iterator<TestResponse> iterator = stub.streamNoRequest(Services.Empty.getDefaultInstance());

        assertThat(toList(iterator), contains("A", "B", "C", "D"));
    }

    // ----- client streaming  ----------------------------------------------

    @Test
    public void shouldCallClientStreaming() {
        ClientStreamingServiceGrpc.ClientStreamingServiceStub stub = ClientStreamingServiceGrpc.newStub(channel);
        TestStreamObserver<TestResponse> observer = new TestStreamObserver<>();
        stream(stub.streaming(observer), "A", "B", "C", "D");

        observer.awaitTerminalEvent();
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(1)
                .assertValue(v -> v.getMessage().equals("A B C D"));
    }

    @Test
    public void shouldCallClientStreamingFutureResponse() {
        ClientStreamingServiceGrpc.ClientStreamingServiceStub stub = ClientStreamingServiceGrpc.newStub(channel);
        TestStreamObserver<TestResponse> observer = new TestStreamObserver<>();
        stream(stub.futureResponse(observer), "A", "B", "C", "D");

        observer.awaitTerminalEvent();
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(1)
                .assertValue(v -> v.getMessage().equals("A B C D"));
    }

    // ----- bi-directional streaming  --------------------------------------

    @Test
    public void shouldCallBidiStreaming() {
        BidiServiceGrpc.BidiServiceStub stub = BidiServiceGrpc.newStub(channel);
        TestStreamObserver<TestResponse> observer = new TestStreamObserver<>();
        stream(stub.bidi(observer), "A", "B", "C", "D");

        observer.awaitTerminalEvent();
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(4);

        assertThat(observer.values().stream().map(TestResponse::getMessage).collect(Collectors.toList()),
                                 contains("A", "B", "C", "D"));
    }

    // ----- helper methods -------------------------------------------------

    private List<String> toList(Iterator<TestResponse> iterator) {
        List<String> list = new ArrayList<>();
        while (iterator.hasNext()) {
            list.add(iterator.next().getMessage());
        }
        return list;
    }

    private void stream(StreamObserver<TestRequest> stream, String... values) {
        try {
            for (String value : values) {
                stream.onNext(TestRequest.newBuilder().setMessage(value).build());
            }

            stream.onCompleted();
        } catch (Throwable t) {
            t.printStackTrace();
            stream.onError(t);
        }
    }

    private static ServiceDescriptor descriptor(Class<?> cls) {
        BeanManager beanManager = mock(BeanManager.class);
        Instance instance = mock(Instance.class);
        when(beanManager.createInstance()).thenReturn(instance);

        GrpcServiceBuilder builder = GrpcServiceBuilder.create(cls, beanManager);

        return builder.build();
    }

    // ----- service implementations ----------------------------------------

    /**
     * The unary methods service implementation.
     */
    @Grpc
    public static class UnaryService {
        @Unary
        public Services.TestResponse requestResponse(Services.TestRequest request) {
            return Services.TestResponse.newBuilder().setMessage(request.getMessage()).build();
        }

        @Unary
        public Services.TestResponse responseOnly() {
            return Services.TestResponse.newBuilder().setMessage("Foo").build();
        }

        @Unary
        public void requestNoResponse(Services.TestRequest request) {
            System.out.println("requestNoResponse method called - message=" + request.getMessage());
        }

        @Unary
        public void noRequestNoResponse() {
            System.out.println("noRequestNoResponse method called");
        }

        @Unary
        public CompletableFuture<TestResponse> futureResponse(Services.TestRequest request) {
            return CompletableFuture.completedFuture(Services.TestResponse.newBuilder().setMessage(request.getMessage()).build());
        }

        @Unary
        public CompletableFuture<TestResponse> futureResponseNoRequest() {
            return CompletableFuture.completedFuture(Services.TestResponse.newBuilder().setMessage("Foo").build());
        }

        @Unary
        public void unary(Services.TestRequest request, StreamObserver<TestResponse> observer) {
            observer.onNext(Services.TestResponse.newBuilder().setMessage(request.getMessage()).build());
            observer.onCompleted();
        }

        @Unary
        public void unaryNoRequest(StreamObserver<TestResponse> observer) {
            observer.onNext(Services.TestResponse.newBuilder().setMessage("Foo").build());
            observer.onCompleted();
        }

        @Unary
        public void unaryFuture(Services.TestRequest request, CompletableFuture<TestResponse> future) {
            future.complete(Services.TestResponse.newBuilder().setMessage(request.getMessage()).build());
        }

        @Unary
        public void unaryFutureNoRequest(CompletableFuture<TestResponse> future) {
            future.complete(Services.TestResponse.newBuilder().setMessage("Foo").build());
        }
    }

    /**
     * The server streaming service.
     */
    @Grpc
    public static class ServerStreamingService {

        @ServerStreaming
        public void streaming(Services.TestRequest request, StreamObserver<TestResponse> observer) {
            ResponseHelper.stream(observer, split(request.getMessage()));
        }

        @ServerStreaming
        public Stream<TestResponse> stream(Services.TestRequest request) {
            return split(request.getMessage());
        }

        @ServerStreaming
        public void streamingNoRequest(StreamObserver<TestResponse> observer) {
            ResponseHelper.stream(observer, split("A B C D"));
        }

        @ServerStreaming
        public Stream<TestResponse> streamNoRequest() {
            return split("A B C D");
        }

        private Stream<TestResponse> split(String text) {
            String[] parts = text.split(" ");
            return Stream.of(parts).map(this::response);
        }

        private TestResponse response(String text) {
            return TestResponse.newBuilder().setMessage(text).build();
        }
    }

    /**
     * The client streaming service.
     */
    @Grpc
    public static class ClientStreamingService {
        @ClientStreaming
        public StreamObserver<TestRequest> streaming(StreamObserver<TestResponse> observer) {
            return new CollectingObserver<>(Collectors.joining(" "),
                                            observer,
                                            TestRequest::getMessage,
                                            this::response);
        }

        @ClientStreaming
        public StreamObserver<TestRequest> futureResponse(CompletableFuture<TestResponse> future) {
            return new StreamObserver<TestRequest>(){
                private List<String> list = new ArrayList<>();
                @Override
                public void onNext(TestRequest request) {
                    list.add(request.getMessage());
                }

                @Override
                public void onError(Throwable error) {
                    error.printStackTrace();
                    list.clear();
                }

                @Override
                public void onCompleted() {
                    future.complete(response(String.join(" ", list)));
                }
            };
        }

        private TestResponse response(String text) {
            return TestResponse.newBuilder().setMessage(text).build();
        }
    }

    /**
     * The bi-directional streaming service.
     */
    @Grpc
    public static class BidiService {
        @Bidirectional
        public StreamObserver<TestRequest> bidi(StreamObserver<TestResponse> observer) {
            return new StreamObserver<TestRequest>() {
                public void onNext(TestRequest request) {
                    observer.onNext(response(request.getMessage()));
                }

                public void onError(Throwable t) {
                    t.printStackTrace();
                }

                public void onCompleted() {
                    observer.onCompleted();
                }
            };
        }

        private TestResponse response(String text) {
            return TestResponse.newBuilder().setMessage(text).build();
        }
    }
}
