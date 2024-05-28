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

package io.helidon.webserver.tests.grpc;

import javax.annotation.Nullable;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.CompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

/**
 * Same as base class but uses {@link CompressorRegistry#getDefaultInstance()} to
 * enable GZIP compression for data frames.
 */
@ServerTest
class GzipStringServiceTest extends BaseStringServiceTest {

    GzipStringServiceTest(WebServer server) {
        super(server);
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new StringService()));
    }

    @Override
    ManagedChannel channel(int port) {
        return ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .compressorRegistry(CompressorRegistry.getDefaultInstance())    // gzip by default
                .intercept(new GzipVerifierInterceptor())
                .build();
    }

    /**
     * Verifies response is encoded using GZIP so negotiation was successful.
     */
    static class GzipVerifierInterceptor implements ClientInterceptor {

        private static final Metadata.Key<String> GRPC_ENCODING = Metadata.Key.of("grpc-encoding",
                Metadata.ASCII_STRING_MARSHALLER);

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                   CallOptions callOptions, Channel next) {
            ClientCall<ReqT, RespT> nextCall = next.newCall(method, callOptions);
            return new ClientCall<>() {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    Listener<RespT> verifierListener = new Listener<>() {
                        @Override
                        public void onHeaders(Metadata headers) {
                            // verify that GZIP is in use
                            String encoding = headers.get(GRPC_ENCODING);
                            if (encoding == null || !encoding.contains("gzip")) {
                                throw new RuntimeException("GZIP encoding has not been used");
                            }

                            // proceed forwarding normal call
                            responseListener.onHeaders(headers);
                        }

                        @Override
                        public void onMessage(RespT message) {
                            responseListener.onMessage(message);
                        }

                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            responseListener.onClose(status, trailers);
                        }

                        @Override
                        public void onReady() {
                            responseListener.onReady();
                        }
                    };
                    nextCall.start(verifierListener, headers);
                }

                @Override
                public void request(int numMessages) {
                    nextCall.request(numMessages);
                }

                @Override
                public void cancel(@Nullable String message, @Nullable Throwable cause) {
                    nextCall.cancel(message, cause);
                }

                @Override
                public void halfClose() {
                    nextCall.halfClose();
                }

                @Override
                public void sendMessage(ReqT message) {
                    nextCall.sendMessage(message);
                }
            };
        }
    }
}
