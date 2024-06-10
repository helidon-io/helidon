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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.tracing.Baggage;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.WritableBaggage;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class TestBaggageApi {

    @Test
    void testWritableBaggageFromSpan() {
        Span span = Tracer.global().spanBuilder("otel-span").start();
        WritableBaggage baggage = span.baggage();
        span.baggage("keyA", "valA");
        assertThat("Assigned baggage via span is present", baggage.containsKey("keyA"), is(true));
        assertThat("Assigned baggage via span value from baggage",
                   baggage.get("keyA"),
                   OptionalMatcher.optionalValue(is(equalTo("valA"))));

        baggage.set("keyB", "valB");
        assertThat("Assigned baggage via baggage API value from span",
                   span.baggage("keyB"),
                   OptionalMatcher.optionalValue(is(equalTo("valB"))));
        assertThat("Assigned via baggage API value is present", baggage.containsKey("keyB"), is(true));
        assertThat("Assigned via baggage API value from baggage API",
                   baggage.get("keyB"),
                   OptionalMatcher.optionalValue(is(equalTo("valB"))));
    }

    @Test
    void testImmutableBaggageFromSpanContext() {
        Optional<SpanContext> spanContextOpt = Tracer.global()
                .extract(HeaderProvider.create(Map.of("baggage", List.of("keyC=valC,keyD=valD"))));

        assertThat("Span context", spanContextOpt, OptionalMatcher.optionalPresent());
        Baggage baggage = spanContextOpt.get().baggage();
        assertThat("Baggage from span context", baggage, is(notNullValue()));
        assertThat("Keys in baggage", baggage.keys(), containsInAnyOrder("keyC", "keyD"));
        assertThat("Value for keyC", baggage.get("keyC"), OptionalMatcher.optionalValue(is(equalTo("valC"))));
        assertThat("Value for keyD", baggage.get("keyD"), OptionalMatcher.optionalValue(is(equalTo("valD"))));
    }
}
