/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.common.LogConfig;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class PojoServiceClientIT {

    private static GrpcServer grpcServer;

    private static Channel channel;

    @BeforeAll
    public static void startServer() throws Exception {

        LogConfig.configureRuntime();

        GrpcRouting routing = GrpcRouting.builder()
                .register(new TreeMapService())
                .register(new StringService())
                .build();

        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(0).build();

        grpcServer = GrpcServer.create(serverConfig, routing)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port()).usePlaintext().build();
    }

    @AfterAll
    public static void shutdownGrpcServer() {
        grpcServer.shutdown();
    }

    @Test
    public void testCreateAndInvokeAsyncUnary() throws Exception {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .unary("get")
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);

        assertThat(client.unary("get", 1).toCompletableFuture().get(), equalTo(TreeMapService.BILBO));
    }

    @Test
    public void testCreateAndInvokeUnary() {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .unary("get")
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);
        TestStreamObserver<Person> observer = new TestStreamObserver<>();
        client.unary("get", 1, observer);

        assertThat(observer.awaitTerminalEvent(10, TimeUnit.SECONDS), is(true));
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(1)
                .assertValue(TreeMapService.BILBO);
    }

    @Test
    public void testCreateAndInvokeServerStreamingMethod() {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .serverStreaming("greaterOrEqualTo")
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);

        TestStreamObserver<Person> observer = new TestStreamObserver<>();

        client.serverStreaming("greaterOrEqualTo", 3, observer);

        assertThat(observer.awaitTerminalEvent(10, TimeUnit.SECONDS), is(true));
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(3);

        List<Integer> ids = observer.values()
                .stream()
                .map(Person::getId)
                .collect(Collectors.toList());

        assertThat(ids, equalTo(Arrays.asList(3, 4, 5)));
    }

    @Test
    public void testCreateAndInvokeClientStreamingMethod() throws ExecutionException, InterruptedException {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .clientStreaming("sumOfAges")
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);

        CompletionStage<Integer> sum = client.clientStreaming("sumOfAges", Arrays.asList(3, 4, 5));

        int expected = TreeMapService.ARAGON.getAge() + TreeMapService.GALARDRIEL.getAge() + TreeMapService.GANDALF.getAge();
        assertThat(sum.toCompletableFuture().get(), is(expected));
    }

    @Test
    public void testCreateAndInvokeObservableClientStreamingMethod() {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .clientStreaming("sumOfAges")
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);
        TestStreamObserver<Integer> observer = new TestStreamObserver<>();

        StreamObserver<Object> requestObserver = client.clientStreaming("sumOfAges", observer);
        requestObserver.onNext(3);
        requestObserver.onNext(4);
        requestObserver.onNext(5);
        requestObserver.onCompleted();

        int expected = TreeMapService.ARAGON.getAge() + TreeMapService.GALARDRIEL.getAge() + TreeMapService.GANDALF.getAge();

        assertThat(observer.awaitTerminalEvent(10, TimeUnit.SECONDS), is(true));
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(1)
                .assertValue(expected);
    }

    @Test
    public void testCreateAndInvokeBidiStreamingMethod() {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .bidirectional("persons")
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(channel, descriptor);

        TestStreamObserver<Person> observer = new TestStreamObserver<>();
        StreamObserver<Integer> requestObserver = client.bidiStreaming("persons", observer);

        requestObserver.onNext(3);
        requestObserver.onNext(4);
        requestObserver.onNext(5);
        requestObserver.onCompleted();

        assertThat(observer.awaitTerminalEvent(10, TimeUnit.SECONDS), is(true));
        observer.assertComplete()
                .assertNoErrors()
                .assertValueCount(3);

        assertThat(observer.values(), contains(TreeMapService.ARAGON, TreeMapService.GALARDRIEL, TreeMapService.GANDALF));
    }

}
