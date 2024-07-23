/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.client;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.Weight;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.grpc.core.GrpcTracingContext;
import io.helidon.grpc.core.GrpcTracingName;
import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

/**
 * A {@link ClientInterceptor} that captures tracing information into
 * Tracing {@link Span}s for client calls.
 */
@Weight(InterceptorWeights.TRACING)
public class ClientTracingInterceptor implements ClientInterceptor {

    private final Tracer tracer;

    private final GrpcTracingName operationNameConstructor;

    private final boolean streaming;

    private final boolean verbose;

    private final Set<ClientRequestAttribute> tracedAttributes;

    /**
     * Private constructor called by {@link Builder}.
     *
     * @param tracer                   the Open Tracing {@link Tracer}
     * @param operationNameConstructor the operation name constructor
     * @param streaming                flag indicating whether to trace streaming calls
     * @param verbose                  flag to indicate verbose logging to spans
     * @param tracedAttributes         the set of request attributes to add to the span
     */
    private ClientTracingInterceptor(Tracer tracer,
                                     GrpcTracingName operationNameConstructor,
                                     boolean streaming,
                                     boolean verbose,
                                     Set<ClientRequestAttribute> tracedAttributes) {
        this.tracer = tracer;
        this.operationNameConstructor = operationNameConstructor;
        this.streaming = streaming;
        this.verbose = verbose;
        this.tracedAttributes = tracedAttributes;
    }

    /**
     * Obtain a builder to build a {@link ClientTracingInterceptor}.
     *
     * @param tracer the {@link Tracer} to use
     * @return a builder to build a {@link ClientTracingInterceptor}
     */
    public static Builder builder(Tracer tracer) {
        return new Builder(tracer);
    }

