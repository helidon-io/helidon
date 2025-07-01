/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.Weight;
import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.tracing.Span;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests gRPC client using stubs and TLS.
 */
@ServerTest
class GrpcServicesTest extends GrpcBaseTest {

    private final GrpcClientConfig grpcClientConfig;
    private final CompletableFuture<Void> tracingEnabled = new CompletableFuture<>();

    private GrpcServicesTest(WebServer server) {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        Config config = Config.create().get("grpc-client");
        GrpcClientConfig.Builder builder = GrpcClientConfig.builder();
        builder.config(config)
                .tls(clientTls)
                .baseUri("https://localhost:" + server.port())
                .build();
        this.grpcClientConfig = builder.buildPrototype();
    }

    @Test
    void testUnaryUpper() {
        GrpcClient grpcClient = GrpcClient.create(grpcClientConfig);
        Channel channel = grpcClient.channel(List.of(new TracingVerifierInterceptor()));
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(channel);
        Strings.StringMessage res = service.upper(newStringMessage("hello"));
        assertThat(res.getText(), is("HELLO"));
        assertThat(tracingEnabled.isDone(), is(true));
    }

    @Weight(100.0)
    class TracingVerifierInterceptor extends BaseInterceptor {

        @Override
        public <ReqT, ResT> ClientCall<ReqT, ResT> interceptCall(MethodDescriptor<ReqT, ResT> method,
                                                                 CallOptions callOptions,
                                                                 Channel channel) {
            ClientCall<ReqT, ResT> call = channel.newCall(method, callOptions);
            return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
                @Override
                public void start(Listener<ResT> responseListener, Metadata headers) {
                    Optional<Span> span = Span.current();
                    if (span.isPresent()) {
                        tracingEnabled.complete(null);
                    }
                    super.start(responseListener, headers);
                }
            };
        }
    }
}
