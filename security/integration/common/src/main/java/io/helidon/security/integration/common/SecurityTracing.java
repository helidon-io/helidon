/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.security.integration.common;

import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.security.SecurityContext;
import io.helidon.tracing.config.ComponentTracingConfig;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Security integration utility for tracing support in integration components.
 */
public final class SecurityTracing extends CommonTracing {
    private static final String COMPONENT = "security";
    private static final String SPAN_SECURITY = "security";
    private static final String SPAN_OUTBOUND = "security:outbound";
    private static final String SPAN_RESPONSE = "security:response";
    private static final String SPAN_AUTHENTICATION = "security:atn";
    private static final String SPAN_AUTHORIZATION = "security:atz";
    private static final String SPAN_ROLE_MAP_PREFIX = "security:rm:";
    private static final String SPAN_TAG_SECURITY_CONTEXT = "security.id";
    private static final String LOG_STATUS = "status";
    private static final String STATUS_PROCEED = "PROCEED";
    private static final String STATUS_DENY = "DENY";

    private final ComponentTracingConfig tracedConfig;

    private final SpanTracingConfig atnSpanConfig;
    private final SpanTracingConfig atzSpanConfig;
    private final SpanTracingConfig outboundSpanConfig;
    private final SpanTracingConfig responseSpanConfig;
    private AtnTracing atnTracing;
    private AtzTracing atzTracing;
    private ResponseTracing responseTracing;

    // avoid instantiation of a utility class
    private SecurityTracing(Optional<SpanContext> parentSpanContext,
                            Optional<Span> parentSpan,
                            Optional<Span> securitySpan,
                            ComponentTracingConfig tracedConfig) {
        super(parentSpanContext,
              parentSpan,
              securitySpan,
              securitySpan,
              tracedConfig.span(SPAN_SECURITY));

        this.tracedConfig = tracedConfig;
        this.atnSpanConfig = tracedConfig.span(SPAN_AUTHENTICATION);
        this.atzSpanConfig = tracedConfig.span(SPAN_AUTHORIZATION);
        this.outboundSpanConfig = tracedConfig.span(SPAN_OUTBOUND);
        this.responseSpanConfig = tracedConfig.span(SPAN_RESPONSE);
    }

    /**
     * Get an instance from the current {@link io.helidon.common.context.Context}
     *  or create a new instance and start the security span.
     *
     * @return existing or a new tracing instance to be used for tracing security events
     */
    public static SecurityTracing get() {
        Optional<Context> context = Contexts.context();

        return tracing(context)
                .orElseGet(() -> createTracing(context));
    }

    private static SecurityTracing createTracing(Optional<Context> context) {
        ComponentTracingConfig componentConfig = context.flatMap(ctx -> ctx.get(TracingConfig.class))
                .orElse(TracingConfig.ENABLED)
                .component(COMPONENT);

        Optional<SpanContext> parentSpanContext = context.flatMap(ctx -> ctx.get(SpanContext.class));
        Optional<Span> parentSpan = context.flatMap(ctx -> ctx.get(Span.class));
        Optional<Span> securitySpan = createSecuritySpan(componentConfig, parentSpanContext);

        SecurityTracing tracing = new SecurityTracing(
                parentSpanContext,
                parentSpan,
                securitySpan,
                componentConfig);

        context.ifPresent(ctx -> ctx.register(tracing));

        return tracing;
    }

    private static Optional<Span> createSecuritySpan(ComponentTracingConfig componentConfig, Optional<SpanContext> parentSpan) {
        return tracer()
                // if there is no tracer registered, ignore tracing
                .flatMap(tracer -> {
                    SpanTracingConfig spanConfig = componentConfig.span(SPAN_SECURITY);
                    if (spanConfig.enabled()) {
                        Tracer.SpanBuilder builder = tracer.buildSpan(spanConfig.newName().orElse(SPAN_SECURITY));
                        parentSpan.ifPresent(builder::asChildOf);

                        return Optional.of(builder.start());
                    } else {
                        return Optional.empty();
                    }
                });
    }

    private static Optional<SecurityTracing> tracing(Optional<Context> context) {
        return context.flatMap(ctx -> ctx.get(SecurityTracing.class));
    }

