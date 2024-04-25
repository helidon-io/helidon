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

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.grpc.stub.StreamObserver;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests gRPC client using stubs and TLS.
 */
@ServerTest
class GrpcStubTest extends GrpcBaseTest {
    private static final long TIMEOUT_SECONDS = 10;

    private final WebClient webClient;

    private GrpcStubTest(WebServer server) {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        this.webClient = WebClient.builder()
                .tls(clientTls)
                .baseUri("https://localhost:" + server.port())
                .build();
    }

    @Test
    void testUnaryUpper() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        Strings.StringMessage res = service.upper(newStringMessage("hello"));
        assertThat(res.getText(), is("HELLO"));
    }

    @Test
    void tesUnaryUpperLongString() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        String s = CharBuffer.allocate(2000).toString().replace('\0', 'a');
        Strings.StringMessage res = service.upper(newStringMessage(s));
        assertThat(res.getText(), is(s.toUpperCase()));
    }

    @Test
    void testUnaryUpperAsync() throws ExecutionException, InterruptedException, TimeoutException {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceStub service = StringServiceGrpc.newStub(grpcClient.channel());
        CompletableFuture<Strings.StringMessage> future = new CompletableFuture<>();
        service.upper(newStringMessage("hello"), singleStreamObserver(future));
        Strings.StringMessage res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.getText(), is("HELLO"));
    }

    @Test
    void testServerStreamingSplit() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        Iterator<Strings.StringMessage> res = service.split(newStringMessage("hello world"));
        assertThat(res.next().getText(), is("hello"));
        assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testServerStreamingSplitEmpty() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        Iterator<Strings.StringMessage> res = service.split(newStringMessage(""));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testServerStreamingSplitAsync() throws ExecutionException, InterruptedException, TimeoutException {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceStub service = StringServiceGrpc.newStub(grpcClient.channel());
        CompletableFuture<Iterator<Strings.StringMessage>> future = new CompletableFuture<>();
        service.split(newStringMessage("hello world"), multiStreamObserver(future));
        Iterator<Strings.StringMessage> res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.next().getText(), is("hello"));
        assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testClientStreamingJoinAsync() throws ExecutionException, InterruptedException, TimeoutException {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceStub service = StringServiceGrpc.newStub(grpcClient.channel());
        CompletableFuture<Strings.StringMessage> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = service.join(singleStreamObserver(future));
        req.onNext(newStringMessage("hello"));
        req.onNext(newStringMessage("world"));
        req.onCompleted();
        Strings.StringMessage res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.getText(), is("hello world"));
    }

    @Test
    void testBidirectionalEchoAsync() throws ExecutionException, InterruptedException, TimeoutException {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceStub service = StringServiceGrpc.newStub(grpcClient.channel());
        CompletableFuture<Iterator<Strings.StringMessage>> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = service.echo(multiStreamObserver(future));
        req.onNext(newStringMessage("hello"));
        req.onNext(newStringMessage("world"));
        req.onCompleted();
        Iterator<Strings.StringMessage> res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.next().getText(), is("hello"));
        assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testBidirectionalEchoAsyncEmpty() throws ExecutionException, InterruptedException, TimeoutException {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceStub service = StringServiceGrpc.newStub(grpcClient.channel());
        CompletableFuture<Iterator<Strings.StringMessage>> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = service.echo(multiStreamObserver(future));
        req.onCompleted();
        Iterator<Strings.StringMessage> res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testBidirectionalEchoAsyncWithLargePayload() throws ExecutionException, InterruptedException, TimeoutException {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceStub service = StringServiceGrpc.newStub(grpcClient.channel());
        CompletableFuture<Iterator<Strings.StringMessage>> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = service.echo(multiStreamObserver(future));
        byte[] array = new byte[2000];
        new Random().nextBytes(array);
        String largeString = new String(array, StandardCharsets.UTF_8);
        req.onNext(newStringMessage(largeString));
        req.onCompleted();
        Iterator<Strings.StringMessage> res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.next().getText(), is(largeString));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testReceiveServerException() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        Assertions.assertThrows(Throwable.class, () -> service.badMethod(newStringMessage("hello")));
    }

    @Test
    void testCallingNotImplementMethodThrowsException() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        Assertions.assertThrows(Throwable.class, () -> service.notImplementedMethod(newStringMessage("hello")));
    }
}
