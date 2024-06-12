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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;

import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.GrpcInterceptor;
import io.helidon.microprofile.grpc.core.GrpcInterceptorBinding;
import io.helidon.microprofile.grpc.core.GrpcInterceptors;
import io.helidon.microprofile.grpc.core.Unary;
import io.helidon.microprofile.grpc.server.test.Echo;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;

class EchoServiceTest extends BaseServiceTest {

    private static final Set<Class<?>> INTERCEPTORS_CALLED = new HashSet<>();

    private final GrpcServiceDescriptor serviceDescriptor;

    @Inject
    public EchoServiceTest(WebTarget webTarget) {
        super(webTarget);
        this.serviceDescriptor = GrpcServiceDescriptor.builder()
                .serviceName("EchoService")
                .putMethod("Echo",
                        GrpcClientMethodDescriptor.unary("EchoService", "Echo")
                                .requestType(Echo.EchoRequest.class)
                                .responseType(Echo.EchoResponse.class)
                                .build())
                .build();
    }

    @Test
    void testEcho() {
        Echo.EchoResponse res = grpcClient().serviceClient(serviceDescriptor)
                .unary("Echo", fromString("Howdy"));
        assertThat(res.getMessage(), is("Howdy"));
        assertThat(INTERCEPTORS_CALLED, hasItems(EchoInterceptor1.class, EchoInterceptor2.class));
    }

    private Echo.EchoRequest fromString(String value) {
        return Echo.EchoRequest.newBuilder().setMessage(value).build();
    }

    @GrpcInterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface EchoInterceptorBinding {
    }

    /**
     * A service that is annotated by {@link Grpc}. Should be discovered by
     * {@link GrpcMpCdiExtension}. References two interceptors, one directly
     * and one via an interceptor binding.
     */
    @Grpc
    @ApplicationScoped
    @EchoInterceptorBinding
    @GrpcInterceptors(EchoInterceptor1.class)
    public static class EchoService {

        /**
         * Echo the message back to the caller.
         *
         * @param request   the echo request containing the message to echo
         * @param observer  the call response
         */
        @Unary(name = "Echo")
        public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {
            String message = request.getMessage();
            Echo.EchoResponse response = Echo.EchoResponse.newBuilder().setMessage(message).build();
            complete(observer, response);
        }
    }

    @GrpcInterceptor
    @ApplicationScoped
    public static class EchoInterceptor1 implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            INTERCEPTORS_CALLED.add(getClass());
            return next.startCall(call, headers);
        }
    }

    @GrpcInterceptor
    @EchoInterceptorBinding
    @ApplicationScoped
    public static class EchoInterceptor2 implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            INTERCEPTORS_CALLED.add(getClass());
            return next.startCall(call, headers);
        }
    }
}
