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
import java.util.TreeMap;

import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        span.end();
        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), equalTo("value"));
    }

    @Test
    void testBadBaggage() {
        Span span = tracer.spanBuilder("test-bad-span").start();
        assertThrows(NullPointerException.class, () -> span.baggage(null, "value"));
        assertThrows(NullPointerException.class, () -> span.baggage("key", null));
        assertThrows(NullPointerException.class, () -> span.baggage(null, null));
    }

    /**
     * Test for: https://github.com/helidon-io/helidon/issues/6970
     */
    @Test
    @Disabled // temporary disabled
    void baggageCanaryMinimal() {
        final var tracer = io.helidon.tracing.Tracer.global();
        final var span = tracer.spanBuilder("baggageCanaryMinimal").start();
        try {
            // Set baggage and confirm that it's known in the span
            span.baggage("fubar", "1");
            assertThat("1", is(span.baggage("fubar").orElseThrow()));

            // Inject the span (context) into the consumer
            final var consumer = HeaderConsumer
                    .create(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
            tracer.inject(span.context(), HeaderProvider.empty(), consumer);

            // Confirm that baggage was NOT propagated (the bug)
            final var allKeys = consumer.keys().toString();
            assertTrue(allKeys.contains("fubar") // this fails!
                    , () -> "No injected baggage-fubar found in " + allKeys);

        } catch (final Exception x) {
            span.end(x);
        } finally {
            span.end();
        }
    }
}
