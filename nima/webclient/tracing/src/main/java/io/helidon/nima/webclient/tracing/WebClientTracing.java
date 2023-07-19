/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.tracing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.context.Context;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;
import io.helidon.nima.webclient.spi.WebClientService;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tag;
import io.helidon.tracing.Tracer;

/**
 * Client service for tracing propagation.
 * This service adds appropriate headers to the outbound request to propagate trace across services, so the spans
 * can form a hierarchy in a tracer, such as Jaeger.
 */
public class WebClientTracing implements WebClientService {
    private final Function<Context, Tracer> tracerFunction;

    private WebClientTracing() {
        tracerFunction = ctx -> ctx.get(Tracer.class)
                .orElseGet(Tracer::global);
    }

    private WebClientTracing(Tracer tracer) {
        this.tracerFunction = ctx -> tracer;
    }

    /**
     * Creates new instance of client tracing service.
     *
     * @return client tracing service
     */
    public static WebClientService create() {
        return new WebClientTracing();
    }

    /**
     * Creates a new client tracing service for a specific tracer.
     *
     * @param tracer to create new spans
     * @return client tracing service
     */
    public static WebClientService create(Tracer tracer) {
        return new WebClientTracing(tracer);
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        String method = request.method().text();

        Tracer tracer = tracerFunction.apply(request.context());

        ClientUri uriInfo = request.uri();
        String url = uriInfo.scheme() + "://" + uriInfo.authority() + uriInfo.path();
        Span.Builder spanBuilder = tracer.spanBuilder(method + "-" + url);

        request.context().get(SpanContext.class).ifPresent(spanBuilder::parent);

        spanBuilder.kind(Span.Kind.CLIENT);
        spanBuilder.tag(Tag.COMPONENT.create("helidon-webclient"));
        spanBuilder.tag(Tag.HTTP_METHOD.create(method));
        spanBuilder.tag(Tag.HTTP_URL.create(url));
        Span span = spanBuilder.start();

        request.context().register(span.context());

        tracer.inject(span.context(),
                      HeaderProvider.empty(),
                      new ClientHeaderConsumer(request.headers()));

        try {
            WebClientServiceResponse response = chain.proceed(request);
            Http.Status status = response.status();
            span.tag(Tag.HTTP_STATUS.create(status.code()));

            Http.Status.Family family = status.family();

            if (status.code() >= 400) {
                String errorKind = family == Http.Status.Family.CLIENT_ERROR ? "ClientError" : "ServerError";
                span.addEvent("error", Map.of("message", "Response HTTP status: " + status,
                                              "error.kind", errorKind));

            }

            span.end();

            return response;
        } catch (Throwable e) {
            span.end(e);
            throw e;
        }
    }

    private String composeName(String method, WebClientServiceRequest request) {
        ClientUri uri = request.uri();
        return method
                + "-"
                + uri.pathWithQueryAndFragment(UriQuery.empty(), UriFragment.empty());
    }

    private static class ClientHeaderConsumer implements HeaderConsumer {
        private final ClientRequestHeaders headers;

        private ClientHeaderConsumer(ClientRequestHeaders headers) {
            this.headers = headers;
        }

        @Override
        public void setIfAbsent(String key, String... values) {
            headers.setIfAbsent(Http.Header.create(Http.Header.create(key), values));
        }

        @Override
        public void set(String key, String... values) {
            headers.set(Http.Header.create(Http.Header.create(key), values));
        }

        @Override
        public Iterable<String> keys() {
            return headers.stream()
                    .map(Http.HeaderValue::name)
                    .toList();
        }

        @Override
        public Optional<String> get(String key) {
            return headers.first(Http.Header.create(key));
        }

        @Override
        public Iterable<String> getAll(String key) {
            return headers.all(Http.Header.create(key), List::of);
        }

        @Override
        public boolean contains(String key) {
            return headers.contains(Http.Header.create(key));
        }
    }
}
