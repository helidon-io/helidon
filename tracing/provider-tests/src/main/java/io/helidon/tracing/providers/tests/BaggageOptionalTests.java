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
package io.helidon.tracing.providers.tests;

import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class BaggageOptionalTests {
    // Demonstrate NPE in io.opentelemetry.api.baggage.BaggageEntry.getValue()
    private static final String KEY = "baggageGetShouldCauseEmptyReturn_NotNPE";

    @Test
    void baggageRetrievalWhenNeverTouchedShouldBeEmptyFromSpan() {
        runWithSpanHarness(span -> {
            // NPE in 4.0.5 -> 4.0.10
            final var actual = span.baggage().get(KEY);
            assertThat("Retrieval of unset baggage", actual, OptionalMatcher.optionalEmpty());
        });
    }

    @Test void baggageRetrievalWhenNeverTouchedShouldBeEmptyFromContext() {
        runWithSpanHarness(span -> {
            // NPE in 4.0.5 -> 4.0.10
            final var actual = span.context().baggage().get(KEY);
            assertThat("Retrieval of unset baggage", actual, OptionalMatcher.optionalEmpty());
        });
    }

    @Test void baggageRetrievalWhenNeverTouchedShouldBeEmptyFromSpanWithWorkaround() {
        runWithSpanHarness(span -> {
            // Apply only known workaround (use containsKey precondition)
            final var actual = !span.baggage().containsKey(KEY) ? Optional.empty()
                    : span.baggage().get(KEY);
            assertThat("Retrieval of unset baggage", actual, OptionalMatcher.optionalEmpty());
        });
    }

    private void runWithSpanHarness(final Consumer<Span> spanConsumer) {
        final var span = Tracer.global().spanBuilder("BaggageOptionalTest").start();
        try {
            spanConsumer.accept(span);

        } finally {
            span.end();
        }
    }
}
