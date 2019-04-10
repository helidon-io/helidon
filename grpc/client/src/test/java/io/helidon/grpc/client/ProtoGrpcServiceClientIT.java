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

package io.helidon.grpc.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import io.helidon.grpc.client.test.StringServiceGrpc;
import io.helidon.grpc.client.util.SingleValueResponseStreamObserverAdapter;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.StringService;
import services.TreeMapService;

import static io.helidon.grpc.client.test.Strings.StringMessage;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProtoGrpcServiceClientIT {

    private static volatile int grpcPort;
    private static volatile GrpcServer grpcServer;

    private static ClientServiceDescriptor stringSvcDesc;

    private static GrpcServiceClient grpcClient;

    private static String inputStr = "Some_String_WITH_lower_and_UPPER_cases";
    private static StringMessage inputMsg = StringMessage.newBuilder().setText(inputStr).build();

    @BeforeAll
    public static void startServer() throws IOException, SecurityException {

        LogManager.getLogManager().readConfiguration();

        GrpcRouting routing = GrpcRouting.builder()
                .register(new TreeMapService())
                .register(new StringService())
                .build();

        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(grpcPort).build();

        GrpcServer.create(serverConfig, routing)
                .start()
                .thenAccept(s -> {
                    System.out.println("gRPC server is UP and listening on localhost:" + s.port());
                    grpcServer = s;
                    grpcPort = s.port();
                    s.whenShutdown().thenRun(() -> System.out.println("gRPC server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });

        Class<StringMessage> strMsgType = StringMessage.class;

        ClientServiceDescriptor stringSvcDesc2 = ClientServiceDescriptor
                .builder(StringService.class, StringServiceGrpc.getServiceDescriptor())
                .intercept(GrpcClientTestUtil.lowPriorityInterceptor())
                .build();


        ClientMethodDescriptor upperDesc = stringSvcDesc2.<StringMessage, StringMessage>method("Upper")
                .toBuilder().intercept(GrpcClientTestUtil.highPriorityInterceptor()).build();

        stringSvcDesc = ClientServiceDescriptor
                .builder(StringService.class, StringServiceGrpc.getServiceDescriptor())
                .intercept(GrpcClientTestUtil.lowPriorityInterceptor())
                .<StringMessage, StringMessage>registerMethod(upperDesc)
                .build();


        // Build GrpcServiceClient
        Channel ch = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
        grpcClient = GrpcServiceClient.builder()
                .channel(ch)
                .callOptions(CallOptions.DEFAULT)
                .clientServiceDescriptor(stringSvcDesc)
                .build();

        System.out.println("Grpc Client created....");
    }

    @AfterAll
    public static void shutdownGrpcServer() {
        grpcServer.shutdown().thenRun(() -> {
            System.out.println("Server shutdown...");
        });
    }

    @Test
    public void shouldHaveCorrectNameForStringService() {

        Set<String> methodNames = new HashSet<>();
        methodNames.addAll(Arrays.asList("Lower", "Upper", "Split", "Join", "Echo"));

        for (String name : methodNames) {
            assertThat(stringSvcDesc.method(name), notNullValue());
        }

        assertThat(stringSvcDesc.methods().size(), is(methodNames.size()));
    }

    @Test
    public void testBlockingUnaryMethods() {
        assertThat(
                grpcClient.<StringMessage, StringMessage>blockingUnary("Lower", inputMsg).getText(),
                equalTo(inputStr.toLowerCase()));
    }

    @Test
    public void testAsyncUnaryMethods() throws InterruptedException, ExecutionException {

        // Async that returns a CompletableFuture.
        CompletableFuture<StringMessage> result = grpcClient.unary("Upper", inputMsg);
        assertThat(result.get().getText(), equalTo(inputStr.toUpperCase()));

        // Async that takes a StreamObserver.
        SingleValueResponseStreamObserverAdapter<StringMessage> observer = new SingleValueResponseStreamObserverAdapter();
        grpcClient.unary("Upper", inputMsg, observer);
        assertThat(observer.getFuture().get().getText(), equalTo(inputStr.toUpperCase()));
    }

    @Test
    public void testAsyncClientStreamingMethodWithIterable() throws Throwable {
        // Prepare the input collection
        final String expectedSentence = "A simple invocation of a client streaming method";
        Collection<StringMessage> input = Arrays.stream(expectedSentence.split(" "))
                .map(w -> StringMessage.newBuilder().setText(w).build())
                .collect(Collectors.toList());

        CompletableFuture<StringMessage> result = grpcClient.clientStreaming("Join", input);
        assertThat(result.get().getText(), equalTo(expectedSentence));
    }

    @Test
    public void testAsyncClientStreamingMethod() throws Throwable {
        SingleValueResponseStreamObserverAdapter<StringMessage> respStream = new SingleValueResponseStreamObserverAdapter();
        StreamObserver<StringMessage> clientStream = grpcClient.clientStreaming("Join", respStream);

        final String expectedSentence = "A simple invocation of a client streaming method";
        for (String word : expectedSentence.split(" ")) {
            clientStream.onNext(StringMessage.newBuilder().setText(word).build());
        }
        clientStream.onCompleted();

        assertThat(respStream.getFuture().get().getText(), equalTo(expectedSentence));
    }

    @Test
    public void testBlockingServerStreamingMethods() throws Throwable {
        final String sentence = "A simple invocation of a client streaming method";
        String[] expectedWords = sentence.split(" ");
        int index = 0;

        // Blocking call
        Iterator<StringMessage> iter = grpcClient.blockingServerStreaming(
                "Split", StringMessage.newBuilder().setText(sentence).build());
        while (iter.hasNext()) {
            assertThat(iter.next().getText(), equalTo(expectedWords[index++]));
        }
    }

    @Test
    public void testAsyncServerStreamingMethods() throws Throwable {
        final String sentence = "A simple invocation of a client streaming method";
        final String[] expectedWords = sentence.split(" ");
        final CompletableFuture<Boolean> done = new CompletableFuture<>();

        // Async serverStreaming call
        grpcClient.serverStreaming("Split", StringMessage.newBuilder().setText(sentence).build(), new StreamObserver<StringMessage>() {
            private int index = 0;
            private boolean currentStatus = true;

            @Override
            public void onNext(StringMessage value) {
                currentStatus = currentStatus && value.getText().equals(expectedWords[index++]);
            }

            @Override
            public void onError(Throwable t) {
                currentStatus = false;
            }

            @Override
            public void onCompleted() {
                done.complete(currentStatus);
            }
        });

        assertThat(done.get(), is(true));
    }

    @Test
    public void invokeBidiStreamingMethodWithIterable() throws InterruptedException, ExecutionException {
        final String sentence = "A simple invocation of a Bidi streaming method";
        final String[] expectedWords = sentence.split(" ");

        Collection<StringMessage> input = Arrays.stream(sentence.split(" "))
                .map(w -> StringMessage.newBuilder().setText(w).build())
                .collect(Collectors.toList());

        CompletableFuture<Boolean> status = new CompletableFuture<>();
        grpcClient.bidiStreaming("Echo", input, new StreamObserver<StringMessage>() {
            private int index = 0;
            private boolean currentStatus = true;

            @Override
            public void onNext(StringMessage value) {
                currentStatus = currentStatus && value.getText().equals(expectedWords[index++]);
            }

            @Override
            public void onError(Throwable t) {
                status.complete(currentStatus = false);
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                status.complete(currentStatus);
            }
        });

        assertThat(status.get(), equalTo(true));
    }

    @Test
    public void invokeBidiStreamingMethod() throws InterruptedException, ExecutionException {
        final String sentence = "A simple invocation of a Bidi streaming method";
        final String[] expectedWords = sentence.split(" ");

        Collection<StringMessage> input = Arrays.stream(sentence.split(" "))
                .map(w -> StringMessage.newBuilder().setText(w).build())
                .collect(Collectors.toList());

        CompletableFuture<Boolean> status = new CompletableFuture<>();
        StreamObserver<StringMessage> clientStream = grpcClient.bidiStreaming("Echo", new StreamObserver<StringMessage>() {
            private int index = 0;
            private boolean currentStatus = true;

            @Override
            public void onNext(StringMessage value) {
                currentStatus = currentStatus && value.getText().equals(expectedWords[index++]);
            }

            @Override
            public void onError(Throwable t) {
                status.complete(currentStatus = false);
            }

            @Override
            public void onCompleted() {
                status.complete(currentStatus);
            }
        });

        for (String w : expectedWords) {
            clientStream.onNext(StringMessage.newBuilder().setText(w).build());
        }
        clientStream.onCompleted();

        assertThat(status.get(), equalTo(true));
    }

}
