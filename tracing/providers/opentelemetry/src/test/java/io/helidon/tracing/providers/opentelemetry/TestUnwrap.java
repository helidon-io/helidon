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


import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;

class TestUnwrap {

    @Test
    void testTracer() {
        var tracer = io.helidon.tracing.Tracer.global();
        assertThat("Tracer unwrapped",
                   tracer.unwrap(Tracer.class),
                   instanceOf(Tracer.class));

        assertThat("Delegate unwrapped",
                   tracer.unwrap(io.opentelemetry.api.trace.Tracer.class),
                   instanceOf(io.opentelemetry.api.trace.Tracer.class));

        assertThat("Object.toString()",
                   tracer.unwrap(Object.class).toString(),
                   containsString(io.opentelemetry.api.trace.Tracer.class.getPackageName()));
    }

    @Test
    void testSpanAndSpanBuilder() {
        var tracer = io.helidon.tracing.Tracer.global();
        var spanBuilder = tracer.spanBuilder("test1");
        assertThat("Span builder unwrapped",
                   spanBuilder.unwrap(SpanBuilder.class),
                   instanceOf(SpanBuilder.class));

        var span = spanBuilder.start();
        assertThat("Span unwrapped",
                   span.unwrap(Span.class),
                   instanceOf(Span.class));

    }
}
