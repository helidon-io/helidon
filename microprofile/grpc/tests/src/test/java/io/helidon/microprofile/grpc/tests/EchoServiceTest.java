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

package io.helidon.microprofile.grpc.tests;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;

import io.helidon.grpc.api.Grpc;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.microprofile.grpc.tests.test.Echo;
import io.helidon.microprofile.grpc.tests.test.EchoServiceGrpc;
import io.helidon.tracing.Tracer;

import io.grpc.Context;
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
import static io.helidon.webserver.grpc.GrpcServiceDescriptor.SERVICE_DESCRIPTOR_KEY;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;

class EchoServiceTest extends BaseServiceTest {

    private static final Set<Class<?>> INTERCEPTORS_CALLED = new HashSet<>();

    @Inject
    public EchoServiceTest(WebTarget webTarget) {
        super(webTarget);
    }

    @Test
    void testEcho() {
        EchoServiceGrpc.EchoServiceBlockingStub service = EchoServiceGrpc.newBlockingStub(grpcClient().channel());
        Echo.EchoResponse res = service.echo(fromString("Howdy"));
        assertThat(res.getMessage(), is("Howdy"));
        assertThat(INTERCEPTORS_CALLED, hasItems(EchoInterceptor1.class, EchoInterceptor2.class));
    }

    private Echo.EchoRequest fromString(String value) {
        return Echo.EchoRequest.newBuilder().setMessage(value).build();
    }

    @Grpc.GrpcInterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface EchoInterceptorBinding {
    }

    /**
     * A service that is annotated by {@link io.helidon.microprofile.grpc.api.Grpc}. Should be discovered by
     * {@link io.helidon.microprofile.grpc.server.GrpcMpCdiExtension}. References two interceptors, one directly
     * and one via an interceptor binding.
     */
    @Grpc.GrpcService
    @ApplicationScoped
    @EchoInterceptorBinding
    @Grpc.GrpcInterceptors(EchoInterceptor1.class)
    public static class EchoService {

        /**
         * Echo the message back to the caller.
         *
         * @param request the echo request containing the message to echo
         * @param observer the call response
         */
        @Grpc.Unary("Echo")
        public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {
            try {
                validateContext();
                String message = request.getMessage();
                Echo.EchoResponse response = Echo.EchoResponse.newBuilder().setMessage(message).build();
                complete(observer, response);
            } catch (IllegalStateException e) {
                observer.onError(e);
            }
        }

    }

    @Grpc.GrpcInterceptor
    @ApplicationScoped
    public static class EchoInterceptor1 implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            validateContext();
            validateTracing();
            INTERCEPTORS_CALLED.add(getClass());
            return next.startCall(call, headers);
        }
    }

    @Grpc.GrpcInterceptor
    @EchoInterceptorBinding
    @ApplicationScoped
    public static class EchoInterceptor2 implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            validateContext();
            validateTracing();
            INTERCEPTORS_CALLED.add(getClass());
            return next.startCall(call, headers);
        }
    }

    /**
     * Validates that a gRPC context is present.
     */
    private static void validateContext() {
        Context context = Context.current();
        if (context == null || SERVICE_DESCRIPTOR_KEY.get() == null) {
            throw new IllegalStateException("Invalid context");
        }
    }

    /**
     * Validates that tracing is enabled. See {@code resources/application.yaml}.
     */
    private static void validateTracing() {
        Context context = Context.current();
        io.helidon.common.context.Context helidonContext = ContextKeys.HELIDON_CONTEXT.get(context);
        if (helidonContext == null || helidonContext.get(Tracer.class).isEmpty()) {
            throw new IllegalStateException("Invalid tracing context");
        }
    }
}
