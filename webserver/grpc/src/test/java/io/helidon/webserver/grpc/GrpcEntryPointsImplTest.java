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

package io.helidon.webserver.grpc;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.service.registry.InterceptionContext;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

class GrpcEntryPointsImplTest {
    private static final Metadata.Key<String> DETAIL = Metadata.Key.of("test-detail",
                                                                       Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void preservesGrpcStatusAndTrailersFromInterceptorFailure() {
        Metadata trailers = new Metadata();
        trailers.put(DETAIL, "detail");
        var failure = Status.PERMISSION_DENIED.withDescription("denied").asRuntimeException(trailers);
        TestServerCall call = new TestServerCall();

        interceptor(failingInterceptor(new Exception("wrapped", failure)))
                .interceptCall(call, new Metadata(), unusedHandler());

        assertThat(call.status.get().getCode(), is(Status.Code.PERMISSION_DENIED));
        assertThat(call.status.get().getDescription(), is("denied"));
        assertThat(call.trailers.get(), sameInstance(trailers));
    }

    @Test
    void mapsGenericInterceptorFailureToInternal() {
        IllegalStateException failure = new IllegalStateException("sensitive detail");
        TestServerCall call = new TestServerCall();

        interceptor(failingInterceptor(failure)).interceptCall(call, new Metadata(), unusedHandler());

        assertThat(call.status.get().getCode(), is(Status.Code.INTERNAL));
        assertThat(call.status.get().getCause(), sameInstance(failure));
        assertThat(call.status.get().getDescription(), is((String) null));
    }

    private static GrpcEntryPointsImpl.EntryPointInterceptor interceptor(GrpcEntryPoint.Interceptor interceptor) {
        return new GrpcEntryPointsImpl.EntryPointInterceptor(null, List.of(interceptor));
    }

    private static GrpcEntryPoint.Interceptor failingInterceptor(Exception failure) {
        return new GrpcEntryPoint.Interceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> intercept(InterceptionContext interceptionContext,
                                                                     Chain<ReqT, RespT> chain,
                                                                     ServerCall<ReqT, RespT> call,
                                                                     Metadata headers) throws Exception {
                throw failure;
            }
        };
    }

    private static ServerCallHandler<String, String> unusedHandler() {
        return (call, headers) -> {
            throw new AssertionError("Handler must not be called");
        };
    }

    private static final class TestServerCall extends ServerCall<String, String> {
        private final AtomicReference<Status> status = new AtomicReference<>();
        private final AtomicReference<Metadata> trailers = new AtomicReference<>();

        @Override
        public void request(int ignored) {
        }

        @Override
        public void sendHeaders(Metadata ignored) {
        }

        @Override
        public void sendMessage(String ignored) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
            this.status.set(status);
            this.trailers.set(trailers);
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(String ignored) {
                    return InputStream.nullInputStream();
                }

                @Override
                public String parse(InputStream ignored) {
                    return "";
                }
            };
            return MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("test/Test")
                    .setRequestMarshaller(marshaller)
                    .setResponseMarshaller(marshaller)
                    .build();
        }
    }
}
