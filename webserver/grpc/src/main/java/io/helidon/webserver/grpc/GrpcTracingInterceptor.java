/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Weight;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.grpc.core.GrpcTracingContext;
import io.helidon.grpc.core.GrpcTracingName;
import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * A {@link ServerInterceptor} that adds tracing to gRPC service calls.
 */
@Weight(InterceptorWeights.TRACING)
public class GrpcTracingInterceptor implements ServerInterceptor {

    /**
     * The Open Tracing {@link Tracer}.
     */
    private final Tracer tracer;

    /**
     * GRPC method name.
     */
    private final GrpcTracingName operationNameConstructor;

    /**
     * A flag indicating streaming logging.
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

    private GrpcTracingInterceptor(Tracer tracer, GrpcTracingConfig tracingConfig) {
        this.tracer = tracer;
        this.operationNameConstructor = tracingConfig.operationNameConstructor().orElse(null);
        this.streaming = tracingConfig.streaming();
        this.verbose = tracingConfig.verbose();
        this.tracedAttributes = tracingConfig.tracedAttributes();
    }

    /**
     * Create a {@link GrpcTracingInterceptor} interceptor.
     *
     * @param tracer the Open Tracing {@link Tracer}
     * @param config the tracing configuration
     * @return a {@link GrpcTracingInterceptor} interceptor instance
     */
    public static GrpcTracingInterceptor create(Tracer tracer, GrpcTracingConfig config) {
        return new GrpcTracingInterceptor(tracer, config);
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

        String operationName = operationNameConstructor != null
                ? operationNameConstructor.name(call.getMethodDescriptor())
                : call.getMethodDescriptor().getFullMethodName();
        Span span = getSpanFromHeaders(headerMap, operationName);

        if (tracedAttributes.contains(ServerRequestAttribute.ALL)) {
            span.tag("grpc.method_type", call.getMethodDescriptor().getType().toString());
            span.tag("grpc.method_name", call.getMethodDescriptor().getFullMethodName());
            span.tag("grpc.call_attributes", call.getAttributes().toString());
            addMetadata(headers, span);
        } else {
            for (ServerRequestAttribute attr : tracedAttributes) {
                switch (attr) {
                    case METHOD_TYPE:
                        span.tag("grpc.method_type", call.getMethodDescriptor().getType().toString());
                        break;
                    case METHOD_NAME:
                        span.tag("grpc.method_name", call.getMethodDescriptor().getFullMethodName());
                        break;
                    case CALL_ATTRIBUTES:
                        span.tag("grpc.call_attributes", call.getAttributes().toString());
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
        Context ctxWithSpan = grpcContext.withValue(GrpcTracingContext.SPAN_KEY, span);
        ServerCall.Listener<ReqT> listenerWithContext = Contexts.interceptCall(ctxWithSpan, call, headers, next);

        return new TracingListener<>(listenerWithContext, span);
    }

    private void updateContext(io.helidon.common.context.Context context, Span span) {
        if (context != null) {
            if (context.get(Tracer.class).isEmpty()) {
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

        span.tag("grpc.headers", metadata.toString());
    }

    private Span getSpanFromHeaders(Map<String, String> headers, String operationName) {
        Span span;

        try {
            SpanContext parentSpanCtx = tracer.extract(new MapHeaderProvider(headers))
                    .orElse(null);

            if (parentSpanCtx == null) {
                span = tracer.spanBuilder(operationName)
                        .start();
            } else {
                span = tracer.spanBuilder(operationName)
                        .parent(parentSpanCtx)
                        .start();
            }
        } catch (IllegalArgumentException iae) {
            span = tracer.spanBuilder(operationName)
                    .tag("Error", "Extract failed and an IllegalArgumentException was thrown")
                    .start();
        }

        return span;
    }

    private static boolean isCaseInsensitive(Map<String, String> headers) {
        return (headers instanceof TreeMap
                && ((TreeMap<?, ?>) headers).comparator() == String.CASE_INSENSITIVE_ORDER)
                || (headers instanceof ConcurrentSkipListMap<?, ?>
                && ((ConcurrentSkipListMap<?, ?>) headers).comparator() == String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * A {@link  ServerCall.Listener} to apply details to a tracing {@link Span} at various points
     * in a call lifecycle.
     *
     * @param <ReqT> the type of the request
     */
    private class TracingListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final AtomicReference<Span> span;
        private Scope scope;

        private TracingListener(ServerCall.Listener<ReqT> delegate, Span span) {
            super(delegate);
            this.span = new AtomicReference<>(span);
            scope = span.activate();
        }

        @Override
        public void onMessage(ReqT message) {
            if (streaming || verbose) {
                span.get().addEvent("onMessage", Map.of("Message received", message));
            }

            delegate().onMessage(message);
        }

        @Override
        public void onHalfClose() {
            if (streaming) {
                span.get().addEvent("Client finished sending messages");
            }

            delegate().onHalfClose();
        }

        @Override
        public void onCancel() {
            span.get().addEvent("Call cancelled");

            try {
                delegate().onCancel();
            } finally {
                finishSpan(false);
            }
        }

        @Override
        public void onComplete() {

            if (verbose) {
                span.get().addEvent("Call completed");
            }

            try {
                delegate().onComplete();
            } finally {
               finishSpan(true);
            }
        }

        private void finishSpan(boolean ok) {
            Span currentSpan = span.get();
            if (currentSpan != null && span.compareAndSet(currentSpan, null)) {
                if (scope != null) {
                    scope.close();
                    scope = null;
                    if (!ok) {
                        currentSpan.status(Span.Status.ERROR);
                    }
                    currentSpan.end();
                }
            }
        }
    }

    private record MapHeaderProvider(Map<String, String> headers) implements HeaderProvider {
        private MapHeaderProvider(Map<String, String> headers) {
            if (isCaseInsensitive(headers)) {
                this.headers = headers;
            } else {
                // headers is not updated, so TreeMap is OK--no need for concurrency.
                this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                this.headers.putAll(headers);
            }
        }

        @Override
        public Iterable<String> keys() {
            return headers.keySet();
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(headers.get(key));
        }

        @Override
        public Iterable<String> getAll(String key) {
            // either map the value to list, or get empty list
            return get(key).map(List::of)
                    .orElseGet(List::of);
        }

        @Override
        public boolean contains(String key) {
            return headers.containsKey(key);
        }
    }
}
