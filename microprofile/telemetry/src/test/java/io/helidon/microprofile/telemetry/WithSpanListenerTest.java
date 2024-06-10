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
package io.helidon.microprofile.telemetry;

import java.util.HashMap;
import java.util.Map;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

class WithSpanListenerTest extends WithSpanTestBase {

    private static final String AFTER_START_BAGGAGE_VALUE = "2";
    private static final String AFTER_ACTIVATE_BAGGAGE_VALUE = "3";
    private static final String AFTER_CLOSE_BAGGAGE_VALUE = "4";
    private static final String AFTER_END_BAGGAGE_VALUE = "5";
    private static final String AFTER_END_BAD_BAGGAGE_VALUE = "6";

    @Inject
    private Tracer tracer;

    @Test
    void checkListenerOnWithSpan() {
        Map<String, Object> info = new HashMap<>();
        tracer.register(new TestListener(info));

        withSpanBean.runWithAttrsScalar("listenerTestScalar", 21L);
        assertThat("Starting", info.keySet(), containsInAnyOrder("starting",
                                                       "started",
                                                       "activated",
                                                       "closed",
                                                       "ended"));

    }

    private record TestListener(Map<String, Object> listenerInfo) implements SpanListener {

        @Override
            public void starting(Span.Builder<?> spanBuilder) throws ForbiddenOperationException {
                listenerInfo.put("starting", spanBuilder);
            }

            @Override
            public void started(Span span) throws ForbiddenOperationException {
                listenerInfo.put("started", AFTER_START_BAGGAGE_VALUE);
            }

            @Override
            public void activated(Span span, Scope scope) throws ForbiddenOperationException {
                listenerInfo.put("activated", AFTER_ACTIVATE_BAGGAGE_VALUE);
            }

            @Override
            public void closed(Span span, Scope scope) throws ForbiddenOperationException {
                listenerInfo.put("closed", AFTER_CLOSE_BAGGAGE_VALUE);
            }

            @Override
            public void ended(Span span) {
                listenerInfo.put("ended", AFTER_END_BAGGAGE_VALUE);
            }

            @Override
            public void ended(Span span, Throwable t) {
                listenerInfo.put("endedBad", AFTER_END_BAD_BAGGAGE_VALUE);
            }
        }

}
