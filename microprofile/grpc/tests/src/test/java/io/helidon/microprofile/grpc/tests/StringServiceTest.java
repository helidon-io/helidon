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

package io.helidon.microprofile.grpc.tests;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.grpc.api.Grpc;
import io.helidon.grpc.core.CollectingObserver;
import io.helidon.grpc.core.ResponseHelper;
import io.helidon.microprofile.grpc.tests.test.StringServiceGrpc;
import io.helidon.microprofile.grpc.tests.test.Strings;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StringServiceTest extends BaseServiceTest {
    private static final long TIMEOUT_SECONDS = 10;

    @Inject
    public StringServiceTest(WebTarget webTarget) {
        super(webTarget);
    }

    @Test
    void testUnaryUpper() {
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient().channel());
        Strings.StringMessage res = service.upper(newStringMessage("hello"));
        MatcherAssert.assertThat(res.getText(), is("HELLO"));
    }

    @Test
    void testUnaryLower() {
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient().channel());
        Strings.StringMessage res = service.lower(newStringMessage("HELLO"));
        MatcherAssert.assertThat(res.getText(), is("hello"));
    }

    @Test
    void testServerStreamingSplit() {
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient().channel());
        Iterator<Strings.StringMessage> res = service.split(newStringMessage("hello world"));
        MatcherAssert.assertThat(res.next().getText(), is("hello"));
        MatcherAssert.assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testClientStreamingJoinAsync() throws ExecutionException, InterruptedException, TimeoutException {
        StringServiceGrpc.StringServiceStub service = StringServiceGrpc.newStub(grpcClient().channel());
        CompletableFuture<Strings.StringMessage> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = service.join(singleStreamObserver(future));
        req.onNext(newStringMessage("hello"));
        req.onNext(newStringMessage("world"));
        req.onCompleted();
        Strings.StringMessage res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(res.getText(), is("hello world"));
    }

    @Test
    void testBidirectionalEcho() throws ExecutionException, InterruptedException, TimeoutException {
        StringServiceGrpc.StringServiceStub service = StringServiceGrpc.newStub(grpcClient().channel());
        CompletableFuture<Strings.StringMessage> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = service.echo(singleStreamObserver(future));
        req.onNext(newStringMessage("hello"));
        req.onCompleted();
        Strings.StringMessage res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(res.getText(), is("hello"));
    }

    Strings.StringMessage newStringMessage(String data) {
        return Strings.StringMessage.newBuilder().setText(data).build();
    }

    @Grpc.GrpcService
    @ApplicationScoped
    public static class StringService {

        @Grpc.Unary("Upper")
        public void upper(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
            ResponseHelper.complete(observer, response(request.getText().toUpperCase()));
        }

        @Grpc.Unary("Lower")
        public void lower(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
            ResponseHelper.complete(observer, response(request.getText().toLowerCase()));
        }

        @Grpc.ServerStreaming("Split")
        public void split(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
            String[] parts = request.getText().split(" ");
            ResponseHelper.stream(observer, Stream.of(parts).map(this::response));
        }

        @Grpc.ClientStreaming("Join")
        public StreamObserver<Strings.StringMessage> join(StreamObserver<Strings.StringMessage> observer) {
            return CollectingObserver.create(
                    Collectors.joining(" "),
                    observer,
                    Strings.StringMessage::getText,
                    this::response);
        }

        @Grpc.Bidirectional("Echo")
        public StreamObserver<Strings.StringMessage> echo(StreamObserver<Strings.StringMessage> observer) {
            return new StreamObserver<>() {
                public void onNext(Strings.StringMessage value) {
                    observer.onNext(value);
                }

                public void onError(Throwable t) {
                }

                public void onCompleted() {
                    observer.onCompleted();
                }
            };
        }

        private Strings.StringMessage response(String text) {
            return Strings.StringMessage.newBuilder().setText(text).build();
        }
    }

    static <ReqT> StreamObserver<ReqT> singleStreamObserver(CompletableFuture<ReqT> future) {
        return new StreamObserver<>() {
            private ReqT value;

            @Override
            public void onNext(ReqT value) {
                this.value = value;
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(value);
            }
        };
    }
}
