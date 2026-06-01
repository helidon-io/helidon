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

package io.helidon.webserver.grpc.tracing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

import io.grpc.Attributes;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testing.Test(perMethod = true)
class GrpcTracingServiceTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void defaultTracerComesFromServiceRegistry() {
        Tracer tracer = mock(Tracer.class);
        Span.Builder spanBuilder = mock(Span.Builder.class);
        Span span = mock(Span.class);
        SpanContext spanContext = mock(SpanContext.class);
        MethodDescriptor<String, String> method = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Service/Call")
                .setRequestMarshaller(StringMarshaller.INSTANCE)
                .setResponseMarshaller(StringMarshaller.INSTANCE)
                .build();
        ServerCall<String, String> call = mock(ServerCall.class);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
        };

        when(tracer.extract(any())).thenReturn(Optional.empty());
        when(tracer.spanBuilder("test.Service/Call")).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(span);
        when(span.context()).thenReturn(spanContext);
        when(call.getMethodDescriptor()).thenReturn(method);
        when(call.getAttributes()).thenReturn(Attributes.EMPTY);
        when(next.startCall(same(call), any(Metadata.class))).thenReturn(listener);
        Services.set(Tracer.class, tracer);

        ServerInterceptor interceptor = GrpcTracingService.create(GrpcTracingConfig.create())
                .interceptors()
                .iterator()
                .next();
        interceptor.interceptCall(call, new Metadata(), next);

        verify(tracer).spanBuilder("test.Service/Call");
    }

    private enum StringMarshaller implements MethodDescriptor.Marshaller<String> {
        INSTANCE;

        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            return stream.toString();
        }
    }
}
