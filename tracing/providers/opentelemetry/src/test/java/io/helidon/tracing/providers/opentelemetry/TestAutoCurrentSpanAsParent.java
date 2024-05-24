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
import static org.hamcrest.Matchers.is;

class TestAutoCurrentSpanAsParent {

    @Test
    void ensureCurrentSpanUsedAsParent() {
        // Keep the default behavior of adopting the current span as the parent of a new one.
        SpanIds spanIds = testSpans(true);
        assertThat("Parent span", spanIds.parentOfSecondSpan, is(spanIds.firstSpanId));
    }

    @Test
    void ensureCurrentSpanNotUsedAsParent() {
        // Suppress the normal behavior of adopting the current span as the parent of a new one; create a new root span instead.
        SpanIds spanIds = testSpans(false);
        assertThat("Parent span", spanIds.parentOfSecondSpan, is("0000000000000000"));
    }

    private SpanIds testSpans(boolean useCurrentSpanAsParent) {
        Tracer tracer = Tracer.global();
        Span parentSpan = tracer.spanBuilder("parentSpan").start();
        String parentSpanId = parentSpan.context().spanId();

        try {
            try (Scope ignore = parentSpan.activate()) {
                Span childSpan = tracer.spanBuilder("childSpan")
                        .update(builder -> {
                            if (!useCurrentSpanAsParent) {
                                builder.unwrap(io.opentelemetry.api.trace.SpanBuilder.class).setNoParent();
                            }
                        } ).start();
                String parentSpanIdAccordingToChild = childSpan.unwrap(ReadableSpan.class).getParentSpanContext().getSpanId();
                childSpan.end();
                return new SpanIds(parentSpanId, parentSpanIdAccordingToChild);
            }
        } finally {
            parentSpan.end();
        }
    }

    private record SpanIds(String firstSpanId, String parentOfSecondSpan) {}
}
