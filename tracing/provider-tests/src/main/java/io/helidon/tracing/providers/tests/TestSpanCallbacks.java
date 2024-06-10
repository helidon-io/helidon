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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.helidon.common.testing.junit5.InMemoryLoggingHandler;
import io.helidon.common.testing.junit5.LogRecordMatcher;
import io.helidon.tracing.Baggage;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

class TestSpanCallbacks {

    private static final String AFTER_START_BAGGAGE_VALUE = "2";
    private static final String AFTER_ACTIVATE_BAGGAGE_VALUE = "3";
    private static final String AFTER_CLOSE_BAGGAGE_VALUE = "4";
    private static final String AFTER_END_BAGGAGE_VALUE = "5";
    private static final String AFTER_END_BAD_BAGGAGE_VALUE = "6";

    private static TracerBuilder<?> tracerBuilder;

    private static List<Logger> strongLoggers = new ArrayList<>();

    @BeforeAll
    static void setup() {
        tracerBuilder = TracerBuilder.create("spanCallbackTracer");
        tracerBuilder.registerGlobal(false);
    }

    @AfterAll
    static void cleanup() {
        strongLoggers.clear();
    }

    @Test
    void checkSpanStartEndOk() {
        checkSpanStartEnd(
                hasEntry("ended", AFTER_END_BAGGAGE_VALUE),
                not(hasKey("afterEndBad")),
                hasEntry("auto-afterEndOk", AutoLoadedSpanListener.AFTER_END_OK),
                not(hasKey("auto-afterEndBad")),
                Span::end);
    }

    @Test
    void checkSpanStartEndNotOk() {
        checkSpanStartEnd(
                not(hasKey("ended")),
                hasEntry("afterEndBad", AFTER_END_BAD_BAGGAGE_VALUE),
                not(hasKey("auto-afterEndOk")),
                hasEntry("auto-afterEndBad", AutoLoadedSpanListener.AFTER_END_BAD),
                span -> span.end(new Throwable()));
    }

