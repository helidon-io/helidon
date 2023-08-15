/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.grpc.webserver;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.nima.grpc.strings.StringServiceGrpc;
import io.helidon.nima.grpc.strings.Strings.StringMessage;
import io.helidon.nima.grpc.webserver.GrpcRouting;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.WebServer;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@ServerTest
abstract class BaseStringServiceTest {
    private final int port;

    protected ManagedChannel channel;
    protected StringServiceGrpc.StringServiceBlockingStub blockingStub;
    protected StringServiceGrpc.StringServiceStub stub;

    BaseStringServiceTest(WebServer server) {
        this.port = server.port();
    }

    @BeforeEach
    void beforeEach() {
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        blockingStub = StringServiceGrpc.newBlockingStub(channel);
        stub = StringServiceGrpc.newStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        blockingStub = null;
        stub = null;
        channel.shutdown();
        if (!channel.awaitTermination(10, TimeUnit.SECONDS)) {
            System.err.println("Failed to terminate channel");
        }
        if (!channel.isTerminated()) {
            System.err.println("Channel is not terminated!!!");
        }
    }

    @RepeatedTest(20)
    void testUnaryUpper() {
        String text = "lower case original";
        StringMessage request = StringMessage.newBuilder().setText(text).build();
        StringMessage response = blockingStub.upper(request);

        assertThat(response.getText(), is(text.toUpperCase(Locale.ROOT)));
    }

    @RepeatedTest(20)
    void testUnaryLower() {
        String text = "UPPER CASE ORIGINAL";
        StringMessage request = StringMessage.newBuilder().setText(text).build();
        StringMessage response = blockingStub.lower(request);

        assertThat(response.getText(), is(text.toLowerCase(Locale.ROOT)));
    }

    @RepeatedTest(20)
    void testBidi() throws Throwable {
        List<String> valuesToStream = List.of("A", "B", "C", "D");

        StringsCollector responseObserver = new StringsCollector();

        StreamObserver<StringMessage> requests = stub.echo(responseObserver);

        // stream the words to the server
        valuesToStream.forEach(word -> requests.onNext(StringMessage.newBuilder().setText(word).build()));
        // signal that we have completed
        requests.onCompleted();

        // wait for the echo responses to complete
        List<String> echoes = responseObserver.awaitResponse();
        assertThat(echoes, is(valuesToStream));
    }

    @RepeatedTest(20)
    void testClientStream() throws Throwable {
        List<String> valuesToStream = List.of("A", "B", "C", "D");
        StringsCollector responseObserver = new StringsCollector();

        StreamObserver<StringMessage> requests = stub.join(responseObserver);
        // stream the words to the server
        valuesToStream.forEach(word -> requests.onNext(StringMessage.newBuilder().setText(word).build()));
        // signal that we have completed
        requests.onCompleted();

        List<String> strings = responseObserver.awaitResponse();

        assertThat(strings, contains("A B C D"));
    }

    @RepeatedTest(20)
    void testServerStream() throws Throwable {
        StringsCollector responseObserver = new StringsCollector();
        stub.split(StringMessage.newBuilder().setText("A B C D").build(), responseObserver);

        assertThat(responseObserver.awaitResponse(), contains("A", "B", "C", "D"));
    }

    private static class StringsCollector implements StreamObserver<StringMessage> {
        private final List<String> collectedString = new LinkedList<>();
        private final CompletableFuture<List<String>> future = new CompletableFuture<>();

        @Override
        public void onNext(StringMessage stringMessage) {
            collectedString.add(stringMessage.getText());
        }

        @Override
        public void onError(Throwable throwable) {
            // wrap in our exception, so we can see who called this (onError) method
            future.completeExceptionally(new RuntimeException(throwable));
        }

        @Override
        public void onCompleted() {
            future.complete(collectedString);
        }

        List<String> awaitResponse() throws Throwable {
            return future.get(10, TimeUnit.SECONDS);
        }
    }
}