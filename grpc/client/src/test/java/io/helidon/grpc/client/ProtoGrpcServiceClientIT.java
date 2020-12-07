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

package io.helidon.grpc.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Priority;

import io.helidon.common.LogConfig;
import io.helidon.grpc.client.test.StringServiceGrpc;
import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.StringService;
import services.TreeMapService;

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

    // Interceptors to be used in the tests.
    private static LowPriorityInterceptor lowPriorityInterceptor = new LowPriorityInterceptor();
    private static MediumPriorityInterceptor mediumPriorityInterceptor = new MediumPriorityInterceptor();
    private static HighPriorityInterceptor highPriorityInterceptor = new HighPriorityInterceptor();

    // CallCredentials to be used in the tests.
    private static CustomCallCredentials serviceCred = new CustomCallCredentials("service-level-key", "service-level-value");
    private static CustomCallCredentials lowerMethodCred = new CustomCallCredentials("lower-method-key", "lower-method-value");
    private static CustomCallCredentials joinMethodCred = new CustomCallCredentials("join-method-key", "join-method-value");

    // A server interceptor to check the headers.
    private static HeaderCheckingInterceptor headerCheckingInterceptor = new HeaderCheckingInterceptor(null);

    @BeforeAll
    public static void startServer() throws Exception {

        LogConfig.configureRuntime();

        GrpcRouting routing = GrpcRouting.builder()
                .intercept(headerCheckingInterceptor)
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
                .callCredentials(serviceCred)
                .callCredentials("Lower", lowerMethodCred)
                .callCredentials("Join", joinMethodCred)
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
        CompletionStage<StringMessage> result = grpcClient.unary("Upper", inputMsg);
        assertThat(result.toCompletableFuture().get().getText(), equalTo(inputStr.toUpperCase()));
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

        CompletionStage<StringMessage> result = grpcClient.clientStreaming("Join", input);
        assertThat(result.toCompletableFuture().get().getText(), equalTo(expectedSentence));
    }

    @Test
    public void testAsyncClientStreamingMethodWithStream() throws Throwable {
        String expectedSentence = "A simple invocation of a client streaming method";
        Collection<StringMessage> input = Arrays.stream(expectedSentence.split(" "))
                .map(w -> StringMessage.newBuilder().setText(w).build())
                .collect(Collectors.toList());

        CompletionStage<StringMessage> result = grpcClient.clientStreaming("Join", input.stream());
        assertThat(result.toCompletableFuture().get().getText(), equalTo(expectedSentence));
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
        assertThat(mediumPriorityInterceptor.getInvocationCount(), equalTo(1));
        assertThat(highPriorityInterceptor.getInvocationCount(), equalTo(0));
    }

    @Test
    public void testHighAndMediumPriorityMethodInterceptors() {
        assertThat(
                grpcClient.<StringMessage, StringMessage>blockingUnary("Upper", inputMsg).getText(),
                equalTo(inputStr.toUpperCase()));

        assertThat(lowPriorityInterceptor.getInvocationCount(), equalTo(0));
        assertThat(mediumPriorityInterceptor.getInvocationCount(), equalTo(1));
        assertThat(highPriorityInterceptor.getInvocationCount(), equalTo(1));
    }


    @Test
    public void testUnaryMethodLevelCallCredentials() {
        headerCheckingInterceptor.resetKeyAndValue("lower-method-key");
        assertThat(
                grpcClient.<StringMessage, StringMessage>blockingUnary("Lower", inputMsg).getText(),
                equalTo(inputStr.toLowerCase()));

        assertThat(headerCheckingInterceptor.getValue(), equalTo("lower-method-value"));

        headerCheckingInterceptor.resetKeyAndValue("service-level-key");
        assertThat(
                grpcClient.<StringMessage, StringMessage>blockingUnary("Upper", inputMsg).getText(),
                equalTo(inputStr.toUpperCase()));

        assertThat(headerCheckingInterceptor.getValue(), equalTo("service-level-value"));
    }

    @Test
    public void testClientStreamingCallCredentials() throws Exception {
        headerCheckingInterceptor.resetKeyAndValue("join-method-key");
        String expectedSentence = "A simple invocation of a client streaming method";
        Collection<StringMessage> input = Arrays.stream(expectedSentence.split(" "))
                .map(w -> StringMessage.newBuilder().setText(w).build())
                .collect(Collectors.toList());

        CompletionStage<StringMessage> result = grpcClient.clientStreaming("Join", input.stream());
        assertThat(result.toCompletableFuture().get().getText(), equalTo(expectedSentence));
        assertThat(headerCheckingInterceptor.getValue(), equalTo("join-method-value"));
    }

    @Test
    public void testServerStreamingCallCredentials() {
        headerCheckingInterceptor.resetKeyAndValue("service-level-key");
        String sentence = "A simple invocation of a client streaming method";
        StringMessage message = StringMessage.newBuilder().setText(sentence).build();

        Iterator<StringMessage> iterator = grpcClient.blockingServerStreaming("Split", message);

        Spliterator<StringMessage> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        String result = StreamSupport.stream(spliterator, false)
                .map(StringMessage::getText)
                .collect(Collectors.joining(" "));

        assertThat(result, is(sentence));
        assertThat(headerCheckingInterceptor.getValue(), equalTo("service-level-value"));
    }

    /**
     * A base {@link ClientInterceptor}.
     */
    abstract static class BaseInterceptor
            implements ClientInterceptor {

        private int invocationCount;

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                   CallOptions callOptions,
                                                                   Channel next) {
            invocationCount++;
            return next.newCall(method, callOptions);
        }

        int getInvocationCount() {
            return invocationCount;
        }

        void reset() {
            this.invocationCount = 0;
        }

    }

    /**
     * A high priority {@link ClientInterceptor}.
     */
    @Priority(InterceptorPriorities.USER)
    static class HighPriorityInterceptor
            extends BaseInterceptor {
    }

    /**
     * A medium priority {@link ClientInterceptor}.
     */
    @Priority(InterceptorPriorities.USER + 1000)
    static class MediumPriorityInterceptor
            extends BaseInterceptor {
    }

    /**
     * A low priority {@link ClientInterceptor}.
     */
    @Priority(InterceptorPriorities.USER + 2000)
    static class LowPriorityInterceptor
            extends BaseInterceptor {
    }

    private static class HeaderCheckingInterceptor
            implements ServerInterceptor {

        private String key;
        private String value;

        public HeaderCheckingInterceptor(String key) {
            this.key = key;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            if (key != null) {
                this.value = headers.get(Metadata.Key.of(this.key, Metadata.ASCII_STRING_MARSHALLER));
            }
            return next.startCall(call, headers);
        }

        public String getValue() {
            return value;
        }

        public void resetKeyAndValue(String key) {
            this.key = key;
            this.value = null;
        }
    }

    private static class CustomCallCredentials
            extends CallCredentials {
        private String key;
        private String value;

        public CustomCallCredentials(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
            if (key != null && value != null) {
                Metadata metadata = new Metadata();
                metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
                applier.apply(metadata);
            }
        }

        @Override
        public void thisUsesUnstableApi() {
        }
    }

}
