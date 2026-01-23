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

package io.helidon.webclient.telemetry.tracing;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.service.registry.Services;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * Implementation of OpenTelemetry semantic conventions for client tracing.
 */
public class WebClientTelemetryTracing implements WebClientService {

    static final String HTTP_REQUEST_METHOD = "http.client.request.method";
    static final String SERVER_ADDRESS = "server.address";
    static final String SERVER_PORT = "server.port";
    static final String URL_FULL =  "url.full";
    static final String ERROR_TYPE = "error.type";
    static final String HTTP_RESPONSE_STATUS_CODE = "http.response.status.code";

    private final Function<Context, Tracer> tracerFunction;

    private WebClientTelemetryTracing() {
        tracerFunction = ctx -> ctx.get(Tracer.class)
                .orElseGet(() -> Services.get(Tracer.class));
    }

    /**
     * Creates a new service instance to emit tracing spans compliant with OpenTelemetry semantic conventions
     * for clients.
     *
     * @return new tracing semantic conventions implementation
     */
    public static WebClientTelemetryTracing create() {
        return new WebClientTelemetryTracing();
    }

    /**
     * Creates a new service instance to emit tracing spans compliant with OpenTelemetry semantic conventions
     * for clients using the provided configuration.
     *
     * @param config telemetry tracing config
     * @return new tracing semantic conventions implementation
     */
    public static WebClientTelemetryTracing create(Config config) {
        return create();
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest clientRequest) {

        var tracer = tracerFunction.apply(clientRequest.context());

        var spanBuilder = tracer.spanBuilder(spanName(clientRequest));
        clientRequest.context().get(SpanContext.class).ifPresent(spanBuilder::parent);

        spanBuilder.kind(Span.Kind.CLIENT)
                .tag(HTTP_REQUEST_METHOD, clientRequest.method().text())
                .tag(SERVER_ADDRESS, clientRequest.uri().host())
                .tag(SERVER_PORT, clientRequest.uri().port())
                .tag(URL_FULL, clientRequest.uri().toString());

        var span = spanBuilder.start();
        clientRequest.context().register(span.context());
        tracer.inject(span.context(),
                            HeaderProvider.empty(),
                            new ClientHeaderConsumer((clientRequest.headers())));

        try {
            var response = chain.proceed(clientRequest);
            Status status = response.status();
            span.tag(HTTP_RESPONSE_STATUS_CODE, status.code())
                    .tag(ERROR_TYPE, status.family() == Status.Family.SUCCESSFUL ? "" : status.codeText());
            span.end();
            return response;

        } catch (Exception ex) {
            span.tag(ERROR_TYPE, ex.getClass().getSimpleName());
            span.end(ex);
            throw ex;
        }
    }

    private String spanName(WebClientServiceRequest clientRequest) {
        /*
        If we could, we would retrieve the URI template from the request and combine the HTTP method with
        it. Given that we cannot, just use the method because the semantic conventions prohibit using the path in the span name.
         */
        return clientRequest.method().text();
    }

    private static class ClientHeaderConsumer implements HeaderConsumer {
        private final ClientRequestHeaders headers;

        private ClientHeaderConsumer(ClientRequestHeaders headers) {
            this.headers = headers;
        }

        @Override
        public void setIfAbsent(String key, String... values) {
            HeaderName name = HeaderNames.create(key);
            if (!headers.contains(name)) {
                headers.set(name, values);
            }
        }

        @Override
        public void set(String key, String... values) {
            headers.set(HeaderValues.create(key, values));
        }

        @Override
        public Iterable<String> keys() {
            return headers.stream()
                    .map(Header::name)
                    .toList();
        }

        @Override
        public Optional<String> get(String key) {
            return headers.first(HeaderNames.create(key));
        }

        @Override
        public Iterable<String> getAll(String key) {
            return headers.all(HeaderNames.create(key), List::of);
        }

        @Override
        public boolean contains(String key) {
            return headers.contains(HeaderNames.create(key));
        }
    }
}