    private static Optional<Span> newSpan(SpanTracingConfig spanConfig,
                                          String spanName,
                                          Optional<SpanContext> parent) {
        // first find if we need to trace
        return tracer().flatMap(tracer -> {
            if (spanConfig.enabled()) {
                Tracer.SpanBuilder builder = tracer.buildSpan(spanConfig.newName().orElse(spanName));

                parent.ifPresent(builder::asChildOf);
                return Optional.of(builder.start());
            } else {
                return Optional.empty();
            }
        });
    }

    private static Optional<Tracer> tracer() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(Tracer.class));
    }

    /**
     * Update security span with information from {@link io.helidon.security.SecurityContext}.
     * The context is expected to be unauthenticated and unauthorized.
     * This method should be called as soon as possible to provide correlation to log statements.
     *
     * @param context security context for this request
     */
    public void securityContext(SecurityContext context) {
        span().ifPresent(span -> span.setTag(SPAN_TAG_SECURITY_CONTEXT, context.id()));
    }

    /**
     * Log security status - proceed.
     * This should be logged when security allows further processing of the request.
     */
    public void logProceed() {
        logStatus(STATUS_PROCEED);
    }

    /**
     * Log security status - deny.
     * This should be logged when security denies further processing of the request.
     */
    public void logDeny() {
        logStatus(STATUS_DENY);
    }

    /**
     * Create a tracing span for authentication.
     * @return authentication tracing
     */
    public AtnTracing atnTracing() {
        if (null != atnTracing) {
            return atnTracing;
        }

        Optional<Span> atnSpan = newSpan(atnSpanConfig, SPAN_AUTHENTICATION, findParent());
        this.atnTracing = new AtnTracing(parentSpanContext(),
                                         parentSpan(),
                                         span(),
                                         atnSpan,
                                         atnSpanConfig);

        return atnTracing;
    }

    /**
     * Create a tracing pan for a role mapper.
     *
     * @param id role mapper identification (such as {@code idcs})
     * @return role mapper tracing (each invocation creates a new instance)
     */
    public RoleMapTracing roleMapTracing(String id) {
        AtnTracing atn = atnTracing();

        String spanName = SPAN_ROLE_MAP_PREFIX + id;
        SpanTracingConfig rmTracingConfig = tracedConfig.span(SPAN_ROLE_MAP_PREFIX + id);

        Optional<Span> atnSpan = newSpan(rmTracingConfig, spanName, atn.findParent());
        return new RoleMapTracing(parentSpanContext(),
                                  parentSpan(),
                                  span(),
                                  atnSpan,
                                  atnSpanConfig);
    }

    /**
     * Create a tracing span for authorization.
     * @return authorization tracing
     */
    public AtzTracing atzTracing() {
        if (null != atzTracing) {
            return atzTracing;
        }

        Optional<Span> atzSpan = newSpan(atzSpanConfig, SPAN_AUTHORIZATION, findParent());
        this.atzTracing = new AtzTracing(parentSpanContext(),
                                         parentSpan(),
                                         span(),
                                         atzSpan,
                                         atzSpanConfig);

        return atzTracing;
    }

    /**
     * Create a tracing span for outbound tracing.
     * Each invocation of this method returns a new tracing instance (to support multiple outbound calls).
     * @return outbound security tracing
     */
    public OutboundTracing outboundTracing() {

        // outbound tracing should be based on current outbound span
        Optional<SpanContext> parentOptional = Contexts.context()
                .flatMap(ctx -> ctx.get(TracingConfigUtil.OUTBOUND_SPAN_QUALIFIER, SpanContext.class));
        if (!parentOptional.isPresent()) {
            parentOptional = parentSpanContext();
        }

        Optional<Span> outboundSpan = newSpan(outboundSpanConfig, SPAN_OUTBOUND, parentOptional);
        return new OutboundTracing(parentSpanContext(),
                                   parentSpan(),
                                   span(),
                                   outboundSpan,
                                   outboundSpanConfig);

    }

    /**
     * Create a tracing span for response.
     * @return response security tracing
     */
    public ResponseTracing responseTracing() {
        if (null != responseTracing) {
            return responseTracing;
        }

        Optional<Span> responseSpan = newSpan(responseSpanConfig, SPAN_RESPONSE, parentSpanContext());
        this.responseTracing = new ResponseTracing(parentSpanContext(),
                                                   parentSpan(),
                                                   span(),
                                                   responseSpan,
                                                   responseSpanConfig);

        return this.responseTracing;
    }

    private void logStatus(String status) {
        super.log(LOG_STATUS, LOG_STATUS + ": " + status, true);
    }
}
