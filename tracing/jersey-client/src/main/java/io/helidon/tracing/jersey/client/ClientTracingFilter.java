/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MultivaluedMap;

import io.helidon.common.context.Contexts;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;
import io.helidon.tracing.jersey.client.internal.TracingContext;
import io.helidon.tracing.spi.TracerProvider;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import jdk.jfr.FlightRecorder;

/**
 * This filter adds tracing information the the associated JAX-RS client call based on the provided properties.
 * <p>
 * In order to inject the tracing information properly, {@link Tracer} and an optional parent
 * {@link SpanContext} needs to be resolved. In case we are in scope of inbound JAX-RS
 * request, the client also uses inbound HTTP headers to determine correct headers for outbound call.
 * <p>
 * The {@link Tracer} gets resolved in the following order:
 * <ol>
 * <li>From request property {@link #TRACER_PROPERTY_NAME}</li>
 * <li>From JAX-RS server, when the client is invoked in scope of a JAX-RS inbound request
 * and appropriate filter is configured (see helidon-tracing-jersey and helidon-microprofile-tracing modules)</li>
 * <li>From {@link GlobalTracer#get()}</li>
 * </ol>
 * <p>
 * The parent {@link SpanContext} is resolved as follows
 * <ol>
 * <li>From request property {@link #CURRENT_SPAN_CONTEXT_PROPERTY_NAME}</li>
 * <li>From JAX-RS server, when the client is invoked in scope of a JAX-rs inbound request</li>
 * </ol>
 * <p>
 * Inbound HTTP headers are resolved from JAX-RS server.
 *
 * <p>
 * For each client call, a new {@link Span} with operation name generated from HTTP method and resource method
 * and class is created based
 * on the resolved {@link Tracer} and an optional parent {@link Span}. The span information is also
 * propagated to the outbound request using HTTP headers injected by tracer.
 * <p>
 * Example 1 - client within a JAX-RS resource (with tracing integration configured on server):
 * <pre>
 * public String someMethod(@Uri(BACKEND) WebTarget target) {
 *   Response response = target.request().get();
 *   // process the response
 * }
 * </pre>
 * <p>
 * Example 2 - standalone client (with access to tracer and span),
 * assuming we have a WebTarget ready as {@code target}
 * <pre>
 * target.request()
 *   .property(TracingClientFilter.TRACER_PROPERTY_NAME, tracer)
 *   .property(TracingClientFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME, spanContext)
 *   .get();
 * </pre>
 */
@Priority(Priorities.AUTHENTICATION - 250)
public class ClientTracingFilter implements ClientRequestFilter, ClientResponseFilter {
    /**
     * Name of tracing component used to retrieve tracing configuration.
     */
    public static final String JAX_RS_TRACING_COMPONENT = "jax-rs";
    /**
     * The {@link Tracer} property name.
     */
    public static final String TRACER_PROPERTY_NAME = "io.helidon.tracing.tracer";
    /**
     * Override name of the span created for client call.
     */
    public static final String SPAN_NAME_PROPERTY_NAME = ClientTracingFilter.class.getName() + ".span-name";
    /**
     * If set to false, tracing will be disabled.
     * If set to true, tracing will depend on overall configuration.
     */
    public static final String ENABLED_PROPERTY_NAME = ClientTracingFilter.class.getName() + ".span-enabled";
    /**
     * The {@link SpanContext} property name.
     */
    public static final String CURRENT_SPAN_CONTEXT_PROPERTY_NAME = "io.helidon.tracing.span-context";
    /**
     * Name of the configuration of a span created for outbound calls.
     */
    private static final String SPAN_OPERATION_NAME = "jersey-client-call";
    /*
     * Known headers to be propagated from inbound request
     */
    /**
     * Header used by Envoy proxy. Automatically propagated when within Jersey and
     * when using helidon-microprofile-tracing module.
     */
    public static final String X_OT_SPAN_CONTEXT = "x-ot-span-context";
    /**
     * Header used by routers. Automatically propagated when within Jersey and
     * when using helidon-microprofile-tracing module.
     */
    public static final String X_REQUEST_ID = "x-request-id";

    static final String SPAN_PROPERTY_NAME = ClientTracingFilter.class.getName() + ".span";

    private static final List<String> PROPAGATED_HEADERS = List.of(X_REQUEST_ID, X_OT_SPAN_CONTEXT);
    private static final int HTTP_STATUS_ERROR_THRESHOLD = 400;
    private static final int HTTP_STATUS_SERVER_ERROR_THRESHOLD = 500;

    private final Optional<TracerProvider> tracerProvider;

    /**
     * Default constructor so this filter can be registered with Jersey
     * as a class.
     * Required by integrated platform.
     */
    public ClientTracingFilter() {
        Iterator<TracerProvider> iterator = HelidonServiceLoader.create(ServiceLoader.load(TracerProvider.class))
                .iterator();

        if (iterator.hasNext()) {
            tracerProvider = Optional.of(iterator.next());
        } else {
            tracerProvider = Optional.empty();
        }
    }




