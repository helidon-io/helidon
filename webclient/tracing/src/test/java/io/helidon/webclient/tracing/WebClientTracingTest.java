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

package io.helidon.webclient.tracing;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
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
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

@Testing.Test(perMethod = true)
class WebClientTracingTest {

    @Test
    void defaultTracingServiceCachesRegistryTracerPerInstance() {
        RecordingTracer firstTracer = new RecordingTracer();
        RecordingTracer secondTracer = new RecordingTracer();
        ServiceRegistryManager firstManager = registryManager(firstTracer);
        ServiceRegistryManager secondManager = registryManager(secondTracer);
        WebClientService service = WebClientTracing.create();

        try {
            Services.registry(firstManager.registry());
            service.handle(WebClientTracingTest::response, request("http://localhost/first"));

            Services.registry(secondManager.registry());
            service.handle(WebClientTracingTest::response, request("http://localhost/second"));

            assertThat("First registry tracer",
                       firstTracer.spanNames(),
                       contains("GET-http://localhost:80/first", "GET-http://localhost:80/second"));
            assertThat("Second registry tracer", secondTracer.spanNames(), empty());
        } finally {
            firstManager.shutdown();
            secondManager.shutdown();
        }
    }

    private static ServiceRegistryManager registryManager(Tracer tracer) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .putContractInstance(Tracer.class, tracer)
                                                     .build());
    }

    private static WebClientServiceRequest request(String uri) {
        return new TestRequest(uri);
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
        private final ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        private final Context context = Context.create();
        private final CompletableFuture<WebClientServiceRequest> whenSent = CompletableFuture.completedFuture(this);
        private final CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        private String requestId = "test-request";

        private TestRequest(String uri) {
            this.uri = ClientUri.create(URI.create(uri));
        }

        @Override
        public ClientUri uri() {
            return uri;
        }

        @Override
        public Method method() {
            return Method.GET;
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
