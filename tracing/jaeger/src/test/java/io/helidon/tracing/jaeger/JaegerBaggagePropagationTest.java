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
package io.helidon.tracing.jaeger;

import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class JaegerBaggagePropagationTest {

    static final String BAGGAGE_KEY = "myKey";
    static final String BAGGAGE_VALUE = "myValue";

    private static final String OTEL_AUTO_CONFIGURE_PROP = "otel.java.global-autoconfigure.enabled";
    private static final String OTEL_SDK_DISABLED_PROP = "otel.sdk.disabled";
    private static String originalOtelSdkAutoConfiguredSetting;
    private static String originalOtelSdkDisabledSetting;

    private static Config config;
    private static Tracer tracer;

    @BeforeAll
    static void init() {
        config = Config.create().get("tracing");
        tracer = TracerBuilder.create(config.get("jaeger-all-propagators")).build();
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
    void testBaggageProp() {

        var span = tracer.spanBuilder("testSpan").start();
        span.baggage(BAGGAGE_KEY, BAGGAGE_VALUE);

        checkBaggage(tracer, span, span::context);
    }

    @Test
    void testBaggageBeforeActivatingSpan() {
        var span = tracer.spanBuilder("activatedTestSpan").start();
        span.baggage(BAGGAGE_KEY, BAGGAGE_VALUE);

        try (Scope scope = span.activate()) {
            checkBaggage(tracer, span, this::currentSpanContext);
        }
    }

    @Test
    void testBaggageAfterActivatingSpan() {
        var span = tracer.spanBuilder("activatedTestSpan").start();

        try (Scope scope = span.activate()) {
            span.baggage(BAGGAGE_KEY, BAGGAGE_VALUE);
            checkBaggage(tracer, span, this::currentSpanContext);
        }
    }

    private void checkBaggage(Tracer tracer, Span span, Supplier<SpanContext> spanContextSupplier) {
        try {
            assertThat("Value after initial storage of baggage",
                       span.baggage(BAGGAGE_KEY),
                       OptionalMatcher.optionalValue(is(equalTo(BAGGAGE_VALUE))));

            var headerConsumer = HeaderConsumer.create(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
            tracer.inject(spanContextSupplier.get(), HeaderProvider.empty(), headerConsumer);

            // Make sure the baggage header was set.
            Optional<String> baggageHeader = headerConsumer.get("baggage");
            assertThat("Baggage contents in propagated headers",
                       baggageHeader,
                       OptionalMatcher.optionalValue(is(equalTo(BAGGAGE_KEY + "=" + BAGGAGE_VALUE))));

            // Now make sure the baggage is propagated to a new span context based on the header.
            Optional<SpanContext> propagatedSpanContext = tracer.extract(headerConsumer);
            assertThat("Propagated span context",
                       propagatedSpanContext,
                       OptionalMatcher.optionalPresent());
            Span spanFromContext = tracer.spanBuilder("fromContext").parent(propagatedSpanContext.get()).build();
            assertThat("Baggage value from propagated span context",
                       spanFromContext.baggage(BAGGAGE_KEY),
                       OptionalMatcher.optionalValue(is(BAGGAGE_VALUE)));

            span.end();
        } catch (Exception ex) {
            span.end(ex);
        }
    }

    private SpanContext currentSpanContext() {
        return Span.current()
                .map(Span::context)
                .orElseThrow(() -> new IllegalStateException("No current span"));
    }
}