    /**
     * Use this interceptor to trace all requests made by this client channel.
     *
     * @param channel to be traced
     * @return intercepted channel
     */
    public Channel intercept(Channel channel) {
        return ClientInterceptors.intercept(channel, this);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {

        String operationName = operationNameConstructor.name(method);
        Span span = createSpanFromParent(operationName);

        for (ClientRequestAttribute attr : tracedAttributes) {
            switch (attr) {
            case ALL_CALL_OPTIONS:
                span.tag("grpc.call_options", callOptions.toString());
                break;
            case AUTHORITY:
                if (callOptions.getAuthority() == null) {
                    span.tag("grpc.authority", "null");
                } else {
                    span.tag("grpc.authority", callOptions.getAuthority());
                }
                break;
            case COMPRESSOR:
                if (callOptions.getCompressor() == null) {
                    span.tag("grpc.compressor", "null");
                } else {
                    span.tag("grpc.compressor", callOptions.getCompressor());
                }
                break;
            case DEADLINE:
                if (callOptions.getDeadline() == null) {
                    span.tag("grpc.deadline_millis", "null");
                } else {
                    span.tag("grpc.deadline_millis", callOptions.getDeadline().timeRemaining(TimeUnit.MILLISECONDS));
                }
                break;
            case METHOD_NAME:
                span.tag("grpc.method_name", method.getFullMethodName());
                break;
            case METHOD_TYPE:
                if (method.getType() == null) {
                    span.tag("grpc.method_type", "null");
                } else {
                    span.tag("grpc.method_type", method.getType().toString());
                }
                break;
            case HEADERS:
                break;
            default:
                // should not happen, but can be ignored
            }
        }

        return new ClientTracingListener<>(next.newCall(method, callOptions), span);
    }

    private Span createSpanFromParent(String operationName) {
        return tracer.spanBuilder(operationName)
                .update(it -> GrpcTracingContext.activeSpan().map(Span::context).ifPresent(it::parent))
                .start();
    }

    /**
     * Builds the configuration of a ClientTracingInterceptor.
     */
    public static class Builder {

        private final Tracer tracer;

        private GrpcTracingName operationNameConstructor;

        private boolean streaming;

        private boolean verbose;

        private Set<ClientRequestAttribute> tracedAttributes;

        /**
         * Constructs a builder from a {@link Tracer}.
         *
         * @param tracer to use for this interceptor
         *               Creates a Builder with default configuration
         */
        public Builder(Tracer tracer) {
            this.tracer = tracer;
            operationNameConstructor = MethodDescriptor::getFullMethodName;
            streaming = false;
            verbose = false;
            tracedAttributes = new HashSet<>();
        }

        /**
         * Sets the operation name.
         *
         * @param operationNameConstructor to name all spans created by this interceptor
         * @return this Builder with configured operation name
         */
        public Builder withOperationName(GrpcTracingName operationNameConstructor) {
            this.operationNameConstructor = operationNameConstructor;
            return this;
        }

        /**
         * Logs streaming events to client spans.
         *
         * @return this Builder configured to log streaming events
         */
        public Builder withStreaming() {
            streaming = true;
            return this;
        }

        /**
         * Sets one or more attributes.
         *
         * @param tracedAttributes to set as tags on client spans
         *                         created by this interceptor
         * @return this Builder configured to trace attributes
         */
        public Builder withTracedAttributes(ClientRequestAttribute... tracedAttributes) {
            this.tracedAttributes = new HashSet<>(Arrays.asList(tracedAttributes));
            return this;
        }

        /**
         * Logs all request life-cycle events to client spans.
         *
         * @return this Builder configured to be verbose
         */
        public Builder withVerbosity() {
            verbose = true;
            return this;
        }

        /**
         * Finishes building the interceptor.
         *
         * @return a ClientTracingInterceptor with this Builder's configuration
         */
        public ClientTracingInterceptor build() {
            return new ClientTracingInterceptor(tracer,
                                                operationNameConstructor,
                                                streaming,
                                                verbose,
                                                tracedAttributes);
        }
    }

    /**
     * A {@link SimpleForwardingClientCall} that adds information
     * to a tracing {@link Span} at different places in the gROC call lifecycle.
     *
     * @param <ReqT>  the gRPC request type
     * @param <RespT> the gRPC response type
     */
    private class ClientTracingListener<ReqT, RespT>
            extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {

        private final Span span;

        private ClientTracingListener(ClientCall<ReqT, RespT> delegate, Span span) {
            super(delegate);
            this.span = span;
        }

        @Override
        public void start(Listener<RespT> responseListener, final Metadata headers) {
            if (verbose) {
                span.addEvent("Started call");
            }

            if (tracedAttributes.contains(ClientRequestAttribute.HEADERS)) {
                // copy the headers and make sure that the AUTHORIZATION header
                // is removed as we do not want auth details to appear in tracing logs
                Metadata metadata = new Metadata();
                metadata.merge(headers);
                metadata.removeAll(ContextKeys.AUTHORIZATION);
                span.tag("grpc.headers", metadata.toString());
            }

            tracer.inject(span.context(), HeaderProvider.empty(), new MetadataHeaderConsumer(headers));

            Listener<RespT> tracingResponseListener
                    = new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                @Override
                public void onHeaders(Metadata headers) {
                    if (verbose) {
                        span.addEvent("headers",
                                      Map.of("Response headers received", headers.toString()));
                    }
                    delegate().onHeaders(headers);
                }

                @Override
                public void onClose(Status status, Metadata trailers) {
                    if (verbose) {
                        if (status.getCode().value() == 0) {
                            span.addEvent("Call closed");
                        } else {
                            String desc = String.valueOf(status.getDescription());

                            span.addEvent("onClose", Map.of("Call failed", desc));
                        }
                    }
                    span.end();

                    delegate().onClose(status, trailers);
                }

                @Override
                public void onMessage(RespT message) {
                    if (streaming || verbose) {
                        span.addEvent("Response received");
                    }
                    delegate().onMessage(message);
                }
            };

            delegate().start(tracingResponseListener, headers);
        }

        @Override
        public void sendMessage(ReqT message) {
            if (streaming || verbose) {
                span.addEvent("Message sent");
            }

            delegate().sendMessage(message);
        }

        @Override
        public void cancel(String message, Throwable cause) {
            String errorMessage;

            errorMessage = message == null ? "Error" : message;

            if (cause == null) {
                span.addEvent(errorMessage);
            } else {
                span.addEvent("error", Map.of(errorMessage, cause.getMessage()));
            }

            delegate().cancel(message, cause);
        }

        @Override
        public void halfClose() {
            if (streaming) {
                span.addEvent("Finished sending messages");
            }

            delegate().halfClose();
        }

        private class MetadataHeaderConsumer implements HeaderConsumer {
            private final Metadata headers;

            private MetadataHeaderConsumer(Metadata headers) {
                this.headers = headers;
            }

            @Override
            public void setIfAbsent(String key, String... values) {
                Metadata.Key<String> headerKey = key(key);
                if (!headers.containsKey(headerKey)) {
                    headers.put(headerKey, values[0]);
                }
            }

            @Override
            public void set(String key, String... values) {
                Metadata.Key<String> headerKey = key(key);
                headers.put(headerKey, values[0]);
            }

            @Override
            public Iterable<String> keys() {
                return headers.keys();
            }

            @Override
            public Optional<String> get(String key) {
                return Optional.ofNullable(headers.get(key(key)));
            }

            @Override
            public Iterable<String> getAll(String key) {
                // map single value to list or get an empty list
                return get(key).map(List::of).orElseGet(List::of);
            }

            @Override
            public boolean contains(String key) {
                return headers.containsKey(key(key));
            }

            private Metadata.Key<String> key(String name) {
                return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
            }

            private void put(Metadata.Key<String> key, String value) {
                headers.put(key, value);
            }
        }
    }
}
