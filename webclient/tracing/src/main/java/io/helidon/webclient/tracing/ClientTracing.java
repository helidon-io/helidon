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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.webclient.ClientServiceRequest;
import io.helidon.webclient.spi.ClientService;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.util.GlobalTracer;

/**
 * Client service for tracing propagation.
 */
public class ClientTracing implements ClientService {

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "WebClient", "Tracing");
    }

    private ClientTracing() {
    }

    /**
     * Creates new instance of client tracing service.
     *
     * @return client tracing service
     */
    public static ClientTracing create() {
        return new ClientTracing();
    }

    @Override
    public CompletionStage<ClientServiceRequest> request(ClientServiceRequest request) {
        Optional<Tracer> optionalTracer = request.context().get(Tracer.class);
        Tracer tracer = optionalTracer.orElseGet(GlobalTracer::get);

        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(request.method().name().toUpperCase() + "-" + request.path());
        request.context().get(SpanContext.class).ifPresent(spanBuilder::asChildOf);

        Span span = spanBuilder.start();
        request.context().register(span.context());

        Map<String, String> tracerHeaders = new HashMap<>();

        tracer.inject(span.context(),
                      Format.Builtin.HTTP_HEADERS,
                      new TextMapAdapter(tracerHeaders));

        tracerHeaders.forEach((name, value) -> request.headers().put(name, value));

        request.whenComplete().thenRun(span::finish);

        return CompletableFuture.completedFuture(request);
    }
}
