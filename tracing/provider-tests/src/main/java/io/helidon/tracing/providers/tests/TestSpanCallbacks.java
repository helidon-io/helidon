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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.tracing.Baggage;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanLifeCycleListener;
import io.helidon.tracing.Tag;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.UnsupportedActivationException;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSpanCallbacks {

    private static final String AFTER_START_BAGGAGE_VALUE = "2";
    private static final String AFTER_ACTIVATE_BAGGAGE_VALUE = "3";
    private static final String AFTER_CLOSE_BAGGAGE_VALUE = "4";
    private static final String AFTER_END_BAGGAGE_VALUE = "5";
    private static final String AFTER_END_BAD_BAGGAGE_VALUE = "6";

    private static TracerBuilder<?> tracerBuilder;

    @BeforeAll
    static void setup() {
        tracerBuilder = TracerBuilder.create("spanCallbackTracer");
        tracerBuilder.registerGlobal(false);
    }


    @Test
    void checkSpanStartEndOk() {
        checkSpanStartEnd(
                hasEntry("afterEnd", AFTER_END_BAGGAGE_VALUE),
                not(hasKey("afterEndBad")),
                hasEntry("auto-afterEndOk", AutoLoadedSpanLifeCycleListener.AFTER_END_OK),
                not(hasKey("auto-afterEndBad")),
                Span::end);
    }

    @Test
    void checkSpanStartEndNotOk() {
        checkSpanStartEnd(
                not(hasKey("afterEnd")),
                hasEntry("afterEndBad", AFTER_END_BAD_BAGGAGE_VALUE),
                not(hasKey("auto-afterEndOk")),
                hasEntry("auto-afterEndBad", AutoLoadedSpanLifeCycleListener.AFTER_END_BAD),
                span -> span.end(new Throwable()));
    }

    @Test
    void checkRejectBeforeStart() {
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void beforeStart(Span.Builder<?> spanBuilder) throws UnsupportedOperationException {
                spanBuilder.start();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectBeforeStart");

        assertThrows(UnsupportedOperationException.class,
                     spanBuilder::build);
    }

    @Test
    void checkRejectAfterStart() {
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void afterStart(Span span) throws UnsupportedOperationException {
                span.end();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterStart");

        assertThrows(UnsupportedOperationException.class,
                     spanBuilder::build);
    }

    @Test
    void checkRejectAfterActivateEndSpan() {
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void afterActivate(Span span, Scope scope) throws UnsupportedOperationException {
                span.end();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterActivateSpan");
        Span span = spanBuilder.start();
        UnsupportedActivationException ex = assertThrows(UnsupportedActivationException.class,
                     span::activate);
        ex.scope().close();
    }

    @Test
    void checkRejectAfterCloseSpan() {
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void afterClose(Span span, Scope scope) throws UnsupportedOperationException {
                span.end();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterCloseSpan");
        Span span = spanBuilder.start();

        Scope scope = span.activate();
        assertThrows(UnsupportedOperationException.class, scope::close);
    }

    @Test
    void checkRejectAfterCloseScope() {
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void afterActivate(Span span, Scope scope) throws UnsupportedOperationException {
                scope.close();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterCloseScope");
        Span span = spanBuilder.start();

        UnsupportedActivationException ex = assertThrows(UnsupportedActivationException.class, span::activate);
        ex.scope().close();
    }

    @Test
    void checkRejectAfterEndOk() {
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void afterEnd(Span span) throws UnsupportedOperationException {
                span.end();
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterEndOk");
        Span span = spanBuilder.start();

        assertThrows(UnsupportedOperationException.class,
                     span::end);
    }

    @Test
    void checkRejectAfterEndBad() {
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void afterEnd(Span span, Throwable t) throws UnsupportedOperationException {
                span.end(new Throwable());
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);
        Span.Builder<?> spanBuilder = tracer.spanBuilder("rejectAfterEndBad");
        Span span = spanBuilder.start();
        assertThrows(UnsupportedOperationException.class,
                     () -> span.end(new Throwable()));
    }

    @Test
    void checkNewSpanBuilder() {
        Tracer tracer = tracerBuilder.build();
        AtomicReference<String> nameRef = new AtomicReference<>();

        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void newSpanBuilder(Tracer tracer, Span.Builder<?> spanBuilder, String name) {
                nameRef.set(name);
            }
        };

        tracer.register(l1);
        tracer.spanBuilder("newSpan");

        assertThat("Span builder name", nameRef.get(), is(equalTo("newSpan")));
    }

    @Test
    void checkTagOnSpanBuilder() {
        Map<String, Object> tags = new HashMap<>();
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void tag(Span.Builder<?> spanBuilder, Tag<?> tag) {
                tags.put(tag.key(), tag.value());
            }

            @Override
            public void tag(Span.Builder<?> spanBuilder, String key, String value) {
                tags.put(key, value);
            }

            @Override
            public void tag(Span.Builder<?> spanBuilder, String key, Boolean value) {
                tags.put(key, value);
            }

            @Override
            public void tag(Span.Builder<?> spanBuilder, String key, Number value) {
                tags.put(key, value);
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);

        Span.Builder<?> spanBuilder = tracer.spanBuilder("builder1");
        spanBuilder.tag(Tag.create("keys1", "string1"));
        spanBuilder.tag(Tag.create("keyb1", true));
        spanBuilder.tag(Tag.create("keyn1", 4.1D));
        spanBuilder.tag("keys2", "string2");
        spanBuilder.tag("keyb2", true);
        spanBuilder.tag("keyn2", 4L);

        assertThat("Tags after assignment to span builder",
                   tags,
                   hasEntry("keys1", "string1"));
        assertThat("Tags after assignment to span builder",
                   tags,
                   hasEntry("keyb1", true));
        assertThat("Tags after assignment to span builder",
                   tags,
                   hasEntry("keyn1", 4.1D));
        assertThat("Tags after assignment to span builder",
                   tags,
                   hasEntry("keys2", "string2"));
        assertThat("Tags after assignment to span builder",
                   tags,
                   hasEntry("keyb2", true));
        assertThat("Tags after assignment to span builder",
                   tags,
                   hasEntry("keyn2", 4L));
    }

    @Test
    void checkTagOnSpan() {
        Map<String, Object> tags = new HashMap<>();
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void tag(Span span, Tag<?> tag) {
                tags.put(tag.key(), tag.value());
            }

            @Override
            public void tag(Span span, String key, String value) {
                tags.put(key, value);
            }

            @Override
            public void tag(Span span, String key, Boolean value) {
                tags.put(key, value);
            }

            @Override
            public void tag(Span span, String key, Number value) {
                tags.put(key, value);
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);

        Span span = tracer.spanBuilder("builder1").start();
        span.tag(Tag.create("keys1", "string1"));
        span.tag(Tag.create("keyb1", true));
        span.tag(Tag.create("keyn1", 4.1D));
        span.tag("keys2", "string2");
        span.tag("keyb2", true);
        span.tag("keyn2", 4L);

        try {
            assertThat("Tags after assignment to span",
                       tags,
                       hasEntry("keys1", "string1"));
            assertThat("Tags after assignment to span",
                       tags,
                       hasEntry("keys1", "string1"));
            assertThat("Tags after assignment to span",
                       tags,
                       hasEntry("keyb1", true));
            assertThat("Tags after assignment to span",
                       tags,
                       hasEntry("keyn1", 4.1D));
            assertThat("Tags after assignment to span",
                       tags,
                       hasEntry("keys2", "string2"));
            assertThat("Tags after assignment to span",
                       tags,
                       hasEntry("keyb2", true));
            assertThat("Tags after assignment to span",
                       tags,
                       hasEntry("keyn2", 4L));
        } catch (Throwable t) {
            span.end();
        }
    }

    @Test
    void checkEventsAddedToSpan() {
        Map<String, Map<String, ?>> events = new HashMap<>();
        SpanLifeCycleListener l1 = new SpanLifeCycleListener() {
            @Override
            public void addEvent(Span span, String message, Map<String, ?> attributes) {
                events.put(message, attributes);
            }
        };

        Tracer tracer = tracerBuilder.build();
        tracer.register(l1);

        Span span = tracer.spanBuilder("builder1").start();
        span.addEvent("ev1");
        span.addEvent("ev2", Map.of("attr1", "val1"));

        try {
            assertThat("Event with no attrs",
                       events,
                       hasEntry("ev1", Map.of()));
            assertThat("Event with attrs",
                       events,
                       hasEntry("ev2", Map.of("attr1", "val1")));
        } catch (Throwable t) {
            span.end();
        }
    }

    private void checkSpanStartEnd(Matcher<Map<? extends String, ?>> spanEndOkMatcher,
                                   Matcher<Map<? extends String, ?>> spanEndBadMatcher,
                                   Matcher<Map<? extends String, ?>> spanAutoEndOkMatcher,
                                   Matcher<Map<? extends String, ?>> spanAutoEndBadMatcher,
                                   Consumer<Span> spanEnder) {
        Map<String, Object> listenerInfo = new HashMap<>();
        TestLifeCycleListener listener = new TestLifeCycleListener(listenerInfo);

        Tracer tracer = tracerBuilder.build();

        tracer.register(listener);
        String spanName = "checkSpanStartEnd";
        Span.Builder<?> spanBuilder = tracer.spanBuilder(spanName);
        Span span = spanBuilder.start();

        assertThat("Before start", listenerInfo, hasKey("beforeStart"));

        assertThat("After start", baggageAsMap(span.baggage()),
                   allOf(hasEntry("afterStart", AFTER_START_BAGGAGE_VALUE),
                         not(hasKey("afterActivate")),
                         not(hasKey("afterClose")),
                         not(hasKey("afterEnd")),
                         not(hasKey("afterEndBad")),
                         hasEntry("auto-afterStart", AutoLoadedSpanLifeCycleListener.AFTER_START),
                         not(hasKey("auto-afterActivate")),
                         not(hasKey("auto-afterClose")),
                         not(hasKey("auto-afterEndOk")),
                         not(hasKey("auto-afterEndBad"))));

        try (Scope scope = span.activate()) {
            assertThat("After activate", baggageAsMap(span.baggage()),
                       allOf(hasEntry("afterStart", AFTER_START_BAGGAGE_VALUE),
                             hasEntry("afterActivate", AFTER_ACTIVATE_BAGGAGE_VALUE),
                             not(hasKey("afterClose")),
                             not(hasKey("afterEnd")),
                             not(hasKey("afterEndBad")),
                             hasEntry("auto-afterStart", AutoLoadedSpanLifeCycleListener.AFTER_START),
                             hasEntry("auto-afterActivate", AutoLoadedSpanLifeCycleListener.AFTER_ACTIVATE),
                             not(hasKey("auto-afterClose")),
                             not(hasKey("auto-afterEndOk")),
                             not(hasKey("auto-afterEndBad"))));
        }

        assertThat("After close", baggageAsMap(span.baggage()),
                   allOf(hasEntry("afterStart", AFTER_START_BAGGAGE_VALUE),
                         hasEntry("afterActivate", AFTER_ACTIVATE_BAGGAGE_VALUE),
                         hasEntry("afterClose", AFTER_CLOSE_BAGGAGE_VALUE),
                         not(hasKey("afterEnd")),
                         not(hasKey("afterEndBad")),
                         hasEntry("auto-afterStart", AutoLoadedSpanLifeCycleListener.AFTER_START),
                         hasEntry("auto-afterActivate", AutoLoadedSpanLifeCycleListener.AFTER_ACTIVATE),
                         hasEntry("auto-afterClose", AutoLoadedSpanLifeCycleListener.AFTER_CLOSE),
                         not(hasKey("auto-afterEndOk")),
                         not(hasKey("auto-afterEndBad"))));

        spanEnder.accept(span);
        assertThat("After end",
                   baggageAsMap(span.baggage()),
                   allOf(hasEntry("afterStart", AFTER_START_BAGGAGE_VALUE),
                         hasEntry("afterActivate", AFTER_ACTIVATE_BAGGAGE_VALUE),
                         hasEntry("afterClose", AFTER_CLOSE_BAGGAGE_VALUE),
                         spanEndOkMatcher,
                         spanEndBadMatcher,
                         hasEntry("auto-afterStart", AutoLoadedSpanLifeCycleListener.AFTER_START),
                         hasEntry("auto-afterActivate", AutoLoadedSpanLifeCycleListener.AFTER_ACTIVATE),
                         hasEntry("auto-afterClose", AutoLoadedSpanLifeCycleListener.AFTER_CLOSE),
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

    private static class TestLifeCycleListener implements SpanLifeCycleListener {
        private final Map<String, Object> listenerInfo;

        private TestLifeCycleListener(Map<String, Object> listenerInfo) {
            this.listenerInfo = listenerInfo;
        }

        @Override
        public void beforeStart(Span.Builder<?> spanBuilder) throws UnsupportedOperationException {
            listenerInfo.put("beforeStart", spanBuilder);
        }

        @Override
        public void afterStart(Span span) throws UnsupportedOperationException {
            span.baggage().set("afterStart", AFTER_START_BAGGAGE_VALUE);
        }

        @Override
        public void afterActivate(Span span, Scope scope) throws UnsupportedOperationException {
            span.baggage().set("afterActivate", AFTER_ACTIVATE_BAGGAGE_VALUE);
        }

        @Override
        public void afterClose(Span span, Scope scope) throws UnsupportedOperationException {
            span.baggage().set("afterClose", AFTER_CLOSE_BAGGAGE_VALUE);
        }

        @Override
        public void afterEnd(Span span) {
            span.baggage().set("afterEnd", AFTER_END_BAGGAGE_VALUE);
        }

        @Override
        public void afterEnd(Span span, Throwable t) {
            span.baggage().set("afterEndBad", AFTER_END_BAD_BAGGAGE_VALUE);
        }
    }

    private static class BadListener implements SpanLifeCycleListener {


        BadListener() {
        }

        @Override
        public void beforeStart(Span.Builder<?> spanBuilder) throws UnsupportedOperationException {
            spanBuilder.start();
        }

        @Override
        public void afterStart(Span span) throws UnsupportedOperationException {
            span.end();
        }

        @Override
        public void afterActivate(Span span, Scope scope) throws UnsupportedOperationException {
            scope.close();
        }

        @Override
        public void afterClose(Span span, Scope scope) throws UnsupportedOperationException {
            span.end();
        }

        @Override
        public void afterEnd(Span span) {
        }

        @Override
        public void afterEnd(Span span, Throwable t) {
            io.helidon.tracing.SpanLifeCycleListener.super.afterEnd(span, t);
        }
    }
}
