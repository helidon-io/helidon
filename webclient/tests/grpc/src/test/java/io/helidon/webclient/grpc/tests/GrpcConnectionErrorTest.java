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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.grpc.stub.StreamObserver;
import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests server connection problems, such as a disappearing server.
 */
@ServerTest
class GrpcConnectionErrorTest extends GrpcBaseTest {
    private static final long TIMEOUT_SECONDS = 1000;

    private final WebServer server;
    private final GrpcClient grpcClient;
    private final GrpcServiceDescriptor serviceDescriptor;

    private GrpcConnectionErrorTest(WebServer server) {
        this.server = server;
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        GrpcClientProtocolConfig config = GrpcClientProtocolConfig.builder()
                .pollWaitTime(Duration.ofSeconds(2))    // detects connection issues
                .abortPollTimeExpired(false)            // checks health with PING
                .build();
        this.grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .protocolConfig(config)
                .baseUri("https://localhost:" + server.port())
                .build();
        this.serviceDescriptor = GrpcServiceDescriptor.builder()
                .serviceName("StringService")
                .putMethod("Join",
                        GrpcClientMethodDescriptor.clientStreaming("StringService", "Join")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .build();
    }

    @Test
    void testClientStreamingWithError() throws InterruptedException {
        CompletableFuture<Strings.StringMessage> future = new CompletableFuture<>();
        StreamObserver<Strings.StringMessage> req = grpcClient.serviceClient(serviceDescriptor)
                .clientStream("Join", singleStreamObserver(future));
        req.onNext(newStringMessage("hello"));
        server.stop();      // kill server!
        assertEventually(future::isCompletedExceptionally, TIMEOUT_SECONDS * 1000);
    }

    private static void assertEventually(Supplier<Boolean> predicate, long millis) throws InterruptedException {
        long start = System.currentTimeMillis();
        do {
            if (predicate.get()) {
                return;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() - start <= millis);
        fail("Predicate failed after " + millis + " milliseconds");
    }
}
