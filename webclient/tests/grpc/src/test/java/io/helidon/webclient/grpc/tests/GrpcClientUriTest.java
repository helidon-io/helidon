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

package io.helidon.webclient.grpc.tests;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.faulttolerance.Retry;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.grpc.ClientUriSuppliers;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests client URI suppliers.
 */
@ServerTest
class GrpcClientUriTest extends GrpcBaseTest {

    private final WebServer server;
    private final Tls clientTls;

    GrpcClientUriTest(WebServer server) {
        this.server = server;
        this.clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }

    /**
     * Shows how each gRPC call advances the iterator in the {@code ClientUriSupplier}.
     */
    @Test
    void testSupplier() {
        CountDownLatch latch = new CountDownLatch(2);
        ClientUri clientUri = ClientUri.create(URI.create("https://localhost:" + server.port()));
        GrpcClient grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .clientUriSupplier(new ClientUriSupplierTest(latch, clientUri, clientUri))
                .build();
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());

        Strings.StringMessage res1 = service.upper(newStringMessage("hello"));
        assertThat(res1.getText(), is("HELLO"));
        Strings.StringMessage res2 = service.upper(newStringMessage("hello"));
        assertThat(res2.getText(), is("HELLO"));
        assertThat(latch.getCount(), is(0L));
    }

    /**
     * Should fail to connect to first URI but succeed with second after retrying.
     */
    @Test
    void testSupplierWithRetries() {
        CountDownLatch latch = new CountDownLatch(2);
        ClientUri badUri = ClientUri.create(URI.create("https://foo:8000"));
        ClientUri goodUri = ClientUri.create(URI.create("https://localhost:" + server.port()));
        GrpcClient grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .clientUriSupplier(new ClientUriSupplierTest(latch, badUri, goodUri))
                .build();
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());

        Retry retry = Retry.builder()
                .overallTimeout(Duration.ofMillis(5000))
                .calls(2)
                .build();
        Strings.StringMessage res = retry.invoke(() -> service.upper(newStringMessage("hello")));
        assertThat(res.getText(), is("HELLO"));
        assertThat(latch.getCount(), is(0L));
    }

    static class ClientUriSupplierTest extends ClientUriSuppliers.RoundRobinSupplier {

        private final CountDownLatch latch;

        ClientUriSupplierTest(CountDownLatch latch, ClientUri... clientUris) {
            super(clientUris);
            this.latch = latch;
        }

        @Override
        public ClientUri next() {
            latch.countDown();      // should be called twice
            return super.next();
        }
    }
}
