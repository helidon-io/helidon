/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Optional;

import io.helidon.security.SecurityResponse;
import io.helidon.tracing.config.SpanTracingConfig;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;

abstract class CommonTracing {
    static final String LOG_STATUS = "status";

    private final Optional<SpanContext> parentSpanContext;
    private final Optional<Span> parentSpan;
    private final Optional<Span> securitySpan;
    private final Optional<Span> span;
    private final SpanTracingConfig spanConfig;

    CommonTracing(Optional<SpanContext> parentSpanContext,
                  Optional<Span> parentSpan,
                  Optional<Span> securitySpan,
                  Optional<Span> span,
                  SpanTracingConfig spanConfig) {

        this.parentSpanContext = parentSpanContext;
        this.parentSpan = parentSpan;
        this.securitySpan = securitySpan;
        this.span = span;
        this.spanConfig = spanConfig;
    }

    /**
     * Finish the span.
     */
    public void finish() {
        span.ifPresent(Span::finish);
    }

    /**
     * Log error and finish the span.
     *
     * @param message log this message as the cause of failure
     */
    public void error(String message) {
        if (!span.isPresent()) {
            return;
        }

        Span theSpan = span.get();

        Tags.ERROR.set(theSpan, true);

        theSpan.log(Map.of("event", "error",
                           "message", message,
                           "error.kind", "SecurityException"));

        theSpan.finish();
    }

    /**
     * Log error and finish the span.
     *
     * @param throwable throwable causing security to fail
     */
    public void error(Throwable throwable) {
        if (!span.isPresent()) {
            return;
        }

        Span theSpan = span.get();

        Tags.ERROR.set(theSpan, true);

        theSpan.log(Map.of("event", "error",
                                            "error.object", throwable));
        theSpan.finish();
    }

    /**
     * Find closes parent span context.
     *
     * @return span context if found
     */
    public Optional<SpanContext> findParent() {
        Optional<Span> closest = closestSecuritySpan();

        return closest.map(Span::context)
                .or(() -> parentSpanContext);
    }

    /**
     * Log response status.
     * This is to be used by authorization, authentication and outbound
     * security.
     * Top level security only traces proceed or deny.
     *
     * @param status status to log
     */
    public void logStatus(SecurityResponse.SecurityStatus status) {
        log(LOG_STATUS, LOG_STATUS + ": " + status, true);
    }

    Optional<Span> span() {
        return span;
    }

    Optional<SpanContext> parentSpanContext() {
        return parentSpanContext;
    }

    Optional<Span> parentSpan() {
        return parentSpan;
    }

    Optional<Span> securitySpan() {
        return securitySpan;
    }

    void log(String logName, String logMessage, boolean enabledByDefault) {
        span().ifPresent(span -> {
            if (spanConfig().logEnabled(logName, enabledByDefault)) {
                span.log(logMessage);
            }
        });
    }

    protected SpanTracingConfig spanConfig() {
        return spanConfig;
    }

    private Optional<Span> closestSecuritySpan() {
        if (span.isPresent()) {
            return span;
        }

        return securitySpan;
    }
}
