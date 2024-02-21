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

package io.helidon.examples.webserver.protocols;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.grpc.stub.StreamObserver;
import io.helidon.examples.grpc.strings.StringServiceGrpc;
import io.helidon.examples.grpc.strings.Strings;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GrpcStubTest {
    private static final long TIMEOUT_SECONDS = 10;

    private final WebClient webClient;

    private GrpcStubTest(WebClient webClient) {
        this.webClient = webClient;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder builder) {
        builder.addRouting(GrpcRouting.builder()
                .unary(Strings.getDescriptor(),
                        "StringService",
                        "Upper",
                        GrpcStubTest::upper)
                .serverStream(Strings.getDescriptor(),
                        "StringService",
                        "Split",
                        GrpcStubTest::split)
                .clientStream(Strings.getDescriptor(),
                        "StringService",
                        "Join",
                        GrpcStubTest::join)
                .bidi(Strings.getDescriptor(),
                        "StringService",
                        "Echo",
                        GrpcStubTest::echo));
    }

    // -- gRPC server methods --

    private static Strings.StringMessage upper(Strings.StringMessage reqT) {
        return Strings.StringMessage.newBuilder()
                .setText(reqT.getText().toUpperCase(Locale.ROOT))
                .build();
    }

    private static void split(Strings.StringMessage reqT,
                              StreamObserver<Strings.StringMessage> streamObserver) {
        String[] strings = reqT.getText().split(" ");
        for (String string : strings) {
            streamObserver.onNext(Strings.StringMessage.newBuilder()
                    .setText(string)
                    .build());

        }
        streamObserver.onCompleted();
    }

    private static StreamObserver<Strings.StringMessage> join(StreamObserver<Strings.StringMessage> streamObserver) {
        return new StreamObserver<>() {
            private StringBuilder builder;

            @Override
            public void onNext(Strings.StringMessage value) {
                if (builder == null) {
                    builder = new StringBuilder();
                    builder.append(value.getText());
                } else {
                    builder.append(" ").append(value.getText());
                }
            }

            @Override
            public void onError(Throwable t) {
                streamObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                streamObserver.onNext(Strings.StringMessage.newBuilder()
                        .setText(builder.toString())
                        .build());
                streamObserver.onCompleted();
            }
        };
    }

    private static StreamObserver<Strings.StringMessage> echo(StreamObserver<Strings.StringMessage> streamObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(Strings.StringMessage value) {
                streamObserver.onNext(value);
            }

            @Override
            public void onError(Throwable t) {
                streamObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                streamObserver.onCompleted();
            }
        };
    }

    // -- Tests --

    // @Test -- blocks indefinitely
    void testUnaryUpper() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        Strings.StringMessage res = service.upper(newStringMessage("hello"));
        assertThat(res.getText(), is("HELLO"));
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

    // -- Utility methods --

    private Strings.StringMessage newStringMessage(String data) {
        return Strings.StringMessage.newBuilder().setText(data).build();
    }

    private static <ReqT> StreamObserver<ReqT> singleStreamObserver(CompletableFuture<ReqT> future) {
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

    private static <ResT> StreamObserver<ResT> multiStreamObserver(CompletableFuture<Iterator<ResT>> future) {
        return new StreamObserver<>() {
            private final List<ResT> value = new ArrayList<>();

            @Override
            public void onNext(ResT value) {
                this.value.add(value);
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(value.iterator());
            }
        };
    }
}
