/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tracing.jersey.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import javax.inject.Provider;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.tracing.spi.TracerProvider;
import io.helidon.webserver.ServerRequest;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

/**
 * This filter adds tracing information the the associated JAX-RS client call based on the provided properties.
 * <p>
 * In order to inject the tracing information properly, {@link io.opentracing.Tracer} and an optional parent
 * {@link io.opentracing.SpanContext}
 * needs to be resolved. The {@link io.opentracing.Tracer} gets resolved in following order
 * <ol>
 * <li>Directly from property {@link #TRACER_PROPERTY_NAME}</li>
 * <li>From {@link io.helidon.webserver.ServerConfiguration#tracer()} </li>
 * <li>Finally, a global tracer is tried by calling {@link io.opentracing.util.GlobalTracer#get()}</li>
 * </ol>
 * The {@link io.opentracing.SpanContext} as a parent span is resolved as follows
 * <ol>
 * <li>Directly from property {@link #CURRENT_SPAN_CONTEXT_PROPERTY_NAME}</li>
 * <li>From {@link ServerRequest#spanContext()} ()}</li>
 * </ol>
 * For each client call, a new {@link io.opentracing.Span} with operation name {@code jersey-client-call} is created based on the
 * resolved {@link io.opentracing.Tracer} and an optional parent {@link io.opentracing.Span}.
 * <p>
 * If {@link io.opentracing.Tracer} doesn't get resolved, a warning is logged.
 */
public class TracingClientFilter implements ClientRequestFilter, ClientResponseFilter {
    private static final String SPAN_PROPERTY_NAME = TracingClientFilter.class.getName() + ".span";
    private final Optional<TracerProvider> tracerProvider;

    /**
     * The {@link Tracer} property reference name.
     */
    public static final String TRACER_PROPERTY_NAME = "io.helidon.tracing.tracer";
    /**
     * The {@link SpanContext} reference property name.
     */
    public static final String CURRENT_SPAN_CONTEXT_PROPERTY_NAME = "io.helidon.tracing.span-context";

    @Context
    private Provider<SpanContext> spanContext;

    @Context
    private Provider<ServerRequest> serverRequest;

    public TracingClientFilter() {
        Iterator<TracerProvider> iterator = ServiceLoader.load(TracerProvider.class)
                .iterator();

        if (iterator.hasNext()) {
            tracerProvider = Optional.of(iterator.next());
        } else {
            tracerProvider = Optional.empty();
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        Optional<ServerRequest> maybeRequest = findRequest();
        Optional<SpanContext> parentSpan = findParentSpan(requestContext, maybeRequest);
        Tracer tracer = findTracer(requestContext, maybeRequest);
        Map<String, List<String>> inboundHeaders = maybeRequest.map(req -> req.headers().toMap())
                .orElse(CollectionsHelper.mapOf());

        Span currentSpan = createSpan(requestContext, tracer, parentSpan);

        requestContext.setProperty(SPAN_PROPERTY_NAME, currentSpan);

        Map<String, List<String>> tracingHeaders = tracingHeaders(tracer, currentSpan);
        Map<String, List<String>> outboundHeaders = tracerProvider
                .map(provider -> provider.updateOutboundHeaders(currentSpan,
                                                                tracer,
                                                                parentSpan.orElse(null),
                                                                tracingHeaders,
                                                                inboundHeaders))
                .orElse(tracingHeaders);

        MultivaluedMap<String, Object> headers = requestContext.getHeaders();
        outboundHeaders.forEach((key, value) -> headers.put(key, new ArrayList<>(value)));
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Object property = requestContext.getProperty(SPAN_PROPERTY_NAME);

        if (property instanceof Span) {
            Span span = (Span) property;
            int status = responseContext.getStatus();
            Tags.HTTP_STATUS.set(span, status);
            if (status >= 400) {
                Tags.ERROR.set(span, true);
                span.log(CollectionsHelper.mapOf("event", "error",
                                                 "message", "Response HTTP status: " + status,
                                                 "error.kind", (status < 500) ? "ClientError" : "ServerError"));
            }
            span.finish();
        }
    }

    private Optional<SpanContext> findParentSpan(ClientRequestContext requestContext, Optional<ServerRequest> maybeRequest) {
        return OptionalHelper
                // from client property
                .from(property(requestContext, SpanContext.class, CURRENT_SPAN_CONTEXT_PROPERTY_NAME))
                // from injected span context
                .or(this::findSpanContext)
                // from injected server request
                .or(() -> maybeRequest.map(ServerRequest::spanContext))
                .asOptional();
    }

    private Optional<SpanContext> findSpanContext() {
        try {
            return Optional.of(spanContext.get());
        } catch (Exception ignored) {
            // not in request scope or not injected
            return Optional.empty();
        }
    }

    private Optional<ServerRequest> findRequest() {
        try {
            return Optional.of(serverRequest.get());
        } catch (Exception ignored) {
            // not in request scope
            return Optional.empty();
        }
    }

    private Tracer findTracer(ClientRequestContext requestContext,
                              Optional<ServerRequest> maybeRequest) {
        return OptionalHelper.from(property(requestContext, Tracer.class, TRACER_PROPERTY_NAME))
                .or(() -> maybeRequest.map(req -> req.webServer().configuration().tracer()))
                .asOptional()
                .orElseGet(GlobalTracer::get);
    }

    private static <T> Optional<T> property(ClientRequestContext requestContext, Class<T> clazz, String propertyName) {
        return OptionalHelper.from(Optional.empty())
                .or(() -> Optional.ofNullable(requestContext.getProperty(propertyName))
                        .filter(clazz::isInstance))
                .or(() -> Optional.ofNullable(requestContext.getConfiguration().getProperty(propertyName))
                        .filter(clazz::isInstance))
                .asOptional()
                .map(clazz::cast);
    }

    private Map<String, List<String>> tracingHeaders(Tracer tracer, Span currentSpan) {
        Map<String, String> result = new HashMap<>();

        tracer.inject(currentSpan.context(),
                      Format.Builtin.HTTP_HEADERS,
                      new TextMapInjectAdapter(result));

        return result.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          entry -> CollectionsHelper.listOf(entry.getValue())));
    }

    private Span createSpan(ClientRequestContext requestContext, Tracer tracer, Optional<SpanContext> parentSpan) {
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan("jersey-client-call")
                .withTag(Tags.HTTP_METHOD.getKey(), requestContext.getMethod())
                .withTag(Tags.HTTP_URL.getKey(), requestContext.getUri().toString());

        parentSpan.ifPresent(spanBuilder::asChildOf);

        return spanBuilder.start();
    }
}
