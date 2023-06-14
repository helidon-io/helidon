/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.tracing.opentelemetry;

import java.util.Optional;

import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test if baggage is handled correctly.
 */
class BaggageTest {

    private final Tracer tracer = TracerBuilder.create("test-service").registerGlobal(false).build();

    @Test
    void testBaggage() {
        Span span = tracer.spanBuilder("test-span").start();
        Span spanWithBaggage = span.baggage("key", "value");
        Optional<String> result = spanWithBaggage.baggage("key");
        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), equalTo("value"));
        span.end();
    }

    @Test
    void testBadBaggage() {
        Span span = tracer.spanBuilder("test-bad-span").start();
        assertThrows(NullPointerException.class, () -> span.baggage(null, "value"));
        assertThrows(NullPointerException.class, () -> span.baggage("key", null));
        assertThrows(NullPointerException.class, () -> span.baggage(null, null));
    }
}
