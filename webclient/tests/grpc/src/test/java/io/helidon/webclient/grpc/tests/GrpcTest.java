/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc.tests;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;
import com.google.protobuf.StringValue;
import io.grpc.stub.StreamObserver;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests gRPC client using low-level API and TLS, no stubs.
 */
@ServerTest
class GrpcTest extends GrpcBaseTest {
    private static final long TIMEOUT_SECONDS = 10;

    private final GrpcClient grpcClient;

    private final GrpcServiceDescriptor serviceDescriptor;

    private GrpcTest(WebServer server) {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        this.grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .baseUri("https://localhost:" + server.port())
                .build();

        this.serviceDescriptor = GrpcServiceDescriptor.builder()
                .serviceName("StringService")
                .putMethod("Upper",
                        GrpcClientMethodDescriptor.unary("StringService", "Upper")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("Split",
                        GrpcClientMethodDescriptor.serverStreaming("StringService", "Split")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("Join",
                        GrpcClientMethodDescriptor.clientStreaming("StringService", "Join")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .putMethod("Echo",
                        GrpcClientMethodDescriptor.bidirectional("StringService", "Echo")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .build();
    }

    @Test
    void testUnaryUpper() {
        Strings.StringMessage res = grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper", newStringMessage("hello"));
        assertThat(res.getText(), is("HELLO"));
    }

    @Test
    void testUnaryUpperAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Strings.StringMessage> future = new CompletableFuture<>();
        grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper",
                        StringValue.of("hello"),
                        singleStreamObserver(future));
        Strings.StringMessage res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.getText(), is("HELLO"));
    }

    @Test
    void testServerStreamingSplit() {
        Iterator<Strings.StringMessage> res = grpcClient.serviceClient(serviceDescriptor)
                .serverStream("Split",
                        newStringMessage("hello world"));
        assertThat(res.next().getText(), is("hello"));
        assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testServerStreamingSplitEmpty() {
        Iterator<Strings.StringMessage> res = grpcClient.serviceClient(serviceDescriptor)
                .serverStream("Split", newStringMessage(""));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testServerStreamingSplitAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Iterator<Strings.StringMessage>> future = new CompletableFuture<>();
        grpcClient.serviceClient(serviceDescriptor)
                .serverStream("Split",
                        newStringMessage("hello world"),
                        multiStreamObserver(future));
        Iterator<Strings.StringMessage> res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.next().getText(), is("hello"));
        assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testClientStreamingJoin() {
        Strings.StringMessage res = grpcClient.serviceClient(serviceDescriptor)
                .clientStream("Join", List.of(newStringMessage("hello"),
                        newStringMessage("world")).iterator());
        assertThat(res.getText(), is("hello world"));
    }

    @Test
    void testClientStreamingJoinAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Strings.StringMessage> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = grpcClient.serviceClient(serviceDescriptor)
                .clientStream("Join", singleStreamObserver(future));
        req.onNext(newStringMessage("hello"));
        req.onNext(newStringMessage("world"));
        req.onCompleted();
        Strings.StringMessage res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.getText(), is("hello world"));
    }

    @Test
    void testBidirectionalEcho() {
        Iterator<Strings.StringMessage> res = grpcClient.serviceClient(serviceDescriptor)
                .bidi("Echo", List.of(newStringMessage("hello"),
                        newStringMessage("world")).iterator());
        assertThat(res.next().getText(), is("hello"));
        assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testBidirectionalEchoAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Iterator<Strings.StringMessage>> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = grpcClient.serviceClient(serviceDescriptor)
                .bidi("Echo", multiStreamObserver(future));
        req.onNext(newStringMessage("hello"));
        req.onNext(newStringMessage("world"));
        req.onCompleted();
        Iterator<Strings.StringMessage> res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.next().getText(), is("hello"));
        assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }
}
