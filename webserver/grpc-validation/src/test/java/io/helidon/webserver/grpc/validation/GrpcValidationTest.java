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

package io.helidon.webserver.grpc.validation;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.validation.ValidationException;
import io.helidon.webserver.grpc.GrpcRouting;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GrpcValidationTest {
    private static final Metadata.Key<String> DETAIL = Metadata.Key.of("test-detail",
                                                                       Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void discoversValidationFromClasspathByDefault() {
        GrpcRouting routing = GrpcRouting.builder()
                .config(Config.empty())
                .build();

        assertEquals(1, routing.interceptors()
                .stream()
                .filter(GrpcValidation.class::isInstance)
                .count());
    }

    @Test
    void discoversValidationWhenAnotherServiceIsExcluded() {
        GrpcRouting routing = GrpcRouting.builder()
                .config(Config.empty())
                .addExcludedServiceName("other")
                .build();

        assertEquals(1, routing.interceptors()
                .stream()
                .filter(GrpcValidation.class::isInstance)
                .count());
    }

    @Test
    void disablesClasspathDiscoveryByConfiguration() {
        Config config = Config.just(ConfigSources.create(Map.of("grpc.grpc-services-discover-services", "false")));
        GrpcRouting routing = GrpcRouting.builder()
                .config(config)
                .build();

        assertEquals(0, routing.interceptors()
                .stream()
                .filter(GrpcValidation.class::isInstance)
                .count());
    }

    @Test
    void mergesLegacyAndCurrentServiceConfigurationBeforeDiscovery() {
        Config legacyDisable = Config.just(ConfigSources.create(Map.of(
                "server.protocols.grpc.grpc-services.validation.enabled", "false",
                "grpc.enable-metrics", "true",
                "grpc.grpc-services-discover-services", "true")));
        GrpcRouting legacyDisableRouting = GrpcRouting.builder()
                .config(legacyDisable)
                .build();

        assertEquals(0, legacyDisableRouting.interceptors()
                .stream()
                .filter(GrpcValidation.class::isInstance)
                .count());

        Config currentOverride = Config.just(ConfigSources.create(Map.of(
                "server.protocols.grpc.grpc-services.validation.enabled", "true",
                "grpc.grpc-services.validation.enabled", "false")));
        GrpcRouting currentOverrideRouting = GrpcRouting.builder()
                .config(currentOverride)
                .build();

        assertEquals(0, currentOverrideRouting.interceptors()
                .stream()
                .filter(GrpcValidation.class::isInstance)
                .count());

        Config distinctNames = Config.just(ConfigSources.create(Map.of(
                "server.protocols.grpc.grpc-services.legacy-validation.type", "validation",
                "grpc.grpc-services.current-validation.type", "validation")));
        GrpcRouting distinctNamesRouting = GrpcRouting.builder()
                .config(distinctNames)
                .build();

        assertEquals(2, distinctNamesRouting.interceptors()
                .stream()
                .filter(GrpcValidation.class::isInstance)
                .count());
    }

    @Test
    void mapsAsynchronousValidationFailureAndPreservesTrailers() {
        TestServerCall call = new TestServerCall();
        AtomicReference<ServerCall<String, String>> interceptedCall = new AtomicReference<>();
        GrpcValidation.create().interceptCall(call, new Metadata(), capturingHandler(interceptedCall));
        Metadata trailers = new Metadata();
        trailers.put(DETAIL, "detail");

        interceptedCall.get().close(Status.UNKNOWN.withCause(new ValidationException("invalid")), trailers);

        assertEquals(Status.Code.INVALID_ARGUMENT, call.status.get().getCode());
        assertEquals("invalid", call.status.get().getDescription());
        assertSame(trailers, call.trailers.get());
        assertEquals("detail", call.trailers.get().get(DETAIL));
    }

    @Test
    void preservesExplicitGrpcStatus() {
        TestServerCall call = new TestServerCall();
        AtomicReference<ServerCall<String, String>> interceptedCall = new AtomicReference<>();
        GrpcValidation.create().interceptCall(call, new Metadata(), capturingHandler(interceptedCall));

        interceptedCall.get().close(Status.PERMISSION_DENIED.withDescription("denied"), new Metadata());

        assertEquals(Status.Code.PERMISSION_DENIED, call.status.get().getCode());
        assertEquals("denied", call.status.get().getDescription());
    }

    @Test
    void mapsValidationFailureWrappedByEntryPointInterceptor() {
        TestServerCall call = new TestServerCall();
        ServerCallHandler<String, String> handler = (ignoredCall, ignoredHeaders) -> {
            throw new ValidationException("invalid entry point");
        };
        ServerInterceptor entryPoint = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> interceptedCall,
                                                                         Metadata headers,
                                                                         ServerCallHandler<ReqT, RespT> next) {
                try {
                    return next.startCall(interceptedCall, headers);
                } catch (ValidationException e) {
                    interceptedCall.close(Status.INTERNAL.withCause(e), new Metadata());
                    return new ServerCall.Listener<>() {
                    };
                }
            }
        };

        GrpcValidation.create().interceptCall(call,
                                              new Metadata(),
                                              (validationCall, headers) -> entryPoint.interceptCall(validationCall,
                                                                                                    headers,
                                                                                                    handler));

        assertEquals(Status.Code.INVALID_ARGUMENT, call.status.get().getCode());
        assertEquals("invalid entry point", call.status.get().getDescription());
    }

    @Test
    void mapsSynchronousListenerValidationFailure() {
        TestServerCall call = new TestServerCall();
        AtomicInteger completeCount = new AtomicInteger();
        ServerCallHandler<String, String> handler = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> ignoredCall, Metadata ignoredHeaders) {
                return new ServerCall.Listener<>() {
                    @Override
                    public void onHalfClose() {
                        throw new ValidationException("invalid listener");
                    }

                    @Override
                    public void onComplete() {
                        completeCount.incrementAndGet();
                    }
                };
            }
        };

        ServerCall.Listener<String> listener = GrpcValidation.create().interceptCall(call, new Metadata(), handler);
        listener.onHalfClose();
        listener.onComplete();

        assertEquals(Status.Code.INVALID_ARGUMENT, call.status.get().getCode());
        assertEquals("invalid listener", call.status.get().getDescription());
        assertEquals(1, call.closeCount.get());
        assertEquals(1, completeCount.get());
    }

    @Test
    void suppressesNonterminalCallbacksAfterValidationFailureAndForwardsCancellation() {
        TestServerCall call = new TestServerCall();
        AtomicInteger halfCloseCount = new AtomicInteger();
        AtomicInteger readyCount = new AtomicInteger();
        AtomicInteger cancelCount = new AtomicInteger();
        ServerCallHandler<String, String> handler = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> ignoredCall, Metadata ignoredHeaders) {
                return new ServerCall.Listener<>() {
                    @Override
                    public void onMessage(String message) {
                        throw new ValidationException("invalid message");
                    }

                    @Override
                    public void onHalfClose() {
                        halfCloseCount.incrementAndGet();
                    }

                    @Override
                    public void onReady() {
                        readyCount.incrementAndGet();
                    }

                    @Override
                    public void onCancel() {
                        cancelCount.incrementAndGet();
                    }
                };
            }
        };

        ServerCall.Listener<String> listener = GrpcValidation.create().interceptCall(call, new Metadata(), handler);
        listener.onMessage("invalid");
        listener.onHalfClose();
        listener.onReady();
        listener.onCancel();

        assertEquals(Status.Code.INVALID_ARGUMENT, call.status.get().getCode());
        assertEquals("invalid message", call.status.get().getDescription());
        assertEquals(1, call.closeCount.get());
        assertEquals(0, halfCloseCount.get());
        assertEquals(0, readyCount.get());
        assertEquals(1, cancelCount.get());
    }

    private static ServerCallHandler<String, String> capturingHandler(
            AtomicReference<ServerCall<String, String>> interceptedCall) {
        return new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata ignored) {
                interceptedCall.set(call);
                return new ServerCall.Listener<>() {
                };
            }
        };
    }

    private static final class TestServerCall extends ServerCall<String, String> {
        private final AtomicInteger closeCount = new AtomicInteger();
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
            closeCount.incrementAndGet();
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
