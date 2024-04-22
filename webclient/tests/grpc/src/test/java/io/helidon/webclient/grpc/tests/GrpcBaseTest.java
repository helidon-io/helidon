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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import io.helidon.common.Weight;
import io.helidon.common.configurable.Resource;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import org.junit.jupiter.api.BeforeEach;

class GrpcBaseTest {

    private final List<Class<?>> calledInterceptors = new CopyOnWriteArrayList<>();

    protected List<Class<?>> calledInterceptors() {
        return calledInterceptors;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder builder) {
        builder.tls(tls -> tls.privateKey(key -> key
                        .keystore(store -> store
                                .passphrase("password")
                                .keystore(Resource.create("server.p12"))))
                .privateKeyCertChain(key -> key
                        .keystore(store -> store
                                .trustStore(true)
                                .passphrase("password")
                                .keystore(Resource.create("server.p12")))));
    }

    @SetUpRoute
    static void setUpRoute(GrpcRouting.Builder routing) {
        routing.unary(Strings.getDescriptor(),
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
                        GrpcStubTest::echo);
    }

    @BeforeEach
    void setUpTest() {
        calledInterceptors.clear();
    }

    static void upper(Strings.StringMessage req,
                              StreamObserver<Strings.StringMessage> streamObserver) {
        Strings.StringMessage msg = Strings.StringMessage.newBuilder()
                .setText(req.getText().toUpperCase(Locale.ROOT))
                .build();
        streamObserver.onNext(msg);
        streamObserver.onCompleted();
    }

    static void split(Strings.StringMessage req,
                              StreamObserver<Strings.StringMessage> streamObserver) {
        String reqString = req.getText();
        if (!reqString.isEmpty()) {
            String[] strings = reqString.split(" ");
            for (String s : strings) {
                streamObserver.onNext(Strings.StringMessage.newBuilder()
                        .setText(s)
                        .build());

            }
        }
        streamObserver.onCompleted();
    }

    static StreamObserver<Strings.StringMessage> join(StreamObserver<Strings.StringMessage> streamObserver) {
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

    static StreamObserver<Strings.StringMessage> echo(StreamObserver<Strings.StringMessage> streamObserver) {
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

    Strings.StringMessage newStringMessage(String data) {
        return Strings.StringMessage.newBuilder().setText(data).build();
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

    static <ResT> StreamObserver<ResT> multiStreamObserver(CompletableFuture<Iterator<ResT>> future) {
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

    class BaseInterceptor implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                   CallOptions callOptions,
                                                                   Channel next) {
            calledInterceptors.add(getClass());
            return next.newCall(method, callOptions);
        }
    }

    @Weight(10.0)
    class Weight10Interceptor extends BaseInterceptor {
    }

    @Weight(50.0)
    class Weight50Interceptor extends BaseInterceptor {
    }

    @Weight(100.0)
    class Weight100Interceptor extends BaseInterceptor {
    }

    @Weight(500.0)
    class Weight500Interceptor extends BaseInterceptor {
    }

    @Weight(1000.0)
    class Weight1000Interceptor extends BaseInterceptor {
    }

    List<ClientInterceptor> allInterceptors() {
        return List.of(new Weight10Interceptor(),
                       new Weight50Interceptor(),
                       new Weight100Interceptor(),
                       new Weight500Interceptor(),
                       new Weight1000Interceptor());
    }
}
