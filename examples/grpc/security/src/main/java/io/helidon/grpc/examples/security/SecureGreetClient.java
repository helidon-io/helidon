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

package io.helidon.grpc.examples.security;

import io.helidon.config.Config;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import io.helidon.grpc.examples.common.Greet;
import io.helidon.grpc.examples.common.GreetServiceGrpc;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.integration.grpc.GrpcClientSecurity;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

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
     * @param args  the program arguments - {@code arg[0]} is the user name
     *              and {@code arg[1] is the password}
     */
    public static void main(String[] args) {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        // Obtain the user name and password from the program arguments
        String user = args.length >= 2 ? args[0] : "Ted";
        String password = args.length >= 2 ? args[1] : "secret";

        Config config = Config.create();

        // configure Helidon security and add the basic auth provider
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("http-basic-auth")))
                .build();

        // create the gRPC client security call credentials
        // setting the properties used by the basic auth provider for user name and password
        GrpcClientSecurity clientSecurity = GrpcClientSecurity.builder(security.createContext("test.client"))
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, user)
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, password)
                .build();

        // Create the client service descriptor and add the call credentials
        ClientServiceDescriptor descriptor = ClientServiceDescriptor
                .builder(GreetServiceGrpc.getServiceDescriptor())
                .callCredentials(clientSecurity)
                .build();

        // create the client for the service
        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);

        greet(client);
        setGreeting(client);
        greet(client);
    }

    private static void greet(GrpcServiceClient client) {
        try {
            Greet.GreetRequest request = Greet.GreetRequest.newBuilder().setName("Aleks").build();
            Greet.GreetResponse response = client.blockingUnary("Greet", request);

            System.out.println(response);
        } catch (Exception e) {
            System.err.println("Caught exception obtaining greeting: " + e.getMessage());
        }
    }

    private static void setGreeting(GrpcServiceClient client) {
        try {
            Greet.SetGreetingRequest setRequest = Greet.SetGreetingRequest.newBuilder().setGreeting("Hey").build();
            Greet.SetGreetingResponse setResponse = client.blockingUnary("SetGreeting", setRequest);

            System.out.println(setResponse);
        } catch (Exception e) {
            System.err.println("Caught exception setting greeting: " + e.getMessage());
        }
    }
}
