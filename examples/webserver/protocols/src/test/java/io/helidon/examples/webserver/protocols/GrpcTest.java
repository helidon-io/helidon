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
                                .requestType(StringValue.class)
                                .responseType(StringValue.class)
                                .build())
                .putMethod("Split",
                        GrpcClientMethodDescriptor.serverStreaming("StringService", "Split")
                                .requestType(StringValue.class)
                                .responseType(StringValue.class)
                                .build())
                .putMethod("Join",
                        GrpcClientMethodDescriptor.clientStreaming("StringService", "Join")
                                .requestType(StringValue.class)
                                .responseType(StringValue.class)
                                .build())
                .putMethod("Echo",
                        GrpcClientMethodDescriptor.bidirectional("StringService", "Echo")
                                .requestType(StringValue.class)
                                .responseType(StringValue.class)
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
        StringValue res = grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper", StringValue.of("hello"));
        assertThat(res.getValue(), is("HELLO"));
    }

    @Test
    void testUnaryUpperAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<StringValue> future = new CompletableFuture<>();
        grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper",
                        StringValue.of("hello"),
                        singleStreamObserver(future));
        StringValue res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.getValue(), is("HELLO"));
    }

    @Test
    void testServerStreamingSplit() {
        Iterator<StringValue> res = grpcClient.serviceClient(serviceDescriptor)
                .serverStream("Split",
                               StringValue.of("hello world"));
        assertThat(res.next().getValue(), is("hello"));
        assertThat(res.next().getValue(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testServerStreamingSplitAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Iterator<StringValue>> future = new CompletableFuture<>();
        grpcClient.serviceClient(serviceDescriptor)
                .serverStream("Split",
                        StringValue.of("hello world"),
                        multiStreamObserver(future));
        Iterator<StringValue> res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.next().getValue(), is("hello"));
        assertThat(res.next().getValue(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testClientStreamingJoin() {
        StringValue res = grpcClient.serviceClient(serviceDescriptor)
                .clientStream("Join", List.of(StringValue.of("hello"),
                        StringValue.of("world")).iterator());
        assertThat(res.getValue(), is("hello world"));
    }

    @Test
    void testClientStreamingJoinAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<StringValue> future = new CompletableFuture<>();
        StreamObserver<StringValue> req = grpcClient.serviceClient(serviceDescriptor)
                .clientStream("Join", singleStreamObserver(future));
        req.onNext(StringValue.of("hello"));
        req.onNext(StringValue.of("world"));
        req.onCompleted();
        StringValue res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.getValue(), is("hello world"));
    }

    @Test
    void testBidirectionalEcho() {
        Iterator<StringValue> res = grpcClient.serviceClient(serviceDescriptor)
                .bidi("Echo", List.of(StringValue.of("hello"),
                        StringValue.of("world")).iterator());
        assertThat(res.next().getValue(), is("hello"));
        assertThat(res.next().getValue(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    @Test
    void testBidirectionalEchoAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Iterator<StringValue>> future = new CompletableFuture<>();
        StreamObserver<StringValue> req = grpcClient.serviceClient(serviceDescriptor)
                .bidi("Echo", multiStreamObserver(future));
        req.onNext(StringValue.of("hello"));
        req.onNext(StringValue.of("world"));
        req.onCompleted();
        Iterator<StringValue> res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(res.next().getValue(), is("hello"));
        assertThat(res.next().getValue(), is("world"));
        assertThat(res.hasNext(), is(false));
    }

    // -- Utility methods --

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
