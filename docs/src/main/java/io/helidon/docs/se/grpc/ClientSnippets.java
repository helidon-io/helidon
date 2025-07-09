/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se.grpc;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.ClientUriSupplier;
import io.helidon.webclient.grpc.ClientUriSuppliers.RoundRobinSupplier;

import com.google.protobuf.Descriptors;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import io.grpc.ClientInterceptor;

import static io.helidon.grpc.core.ResponseHelper.complete;

@SuppressWarnings("ALL")
class ClientSnippets {

    class StringServiceGrpc {

        class StringServiceBlockingStub {
            Strings.StringMessage upper(Strings.StringMessage msg) {
                return null;
            }
            Iterator<Strings.StringMessage> split(Strings.StringMessage msg) {
                return null;
            }
            void split(Strings.StringMessage msg, StreamObserver<?> observer) {
            }
        }

        static StringServiceGrpc.StringServiceBlockingStub newBlockingStub(Channel c) {
            return null;
        }
    }

    class Strings {

        class StringMessage {
            String getText() {
                return null;
            }
        }
    }

    Strings.StringMessage newMessage(String s) {
        return null;
    }

    ClientUri[] myServers() {
        return null;
    }

    List<ClientInterceptor> myInterceptors() {
        return null;
    }

    void snippets() {
        // tag::snippet_1[]
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        // end::snippet_1[]

        // tag::snippet_2[]
        WebClient webClient = WebClient.builder()
                .tls(clientTls)
                .baseUri("https://localhost:8080")
                .build();
        // end::snippet_2[]

        // tag::snippet_3[]
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        StringServiceGrpc.StringServiceBlockingStub service =
                StringServiceGrpc.newBlockingStub(grpcClient.channel());
        // end::snippet_3[]

        // tag::snippet_4[]
        Strings.StringMessage msg1 = newMessage("hello");
        Strings.StringMessage res1 = service.upper(msg1);
        String uppercased = res1.getText();
        // end::snippet_4[]

        // tag::snippet_5[]
        Strings.StringMessage msg2 = newMessage("hello world");
        Iterator<Strings.StringMessage> res2 = service.split(msg2);
        while (res2.hasNext()) {
            // ...
        }
        // end::snippet_5[]

        // tag::snippet_6[]
        Strings.StringMessage msg3 = newMessage("hello world");
        CompletableFuture<Iterator<Strings.StringMessage>> future = new CompletableFuture<>();
        service.split(msg3, new StreamObserver<Strings.StringMessage>() {
            private final List<Strings.StringMessage> value = new ArrayList<>();

            @Override
            public void onNext(Strings.StringMessage value) {
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
        });
        // end::snippet_6[]

        // tag::snippet_7[]
        GrpcServiceDescriptor serviceDescriptor = GrpcServiceDescriptor.builder()
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
                .build();
        // end::snippet_7[]

        // tag::snippet_8[]
        Strings.StringMessage res = grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper", newMessage("hello"));
        // end::snippet_8[]

    }

    void snippets2() {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();

        // tag::snippet_9[]
        GrpcClient grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .clientUriSupplier(RoundRobinSupplier.create(myServers()))
                .build();
        // end::snippet_9[]

        // tag::snippet_10[]
        Channel newChannel = grpcClient.channel(myInterceptors());
        // end::snippet_10[]
    }

    void snippets3() {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();

        // tag::snippet_11[]
        GrpcClient grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .baseUri("https://localhost:8080")
                .enableMetrics(true)        // enables metrics
                .build();
        // end::snippet_11[]

    }

    void snippets4() {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();

        // tag::snippet_12[]
        Config config = Config.create().get("grpc-client");
        GrpcClient grpcClient = GrpcClient.builder()
                .config(config)     // with tracing
                .tls(clientTls)
                .baseUri("https://localhost:8080")
                .build();
        // end::snippet_12[]
    }
}