    public void filterForJfr(ClientRequestContext requestContext)
             {

        JaxRsInvocationEvent event = new JaxRsInvocationEvent();
        System.out.println("ClientTracingFilter.filterForJfr event.isEnabled():" + event.isEnabled());
        if (!event.isEnabled()) {
            return;
        }
        System.out.println("ClientTracingFilter.filterForJfr event.begin()...");
        event.begin();

        requestContext.setProperty(JaxRsInvocationEvent.NAME, event);
    }

    static {
        System.out.println("ClientTracingFilter... JaxRsInvocationEvent registered with FlightRecorder");
        FlightRecorder.getFlightRecorder().register(JaxRsInvocationEvent.class);
    }


    @Override
    public void filter(ClientRequestContext requestContext) {
        filterForJfr(requestContext);
        // if we run within Jersey server, the tracing context will be filled in by TracingHelperFilter
        // if not, it will be empty
        Optional<TracingContext> tracingContext = Contexts.context().flatMap(ctx -> ctx.get(TracingContext.class));

        // maybe we are disabled
        if (tracingDisabled(requestContext, tracingContext)) {
            return;
        }

        // also we may configure tracing through other means
        SpanTracingConfig spanConfig = TracingConfigUtil.spanConfig(JAX_RS_TRACING_COMPONENT, SPAN_OPERATION_NAME);
        if (!spanConfig.enabled()) {
            return;
        }

        Tracer tracer = findTracer(requestContext, tracingContext);
        Optional<SpanContext> parentSpan = findParentSpan(tracer, requestContext, tracingContext);
        Map<String, List<String>> inboundHeaders = findInboundHeaders(tracingContext);
        String spanName = findSpanName(requestContext, spanConfig);

        // create a new span for this jersey client request
        Span currentSpan = createSpan(requestContext,
                                      tracer,
                                      parentSpan,
                                      spanName);

        // register it so we can close the span on response
        requestContext.setProperty(SPAN_PROPERTY_NAME, currentSpan);

        // and also register it with Context, so we can close the span in case of an exception that does not hit the
        // response filter
        Contexts.context().ifPresent(ctx -> ctx.register(SPAN_PROPERTY_NAME, currentSpan));
        Contexts.context().ifPresent(ctx -> ctx.register(TracingConfigUtil.OUTBOUND_SPAN_QUALIFIER, currentSpan.context()));

        // propagate tracing headers, so remote service can use currentSpan as its parent
        Map<String, List<String>> tracingHeaders = tracingHeaders(tracer, currentSpan);
        Map<String, List<String>> outboundHeaders = tracerProvider
                .map(provider -> provider.updateOutboundHeaders(currentSpan,
                                                                tracer,
                                                                parentSpan.orElse(null),
                                                                tracingHeaders,
                                                                inboundHeaders))
                .orElse(tracingHeaders);

        // add headers from inbound request that were not added by tracing provider and are needed
        // by supported proxies or routing services
        outboundHeaders = updateOutboundHeaders(outboundHeaders, inboundHeaders);

        // update the headers to be correctly propagated to remote service
        MultivaluedMap<String, Object> headers = requestContext.getHeaders();
        outboundHeaders.forEach((key, value) -> headers.put(key, new ArrayList<>(value)));
    }

    private boolean tracingDisabled(ClientRequestContext requestContext,
                                    Optional<TracingContext> tracingContext) {
        Optional<Boolean> enabled = property(requestContext, Boolean.class, ENABLED_PROPERTY_NAME);
        if (enabled.isPresent() && !enabled.get()) {
            return true;
        }
        return tracingContext.map(TracingContext::traceClient)
                .map(value -> !value) // invert, as configuration says enabled, we are interested in disabled
                .orElse(false); // by default enabled
    }

    private Map<String, List<String>> findInboundHeaders(Optional<TracingContext> tracingContext) {
        return tracingContext.map(TracingContext::inboundHeaders)
                .orElse(Map.of());
    }

    private Map<String, List<String>> updateOutboundHeaders(Map<String, List<String>> outboundHeaders,
                                                            Map<String, List<String>> inboundHeaders) {
        if (inboundHeaders.isEmpty()) {
            return outboundHeaders;
        }

        // copy all existing headers to the result
        Map<String, List<String>> result = new HashMap<>(outboundHeaders);
        PROPAGATED_HEADERS.forEach(header -> result.computeIfAbsent(header, inboundHeaders::get));
        return result;
    }

