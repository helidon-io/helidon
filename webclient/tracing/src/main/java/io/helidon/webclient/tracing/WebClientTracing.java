/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Single;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tag;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.WebClientRequestHeaders;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.spi.WebClientService;

/**
 * Client service for tracing propagation.
 */
public final class WebClientTracing implements WebClientService {
    private static final int HTTP_STATUS_ERROR_THRESHOLD = 400;
    private static final int HTTP_STATUS_SERVER_ERROR_THRESHOLD = 500;

    private WebClientTracing() {
    }

    /**
     * Creates new instance of client tracing service.
     *
     * @return client tracing service
     */
    public static WebClientTracing create() {
        return new WebClientTracing();
    }

    @Override
    public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
        String method = request.method().name().toUpperCase();
        Optional<Tracer> optionalTracer = request.context().get(Tracer.class);
        Tracer tracer = optionalTracer.orElseGet(Tracer::global);

        Span.Builder spanBuilder = tracer.spanBuilder(composeName(method, request));

        request.context().get(SpanContext.class).ifPresent(spanBuilder::parent);

        spanBuilder.kind(Span.Kind.CLIENT);
        spanBuilder.tag(Tag.COMPONENT.create("helidon-webclient"));
        spanBuilder.tag(Tag.HTTP_METHOD.create(method));
        spanBuilder.tag(Tag.HTTP_URL.create(request.uri().toString()));
        Span span = spanBuilder.start();

        request.context().register(span.context());

        tracer.inject(span.context(),
                      HeaderProvider.empty(),
                      new ClientHeaderConsumer(request.headers()));

        request.whenResponseReceived()
                .thenAccept(response -> {
                    int status = response.status().code();
                    span.tag(Tag.HTTP_STATUS.create(status));

                    if (status >= HTTP_STATUS_ERROR_THRESHOLD) {
                        span.status(Span.Status.ERROR);

                        span.addEvent("error", Map.of("message",
                                                      "Response HTTP status: " + status,
                                                      "error.kind",
                                                      (status < HTTP_STATUS_SERVER_ERROR_THRESHOLD)
                                                              ? "ClientError"
                                                              : "ServerError"));
                    }
                    span.end();
                })
                .exceptionallyAccept(span::end);

        return Single.just(request);
    }

    private String composeName(String method, WebClientServiceRequest request) {
        return method
                + "-"
                + request.schema() + "://"
                + request.host() + ":"
                + request.port()
                + request.path().toString();
    }

    private static class ClientHeaderConsumer implements HeaderConsumer {
        private final WebClientRequestHeaders headers;
        private final LazyValue<Map<String, List<String>>> headerMap;

        private ClientHeaderConsumer(WebClientRequestHeaders headers) {
            this.headers = headers;
            this.headerMap = LazyValue.create(headers::toMap);
        }

        @Override
        public void setIfAbsent(String key, String... values) {
            headers.putIfAbsent(key, values);
        }

        @Override
        public void set(String key, String... values) {
            headers.put(key, values);
        }

        @Override
        public Iterable<String> keys() {
            return headerMap.get().keySet();
        }

        @Override
        public Optional<String> get(String key) {
            return headers.first(key);
        }

        @Override
        public Iterable<String> getAll(String key) {
            return headers.all(key);
        }

        @Override
        public boolean contains(String key) {
            return headerMap.get().containsKey(key);
        }
    }
}
