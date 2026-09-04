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

package io.helidon.webclient.grpc.tracing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.grpc.GrpcClientConfig;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@Testing.Test(perMethod = true)
class GrpcClientTracingInterceptorTest {
    @Test
    void registryConfiguredTracingUsesOwningTracer() {
        RecordingTracer tracer = new RecordingTracer();
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Tracer.class, tracer)
                                                                                .build());
        try {
            Config config = Config.just(ConfigSources.create(Map.of("grpc-services.tracing.enabled", "true")));
            GrpcClientConfig clientConfig = GrpcClientConfig.builder()
                    .config(config)
                    .serviceRegistry(manager.registry())
                    .buildPrototype();
            ClientInterceptor interceptor = clientConfig.grpcServices()
                    .stream()
                    .filter(service -> service.type().equals("tracing"))
                    .findFirst()
                    .orElseThrow()
                    .interceptors()
                    .iterator()
                    .next();
            invoke(interceptor);

            assertThat(tracer.spanNames(), contains("test.Service-test.Service/Call"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void directTracingUsesStaticTracer() {
        RecordingTracer tracer = new RecordingTracer();
        ClientInterceptor interceptor = GrpcClientTracing.create(Config.empty())
                .interceptors()
                .iterator()
                .next();
        Services.set(Tracer.class, tracer);

        invoke(interceptor);

        assertThat(tracer.spanNames(), contains("test.Service-test.Service/Call"));
    }

    @Test
    void testMetadataLoggingDoesNotIncludeValues() {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer secret-token");
        metadata.put(Metadata.Key.of("x-safe", Metadata.ASCII_STRING_MARSHALLER), "visible-value");

        String logged = GrpcClientTracingInterceptor.loggableMetadata(metadata);

        assertThat(logged, containsString("authorization"));
        assertThat(logged, containsString("x-safe"));
        assertThat(logged, not(containsString("Bearer secret-token")));
        assertThat(logged, not(containsString("visible-value")));
    }

    private static void invoke(ClientInterceptor interceptor) {
        MethodDescriptor<String, String> method = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Service/Call")
                .setRequestMarshaller(StringMarshaller.INSTANCE)
                .setResponseMarshaller(StringMarshaller.INSTANCE)
                .build();
        ClientCall<String, String> call = interceptor.interceptCall(method, CallOptions.DEFAULT, new NoOpChannel());
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());
        call.halfClose();
    }

    private enum StringMarshaller implements MethodDescriptor.Marshaller<String> {
        INSTANCE;

        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class NoOpChannel extends Channel {
        @Override
        public <ReqT, ResT> ClientCall<ReqT, ResT> newCall(MethodDescriptor<ReqT, ResT> methodDescriptor,
                                                           CallOptions callOptions) {
            return new NoOpClientCall<>();
        }

        @Override
        public String authority() {
            return "test";
        }
    }

    private static final class NoOpClientCall<ReqT, ResT> extends ClientCall<ReqT, ResT> {
        @Override
        public void start(Listener<ResT> responseListener, Metadata headers) {
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(ReqT message) {
        }
    }

    private static final class RecordingTracer implements Tracer {
        private final Tracer delegate = Tracer.noOp();
        private final List<String> spanNames = new ArrayList<>();

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Span.Builder<?> spanBuilder(String name) {
            spanNames.add(name);
            return delegate.spanBuilder(name);
        }

        @Override
        public Optional<SpanContext> extract(HeaderProvider headersProvider) {
            return delegate.extract(headersProvider);
        }

        @Override
        public void inject(SpanContext spanContext,
                           HeaderProvider inboundHeadersProvider,
                           HeaderConsumer outboundHeadersConsumer) {
            delegate.inject(spanContext, inboundHeadersProvider, outboundHeadersConsumer);
        }

        @Override
        public Tracer register(SpanListener listener) {
            return this;
        }

        private List<String> spanNames() {
            return List.copyOf(spanNames);
        }
    }
}
