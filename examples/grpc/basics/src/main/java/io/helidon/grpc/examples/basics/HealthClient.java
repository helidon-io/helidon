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

package io.helidon.grpc.examples.basics;

import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;

/**
 * A gRPC health check client implemented with Helidon gRPC client API.
 * <p>
 * This example uses the java-grpc built in health check client.
 */
public class HealthClient {

    private HealthClient() {
    }

    /**
     * The program entry point.
     *
     * @param args  the program arguments
     */
    public static void main(String[] args) {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor
                .builder(HealthGrpc.getServiceDescriptor())
                .build();

        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        GrpcServiceClient grpcClient = GrpcServiceClient.create(channel, descriptor);

        // query the health of a deployed service
        HealthCheckResponse response = grpcClient.blockingUnary("Check",
                HealthCheckRequest.newBuilder().setService("GreetService").build());

        System.out.println(response);

        // query the health of a non-existent service
        try {
            grpcClient.blockingUnary("Check",
                    HealthCheckRequest.newBuilder().setService("FooService").build());
        } catch (StatusRuntimeException e) {
            // expect to catch a NOT_FOUND exception
            System.out.println(e.getMessage());
        }
    }
}
