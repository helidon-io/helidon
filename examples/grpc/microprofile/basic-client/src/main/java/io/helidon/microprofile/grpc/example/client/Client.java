/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.grpc.example.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;

import io.helidon.microprofile.grpc.client.GrpcChannel;
import io.helidon.microprofile.grpc.client.GrpcProxy;

import io.grpc.stub.StreamObserver;

/**
 * A client to the {@link StringService}.
 * <p>
 * This client is a CDI bean which will be initialised when the CDI container
 * is initialised in the {@link #main(String[])} method.
 */
@ApplicationScoped
public class Client {

    /**
     * The {@link StringService} client to use to call methods on the server.
     * <p>
     * A dynamic proxy of the {@link StringService} interface will be injected by CDI.
     * This proxy will connect to the service using the default {@link io.grpc.Channel}.
     */
    @Inject
    @GrpcProxy
    @GrpcChannel(name = "test-server")
    private StringService stringService;


    /**
     * Program entry point.
     *
     * @param args  the program arguments
     *
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        SeContainer container = initializer.initialize();

        Client client = container.select(Client.class).get();

        client.unary();
        client.serverStreaming();
        client.clientStreaming();
        client.bidirectional();
    }

    /**
     * Call the unary {@code Lower} method.
     */
    public void unary() {
        String response = stringService.lower("ABCD");
        System.out.println("Unary Lower response: '" + response + "'");
    }

    /**
     * Call the server streaming {@code Split} method.
     */
    public void serverStreaming() {
        String stringToSplit = "A B C D E";

        stringService.split(stringToSplit)
                .forEach(response -> System.out.println("Response from blocking Split: '" + response + "'"));
    }

    /**
     * Call the client streaming {@code Join} method.
     *
     * @throws Exception if the call fails
     */
    public void clientStreaming() throws Exception {
        FutureObserver responses = new FutureObserver();
        StreamObserver<String> requests = stringService.join(responses);

        List<String> joinValues = List.of("A", "B", "C", "D");

        // stream the values to the server
        joinValues.forEach(requests::onNext);
        requests.onCompleted();

        // wait for the response observer to complete
        String joined = responses.get();

        System.out.println("Join response: '" + joined + "'");
    }

    /**
     * Call the bidirectional streaming {@code Echo} method.
     * @throws Exception if the call fails
     */
    public void bidirectional() throws Exception {
        List<String> valuesToStream = List.of("A", "B", "C", "D");
        FutureStreamingObserver responses = new FutureStreamingObserver();

        StreamObserver<String> requests = stringService.echo(responses);

        // stream the words to the server
        valuesToStream.forEach(requests::onNext);
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
            implements StreamObserver<String> {

        private String value;

        @Override
        public void onNext(String value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            completeExceptionally(t);
        }

        @Override
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
     * {@link #onNext(String)}.
     */
    static class FutureStreamingObserver
            extends CompletableFuture<List<String>>
            implements StreamObserver<String> {

        private List<String> values = new ArrayList<>();

        @Override
        public void onNext(String value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            completeExceptionally(t);
        }

        @Override
        public void onCompleted() {
            complete(values);
        }
    }
}
