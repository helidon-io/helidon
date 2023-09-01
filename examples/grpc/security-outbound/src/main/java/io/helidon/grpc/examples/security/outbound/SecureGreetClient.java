/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.grpc.examples.security.outbound;


import io.helidon.config.Config;
import io.helidon.grpc.examples.common.Greet;
import io.helidon.grpc.examples.common.GreetServiceGrpc;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.integration.grpc.GrpcClientSecurity;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

/**
 * A GreetService client that uses {@link io.grpc.CallCredentials} using basic auth.
 */
public class SecureGreetClient {

    private SecureGreetClient() {
    }

    /**
     * Program entry point.
     *
     * @param args  program arguments
     */
    public static void main(String[] args) {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        Config config = Config.create();

        // configure Helidon security and add the basic auth provider
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("http-basic-auth")))
                .build();

        // create the gRPC client security call credentials
        // setting the properties used by the basic auth provider for user name and password
        GrpcClientSecurity clientSecurity = GrpcClientSecurity.builder(security.createContext("test.client"))
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, "Bob")
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, "password")
                .build();

        // create the GreetService client stub and use the GrpcClientSecurity call credentials
        GreetServiceGrpc.GreetServiceBlockingStub stub = GreetServiceGrpc.newBlockingStub(channel)
                .withCallCredentials(clientSecurity);

        Greet.GreetResponse greetResponse = stub.greet(Greet.GreetRequest.newBuilder().setName("Bob").build());

        System.out.println(greetResponse.getMessage());

        Greet.SetGreetingResponse setGreetingResponse =
                stub.setGreeting(Greet.SetGreetingRequest.newBuilder().setGreeting("Merhaba").build());

        System.out.println("Greeting set to: " + setGreetingResponse.getGreeting());

        greetResponse = stub.greet(Greet.GreetRequest.newBuilder().setName("Bob").build());

        System.out.println(greetResponse.getMessage());
    }
}
