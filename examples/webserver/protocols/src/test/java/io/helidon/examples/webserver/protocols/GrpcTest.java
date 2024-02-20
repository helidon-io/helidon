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

import com.google.protobuf.StringValue;
import io.grpc.stub.StreamObserver;
import io.helidon.examples.grpc.strings.Strings;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GrpcTest {
    private static final long TIMEOUT_SECONDS = 10;

    private final GrpcClient grpcClient;
    private final GrpcServiceDescriptor serviceDescriptor;

    private GrpcTest(GrpcClient grpcClient) {
        this.grpcClient = grpcClient;
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

    @SetUpServer
    public static void setup(WebServerConfig.Builder builder) {
        builder.addRouting(GrpcRouting.builder()
                .unary(Strings.getDescriptor(),
                        "StringService",
                        "Upper",
                        GrpcTest::upper)
                .serverStream(Strings.getDescriptor(),
                        "StringService",
                        "Split",
                        GrpcTest::split)
                .clientStream(Strings.getDescriptor(),
                        "StringService",
                        "Join",
                        GrpcTest::join)
                .bidi(Strings.getDescriptor(),
                        "StringService",
                        "Echo",
                        GrpcTest::echo));
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
