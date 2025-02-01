/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.SpanListener;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@HelidonTest
@AddBean(TestSpanListenersWithInjection.MyBean.class)
class TestSpanListenersWithInjection {

    private static MyListener listener;

    @Inject
    private MyBean myBean;

    @BeforeAll
    static void setup() {
        listener = new MyListener();
        io.helidon.tracing.Tracer.global().register(listener);
    }

    @BeforeEach
    void clearListener() {
        listener.clear();

    }

    @Test
    void testSpanFromInjectedTracer() {
        Tracer injectedTracer = myBean.injectedTracer();
        checkListenerCounts("Init", 0, 0, 0, 0, 0);

        // Make sure building a span from the injected tracer and making it current triggers notifications.
        SpanBuilder spanBuilderFromInjectedTracer = injectedTracer.spanBuilder("span-from-tracer-from-injected-opentelemetry");
        Span spanFromInjectedTracer = spanBuilderFromInjectedTracer.startSpan();
        checkListenerCounts("After span start", 1, 1, 0, 0, 0);

        Scope scope = spanFromInjectedTracer.makeCurrent();
        checkListenerCounts("After make current", 1, 1, 0, 1, 0);

        scope.close();
        checkListenerCounts("After close", 1, 1, 0, 1, 1);

        spanFromInjectedTracer.end();
        checkListenerCounts("After span end", 1, 1, 1, 1, 1);
    }

    @Test
    void testInjectedSpan() {

        checkListenerCounts("Init", 0, 0, 0, 0, 0);

        // First, before accessing the injected current span, create a new span and make it current explicitly.
        Span span = myBean.injectedTracer().spanBuilder("span-from-injected-tracer-for-currency-check").startSpan();
        checkListenerCounts("After span start", 1, 1, 0, 0, 0);

        Scope scope = span.makeCurrent();
        checkListenerCounts("After make current", 1, 1, 0, 1, 0);

        // Now retrieve the "currently current" span from the bean. It should be the "same" one we just made current, but
        // we cannot use sameInstance because our span provider method must inject its own wrapper around the current span.
        Span currentSpan = myBean.currentSpanFromInjection();
        assertThat("Current span vs. activated span", currentSpan.getSpanContext(), equalTo(span.getSpanContext()));

        scope.close();
        checkListenerCounts("After close", 1, 1, 0, 1, 1);

        span.end();
        checkListenerCounts("After span end", 1, 1, 1, 1, 1);
    }

    private void checkListenerCounts(String message,
                                     int expectedStarting,
                                     int expectedStarted,
                                     int expectedEnded,
                                     int expectedActivated,
                                     int expectedClosed) {
        assertThat(message + ": Starting spans", listener.starting(), hasSize(expectedStarting));
        assertThat(message + ": Started spans", listener.started(), hasSize(expectedStarted));
        assertThat(message + ": Ended spans", listener.ended(), hasSize(expectedEnded));
        assertThat(message + ": Activated scopes", listener.activated().keySet(), hasSize(expectedActivated));
        assertThat(message + ": Closed scopes", listener.closed().keySet(), hasSize(expectedClosed));
    }

    static class MyBean {

        @Inject
        @CallbackEnabled
        private Tracer otelTracer;

        @Inject
        @CallbackEnabled
        private Span otelSpan;

        Tracer injectedTracer() {
            return otelTracer;
        }

        Span currentSpanFromInjection() {
            return otelSpan;
        }
    }

    /**
     * A span lifecycle listener that just saves the span builder, span, and/or scope associated with each reported event.
     */
    static class MyListener implements SpanListener {

        private final List<io.helidon.tracing.Span.Builder<?>> starting = new ArrayList<>();
        private final List<io.helidon.tracing.Span> started = new ArrayList<>();
        private final List<io.helidon.tracing.Span> ended = new ArrayList<>();
        private final Map<io.helidon.tracing.Span, io.helidon.tracing.Scope> activated = new HashMap<>();
        private final Map<io.helidon.tracing.Span, io.helidon.tracing.Scope> closed = new HashMap<>();

        @Override
        public void starting(io.helidon.tracing.Span.Builder<?> spanBuilder) {
            starting.add(spanBuilder);
        }

        @Override
        public void started(io.helidon.tracing.Span span) {
            started.add(span);
        }

        @Override
        public void ended(io.helidon.tracing.Span span) {
            ended.add(span);
        }

        @Override
        public void ended(io.helidon.tracing.Span span, Throwable t) {
            ended.add(span);
        }

        @Override
        public void activated(io.helidon.tracing.Span span, io.helidon.tracing.Scope scope) {
            activated.put(span, scope);
        }

        @Override
        public void closed(io.helidon.tracing.Span span, io.helidon.tracing.Scope scope) {
            closed.put(span, scope);
        }

        void clear() {
            starting.clear();
            started.clear();
            ended.clear();
            activated.clear();
            closed.clear();
        }

        List<io.helidon.tracing.Span.Builder<?>> starting() {
            return starting;
        }

        List<io.helidon.tracing.Span> started() {
            return started;
        }

        List<io.helidon.tracing.Span> ended() {
            return ended;
        }

        Map<io.helidon.tracing.Span, io.helidon.tracing.Scope> activated() {
            return activated;
        }

        Map<io.helidon.tracing.Span, io.helidon.tracing.Scope> closed() {
            return closed;
        }
    }
}
