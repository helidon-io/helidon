/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.opentracing;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

import io.helidon.common.CollectionsHelper;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.ServerRequest;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

import static io.helidon.common.CollectionsHelper.listOf;

/**
 * This filter adds tracing information the the associated JAX-RS client call based on the provided properties.
 * <p>
 * In order to inject the tracing information properly, {@link Tracer} and an optional parent {@link SpanContext}
 * needs to be resolved. The {@link Tracer} gets resolved in following order
 * <ol>
 * <li>Directly from property {@link #TRACER_PROPERTY_NAME}</li>
 * <li>From {@link ServerConfiguration#tracer()} referenced by property {@link #SERVER_REQUEST_PROPERTY_NAME} where
 * an instance of {@link ServerRequest} is expected</li>
 * <li>Finally, a global tracer is tried by calling {@link GlobalTracer#get()}</li>
 * </ol>
 * The {@link SpanContext} as a parent span is resolved as follows
 * <ol>
 * <li>Directly from property {@link #CURRENT_SPAN_CONTEXT_PROPERTY_NAME}</li>
 * <li>From {@link ServerRequest#spanContext()} ()} referenced by property {@link #SERVER_REQUEST_PROPERTY_NAME} where
 * an instance of {@link ServerRequest} is expected</li>
 * </ol>
 * For each client call, a new {@link Span} with operation name {@code jersey-client-call} is created based on the
 * resolved {@link Tracer} and an optional parent {@link Span}.
 * <p>
 * If {@link Tracer} doesn't get resolved, a warning is logged.
 *
 * @see Opentraceable
 */