    @Test
    void checkRejectBeforeStart() {
        SpanListener l1 = new SpanListener() {
            @Override
            public void starting(Span.Builder<?> spanBuilder) throws SpanListener.ForbiddenOperationException {
                spanBuilder.start();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectBeforeStart");

        checkForUnsupported(spanBuilder, () -> spanBuilder.start());
    }

    @Test
    void checkRejectAfterStart() {
        SpanListener l1 = new SpanListener() {
            @Override
            public void started(Span span) throws SpanListener.ForbiddenOperationException {
                span.end();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterStart");

        checkForUnsupported(spanBuilder, spanBuilder::build);
    }

    @Test
    void checkRejectAfterActivateEndSpan() {
        SpanListener l1 = new SpanListener() {
            @Override
            public void activated(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
                span.end();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterActivateSpan");
        Span span = spanBuilder.start();
        try (Scope scope = checkForUnsupported(span, span::activate)) {
            assertThat("Scope with bad listener", span, is(notNullValue()));
        }
    }

    @Test
    void checkRejectAfterCloseSpan() {
        SpanListener l1 = new SpanListener() {
            @Override
            public void closed(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
                span.end();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterCloseSpan");
        Span span = spanBuilder.start();

        try (Scope scope = span.activate()) {
            checkForUnsupported(scope, () -> scope.close());
        }
    }

    @Test
    void checkRejectAfterCloseScope() {
        SpanListener l1 = new SpanListener() {
            @Override
            public void activated(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
                scope.close();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterCloseScope");
        Span span = spanBuilder.start();

        try (Scope scope = checkForUnsupported(span, span::activate)) {
            assertThat("Scope with bad listener", scope, is(notNullValue()));
        }
    }

    @Test
    void checkRejectAfterEndOk() {
        SpanListener l1 = new SpanListener() {
            @Override
            public void ended(Span span) throws SpanListener.ForbiddenOperationException {
                span.end();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterEndOk");
        Span span = spanBuilder.start();

        checkForUnsupported(span, () -> span.end());

    }

    @Test
    void checkRejectAfterEndBad() {
        SpanListener l1 = new SpanListener() {
            @Override
            public void ended(Span span, Throwable t) throws SpanListener.ForbiddenOperationException {
                span.end(new Throwable());
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterEndBad");
        Span span = spanBuilder.start();
        checkForUnsupported(span, () -> span.end(new Throwable()));
    }

    private <V> V checkForUnsupported(Object tracingItem, Callable<V> work) {
        Logger logger = Logger.getLogger(tracingItem.getClass().getName());
        strongLoggers.add(logger); // To avoid SpotBugs complaints about LogManager keeping wk refcs.
        InMemoryLoggingHandler handler = InMemoryLoggingHandler.create(logger);
        logger.addHandler(handler);

        try {
            V result = work.call();
            assertThat("Expected exception",
                       handler.logRecords(),
                       hasItem(LogRecordMatcher.withThrown(instanceOf(SpanListener.ForbiddenOperationException.class))));
            return result;

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            logger.removeHandler(handler);
            strongLoggers.remove(logger);
        }
    }

    private void checkForUnsupported(Object tracingItem, Runnable work) {
        Logger logger = Logger.getLogger(tracingItem.getClass().getName());
        strongLoggers.add(logger); // To avoid SpotBugs complaints about LogManager keeping wk refcs.
        InMemoryLoggingHandler handler = InMemoryLoggingHandler.create(logger);
        logger.addHandler(handler);

        try {
            work.run();
            assertThat("Expected exception",
                       handler.logRecords(),
                       hasItem(LogRecordMatcher.withThrown(instanceOf(SpanListener.ForbiddenOperationException.class))));

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            logger.removeHandler(handler);
            strongLoggers.remove(logger);
        }
    }


    private void checkSpanStartEnd(Matcher<Map<? extends String, ?>> spanEndOkMatcher,
                                   Matcher<Map<? extends String, ?>> spanEndBadMatcher,
                                   Matcher<Map<? extends String, ?>> spanAutoEndOkMatcher,
                                   Matcher<Map<? extends String, ?>> spanAutoEndBadMatcher,
                                   Consumer<Span> spanEnder) {
        Map<String, Object> listenerInfo = new HashMap<>();
        TestListener listener = new TestListener(listenerInfo);

        Tracer tracer = tracerBuilder.build();

        tracer.register(listener);
        String spanName = "checkSpanStartEnd";
        Span.Builder<?> spanBuilder = tracer.spanBuilder(spanName);
        Span span = spanBuilder.start();

        assertThat("Before start", listenerInfo, hasKey("starting"));

        assertThat("After start", baggageAsMap(span.baggage()),
                   allOf(hasEntry("started", AFTER_START_BAGGAGE_VALUE),
                         not(hasKey("activated")),
                         not(hasKey("closed")),
                         not(hasKey("ended")),
                         not(hasKey("afterEndBad")),
                         hasEntry("auto-started", AutoLoadedSpanListener.AFTER_START),
                         not(hasKey("auto-activated")),
                         not(hasKey("auto-closed")),
                         not(hasKey("auto-afterEndOk")),
                         not(hasKey("auto-afterEndBad"))));

        try (Scope scope = span.activate()) {
            assertThat("After activate", baggageAsMap(span.baggage()),
                       allOf(hasEntry("started", AFTER_START_BAGGAGE_VALUE),
                             hasEntry("activated", AFTER_ACTIVATE_BAGGAGE_VALUE),
                             not(hasKey("closed")),
                             not(hasKey("ended")),
                             not(hasKey("afterEndBad")),
                             hasEntry("auto-started", AutoLoadedSpanListener.AFTER_START),
                             hasEntry("auto-activated", AutoLoadedSpanListener.AFTER_ACTIVATE),
                             not(hasKey("auto-closed")),
                             not(hasKey("auto-afterEndOk")),
                             not(hasKey("auto-afterEndBad"))));
        }

        assertThat("After close", baggageAsMap(span.baggage()),
                   allOf(hasEntry("started", AFTER_START_BAGGAGE_VALUE),
                         hasEntry("activated", AFTER_ACTIVATE_BAGGAGE_VALUE),
                         hasEntry("closed", AFTER_CLOSE_BAGGAGE_VALUE),
                         not(hasKey("ended")),
                         not(hasKey("afterEndBad")),
                         hasEntry("auto-started", AutoLoadedSpanListener.AFTER_START),
                         hasEntry("auto-activated", AutoLoadedSpanListener.AFTER_ACTIVATE),
                         hasEntry("auto-closed", AutoLoadedSpanListener.AFTER_CLOSE),
                         not(hasKey("auto-afterEndOk")),
                         not(hasKey("auto-afterEndBad"))));

        spanEnder.accept(span);
        assertThat("After end",
                   baggageAsMap(span.baggage()),
                   allOf(hasEntry("started", AFTER_START_BAGGAGE_VALUE),
                         hasEntry("activated", AFTER_ACTIVATE_BAGGAGE_VALUE),
                         hasEntry("closed", AFTER_CLOSE_BAGGAGE_VALUE),
                         spanEndOkMatcher,
                         spanEndBadMatcher,
                         hasEntry("auto-started", AutoLoadedSpanListener.AFTER_START),
                         hasEntry("auto-activated", AutoLoadedSpanListener.AFTER_ACTIVATE),
                         hasEntry("auto-closed", AutoLoadedSpanListener.AFTER_CLOSE),
                         spanAutoEndOkMatcher,
                         spanAutoEndBadMatcher));
    }

    private Map<String, String> baggageAsMap(Baggage baggage) {
        return baggage.keys().stream()
                .map(key -> new AbstractMap.SimpleEntry<>(key, baggage.get(key).orElseThrow()))
                .collect(HashMap::new,
                         (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                         HashMap::putAll);
    }

    private static class TestListener implements SpanListener {
        private final Map<String, Object> listenerInfo;

        private TestListener(Map<String, Object> listenerInfo) {
            this.listenerInfo = listenerInfo;
        }

        @Override
        public void starting(Span.Builder<?> spanBuilder) throws SpanListener.ForbiddenOperationException {
            listenerInfo.put("starting", spanBuilder);
        }

        @Override
        public void started(Span span) throws SpanListener.ForbiddenOperationException {
            span.baggage().set("started", AFTER_START_BAGGAGE_VALUE);
        }

        @Override
        public void activated(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
            span.baggage().set("activated", AFTER_ACTIVATE_BAGGAGE_VALUE);
        }

        @Override
        public void closed(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
            span.baggage().set("closed", AFTER_CLOSE_BAGGAGE_VALUE);
        }

        @Override
        public void ended(Span span) {
            span.baggage().set("ended", AFTER_END_BAGGAGE_VALUE);
        }

        @Override
        public void ended(Span span, Throwable t) {
            span.baggage().set("afterEndBad", AFTER_END_BAD_BAGGAGE_VALUE);
        }
    }

    private static class BadListener implements SpanListener {


        BadListener() {
        }

        @Override
        public void starting(Span.Builder<?> spanBuilder) throws SpanListener.ForbiddenOperationException {
            spanBuilder.start();
        }

        @Override
        public void started(Span span) throws SpanListener.ForbiddenOperationException {
            span.end();
        }

        @Override
        public void activated(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
            scope.close();
        }

        @Override
        public void closed(Span span, Scope scope) throws SpanListener.ForbiddenOperationException {
            span.end();
        }

        @Override
        public void ended(Span span) {
        }

        @Override
        public void ended(Span span, Throwable t) {
            SpanListener.super.ended(span, t);
        }
    }
}
