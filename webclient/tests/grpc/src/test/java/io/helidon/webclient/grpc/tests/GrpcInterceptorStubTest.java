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

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import io.grpc.Channel;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Tests gRPC client using stubs and TLS.
 */
@ServerTest
class GrpcInterceptorStubTest extends GrpcBaseTest {

    private final WebClient webClient;

    private GrpcInterceptorStubTest(WebServer server) {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        this.webClient = WebClient.builder()
                .tls(clientTls)
                .baseUri("https://localhost:" + server.port())
                .build();
    }

    @Test
    void testUnaryUpper() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        Channel channel = grpcClient.channel(allInterceptors());        // channel with all interceptors
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(channel);
        Strings.StringMessage res = service.upper(newStringMessage("hello"));
        assertThat(res.getText(), is("HELLO"));
        assertThat(calledInterceptors(), contains(Weight1000Interceptor.class,
                                                  Weight500Interceptor.class,
                                                  Weight100Interceptor.class,
                                                  Weight50Interceptor.class,
                                                  Weight10Interceptor.class));
    }
}