public class OpentracingClientFilter implements ClientRequestFilter, ClientResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(OpentracingClientFilter.class.getName());

    /** The {@link ServerRequest} reference property name. */
    public static final String SERVER_REQUEST_PROPERTY_NAME = ServerRequest.class.getName();
    /** The {@link Tracer} property reference name. */
    public static final String TRACER_PROPERTY_NAME = "io.helidon.tracing.tracer";
    /** The {@link SpanContext} reference property name. */
    public static final String CURRENT_SPAN_CONTEXT_PROPERTY_NAME = "io.helidon.tracing.span-context";

    static final String X_B3_TRACE_ID = "x-b3-traceid";
    static final String X_B3_SPAN_ID = "x-b3-spanid";
    static final String X_B3_PARENT_SPAN_ID = "x-b3-parentspanid";
    static final String X_OT_SPAN_CONTEXT = "x-ot-span-context";
    static final String X_REQUEST_ID = "x-request-id";
    static final String X_B3_SAMPLED = "x-b3-sampled";
    static final String X_B3_FLAGS = "x-b3-flags";

    private static final List<String> TRACING_CONTEXT_PROPAGATION_HEADERS =
            listOf(X_REQUEST_ID, X_B3_TRACE_ID, X_B3_SPAN_ID, X_B3_PARENT_SPAN_ID, X_B3_SAMPLED, X_B3_FLAGS, X_OT_SPAN_CONTEXT);

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {

        Optional<ServerRequest> requestOptional = Optional.ofNullable(
                property(requestContext, ServerRequest.class, SERVER_REQUEST_PROPERTY_NAME)
                        .orElse(null));
        Optional<Tracer> tracerOptional = property(requestContext, Tracer.class, TRACER_PROPERTY_NAME);
        Optional<SpanContext> spanContextOptional = property(requestContext,
                                                             SpanContext.class,
                                                             CURRENT_SPAN_CONTEXT_PROPERTY_NAME);

        Tracer tracer = tracerOptional.orElse(requestOptional.map(req -> req.webServer().configuration().tracer())
                                                             .orElse(GlobalTracer.get()));

        if (tracer == null) {
            LOGGER.warning(() -> "Cannot propagate tracing context to the client call! Tracer not found.");
            return;
        }

        SpanContext parentSpan = spanContextOptional.orElse(requestOptional.map(ServerRequest::spanContext).orElse(null));

        Tracer.SpanBuilder spanBuilder = tracer.buildSpan("jersey-client-call")
                                               .withTag(Tags.HTTP_METHOD.getKey(), requestContext.getMethod())
                                               .withTag(Tags.HTTP_URL.getKey(), requestContext.getUri().toString());
        if (parentSpan != null) {
            spanBuilder.asChildOf(parentSpan);
        }
        Span currentSpan = spanBuilder.start();

        requestContext.setProperty(Span.class.getName(), currentSpan);

        Map<String, String> map = propagateTracing(new TreeMap<>(String.CASE_INSENSITIVE_ORDER), tracer, currentSpan.context());

        map.forEach((s, s2) -> LOGGER.fine(() -> "Opentracing inject: " + s + " = " + s2));

        Map<String, List<String>> incomingHeaders = requestOptional.map(sr -> sr.headers().toMap()).orElse(null);
        for (Map.Entry<String, String> entry : propagateTracingContext(map, incomingHeaders).entrySet()) {
            requestContext.getHeaders().putSingle(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Object property = requestContext.getProperty(Span.class.getName());
        if (property != null && property instanceof Span) {
            Span span = (Span) property;
            int status = responseContext.getStatus();
            Tags.HTTP_STATUS.set(span, status);
            if (status >= 400) {
                Tags.ERROR.set(span, true);
                span.log(CollectionsHelper.mapOf("event", "error",
                                "message", "Response HTTP status: " + status,
                                "error.kind", status < 500 ? "ClientError" : "ServerError"));
            }
            span.finish();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> property(ClientRequestContext requestContext, Class<T> clazz, String propertyName) {
        Optional<T> first = Optional.ofNullable(requestContext.getProperty(propertyName))
                                    .filter(o -> clazz.isAssignableFrom(o.getClass()))
                                    .map(o -> (T) o);
        Optional<T> second = Optional.ofNullable(requestContext.getConfiguration().getProperty(propertyName))
                                     .filter(o -> clazz.isAssignableFrom(o.getClass()))
                                     .map(o -> (T) o);

        return Optional.ofNullable(first.orElse(second.orElse(null)));
    }

    /**
     * Propagates the tracing context as a list of headers as declared at {@link #TRACING_CONTEXT_PROPAGATION_HEADERS}.
     *
     * @param target the target map where to propagate the Istio tracing context
     * @param source the http headers as a source of the Istio tracing context
     * @return the {@code map} with the Istio tracing context
     */
    private static Map<String, String> propagateTracingContext(Map<String, String> target, Map<String, List<String>> source) {
        if (target == null) {
            target = new HashMap<>();
        }
        if (source == null) {
            return target;
        }

        for (String istioHeader : TRACING_CONTEXT_PROPAGATION_HEADERS) {
            List<String> value = source.get(istioHeader);
            boolean hasHeader = target.containsKey(istioHeader);
            if (!hasHeader && value != null && !value.isEmpty()) {
                String s = value.get(0);
                LOGGER.fine(() -> "Tracing Context inject: " + istioHeader + " = " + s);
                target.put(istioHeader, s);
            } else {
                LOGGER.fine(() -> "Skipping Tracing Context header: " + istioHeader + " (already present: " + hasHeader + ")");
            }
        }

        fixXOtSpanContext(target);

        return target;
    }

    /**
     * Updates the {@link #X_OT_SPAN_CONTEXT} with the current tracing context. This header
     * is used by the tracing proxy (e.g., Envoy) to correlate the tracing between services.
     * <p>
     * The format of {@link #X_OT_SPAN_CONTEXT} is: <code>{@literal <}trace-id{@literal
     * >};{@literal <}span-id{@literal >};{@literal <}parent-span-id{@literal >};{@literal <}flags{@literal >}</code>.
     * <p>
     * The first three items need to be updated with the current tracing context which might
     * have changed between the incoming server call and the current outgoing client call.
     *
     * @param map the map with the tracing context where the {@link #X_OT_SPAN_CONTEXT} record
     *            gets updated based on the {@link #X_B3_TRACE_ID}, {@link #X_B3_SPAN_ID} and
     *            {@link #X_B3_PARENT_SPAN_ID}
     */
    static void fixXOtSpanContext(Map<String, String> map) {
        if (map.containsKey(X_OT_SPAN_CONTEXT)) {
            String value = map.get(X_OT_SPAN_CONTEXT);

            String[] split = value.split(";");

            substitute(map, split, X_B3_TRACE_ID, 0);
            substitute(map, split, X_B3_SPAN_ID, 1);
            substitute(map, split, X_B3_PARENT_SPAN_ID, 2);

            String result = Stream.of(split).collect(Collectors.joining(";"));
            LOGGER.fine(() -> X_OT_SPAN_CONTEXT + " header fixed: " + value + " -> " + result);
            map.put(X_OT_SPAN_CONTEXT, result);
        }
    }

    private static void substitute(Map<String, String> map, String[] split, String key, int i) {
        if (split.length > i && map.containsKey(key)) {
            split[i] = map.get(key);
        }
    }

    /**
     * Propagate the tracing context given by the provided {@link Tracer} and {@link Span} to the headers
     * map.
     *
     * @param headersMap  the map where to inject tracing propagation context, if {@code null} a new {@link HashMap}
     *                    is created
     * @param tracer      the tracer to use; must not be {@code null}
     * @param spanContext the span context to use; must not be {@code null}
     * @return the updated headers map
     */
    private static Map<String, String> propagateTracing(Map<String, String> headersMap, Tracer tracer, SpanContext spanContext) {
        Objects.requireNonNull(tracer, "Tracer must not be null!");
        Objects.requireNonNull(spanContext, "Span context must not be null!");

        if (headersMap == null) {
            headersMap = new HashMap<>();
        }
        tracer.inject(spanContext, Format.Builtin.HTTP_HEADERS, new TextMapInjectAdapter(headersMap));
        return headersMap;
    }
}
