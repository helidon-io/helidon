/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tracing.opentelemetry;

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.nima.webserver.http.Filter;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.RoutingRequest;
import io.helidon.nima.webserver.http.RoutingResponse;
import io.helidon.nima.webserver.http.ServerRequest;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

/**
 * Open Telemetry tracing support.
 */
public class OpenTelemetryTracingSupport {
    private final boolean enabled;
    private final Filter filter;

    private OpenTelemetryTracingSupport(Filter filter) {
        this.enabled = true;
        this.filter = filter;
    }

    private OpenTelemetryTracingSupport() {
        this.enabled = false;
        this.filter = null;
    }

    /**
     * Create new support from an open telemetry instance and a chosen tracer.
     *
     * @param telemetry open telemetry
     * @param tracer    tracer
     * @return new tracing support
     */
    public static OpenTelemetryTracingSupport create(OpenTelemetry telemetry, Tracer tracer) {
        if (tracer.getClass().getSimpleName().equals("DefaultTracer")) {
            return new OpenTelemetryTracingSupport();
        }
        return new OpenTelemetryTracingSupport(new TracingFilter(telemetry, tracer));
    }

    /**
     * Register tracing support filter with the HTTP routing.
     *
     * @param builder routing builder
     */
    public void register(HttpRouting.Builder builder) {
        if (enabled) {
            builder.addFilter(filter);
        }
    }

    private static class TracingFilter implements Filter {
        private final Tracer tracer;
        private final TextMapPropagator propagator;

        private TracingFilter(OpenTelemetry telemetry, Tracer tracer) {
            this.tracer = tracer;
            this.propagator = telemetry.getPropagators().getTextMapPropagator();
        }

        @Override
        public void handle(RoutingRequest req, RoutingResponse res) {
            Context inboundContext = propagator.extract(Context.current(), req, new NimaTextMapGetter());

            HttpPrologue prologue = req.prologue();

            // we have current span parent - either null or actual span context
            // for now, let's hardcode stuff
            inboundContext.makeCurrent();
            Span span = tracer.spanBuilder(prologue.method()
                                                   + " " + req.path().rawPath()
                                                   + " " + prologue.protocol()
                                                   + "/" + prologue.protocolVersion())
                    .setSpanKind(SpanKind.SERVER)
                    .startSpan();

            Scope scope = span.makeCurrent();

            span.setAttribute(SemanticAttributes.HTTP_METHOD, prologue.method().text());
            // TODO we need to check if request was HTTPS
            span.setAttribute(SemanticAttributes.HTTP_SCHEME, prologue.protocol());
            span.setAttribute(SemanticAttributes.HTTP_HOST, req.authority());
            span.setAttribute(SemanticAttributes.HTTP_TARGET, prologue.uriPath().rawPath());
            span.setAttribute("http.version", prologue.protocolVersion());

            res.whenSent(() -> {
                Http.Status status = res.status();
                span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, status.code());

                if (status.code() >= 400) {
                    span.setStatus(StatusCode.ERROR);
                } else {
                    span.setStatus(StatusCode.OK);
                }

                scope.close();
                span.end();
            });
        }

        private static class NimaTextMapGetter implements TextMapGetter<ServerRequest> {
            @Override
            public Iterable<String> keys(ServerRequest carrier) {
                List<String> result = new LinkedList<>();
                for (Http.HeaderValue header : carrier.headers()) {
                    result.add(header.headerName().lowerCase());
                }
                return result;
            }

            @Override
            public String get(ServerRequest carrier, String key) {
                if (carrier == null) {
                    return null;
                }

                Headers headers = carrier.headers();
                Http.HeaderName header = Http.Header.create(key);
                if (headers.contains(header)) {
                    return headers.get(header).value();
                }

                return null;
            }
        }
    }
}
