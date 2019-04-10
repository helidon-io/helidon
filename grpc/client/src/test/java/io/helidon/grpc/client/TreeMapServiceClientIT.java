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

package io.helidon.grpc.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import io.helidon.grpc.client.util.AccumulatingResponseStreamObserverAdapter;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.StringService;
import services.TreeMapService;
import services.TreeMapService.Person;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class TreeMapServiceClientIT {

    private static volatile int grpcPort;
    private static volatile GrpcServer grpcServer;

    @BeforeAll
    public static void startServer() throws IOException, SecurityException {

        LogManager.getLogManager().readConfiguration();

        GrpcRouting routing = GrpcRouting.builder()
                .register(new TreeMapService())
                .register(new StringService())
                .build();

        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(grpcPort).build();

        GrpcServer.create(serverConfig, routing)
                .start()
                .thenAccept(s -> {
                    System.out.println("gRPC server is UP and listening on localhost:" + s.port());
                    grpcServer = s;
                    grpcPort = s.port();
                    s.whenShutdown().thenRun(() -> System.out.println("gRPC server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });
    }

    @AfterAll
    public static void shutdownGrpcServer() {
        grpcServer.shutdown().thenRun(() -> {
            System.out.println("Server shutdown...");
        });
    }

    @Test
    public void createAndInvokeUnary() throws ExecutionException, InterruptedException {
        ClientServiceDescriptor svcDesc = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .unary("get", Integer.class, Person.class)
                .build();

        Channel ch = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
        GrpcServiceClient treeSvcClient = GrpcServiceClient.builder()
                .clientServiceDescriptor(svcDesc)
                .channel(ch)
                .callOptions(CallOptions.DEFAULT)
                .build();

        assertThat(treeSvcClient.unary("get", 1).get(), equalTo(TreeMapService.BILBO));
    }

    @Test
    public void createAndInvokeServerStreamingMethod() throws ExecutionException, InterruptedException {
        ClientServiceDescriptor svcDesc = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .unary("get", Integer.class, Person.class)
                .serverStreaming("greaterOrEqualTo", Integer.class, Person.class)
                .build();

        Channel ch = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
        GrpcServiceClient treeSvcClient = GrpcServiceClient.builder()
                .clientServiceDescriptor(svcDesc)
                .channel(ch)
                .callOptions(CallOptions.DEFAULT)
                .build();

        assertThat(treeSvcClient.unary("get", 1).get(), equalTo(TreeMapService.BILBO));

        AccumulatingResponseStreamObserverAdapter<Person> respStream = new AccumulatingResponseStreamObserverAdapter<>();
        treeSvcClient.serverStreaming("greaterOrEqualTo", 3, respStream);
        respStream.waitForCompletion();

        assertThat(respStream.getResult().size(), equalTo(3));
        List<Integer> ids = respStream.getResult().stream().map(p -> p.getId()).collect(Collectors.toList());

        assertThat(ids, equalTo(Arrays.asList(3, 4, 5)));
    }


    @Test
    public void createAndInvokeClientStreamingMethod() throws ExecutionException, InterruptedException {
        ClientServiceDescriptor svcDesc = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .unary("get", Integer.class, Person.class)
                .serverStreaming("greaterOrEqualTo", Integer.class, Person.class)
                .clientStreaming("sumOfAges", Integer.class, Integer.class)
                .build();

        Channel ch = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
        GrpcServiceClient treeSvcClient = GrpcServiceClient.builder()
                .clientServiceDescriptor(svcDesc)
                .channel(ch)
                .callOptions(CallOptions.DEFAULT)
                .build();

        CompletableFuture<Integer> sum = treeSvcClient.clientStreaming("sumOfAges", Arrays.asList(3, 4, 5));
        assertThat(sum.get(), equalTo(
                TreeMapService.ARAGON.getAge() +
                        TreeMapService.GALARDRIAL.getAge() +
                        TreeMapService.GANDALF.getAge()));
    }


    @Test
    public void createAndInvokeBidiStreamingMethod() throws ExecutionException, InterruptedException {
        ClientServiceDescriptor svcDesc = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .unary("get", Integer.class, Person.class)
                .bidirectional("persons", Integer.class, Person.class)
                .build();

        Channel ch = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
        GrpcServiceClient treeSvcClient = GrpcServiceClient.builder()
                .clientServiceDescriptor(svcDesc)
                .channel(ch)
                .callOptions(CallOptions.DEFAULT)
                .build();

        AccumulatingResponseStreamObserverAdapter<Person> persons = new AccumulatingResponseStreamObserverAdapter<>();
        StreamObserver<Integer> ids = treeSvcClient.bidiStreaming("persons", persons);
        for (int id : new int[] {3, 4, 5}) {
            ids.onNext(id);
        }
        ids.onCompleted();

        persons.waitForCompletion();

        assertThat(persons.getResult(), equalTo(Arrays.asList(
                TreeMapService.ARAGON, TreeMapService.GALARDRIAL, TreeMapService.GANDALF
        )));
    }

}
