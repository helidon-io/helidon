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
package io.helidon.microprofile.grpc.server;

import java.time.Duration;

import io.grpc.stub.StreamObserver;
import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.Unary;
import io.helidon.microprofile.grpc.server.test.Echo;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddExtension(GrpcMpCdiExtension.class)
class EchoServiceTest {

    private final GrpcClient grpcClient;
    private final GrpcServiceDescriptor serviceDescriptor;

    @Inject
    public EchoServiceTest(WebTarget webTarget) {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        this.grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .readTimeout(Duration.ofSeconds(300))    // debugging
                .baseUri("https://localhost:" + webTarget.getUri().getPort())
                .build();

        this.serviceDescriptor = GrpcServiceDescriptor.builder()
                .serviceName("EchoService")
                .putMethod("echo",
                        GrpcClientMethodDescriptor.unary("EchoService", "echo")
                                .requestType(Echo.EchoRequest.class)
                                .responseType(Echo.EchoResponse.class)
                                .build())
                .build();
    }

    @Test
    void test() {
        Echo.EchoResponse res = grpcClient.serviceClient(serviceDescriptor)
                .unary("echo", fromString("Howdy"));
        assertThat(res.getMessage(), is("Howdy"));
    }

    private Echo.EchoRequest fromString(String value) {
        return Echo.EchoRequest.newBuilder().setMessage(value).build();
    }

    @Grpc
    @ApplicationScoped
    public static class EchoService {

        /**
         * Echo the message back to the caller.
         *
         * @param request   the echo request containing the message to echo
         * @param observer  the call response
         */
        @Unary
        public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {
            String message = request.getMessage();
            Echo.EchoResponse response = Echo.EchoResponse.newBuilder().setMessage(message).build();
            complete(observer, response);
        }
    }
}
