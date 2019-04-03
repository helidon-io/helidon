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

package io.helidon.grpc.examples.security;


import io.helidon.grpc.examples.common.Greet;
import io.helidon.grpc.examples.common.GreetServiceGrpc;

import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

/**
 * A {@link io.helidon.grpc.examples.common.GreetService} client that optionally
 * provides {@link CallCredentials} using basic auth.
 */
public class SecureGreetClient {

    private SecureGreetClient() {
    }

    /**
     * Main entry point.
     *
     * @param args  the program arguments
     */
    public static void main(String[] args) {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        GreetServiceGrpc.GreetServiceBlockingStub greetSvc = GreetServiceGrpc.newBlockingStub(channel)
                .withCallCredentials(new BasicAuthCallCredentials(args));

        greet(greetSvc);
        setGreeting(greetSvc);
        greet(greetSvc);
    }

    private static void greet(GreetServiceGrpc.GreetServiceBlockingStub greetSvc) {
        try {
            Greet.GreetRequest request = Greet.GreetRequest.newBuilder().setName("Aleks").build();
            Greet.GreetResponse response = greetSvc.greet(request);

            System.out.println(response);
        } catch (Exception e) {
            System.err.println("Caught exception obtaining greeting: " + e.getMessage());
        }
    }

    private static void setGreeting(GreetServiceGrpc.GreetServiceBlockingStub greetSvc) {
        try {
            Greet.SetGreetingRequest setRequest = Greet.SetGreetingRequest.newBuilder().setGreeting("Hey").build();
            Greet.SetGreetingResponse setResponse = greetSvc.setGreeting(setRequest);

            System.out.println(setResponse);
        } catch (Exception e) {
            System.err.println("Caught exception setting greeting: " + e.getMessage());
        }
    }
}
