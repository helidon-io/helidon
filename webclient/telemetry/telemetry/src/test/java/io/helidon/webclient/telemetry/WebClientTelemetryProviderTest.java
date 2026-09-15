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

package io.helidon.webclient.telemetry;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WebClientTelemetryProviderTest {
    private static final String OTEL_SERVICE = "owning-registry-service";
    private static final Config METRICS_CONFIG = Config.just(ConfigSources.create(Map.of("metrics.enabled", "true")));
    private static final Config TRACING_CONFIG = Config.just(ConfigSources.create(Map.of("tracing.enabled", "true")));

    @Test
    void registryCreationUsesOwningRegistryTelemetry() {
        OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
        MeterProvider meterProvider = mock(MeterProvider.class);
        Meter meter = mock(Meter.class);
        DoubleHistogramBuilder histogramBuilder = mock(DoubleHistogramBuilder.class);
        DoubleHistogram histogram = mock(DoubleHistogram.class);
        when(openTelemetry.getMeterProvider()).thenReturn(meterProvider);
        when(meterProvider.get(OTEL_SERVICE)).thenReturn(meter);
        when(meter.histogramBuilder("http.client.request.duration")).thenReturn(histogramBuilder);
        when(histogramBuilder.setDescription("Outbound HTTP request duration")).thenReturn(histogramBuilder);
        when(histogramBuilder.setUnit("s")).thenReturn(histogramBuilder);
        when(histogramBuilder.setExplicitBucketBoundariesAdvice(any())).thenReturn(histogramBuilder);
        when(histogramBuilder.build()).thenReturn(histogram);
        Config rootConfig = Config.just(ConfigSources.create(Map.of("telemetry.service", OTEL_SERVICE)));
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class, rootConfig)
                                                                                .putContractInstance(OpenTelemetry.class,
                                                                                                     openTelemetry)
                                                                                .build());
        try {
            WebClientService service = new WebClientTelemetryProvider()
                    .create(METRICS_CONFIG, "telemetry", manager.registry());
            verifyNoMoreInteractions(openTelemetry);

            service.handle(WebClientTelemetryProviderTest::response,
                           request("http://localhost/metrics", Method.create("get")));

            ArgumentCaptor<Attributes> attributes = ArgumentCaptor.forClass(Attributes.class);
            verify(meterProvider).get(OTEL_SERVICE);
            verify(histogram).record(anyDouble(), attributes.capture());
            assertThat(attributes.getValue().get(AttributeKey.stringKey("http.request.method")), is("_OTHER"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registryCreationUsesOwningRegistryTracer() {
        RecordingTracer tracer = new RecordingTracer();
        AtomicBoolean resolved = new AtomicBoolean();
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        when(serviceRegistry.supply(Tracer.class)).thenReturn(() -> {
            resolved.set(true);
            return tracer;
        });
        WebClientService service = new WebClientTelemetryProvider()
                .create(TRACING_CONFIG, "telemetry", serviceRegistry);

        assertThat(resolved.get(), is(false));

        service.handle(WebClientTelemetryProviderTest::response, request("http://localhost/registry"));

        assertThat(resolved.get(), is(true));
        assertThat(tracer.spanNames(), contains("GET"));
        assertThat(tracer.spanTags().getFirst(), allOf(
                hasEntry("http.request.method", "GET"),
                not(hasKey("http.request.method_original"))));
    }

    @Test
    void tracingClassifiesUnknownMethodAndPreservesOriginal() {
        RecordingTracer tracer = new RecordingTracer();
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        when(serviceRegistry.supply(Tracer.class)).thenReturn(() -> tracer);
        WebClientService service = new WebClientTelemetryProvider()
                .create(TRACING_CONFIG, "telemetry", serviceRegistry);

        service.handle(WebClientTelemetryProviderTest::response,
                       request("http://localhost/unknown", Method.create("get")));

        assertThat(tracer.spanNames(), contains("HTTP"));
        assertThat(tracer.spanTags().getFirst(), allOf(
                hasEntry("http.request.method", "_OTHER"),
                hasEntry("http.request.method_original", "get")));
    }

    @Test
    void requestContextTracerTakesPrecedenceOverOwningRegistryTracer() {
        RecordingTracer registryTracer = new RecordingTracer();
        RecordingTracer contextTracer = new RecordingTracer();
        AtomicBoolean resolved = new AtomicBoolean();
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        when(serviceRegistry.supply(Tracer.class)).thenReturn(() -> {
            resolved.set(true);
            return registryTracer;
        });
        WebClientService service = new WebClientTelemetryProvider()
                .create(TRACING_CONFIG, "telemetry", serviceRegistry);
        WebClientServiceRequest request = request("http://localhost/context");
        request.context().register(contextTracer);

        service.handle(WebClientTelemetryProviderTest::response, request);

        assertThat(resolved.get(), is(false));
        assertThat(contextTracer.spanNames(), contains("GET"));
        assertThat(registryTracer.spanNames(), empty());
    }

    @Test
    void providerDoesNotRetainServicesAcrossClients() {
        WebClientTelemetryProvider provider = new WebClientTelemetryProvider();
        RecordingTracer tracer = new RecordingTracer();
        WebClientServiceRequest request = request("http://localhost/isolated");
        request.context().register(tracer);
        provider.create(TRACING_CONFIG, "first");
        WebClientService second = provider.create(Config.empty(), "second");
        WebClientServiceResponse response = response(request);

        assertThat(second.handle(_ -> response, request), sameInstance(response));
        assertThat(tracer.spanNames(), empty());
    }

    private static WebClientServiceRequest request(String uri) {
        return request(uri, Method.GET);
    }

    private static WebClientServiceRequest request(String uri, Method method) {
        return new TestRequest(uri, method);
    }

    private static WebClientServiceResponse response(WebClientServiceRequest request) {
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientServiceResponse response = WebClientServiceResponse.builder()
                .connection(() -> {
                })
                .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                .status(Status.OK_200)
                .whenComplete(whenComplete)
                .serviceRequest(request)
                .build();
        whenComplete.complete(response);
        return response;
    }

    private static final class TestRequest implements WebClientServiceRequest {
        private final ClientUri uri;
        private final Method method;
        private final ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        private final Context context = Context.create();
        private final CompletableFuture<WebClientServiceRequest> whenSent = CompletableFuture.completedFuture(this);
        private final CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        private String requestId = "test-request";

        private TestRequest(String uri, Method method) {
            this.uri = ClientUri.create(URI.create(uri));
            this.method = method;
        }

        @Override
        public ClientUri uri() {
            return uri;
        }

        @Override
        public Method method() {
            return method;
        }

        @Override
        public String protocolId() {
            return "http/1.1";
        }

        @Override
        public ClientRequestHeaders headers() {
            return headers;
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public String requestId() {
            return requestId;
        }

        @Override
        public void requestId(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public CompletionStage<WebClientServiceRequest> whenSent() {
            return whenSent;
        }

        @Override
        public CompletionStage<WebClientServiceResponse> whenComplete() {
            return whenComplete;
        }

        @Override
        public Map<String, String> properties() {
            return Map.of();
        }
    }

    private static final class RecordingTracer implements Tracer {
        private final Tracer delegate = Tracer.noOp();
        private final List<String> spanNames = new ArrayList<>();
        private final List<Map<String, Object>> spanTags = new ArrayList<>();

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Span.Builder<?> spanBuilder(String name) {
            spanNames.add(name);
            Map<String, Object> tags = new LinkedHashMap<>();
            spanTags.add(tags);
            return new RecordingSpanBuilder(delegate.spanBuilder(name), tags);
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

        private List<Map<String, Object>> spanTags() {
            return spanTags.stream()
                    .map(Map::copyOf)
                    .toList();
        }
    }

    private static final class RecordingSpanBuilder implements Span.Builder<RecordingSpanBuilder> {
        private final Span.Builder<?> delegate;
        private final Map<String, Object> tags;

        private RecordingSpanBuilder(Span.Builder<?> delegate, Map<String, Object> tags) {
            this.delegate = delegate;
            this.tags = tags;
        }

        @Override
        public RecordingSpanBuilder parent(SpanContext spanContext) {
            delegate.parent(spanContext);
            return this;
        }

        @Override
        public RecordingSpanBuilder kind(Span.Kind kind) {
            delegate.kind(kind);
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, String value) {
            delegate.tag(key, value);
            tags.put(key, value);
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, Boolean value) {
            delegate.tag(key, value);
            tags.put(key, value);
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, Number value) {
            delegate.tag(key, value);
            tags.put(key, value);
            return this;
        }

        @Override
        public Span start(Instant instant) {
            return delegate.start(instant);
        }

        @Override
        public Span build() {
            return delegate.build();
        }
    }
}
