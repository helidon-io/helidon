/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.grpc.tracing;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Optional;

import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * A gRPC channel interceptor for tracing.
 */
public class GrpcClientTracingInterceptor implements ClientInterceptor {
    private static final Logger LOGGER = System.getLogger(GrpcClientTracingInterceptor.class.getName());

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel channel) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(channel.newCall(method, callOptions)) {

            private Span outgoingClientSpan;
            private Scope outgoingClientScope;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                LOGGER.log(Level.DEBUG, "Call start; metadata: {0}", headers);
                // Start a new span for the outgoing gRPC client call.
                var outgoingClientSpanBuilder = Tracer.global()
                        .spanBuilder(method.getServiceName() + "-" + method.getFullMethodName())
                        .kind(Span.Kind.CLIENT);
                Span.current().ifPresent(parent -> outgoingClientSpanBuilder.parent(parent.context()));
                outgoingClientSpan = outgoingClientSpanBuilder.start();

                try {
                    Tracer.global().inject(outgoingClientSpan.context(), null, new GrpcHeaderConsumer(headers));
                    LOGGER.log(Level.DEBUG, "After injecting span context; metadata: {0}", headers);
                    outgoingClientScope = outgoingClientSpan.activate();

                    super.start(responseListener, headers);

                } catch (Exception e) {
                    closeScopeIfActive();
                    endSpanIfPresent(e);
                }
            }

            @Override
            public void halfClose() {
                closeScopeIfActive();
                endSpanIfPresent(null);
                super.halfClose();
            }

            private void closeScopeIfActive() {
                if (outgoingClientScope != null) {
                    outgoingClientScope.close();
                    outgoingClientScope = null;
                }
            }

            private void endSpanIfPresent(Exception e) {
                if (outgoingClientSpan != null) {
                    if (e == null) {
                        outgoingClientSpan.end();
                    } else {
                        outgoingClientSpan.end(e);
                    }
                    outgoingClientSpan = null;
                }
            }
        };
    }

    private record GrpcHeaderConsumer(Metadata metadata) implements HeaderConsumer {

        @Override
        public void setIfAbsent(String s, String... strings) {
            Metadata.Key<String> key = key(s);
            if (!metadata.containsKey(key)) {
                set(key, strings);
            }
        }

        @Override
        public void set(String s, String... strings) {
            set(key(s), strings);
        }

        @Override
        public Iterable<String> keys() {
            return metadata.keys();
        }

        @Override
        public Optional<String> get(String s) {
            Metadata.Key<String> key = key(s);
            return Optional.ofNullable(metadata.get(key));
        }

        @Override
        public Iterable<String> getAll(String s) {
            Metadata.Key<String> key = key(s);
            return metadata.containsKey(key) ? metadata.getAll(key) : List.of();
        }

        @Override
        public boolean contains(String s) {
            return metadata.containsKey(key(s));
        }

        private Metadata.Key<String> key(String key) {
            return Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
        }

        private void set(Metadata.Key<String> key, String... strings) {
            for (String value : strings) {
                metadata.put(key, value);
            }
        }
    }
}