    public void filterForJfr(ClientRequestContext requestContext,
                             ClientResponseContext responseContext){
        JaxRsInvocationEvent event = (JaxRsInvocationEvent) requestContext
                .getProperty(JaxRsInvocationEvent.NAME);
        System.out.println("FlightRecorderFilter.filter");
        if (event == null || !event.isEnabled()) {
            return;
        }
        System.out.println("FlightRecorderFilter.filter event.end()...");
        event.end();
        event.path = String.valueOf(requestContext.getUri());
//        event.path = String.valueOf(requestContext.getUriInfo().getPath());

        System.out.println("FlightRecorderFilter.filter event.shouldCommit():" + event.shouldCommit());
        if (event.shouldCommit()) {
            event.method = requestContext.getMethod();
            event.mediaType = String.valueOf(requestContext.getMediaType());
            event.length = 1;//requestContext.getLength();
            event.queryParameters = "query params"; //requestContext.getUriInfo()
//                    .getQueryParameters().toString();
            event.headers = requestContext.getHeaders().toString();
            event.javaMethod = "getJavaMethod(requestContext)"; //todo
            event.responseLength = responseContext.getLength();
            event.responseHeaders = responseContext.getHeaders().toString();
            event.status = responseContext.getStatus();

            event.commit();
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        filterForJfr(requestContext, responseContext);
        Object property = requestContext.getProperty(SPAN_PROPERTY_NAME);

        if (property instanceof Span) {
            Span span = (Span) property;
            int status = responseContext.getStatus();
            Tags.HTTP_STATUS.set(span, status);
            if (status >= HTTP_STATUS_ERROR_THRESHOLD) {
                Tags.ERROR.set(span, true);
                span.log(Map.of("event",
                                                 "error",
                                                 "message",
                                                 "Response HTTP status: " + status,
                                                 "error.kind",
                                                 (status < HTTP_STATUS_SERVER_ERROR_THRESHOLD) ? "ClientError" : "ServerError"));
            }
            span.finish();
        }
    }

    private Optional<SpanContext> findParentSpan(Tracer tracer, ClientRequestContext requestContext,
                                                 Optional<TracingContext> tracingContext) {

        // parent span lookup
        // first is the configured span in request properties
        Optional<SpanContext> property = property(requestContext, SpanContext.class, CURRENT_SPAN_CONTEXT_PROPERTY_NAME);
        if (property.isPresent()) {
            return property;
        }

        // then the active span
        Span activeSpan = tracer.activeSpan();
        if (null != activeSpan) {
            return Optional.of(activeSpan.context());
        }

        // then spans registered in context
        return  // from injected span context
                tracingContext.map(TracingContext::parentSpan)
                // first look for "our" span context (e.g. one registered by a component that is aware that we exist)
                .or(() -> Contexts.context().flatMap(ctx -> ctx.get(ClientTracingFilter.class, SpanContext.class)))
                // then look for overall span context
                .or(() -> Contexts.context().flatMap(ctx -> ctx.get(SpanContext.class)));
    }

    private String findSpanName(ClientRequestContext requestContext, SpanTracingConfig spanConfig) {
        return property(requestContext, String.class, SPAN_NAME_PROPERTY_NAME)
                .or(spanConfig::newName)
                .orElseGet(() -> requestContext.getMethod().toUpperCase());
    }

    private Tracer findTracer(ClientRequestContext requestContext,
                              Optional<TracingContext> tracingContext) {
        return property(requestContext, Tracer.class, TRACER_PROPERTY_NAME)
                .or(() -> tracingContext.map(TracingContext::tracer))
                .or(() -> Contexts.context().flatMap(ctx -> ctx.get(Tracer.class)))
                .orElseGet(GlobalTracer::get);
    }

    private static <T> Optional<T> property(ClientRequestContext requestContext, Class<T> clazz, String propertyName) {
        return Optional.ofNullable(requestContext.getProperty(propertyName))
                        .filter(clazz::isInstance)
                .or(() -> Optional.ofNullable(requestContext.getConfiguration().getProperty(propertyName))
                        .filter(clazz::isInstance))
                .map(clazz::cast);
    }

    private Map<String, List<String>> tracingHeaders(Tracer tracer, Span currentSpan) {
        Map<String, String> tracerHeaders = new HashMap<>();

        tracer.inject(currentSpan.context(),
                      Format.Builtin.HTTP_HEADERS,
                      new TextMapAdapter(tracerHeaders));

        return new HashMap<>(tracerHeaders.entrySet()
                                     .stream()
                                     .collect(Collectors.toMap(Map.Entry::getKey,
                                                               entry -> List.of(entry.getValue()))));
    }

    private Span createSpan(ClientRequestContext requestContext,
                            Tracer tracer,
                            Optional<SpanContext> parentSpan,
                            String spanName) {

        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(spanName);

        parentSpan.ifPresent(spanBuilder::asChildOf);

        Span span = spanBuilder.start();

        Tags.COMPONENT.set(span, "jaxrs");
        Tags.HTTP_METHOD.set(span, requestContext.getMethod());
        Tags.HTTP_URL.set(span, url(requestContext.getUri()));
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);

        return span;
    }

    private String url(URI uri) {
        String host = uri.getHost();
        host = host.replace("127.0.0.1", "localhost");
        String query = uri.getQuery();
        if (null == query) {
            query = "";
        } else {
            if (!query.isEmpty()) {
                query = "?" + query;
            }
        }
        return uri.getScheme() + "://" + host + ":" + uri.getPort() + uri.getPath() + query;
    }

}
