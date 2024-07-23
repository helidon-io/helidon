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

package io.helidon.microprofile.grpc.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.grpc.api.Grpc;
import io.helidon.microprofile.grpc.server.GrpcMpCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webclient.grpc.GrpcClient;

import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(EchoServiceTest.EchoService.class)
@AddBean(JavaMarshaller.Supplier.class)
@AddExtension(GrpcMpCdiExtension.class)
@AddExtension(GrpcClientCdiExtension.class)
class EchoServiceTest {

    @Inject
    private WebTarget webTarget;

    @Inject
    @Grpc.GrpcProxy
    private EchoServiceClient proxyClient;

    @Test
    void testEcho() throws InterruptedException, ExecutionException, TimeoutException {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        GrpcClient grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .baseUri("https://localhost:" + webTarget.getUri().getPort())
                .build();

        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(EchoService.class)
                .name("EchoService")
                .marshallerSupplier(new JavaMarshaller.Supplier())
                .unary("Echo")
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        GrpcServiceClient client = GrpcServiceClient.create(grpcClient.channel(), descriptor);
        StreamObserver<String> observer = new StreamObserver<>() {
            @Override
            public void onNext(String value) {
                future.complete(value);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        };
        client.unary("Echo", "Howdy", observer);
        assertThat(future.get(5, TimeUnit.SECONDS), is("Howdy"));
    }

    @Test
    void testEchoInject() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<String> future = new CompletableFuture<>();
        StreamObserver<String> observer = new StreamObserver<>() {
            @Override
            public void onNext(String value) {
                future.complete(value);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        };
        proxyClient.echo("Howdy", observer);
        assertThat(future.get(5, TimeUnit.SECONDS), is("Howdy"));
    }

    @Grpc.GrpcService
    @Grpc.GrpcMarshaller("java")
    public static class EchoService {

        @Grpc.Unary("Echo")
        public void echo(String request, StreamObserver<String> observer) {
            try {
                complete(observer, request);
            } catch (IllegalStateException e) {
                observer.onError(e);
            }
        }
    }

    @Grpc.GrpcService("EchoService")
    @Grpc.GrpcMarshaller("java")
    @Grpc.GrpcChannel(value = "echo-channel")
    public interface EchoServiceClient {

        @Grpc.Unary("Echo")
        void echo(String request, StreamObserver<String> observer);
    }
}
