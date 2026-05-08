/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verifies that the Helidon gRPC webclient sends the {@code te: trailers} header on the wire.
 *
 * <p>The gRPC-over-HTTP/2 spec lists {@code TE} in the Call-Definition as a non-optional field.
 * Intermediaries use it to decide whether to forward HTTP/2 trailers end-to-end, so its absence
 * causes proxies to silently drop {@code grpc-status} and trailing metadata.
 *
 * <p>This test uses a server-side {@link ServerInterceptor} to capture the raw {@link Metadata}
 * as received by the server, which proves the header survived the full client encoding pipeline,
 * not just that it was added to a {@link io.helidon.http.WritableHeaders} in-process.
 */
@ServerTest
class GrpcTeTrailersTest {

    private static final Metadata.Key<String> TE_KEY =
            Metadata.Key.of("te", Metadata.ASCII_STRING_MARSHALLER);

    private static final AtomicReference<Metadata> capturedMetadata = new AtomicReference<>();

    private final GrpcClient grpcClient;
    private final GrpcServiceDescriptor serviceDescriptor;

    GrpcTeTrailersTest(WebServer server) {
        this.grpcClient = GrpcClient.builder()
                .tls(t -> t.enabled(false))
                .baseUri("http://localhost:" + server.port())
                .build();
        this.serviceDescriptor = GrpcServiceDescriptor.builder()
                .serviceName("StringService")
                .putMethod("Upper",
                        GrpcClientMethodDescriptor.unary("StringService", "Upper")
                                .requestType(Strings.StringMessage.class)
                                .responseType(Strings.StringMessage.class)
                                .build())
                .build();
    }

    @SetUpRoute
    static void setUpRoute(GrpcRouting.Builder routing) {
        routing.intercept(new MetadataCapturingInterceptor())
                .unary(Strings.getDescriptor(), "StringService", "Upper", GrpcTeTrailersTest::upper);
    }

    @Test
    void teTrailersHeaderIsSentToServer() {
        grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper", Strings.StringMessage.newBuilder().setText("hello").build());

        Metadata metadata = capturedMetadata.get();
        assertThat("server interceptor was not called", metadata, notNullValue());
        assertThat(metadata.get(TE_KEY), is("trailers"));
    }

    private static void upper(Strings.StringMessage req, StreamObserver<Strings.StringMessage> observer) {
        observer.onNext(Strings.StringMessage.newBuilder()
                .setText(req.getText().toUpperCase(Locale.ROOT))
                .build());
        observer.onCompleted();
    }

    private static class MetadataCapturingInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            capturedMetadata.set(headers);
            return next.startCall(call, headers);
        }
    }
}
