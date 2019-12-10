/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Priority;

import io.helidon.grpc.core.ContextKeys;
import io.helidon.grpc.core.InterceptorPriorities;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.OpenTracingContextKey;
import io.opentracing.contrib.grpc.OperationNameConstructor;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;

/**
 * A {@link ServerInterceptor} that adds tracing to gRPC service calls.
 */
@Priority(InterceptorPriorities.TRACING)
public class GrpcTracing
        implements ServerInterceptor {

    private GrpcTracing(Tracer tracer, GrpcTracingConfig tracingConfig) {
        this.tracer = tracer;
        operationNameConstructor = tracingConfig.operationNameConstructor();
        streaming = tracingConfig.isStreaming();
        verbose = tracingConfig.isVerbose();
        tracedAttributes = tracingConfig.tracedAttributes();
    }

    /**
     * Create a {@link GrpcTracing} interceptor.
     *
     * @param tracer the Open Tracing {@link Tracer}
     * @param config the tracing configuration
     *
     * @return a {@link GrpcTracing} interceptor instance
     */
    static GrpcTracing create(Tracer tracer, GrpcTracingConfig config) {
        return new GrpcTracing(tracer, config);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        Map<String, String> headerMap = new HashMap<>();

        for (String key : headers.keys()) {
            if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                String value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                headerMap.put(key, value);
            }
        }

        String operationName = operationNameConstructor.constructOperationName(call.getMethodDescriptor());
        Span span = getSpanFromHeaders(headerMap, operationName);

        if (tracedAttributes.contains(ServerRequestAttribute.ALL)) {
            span.setTag("grpc.method_type", call.getMethodDescriptor().getType().toString());
            span.setTag("grpc.method_name", call.getMethodDescriptor().getFullMethodName());
            span.setTag("grpc.call_attributes", call.getAttributes().toString());
            addMetadata(headers, span);
        } else {
            for (ServerRequestAttribute attr : tracedAttributes) {
                switch (attr) {
                    case METHOD_TYPE:
                        span.setTag("grpc.method_type", call.getMethodDescriptor().getType().toString());
                        break;
                    case METHOD_NAME:
                        span.setTag("grpc.method_name", call.getMethodDescriptor().getFullMethodName());
                        break;
                    case CALL_ATTRIBUTES:
                        span.setTag("grpc.call_attributes", call.getAttributes().toString());
                        break;
                    case HEADERS:
                        addMetadata(headers, span);
                        break;
                    default:
                        // ignored - should never happen
                }
            }
        }

        Context grpcContext = Context.current();

        updateContext(ContextKeys.HELIDON_CONTEXT.get(grpcContext), span);
        io.helidon.common.context.Contexts.context().ifPresent(ctx -> updateContext(ctx, span));

        Context ctxWithSpan = grpcContext.withValue(OpenTracingContextKey.getKey(), span);
        ServerCall.Listener<ReqT> listenerWithContext = Contexts.interceptCall(ctxWithSpan, call, headers, next);

        return new TracingListener<>(listenerWithContext, span);
    }

    private void updateContext(io.helidon.common.context.Context context, Span span) {
        if (context != null) {
            if (!context.get(Tracer.class).isPresent()) {
                context.register(tracer);
            }

            context.register(span.context());
        }
    }

    private void addMetadata(Metadata headers, Span span) {
        // copy the headers and make sure that the AUTHORIZATION header
        // is removed as we do not want auth details to appear in tracing logs
        Metadata metadata = new Metadata();

        metadata.merge(headers);
        metadata.removeAll(ContextKeys.AUTHORIZATION);

        span.setTag("grpc.headers", metadata.toString());
    }

    private Span getSpanFromHeaders(Map<String, String> headers, String operationName) {
        Span span;

        try {
            SpanContext parentSpanCtx = tracer.extract(Format.Builtin.HTTP_HEADERS,
                                                       new TextMapAdapter(headers));
            if (parentSpanCtx == null) {
                span = tracer.buildSpan(operationName)
                        .start();
            } else {
                span = tracer.buildSpan(operationName)
                        .asChildOf(parentSpanCtx)
                        .start();
            }
        } catch (IllegalArgumentException iae) {
            span = tracer.buildSpan(operationName)
                    .withTag("Error", "Extract failed and an IllegalArgumentException was thrown")
                    .start();
        }

        return span;
    }

    /**
     * A {@link  ServerCall.Listener} to apply details to a tracing {@link Span} at various points
     * in a call lifecycle.
     *
     * @param <ReqT>  the type of the request
     */
    private class TracingListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final Span span;

        private TracingListener(ServerCall.Listener<ReqT> delegate, Span span) {
            super(delegate);
            this.span = span;
        }

        @Override
        public void onMessage(ReqT message) {
            if (streaming || verbose) {
                span.log(Collections.singletonMap("Message received", message));
            }

            delegate().onMessage(message);
        }

        @Override
        public void onHalfClose() {
            if (streaming) {
                span.log("Client finished sending messages");
            }

            delegate().onHalfClose();
        }

        @Override
        public void onCancel() {
            span.log("Call cancelled");

            try {
                delegate().onCancel();
            } finally {
                span.finish();
            }
        }

        @Override
        public void onComplete() {
            if (verbose) {
                span.log("Call completed");
            }

            try {
                delegate().onComplete();
            } finally {
                span.finish();
            }
        }
    }

    /**
     * The Open Tracing {@link Tracer}.
     */
    private final Tracer tracer;

    /**
     * A flag indicating whether to log streaming.
     */
    private final OperationNameConstructor operationNameConstructor;

    /**
     *
     */
    private final boolean streaming;

    /**
     * A flag indicating verbose logging.
     */
    private final boolean verbose;

    /**
     * The set of attributes to log in spans.
     */
    private final Set<ServerRequestAttribute> tracedAttributes;
}
