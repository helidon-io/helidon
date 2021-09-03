/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.grpc.examples.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import io.helidon.grpc.examples.common.Strings.StringMessage;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * A client to the {@link StringService} implemented with Helidon gRPC client API.
 */
public class StringClient {
    private static String inputStr = "Test_String_for_Lower_and_Upper";
    private static StringMessage inputMsg = StringMessage.newBuilder().setText(inputStr).build();

    private StringClient() {
    }

    /**
     * Program entry point.
     *
     * @param args  the program arguments
     *
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor
                .builder(StringServiceGrpc.getServiceDescriptor())
                .build();

        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);

        unary(client);
        asyncUnary(client);
        blockingUnary(client);
        clientStreaming(client);
        clientStreamingOfIterable(client);
        serverStreamingBlocking(client);
        serverStreaming(client);
        bidirectional(client);
    }

    /**
     * Call the unary {@code Lower} method using an normal unary call.
     *
     * @param client  the StringService {@link GrpcServiceClient}
     * @throws java.lang.Exception if the call fails
     */
    public static void unary(GrpcServiceClient client) throws Exception {
        FutureObserver observer = new FutureObserver();
        client.unary("Lower", inputMsg, observer);

        String response = observer.get();

        System.out.println("Unary Lower response: '" + response + "'");
    }

    /**
     * Call the unary {@code Lower} method using an async call.
     *
     * @param client  the StringService {@link GrpcServiceClient}
     */
    public static void asyncUnary(GrpcServiceClient client) {
        CompletionStage<StringMessage> stage = client.unary("Lower", inputMsg);

        stage.handle((response, error) -> {
            if (error == null) {
                System.out.println("Async Lower response: '" + response.getText() + "'");
            } else {
                error.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Call the unary {@code Upper} method using a blocking call.
     *
     * @param client  the StringService {@link GrpcServiceClient}
     */
    public static void blockingUnary(GrpcServiceClient client) {
        StringMessage upperResonse = client.blockingUnary("Upper", inputMsg);

        System.out.println("Blocking Upper response: '" + upperResonse.getText() + "'");
    }

    /**
     * Call the client streaming {@code Join} method.
     *
     * @param client  the StringService {@link GrpcServiceClient}
     * @throws java.lang.Exception if the call fails
     */
    public static void clientStreaming(GrpcServiceClient client) throws Exception {
        FutureObserver responses = new FutureObserver();
        StreamObserver<StringMessage> requests = client.clientStreaming("Join", responses);

        List<String> joinValues = List.of("A", "B", "C", "D");

        // stream the values to the server
        joinValues.forEach(word -> requests.onNext(StringMessage.newBuilder().setText(word).build()));
        requests.onCompleted();

        // wait for the response observer to complete
        String joined = responses.get();

        System.out.println("Join response: '" + joined + "'");
    }

    /**
     * Call the client streaming {@code Join} method streaming the contents of an {@link Iterable}.
     *
     * @param client  the StringService {@link GrpcServiceClient}
     * @throws java.lang.Exception if the call fails
     */
    public static void clientStreamingOfIterable(GrpcServiceClient client) throws Exception {
        List<StringMessage> joinValues = List.of("A", "B", "C", "D")
                .stream()
                .map(val -> StringMessage.newBuilder().setText(val).build())
                .collect(Collectors.toList());

        // stream the value to the server
        CompletionStage<StringMessage> stage = client.clientStreaming("Join", joinValues);

        // wait for the response future to complete
        stage.handle((response, error) -> {
            if (error == null) {
                System.out.println("Join response: '" + response.getText() + "'");
            } else {
                error.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Call the server streaming {@code Split} method using a blocking call.
     *
     * @param client  the StringService {@link GrpcServiceClient}
     */
    public static void serverStreamingBlocking(GrpcServiceClient client) {
        String stringToSplit = "A B C D E";
        StringMessage request = StringMessage.newBuilder().setText(stringToSplit).build();

        Iterator<StringMessage> iterator = client.blockingServerStreaming("Split", request);

        while (iterator.hasNext()) {
            StringMessage response = iterator.next();
            System.out.println("Response from blocking Split: '" + response.getText() + "'");
        }
    }

    /**
     * Call the server streaming {@code Split} method using an async call.
     *
     * @param client  the StringService {@link GrpcServiceClient}
     * @throws java.lang.Exception if the call fails
     */
    public static void serverStreaming(GrpcServiceClient client) throws Exception {
        String stringToSplit = "A B C D E";
        FutureStreamingObserver responses = new FutureStreamingObserver();
        StringMessage request = StringMessage.newBuilder().setText(stringToSplit).build();

        client.serverStreaming("Split", request, responses);

        // wait for the call to complete
        List<String> words = responses.get();

        for (String word : words) {
            System.out.println("Response from async Split: '" + word + "'");
        }
    }

    /**
     * Call the bidirectional streaming {@code Echo} method using an async call.
     *
     * @param client  the StringService {@link GrpcServiceClient}
     * @throws java.lang.Exception if the call fails
     */
    public static void bidirectional(GrpcServiceClient client) throws Exception {
        List<String> valuesToStream = List.of("A", "B", "C", "D");
        FutureStreamingObserver responses = new FutureStreamingObserver();

        StreamObserver<StringMessage> requests = client.bidiStreaming("Echo", responses);

        // stream the words to the server
        valuesToStream.forEach(word -> requests.onNext(StringMessage.newBuilder().setText(word).build()));
        // signal that we have completed
        requests.onCompleted();

        // wait for the echo responses to complete
        List<String> echoes = responses.get();

        for (String word : echoes) {
            System.out.println("Response from Echo: '" + word + "'");
        }
    }


    /**
     * A combination {@link java.util.concurrent.CompletableFuture} and
     * {@link io.grpc.stub.StreamObserver}.
     * <p>
     * This future will complete when the {@link #onCompleted()}  or the
     * {@link #onError(Throwable)} methods are called.
     * <p>
     * This implementation expects a single result.
     */
    static class FutureObserver
            extends CompletableFuture<String>
            implements StreamObserver<StringMessage> {

        private String value;

        public void onNext(StringMessage value) {
            this.value = value.getText();
        }

        public void onError(Throwable t) {
            completeExceptionally(t);
        }

        public void onCompleted() {
            complete(value);
        }
    }

    /**
     * A combination {@link java.util.concurrent.CompletableFuture} and
     * {@link io.grpc.stub.StreamObserver}.
     * <p>
     * This future will complete when the {@link #onCompleted()}  or the
     * {@link #onError(Throwable)} methods are called.
     * <p>
     * This implementation can handle multiple calls to
     * {@link #onNext(StringMessage)}.
     */
    static class FutureStreamingObserver
            extends CompletableFuture<List<String>>
            implements StreamObserver<StringMessage> {

        private List<String> values = new ArrayList<>();

        public void onNext(StringMessage value) {
            values.add(value.getText());
        }

        public void onError(Throwable t) {
            completeExceptionally(t);
        }

        public void onCompleted() {
            complete(values);
        }
    }
}
