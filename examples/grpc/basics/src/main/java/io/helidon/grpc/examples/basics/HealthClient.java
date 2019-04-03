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

package io.helidon.grpc.examples.basics;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;

/**
 * A simple gRPC health check client.
 *
 * @author Aleksandar Seovic
 */
public class HealthClient {

    private HealthClient() {
    }

    /**
     * The program entry point.
     *
     * @param args  the program arguments
     *
     * @throws Exception  if an error occurs
     */
    public static void main(String[] args) {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408).usePlaintext().build();

        HealthGrpc.HealthBlockingStub health = HealthGrpc.newBlockingStub(channel);
        System.out.println(health.check(HealthCheckRequest.newBuilder().setService("GreetService").build()));
        System.out.println(health.check(HealthCheckRequest.newBuilder().setService("FooService").build()));
    }
}
