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

package io.helidon.microprofile.grpc.client;

import java.util.ArrayList;
import java.util.List;
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

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @BeforeEach
    void updatePort() {
        if (proxyClient instanceof GrpcConfigurablePort client) {
            client.channelPort(webTarget.getUri().getPort());
        }
    }

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
    void testEchoInject() {
        String result = proxyClient.echo("Howdy");
        assertThat(result, is("Howdy"));
    }

    @Test
    void testEchoMany() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        List<String> values = new ArrayList<>();
        StreamObserver<String> observer = new StreamObserver<>() {
            @Override
            public void onNext(String value) {
                values.add(value);
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(values);
            }
        };
        proxyClient.echoMany("Howdy", observer);
        assertThat(future.get(5, TimeUnit.SECONDS), is(List.of("Howdy", "Howdy", "Howdy")));
    }

    @Test
    void testEchoError() {
        assertThrows(StatusRuntimeException.class, () -> proxyClient.echo("[FAIL]"));
    }

    @Test
    void testEchoManyError() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        StreamObserver<String> observer = new StreamObserver<>() {
            @Override
            public void onNext(String value) {
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
            }
        };
        proxyClient.echoMany("[FAIL]", observer);
        assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    @Grpc.GrpcService
    @Grpc.GrpcMarshaller("java")
    public static class EchoService {

        @Grpc.Unary("Echo")
        public String echo(String request) {
            if (request.equals("[FAIL]")) {
                throw new RuntimeException("Method call failed");
            }
            return request;
        }

        @Grpc.ServerStreaming("EchoMany")
        public void echoMany(String request, StreamObserver<String> observer) {
            try {
                if (request.equals("[FAIL]")) {
                    throw new RuntimeException("Method call failed");
                }
                observer.onNext(request);
                observer.onNext(request);
                observer.onNext(request);
                observer.onCompleted();
            }  catch (Exception e) {
                observer.onError(e);
            }
        }
    }

    @Grpc.GrpcService("EchoService")
    @Grpc.GrpcMarshaller("java")
    @Grpc.GrpcChannel("echo-channel")
    public interface EchoServiceClient {

        @Grpc.Unary("Echo")
        String echo(String request);

        @Grpc.ServerStreaming("EchoMany")
        void echoMany(String request, StreamObserver<String> observer);
    }
}
