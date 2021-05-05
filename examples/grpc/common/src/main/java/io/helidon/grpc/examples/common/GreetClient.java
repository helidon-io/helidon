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

import java.net.URI;

import io.helidon.grpc.client.ClientRequestAttribute;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.ClientTracingInterceptor;
import io.helidon.grpc.client.GrpcServiceClient;
import io.helidon.grpc.examples.common.Greet.GreetRequest;
import io.helidon.grpc.examples.common.Greet.GreetResponse;
import io.helidon.grpc.examples.common.Greet.SetGreetingRequest;
import io.helidon.grpc.examples.common.Greet.SetGreetingResponse;
import io.helidon.tracing.TracerBuilder;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.opentracing.Tracer;

/**
 * A client for the {@link GreetService} implemented with Helidon gRPC client API.
 */
public class GreetClient {

    private GreetClient() {
    }

    /**
     * The program entry point.
     *
     * @param args  the program arguments
     */
    public static void main(String[] args) {
        Tracer tracer = TracerBuilder.create("Client")
                .collectorUri(URI.create("http://localhost:9411/api/v2/spans"))
                .build();

        ClientTracingInterceptor tracingInterceptor = ClientTracingInterceptor.builder(tracer)
                .withVerbosity()
                .withTracedAttributes(ClientRequestAttribute.ALL_CALL_OPTIONS)
                .build();

        ClientServiceDescriptor descriptor = ClientServiceDescriptor
                .builder(GreetServiceGrpc.getServiceDescriptor())
                .intercept(tracingInterceptor)
                .build();

        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);

        // Obtain a greeting from the GreetService
        GreetRequest request = GreetRequest.newBuilder().setName("Aleks").build();
        GreetResponse firstGreeting = client.blockingUnary("Greet", request);
        System.out.println("First greeting: '" + firstGreeting.getMessage() + "'");

        // Change the greeting
        SetGreetingRequest setRequest = SetGreetingRequest.newBuilder().setGreeting("Ciao").build();
        SetGreetingResponse setResponse = client.blockingUnary("SetGreeting", setRequest);
        System.out.println("Greeting set to: '" + setResponse.getGreeting() + "'");

        // Obtain a second greeting from the GreetService
        GreetResponse secondGreeting = client.blockingUnary("Greet", request);
        System.out.println("Second greeting: '" + secondGreeting.getMessage() + "'");
    }
}
