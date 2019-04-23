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

package io.helidon.grpc.examples.common;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import io.helidon.grpc.examples.common.Strings.StringMessage;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * A client to the {@link StringService} implemented with Helidon gRPC client API.
 *
 * @author Bin Chen
 */
public class StringClientApi {
    private static String inputStr = "Test_String_for_Lower_and_Upper";
    private static StringMessage inputMsg = StringMessage.newBuilder().setText(inputStr).build();

    private StringClientApi() { }

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

        GrpcServiceClient grpcClient = GrpcServiceClient.create(channel, descriptor);

        // async unary
        grpcClient.<StringMessage, StringMessage>unary("Lower", inputMsg, new PrintObserver<>());
        Thread.sleep(500L);

        grpcClient.<StringMessage, StringMessage>unary("Upper", inputMsg, new PrintObserver<>());
        Thread.sleep(500L);

        // blocking client streaming
        String testStr = "A simple invocation of a client streaming method";
        PrintObserver<StringMessage> observer = new PrintObserver<>();
        StreamObserver<StringMessage> serverStream = grpcClient.clientStreaming("Join", observer);

        for (String word : testStr.split(" ")) {
            serverStream.onNext(StringMessage.newBuilder().setText(word).build());
        }
        serverStream.onCompleted();

        // asynchronous client streaming
        Collection<StringMessage> input = Arrays.stream(testStr.split(" "))
                .map(w -> StringMessage.newBuilder().setText(w).build())
                .collect(Collectors.toList());

        CompletableFuture<StringMessage> result = grpcClient.clientStreaming("Join", input);
        System.out.println(result.get().getText());
        Thread.sleep(500L);

        // blocking server streaming
        testStr = "A simple invocation of a server streaming method";
        StringMessage request = StringMessage.newBuilder().setText(testStr).build();
        Iterator<StringMessage> iterator = grpcClient.blockingServerStreaming("Split", request);

        Spliterator<StringMessage> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        String resultStr = StreamSupport.stream(spliterator, false)
                .map(StringMessage::getText)
                .collect(Collectors.joining(" "));
        System.out.println(resultStr);
        Thread.sleep(500L);

        // asynchronous server streaming
        observer = new PrintObserver<>();
        grpcClient.serverStreaming("Split", request, observer);
        Thread.sleep(1000L); // wait for server to complete all responses
        resultStr = observer.values
                .stream()
                .map(StringMessage::getText)
                .collect(Collectors.joining(" "));
        System.out.println(resultStr);
        Thread.sleep(500L);
    }

    private static StringMessage stringMessage(String text) {
        return StringMessage.newBuilder().setText(text).build();
    }

    static class PrintObserver<T> implements StreamObserver<T> {
        private List<T> values = new ArrayList<>();

        public void onNext(T value) {
            System.out.println(value);
            values.add(value);
        }

        public void onError(Throwable t) {
            t.printStackTrace();
        }

        public void onCompleted() {
            System.out.println("<completed>");
        }
    }
}
