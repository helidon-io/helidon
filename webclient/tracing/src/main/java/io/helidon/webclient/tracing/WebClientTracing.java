/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.spi.WebClientService;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

/**
 * Client service for tracing propagation.
 */
public final class WebClientTracing implements WebClientService {
    private static final int HTTP_STATUS_ERROR_THRESHOLD = 400;
    private static final int HTTP_STATUS_SERVER_ERROR_THRESHOLD = 500;

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "WebClient", "Tracing");
    }

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
        Tracer tracer = optionalTracer.orElseGet(GlobalTracer::get);

        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(method
                                                                  + "-"
                                                                  + request.uri());

        request.context().get(SpanContext.class).ifPresent(spanBuilder::asChildOf);

        Span span = spanBuilder.start();
        Tags.COMPONENT.set(span, "helidon-webclient");
        Tags.HTTP_METHOD.set(span, method);
        Tags.HTTP_URL.set(span, request.uri().toString());

        request.context().register(span.context());

        Map<String, String> tracerHeaders = new HashMap<>();

        tracer.inject(span.context(),
                      Format.Builtin.HTTP_HEADERS,
                      new TextMapAdapter(tracerHeaders));

        tracerHeaders.forEach((name, value) -> request.headers().put(name, value));

        request.whenResponseReceived().thenAccept(response -> {
            int status = response.status().code();
            Tags.HTTP_STATUS.set(span, status);
            if (status >= HTTP_STATUS_ERROR_THRESHOLD) {
                Tags.ERROR.set(span, true);
                span.log(Map.of("event", "error",
                                "message", "Response HTTP status: " + status,
                                "error.kind", (status < HTTP_STATUS_SERVER_ERROR_THRESHOLD) ? "ClientError" : "ServerError"));
            }
            span.finish();
        });

        return Single.just(request);
    }
}
