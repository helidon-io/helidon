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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.helidon.grpc.client.test.StringServiceGrpc;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.StringService;
import services.TreeMapService;

import static io.helidon.grpc.client.GrpcClientTestUtil.HighPriorityInterceptor;
import static io.helidon.grpc.client.GrpcClientTestUtil.LowPriorityInterceptor;
import static io.helidon.grpc.client.GrpcClientTestUtil.MediumPriorityInterceptor;
import static io.helidon.grpc.client.test.Strings.StringMessage;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test a {@link GrpcServiceClient} that contains a {@link ClientServiceDescriptor}
 * created from a protocol buffer generated service.
 */
public class ProtoGrpcServiceClientIT {

    private static volatile GrpcServer grpcServer;

    private static GrpcServiceClient grpcClient;

    private static String inputStr = "Some_String_WITH_lower_and_UPPER_cases";
    private static StringMessage inputMsg = StringMessage.newBuilder().setText(inputStr).build();

    private static LowPriorityInterceptor lowPriorityInterceptor = new LowPriorityInterceptor();
    private static MediumPriorityInterceptor mediumPriorityInterceptor = new MediumPriorityInterceptor();
    private static HighPriorityInterceptor highPriorityInterceptor = new HighPriorityInterceptor();

    @BeforeAll
    public static void startServer() throws Exception {

        LogManager.getLogManager().readConfiguration();

        GrpcRouting routing = GrpcRouting.builder()
                .register(new TreeMapService())
                .register(new StringService())
                .build();

        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(0).build();

        grpcServer = GrpcServer.create(serverConfig, routing)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        ClientServiceDescriptor descriptor = ClientServiceDescriptor
                .builder(StringServiceGrpc.getServiceDescriptor())
                .intercept(mediumPriorityInterceptor)
                .intercept("Upper", highPriorityInterceptor)
                .intercept("Lower", lowPriorityInterceptor)
                .build();

        Channel channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port())
                .usePlaintext()
                .build();

        grpcClient = GrpcServiceClient.create(channel, descriptor);
    }

    @AfterAll
    public static void shutdownGrpcServer() {
        grpcServer.shutdown();
    }

    @BeforeEach
    public void resetInterceptors() {
        lowPriorityInterceptor.reset();
        mediumPriorityInterceptor.reset();
        highPriorityInterceptor.reset();
    }

    @Test
    public void testServiceName() {
        assertThat(grpcClient.serviceName(), is("StringService"));
    }

    @Test
    public void testBlockingUnaryMethods() {
        assertThat(grpcClient.<StringMessage, StringMessage>blockingUnary("Lower", inputMsg).getText(),
                   is(inputStr.toLowerCase()));
    }

    @Test
    public void testAsyncUnaryMethodWithCompletableFuture() throws InterruptedException, ExecutionException {
        CompletableFuture<StringMessage> result = grpcClient.unary("Upper", inputMsg);
        assertThat(result.get().getText(), equalTo(inputStr.toUpperCase()));
    }

    @Test
    public void testAsyncUnaryMethodWithStreamObserver() {
        TestStreamObserver<StringMessage> observer = new TestStreamObserver<>();
        grpcClient.unary("Upper", inputMsg, observer);

        assertThat(observer.awaitTerminalEvent(10, TimeUnit.SECONDS), is(true));
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(v -> v.getText().equals(inputStr.toUpperCase()));
    }

    @Test
    public void testAsyncClientStreamingMethodWithIterable() throws Throwable {
        String expectedSentence = "A simple invocation of a client streaming method";
        Collection<StringMessage> input = Arrays.stream(expectedSentence.split(" "))
                                                .map(w -> StringMessage.newBuilder().setText(w).build())
                                                .collect(Collectors.toList());

        CompletableFuture<StringMessage> result = grpcClient.clientStreaming("Join", input);
        assertThat(result.get().getText(), equalTo(expectedSentence));
    }

    @Test
    public void testAsyncClientStreamingMethod() {
        TestStreamObserver<StringMessage> observer = new TestStreamObserver<>();
        StreamObserver<StringMessage> clientStream = grpcClient.clientStreaming("Join", observer);

        String expectedSentence = "A simple invocation of a client streaming method";
        for (String word : expectedSentence.split(" ")) {
            clientStream.onNext(StringMessage.newBuilder().setText(word).build());
        }
        clientStream.onCompleted();

        assertThat(observer.awaitTerminalEvent(10, TimeUnit.SECONDS), is(true));
        observer.assertComplete()
                .assertNoErrors()
                .assertValue(v -> v.getText().equals(expectedSentence));
    }

    @Test
    public void testBlockingServerStreamingMethods() {
        String sentence = "A simple invocation of a client streaming method";
        StringMessage message = StringMessage.newBuilder().setText(sentence).build();

        Iterator<StringMessage> iterator = grpcClient.blockingServerStreaming("Split", message);

        Spliterator<StringMessage> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        String result = StreamSupport.stream(spliterator, false)
                                            .map(StringMessage::getText)
                                            .collect(Collectors.joining(" "));

        assertThat(result, is(sentence));
    }

    @Test
    public void testAsyncServerStreamingMethods() {
        String sentence = "A simple invocation of a client streaming method";
        String[] expectedWords = sentence.split(" ");
        StringMessage request = StringMessage.newBuilder().setText(sentence).build();
        TestStreamObserver<StringMessage> observer = new TestStreamObserver<>();

        grpcClient.serverStreaming("Split", request, observer);

        assertThat(observer.awaitTerminalEvent(10, TimeUnit.SECONDS), is(true));
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(expectedWords.length);

        String[] results = observer.values()
                                   .stream()
                                   .map(StringMessage::getText)
                                   .toArray(String[]::new);

        assertThat(results, is(expectedWords));
    }

    @Test
    public void testInvokeBidiStreamingMethod() {
        String sentence = "A simple invocation of a Bidi streaming method";
        String[] expectedWords = sentence.split(" ");

        TestStreamObserver<StringMessage> observer = new TestStreamObserver<>();
        StreamObserver<StringMessage> clientStream = grpcClient.bidiStreaming("Echo", observer);

        for (String w : expectedWords) {
            clientStream.onNext(StringMessage.newBuilder().setText(w).build());
        }
        clientStream.onCompleted();

        assertThat(observer.awaitTerminalEvent(10, TimeUnit.SECONDS), is(true));
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(expectedWords.length);


        String[] results = observer.values()
                                   .stream()
                                   .map(StringMessage::getText)
                                   .toArray(String[]::new);

        assertThat(results, is(expectedWords));
    }

    @Test
    public void testLowAndMediumPriorityMethodInterceptors() {
        assertThat(
                grpcClient.<StringMessage, StringMessage>blockingUnary("Lower", inputMsg).getText(),
                equalTo(inputStr.toLowerCase()));

        assertThat(lowPriorityInterceptor.getInvocationCount(), equalTo(1));
        assertThat(mediumPriorityInterceptor.getInvocationCount(), equalTo(2));
        assertThat(highPriorityInterceptor.getInvocationCount(), equalTo(0));
    }

    @Test
    public void testHighAndMediumPriorityMethodInterceptors() {
        assertThat(
                grpcClient.<StringMessage, StringMessage>blockingUnary("Upper", inputMsg).getText(),
                equalTo(inputStr.toUpperCase()));

        assertThat(lowPriorityInterceptor.getInvocationCount(), equalTo(0));
        assertThat(mediumPriorityInterceptor.getInvocationCount(), equalTo(2));
        assertThat(highPriorityInterceptor.getInvocationCount(), equalTo(1));
    }
}
