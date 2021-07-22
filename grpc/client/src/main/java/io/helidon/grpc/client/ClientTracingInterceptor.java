/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.grpc.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Priority;

import io.helidon.grpc.core.ContextKeys;
import io.helidon.grpc.core.InterceptorPriorities;

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
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.ActiveSpanSource;
import io.opentracing.contrib.grpc.OperationNameConstructor;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

/**
 * A {@link ClientInterceptor} that captures tracing information into
 * Open Tracing {@link Span}s for client calls.
 */
@Priority(InterceptorPriorities.TRACING)
public class ClientTracingInterceptor
        implements ClientInterceptor {

    private final Tracer tracer;

    private final OperationNameConstructor operationNameConstructor;

    private final boolean streaming;

    private final boolean verbose;

    private final Set<ClientRequestAttribute> tracedAttributes;

    private final ActiveSpanSource activeSpanSource;

    /**
     * Private constructor called by {@link Builder}.
     *
     * @param tracer                   the Open Tracing {@link Tracer}
     * @param operationNameConstructor the operation name constructor
     * @param streaming                flag indicating whether to trace streaming calls
     * @param verbose                  flag to indicate verbose logging to spans
     * @param tracedAttributes         the set of request attributes to add to the span
     * @param activeSpanSource         the source of the active span
     */
    private ClientTracingInterceptor(Tracer tracer,
                                     OperationNameConstructor operationNameConstructor,
                                     boolean streaming,
                                     boolean verbose,
                                     Set<ClientRequestAttribute> tracedAttributes,
                                     ActiveSpanSource activeSpanSource) {
        this.tracer = tracer;
        this.operationNameConstructor = operationNameConstructor;
        this.streaming = streaming;
        this.verbose = verbose;
        this.tracedAttributes = tracedAttributes;
        this.activeSpanSource = activeSpanSource;
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

        String operationName = operationNameConstructor.constructOperationName(method);
        Span span = createSpanFromParent(activeSpanSource.getActiveSpan(), operationName);

        for (ClientRequestAttribute attr : tracedAttributes) {
            switch (attr) {
            case ALL_CALL_OPTIONS:
                span.setTag("grpc.call_options", callOptions.toString());
                break;
            case AUTHORITY:
                if (callOptions.getAuthority() == null) {
                    span.setTag("grpc.authority", "null");
                } else {
                    span.setTag("grpc.authority", callOptions.getAuthority());
                }
                break;
            case COMPRESSOR:
                if (callOptions.getCompressor() == null) {
                    span.setTag("grpc.compressor", "null");
                } else {
                    span.setTag("grpc.compressor", callOptions.getCompressor());
                }
                break;
            case DEADLINE:
                if (callOptions.getDeadline() == null) {
                    span.setTag("grpc.deadline_millis", "null");
                } else {
                    span.setTag("grpc.deadline_millis", callOptions.getDeadline().timeRemaining(TimeUnit.MILLISECONDS));
                }
                break;
            case METHOD_NAME:
                span.setTag("grpc.method_name", method.getFullMethodName());
                break;
            case METHOD_TYPE:
                if (method.getType() == null) {
                    span.setTag("grpc.method_type", "null");
                } else {
                    span.setTag("grpc.method_type", method.getType().toString());
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

    private Span createSpanFromParent(Span parentSpan, String operationName) {
        if (parentSpan == null) {
            return tracer.buildSpan(operationName).start();
        } else {
            return tracer.buildSpan(operationName).asChildOf(parentSpan).start();
        }
    }

    /**
     * Obtain a builder to build a {@link ClientTracingInterceptor}.
     *
     * @param tracer  the {@link Tracer} to use
     *
     * @return  a builder to build a {@link ClientTracingInterceptor}
     */
    public static Builder builder(Tracer tracer) {
        return new Builder(tracer);
    }

    /**
     * Builds the configuration of a ClientTracingInterceptor.
     */
    public static class Builder {

        private final Tracer tracer;

        private OperationNameConstructor operationNameConstructor;

        private boolean streaming;

        private boolean verbose;

        private Set<ClientRequestAttribute> tracedAttributes;

        private ActiveSpanSource activeSpanSource;

        /**
         * @param tracer to use for this intercepter
         *               Creates a Builder with default configuration
         */
        public Builder(Tracer tracer) {
            this.tracer = tracer;
            operationNameConstructor = OperationNameConstructor.DEFAULT;
            streaming = false;
            verbose = false;
            tracedAttributes = new HashSet<>();
            activeSpanSource = ActiveSpanSource.GRPC_CONTEXT;
        }

        /**
         * @param operationNameConstructor to name all spans created by this intercepter
         * @return this Builder with configured operation name
         */
        public ClientTracingInterceptor.Builder withOperationName(OperationNameConstructor operationNameConstructor) {
            this.operationNameConstructor = operationNameConstructor;
            return this;
        }

        /**
         * Logs streaming events to client spans.
         *
         * @return this Builder configured to log streaming events
         */
        public ClientTracingInterceptor.Builder withStreaming() {
            streaming = true;
            return this;
        }

        /**
         * @param tracedAttributes to set as tags on client spans
         *                         created by this intercepter
         * @return this Builder configured to trace attributes
         */
        public ClientTracingInterceptor.Builder withTracedAttributes(ClientRequestAttribute... tracedAttributes) {
            this.tracedAttributes = new HashSet<>(Arrays.asList(tracedAttributes));
            return this;
        }

        /**
         * Logs all request life-cycle events to client spans.
         *
         * @return this Builder configured to be verbose
         */
        public ClientTracingInterceptor.Builder withVerbosity() {
            verbose = true;
            return this;
        }

        /**
         * @param activeSpanSource that provides a method of getting the
         *                         active span before the client call
         * @return this Builder configured to start client span as children
         *         of the span returned by activeSpanSource.getActiveSpan()
         */
        public ClientTracingInterceptor.Builder withActiveSpanSource(ActiveSpanSource activeSpanSource) {
            this.activeSpanSource = activeSpanSource;
            return this;
        }

        /**
         * @return a ClientTracingInterceptor with this Builder's configuration
         */
        public ClientTracingInterceptor build() {
            return new ClientTracingInterceptor(tracer,
                                                operationNameConstructor,
                                                streaming,
                                                verbose,
                                                tracedAttributes,
                                                activeSpanSource);
        }
    }

    /**
     * A {@link ForwardingClientCall.SimpleForwardingClientCall} that adds information
     * to a tracing {@link Span} at different places in the gROC call lifecycle.
     *
     * @param <ReqT>   the gRPC request type
     * @param <RespT>  the gRPC response type
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
                span.log("Started call");
            }

            if (tracedAttributes.contains(ClientRequestAttribute.HEADERS)) {
                // copy the headers and make sure that the AUTHORIZATION header
                // is removed as we do not want auth details to appear in tracing logs
                Metadata metadata = new Metadata();
                metadata.merge(headers);
                metadata.removeAll(ContextKeys.AUTHORIZATION);
                span.setTag("grpc.headers", metadata.toString());
            }

            tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMap() {
                @Override
                public void put(String key, String value) {
                    Metadata.Key<String> headerKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                    headers.put(headerKey, value);
                }

                @Override
                public Iterator<Map.Entry<String, String>> iterator() {
                    throw new UnsupportedOperationException(
                            "TextMapInjectAdapter should only be used with Tracer.inject()");
                }
            });

            Listener<RespT> tracingResponseListener
                    = new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                @Override
                public void onHeaders(Metadata headers) {
                    if (verbose) {
                        span.log(Collections.singletonMap("Response headers received", headers.toString()));
                    }
                    delegate().onHeaders(headers);
                }

                @Override
                public void onMessage(RespT message) {
                    if (streaming || verbose) {
                        span.log("Response received");
                    }
                    delegate().onMessage(message);
                }

                @Override
                public void onClose(Status status, Metadata trailers) {
                    if (verbose) {
                        if (status.getCode().value() == 0) {
                            span.log("Call closed");
                        } else {
                            String desc = String.valueOf(status.getDescription());

                            span.log(Collections.singletonMap("Call failed", desc));
                        }
                    }
                    span.finish();

                    delegate().onClose(status, trailers);
                }
            };

            delegate().start(tracingResponseListener, headers);
        }

        @Override
        public void cancel(String message, Throwable cause) {
            String errorMessage;

            errorMessage = message == null ? "Error" : message;

            if (cause == null) {
                span.log(errorMessage);
            } else {
                span.log(Collections.singletonMap(errorMessage, cause.getMessage()));
            }

            delegate().cancel(message, cause);
        }

        @Override
        public void halfClose() {
            if (streaming) {
                span.log("Finished sending messages");
            }

            delegate().halfClose();
        }

        @Override
        public void sendMessage(ReqT message) {
            if (streaming || verbose) {
                span.log("Message sent");
            }

            delegate().sendMessage(message);
        }
    }
}
