/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.opentelemetry;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import io.opentelemetry.sdk.trace.ReadableSpan;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

class TestAutoCurrentSpanAsParent {

    @Test
    void ensureCurrentSpanUsedAsParent() {
        // Request the legacy behavior in which an active current span was automatically set as the parent of any new span.
        String originalUseCurrentSpanAsParent = System.setProperty(OpenTelemetryTracer.USE_CURRENT_SPAN_AS_PARENT, "true");

        SpanIds spanIds = testSpans(originalUseCurrentSpanAsParent);
        assertThat("Parent span", spanIds.parentOfSecondSpan, is(spanIds.firstSpanId));
    }

    @Test
    void ensureCurrentSpanNotUsedAsParent() {
        // Do not set the property; we want to test the normal scenario where the property is not assigned and
        // the active span IS NOT used automatically as the parent of a new span. But make sure that the property is
        // NOT set (or is at least set to false) so the actual test will exercise what we want.
        
        assertThat("Property triggering automatic use of current span as parent for new span",
                   System.getProperty(OpenTelemetryTracer.USE_CURRENT_SPAN_AS_PARENT),
                   anyOf(is(nullValue()), is("false")));
        SpanIds spanIds = testSpans(null);
        assertThat("Parent span", spanIds.parentOfSecondSpan, is(not(spanIds.firstSpanId)));
    }

    private SpanIds testSpans(String originalUseCurrentSpanAsParentPropertyValue) {
        Tracer tracer = Tracer.global();
        Span parentSpan = tracer.spanBuilder("parentSpan").start();
        String parentSpanId = parentSpan.context().spanId();

        try {
            try (Scope ignore = parentSpan.activate()) {
                Span childSpan = tracer.spanBuilder("childSpan").start();
                String parentSpanIdAccordingToChild = childSpan.unwrap(ReadableSpan.class).getParentSpanContext().getSpanId();
                childSpan.end();
                return new SpanIds(parentSpanId, parentSpanIdAccordingToChild);
            }
        } finally {
            restoreProperty(originalUseCurrentSpanAsParentPropertyValue);
            parentSpan.end();
        }
    }

    private record SpanIds(String firstSpanId, String parentOfSecondSpan) {}

    private void restoreProperty(String savedSetting) {
        if (savedSetting == null) {
            System.clearProperty(OpenTelemetryTracer.USE_CURRENT_SPAN_AS_PARENT);
        } else {
            System.setProperty(OpenTelemetryTracer.USE_CURRENT_SPAN_AS_PARENT, savedSetting);
        }
    }
}
