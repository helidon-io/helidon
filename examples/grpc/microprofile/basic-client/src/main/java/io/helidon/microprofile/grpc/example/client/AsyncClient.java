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

package io.helidon.microprofile.grpc.example.client;

import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;

import io.helidon.microprofile.grpc.client.GrpcChannel;
import io.helidon.microprofile.grpc.client.GrpcProxy;

/**
 * A client to the {@link io.helidon.microprofile.grpc.example.client.AsyncStringService}.
 * <p>
 * This client is a CDI bean which will be initialised when the CDI container
 * is initialised in the {@link #main(String[])} method.
 */
@ApplicationScoped
public class AsyncClient {

    /**
     * The {@link io.helidon.microprofile.grpc.example.client.StringService} client to use to call methods on the server.
     * <p>
     * A dynamic proxy of the {@link io.helidon.microprofile.grpc.example.client.StringService} interface will be injected by CDI.
     * This proxy will connect to the service using the default {@link io.grpc.Channel}.
     */
    @Inject
    @GrpcProxy
    @GrpcChannel(name = "test-server")
    private AsyncStringService stringService;


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

        AsyncClient client = container.select(AsyncClient.class).get();

        client.asyncUnary();
    }

    /**
     * Call the unary {@code Lower} method.
     * @throws java.lang.Exception if the async call fails
     */
    void asyncUnary() throws Exception {
        CompletionStage<String> response = stringService.lower("ABCD");
        String value = response.toCompletableFuture().get();
        System.out.println("Async Unary Lower response: '" + value + "'");
    }
}
