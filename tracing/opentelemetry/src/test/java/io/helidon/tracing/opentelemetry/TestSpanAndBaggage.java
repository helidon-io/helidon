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
package io.helidon.tracing.opentelemetry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TestSpanAndBaggage {

    private static final String OTEL_AUTO_CONFIGURE_PROP = "otel.java.global-autoconfigure.enabled";
    private static final String OTEL_SDK_DISABLED_PROP = "otel.sdk.disabled";
    private static String originalOtelSdkAutoConfiguredSetting;
    private static String originalOtelSdkDisabledSetting;

    private static final String BAGGAGE_KEY = "mykey";
    private static final String BAGGAGE_VALUE = "myvalue";


    @BeforeAll
    static void init() {
        originalOtelSdkAutoConfiguredSetting = System.setProperty(OTEL_AUTO_CONFIGURE_PROP, "true");
        originalOtelSdkDisabledSetting = System.setProperty(OTEL_SDK_DISABLED_PROP, "false");
    }

    @AfterAll
    static void wrapup() {
        if (originalOtelSdkAutoConfiguredSetting != null) {
            System.setProperty(OTEL_AUTO_CONFIGURE_PROP, originalOtelSdkAutoConfiguredSetting);
        }
        if (originalOtelSdkDisabledSetting != null) {
            System.setProperty(OTEL_SDK_DISABLED_PROP, originalOtelSdkDisabledSetting);
        }
    }

    @Test
    void testActiveSpanScopeWithoutBaggage() {
        Tracer tracer = Tracer.global();
        Span span = tracer.spanBuilder("myParent")
                .start();

        // Make sure we get valid spans, not no-op ones all of which have zeros for span IDs.
        assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));

        try (Scope scope = span.activate()) {
            Optional<Span> spanInsideActivation = Span.current();
            assertThat("Current span while activated", spanInsideActivation, OptionalMatcher.optionalPresent());
            assertThat("Current span while activated",
                       spanInsideActivation.get().context().spanId(),
                       is(span.context().spanId()));
            span.end();
        } catch (Exception e) {
            span.end(e);
        }
    }

    @Test
    void testActiveSpanScopeWithBaggage() {
        // Make sure accessing baggage after span activation does not disrupt the scopes.

        // otel.java.global-autoconfigure.enabled

        Tracer tracer = Tracer.global();
        Span outerSpan = tracer.spanBuilder("outer").start();

        try (Scope outerScope = outerSpan.activate()) {

            Optional<Span> currentJustAfterActivation = Span.current();
            assertThat("Current span just after activation",
                       currentJustAfterActivation,
                       OptionalMatcher.optionalPresent());
            assertThat("Current span just after activation",
                       currentJustAfterActivation.get().context().spanId(),
                       is(outerSpan.context().spanId()));

            outerSpan.baggage("myItem", "myValue");
            outerSpan.end();
        } catch (Exception e) {
            outerSpan.end(e);
        }

        // There was no active span before outerSpan was activated, so expect an empty current span.
        Optional<Span> currentSpanAfterTryResourcesBlock = Span.current();
        assertThat("Current span just after try-resources block",
                   currentSpanAfterTryResourcesBlock,
                   OptionalMatcher.optionalEmpty());
    }


    @Test
    void testIncomingBaggage() {
        Tracer tracer = Tracer.global();
        // Need to supply both the traceparent and the baggage for OTel to construct a proper span context.
        HeaderProvider inboundHeaders = new MapHeaderProvider(
                Map.of("traceparent", List.of("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"),
                       "baggage", List.of("bag1=val1,bag2=val2")));
        Optional<SpanContext> spanContextOpt = tracer.extract(inboundHeaders);
        assertThat("Span context from inbound headers", spanContextOpt, OptionalMatcher.optionalPresent());
        Span span = tracer.spanBuilder("inbound").parent(spanContextOpt.get()).start();
        span.end();
        assertThat("Inbound baggage bag1", span.baggage("bag1"), OptionalMatcher.optionalValue(is("val1")));
        assertThat("Inbound baggage bag1", span.baggage("bag2"), OptionalMatcher.optionalValue(is("val2")));
    }


    @Test
    void testBaggageWithoutActivation() {
        final var tracer = io.helidon.tracing.Tracer.global();
        final var span = tracer.spanBuilder("baggageCanaryMinimal").start();
        try {
            // Set baggage and confirm that it's known in the span
            span.baggage(BAGGAGE_KEY, BAGGAGE_VALUE);
            checkBaggage(tracer, span, span::context);
        } catch (final Exception x) {
            span.end(x);
        } finally {
            span.end();
        }
    }

    @Test
    void testBaggageAddedBeforeActivation() {
        final var tracer = io.helidon.tracing.Tracer.global();
        final var span = tracer.spanBuilder("baggageCanaryMinimal").start();

        try {
            // Set baggage and confirm that it's known in the span
            span.baggage(BAGGAGE_KEY, BAGGAGE_VALUE);
            try (Scope scope = span.activate()) {
                checkBaggage(tracer, span, this::currentSpanContext);
            }
        } catch (final Exception x) {
            span.end(x);
        } finally {
            span.end();
        }
    }

    @Test
    void testBaggageAddedAfterActivation() {
        final String BAGGAGE_KEY = "mykey";
        final String BAGGAGE_VALUE = "myvalue";
        final var tracer = io.helidon.tracing.Tracer.global();
        final var span = tracer.spanBuilder("baggageMinimal").start();
        try {
            // Set baggage and confirm that it's known in the span
            try (Scope scope = span.activate()) {
                span.baggage(BAGGAGE_KEY, BAGGAGE_VALUE);
                checkBaggage(tracer, span, this::currentSpanContext);
            }
        } catch (final Exception x) {
            span.end(x);
        } finally {
            span.end();
        }
    }

    @Test
    void testExtractWithNoCurrentSpan() {
        final var tracer = io.helidon.tracing.Tracer.global();

        HeaderProvider headers = HeaderProvider.create(Map.of("not-a-trace", List.of("1234567890123456"),
                                                              "not-a-span", List.of("6543210987654321")));
        Optional<SpanContext> spanContext = tracer.extract(headers);

        assertThat("Current span reported", spanContext, OptionalMatcher.optionalEmpty());
    }

    private void checkBaggage(Tracer tracer, Span span, Supplier<SpanContext> spanContextSupplier) {
        String value = span.baggage(BAGGAGE_KEY).orElseThrow();
        assertThat("baggage value right after set", value, Matchers.is(Matchers.equalTo(BAGGAGE_VALUE)));

        // Inject the span (context) into the consumer
        final var consumer = HeaderConsumer
                .create(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        tracer.inject(spanContextSupplier.get(), HeaderProvider.empty(), consumer);
        // Confirm that baggage was NOT propagated (the bug)
        final var allKeys = consumer.keys();
        assertThat("Injected headers", allKeys, hasItem("baggage"));
        assertThat("Injected baggage expression",
                   consumer.get("baggage"),
                   OptionalMatcher.optionalValue(Matchers.equalTo(BAGGAGE_KEY + "=" + BAGGAGE_VALUE)));
    }

    private SpanContext currentSpanContext() {
        return Span.current()
                .map(Span::context)
                .orElseThrow(() -> new IllegalStateException("No current span"));
    }

}
