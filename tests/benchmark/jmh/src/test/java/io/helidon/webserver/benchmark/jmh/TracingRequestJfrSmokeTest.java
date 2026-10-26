/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.benchmark.jmh;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.helidon.common.context.Contexts;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TracingRequestJfrSmokeTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({"HTTP1, OFF, SERVER_SPAN", "HTTP1, DISABLED, SERVER_SPAN", "HTTP1, ENABLED, SERVER_SPAN",
                "HTTP2, OFF, SERVER_SPAN", "HTTP2, DISABLED, SERVER_SPAN", "HTTP2, ENABLED, SERVER_SPAN",
                "HTTP1, OFF, INBOUND_CONTEXT", "HTTP1, DISABLED, INBOUND_CONTEXT", "HTTP1, ENABLED, INBOUND_CONTEXT",
                "HTTP2, OFF, INBOUND_CONTEXT", "HTTP2, DISABLED, INBOUND_CONTEXT", "HTTP2, ENABLED, INBOUND_CONTEXT"})
    void tracingBranchesAndRecordingModes(String protocol, String mode, String tracingMode) throws Exception {
        var observations = new ConcurrentLinkedQueue<Observation>();
        var state = new TracingRequestJfrJmhBenchmark();
        state.protocol = protocol;
        state.recordingMode = mode;
        state.tracingMode = tracingMode;
        try {
            state.setup(() -> {
                var context = Contexts.context().orElseThrow().get(SpanContext.class).orElseThrow();
                observations.add(new Observation(context.traceId(), context.spanId(), Span.current().isPresent(),
                                                 Thread.currentThread().threadId()));
            });
            observations.clear();
            assertThat(state.tracedKeepAliveExchange(), is("Hello, World!"));
            assertThat(state.tracedKeepAliveExchange(), is("Hello, World!"));
            assertThat(observations, hasSize(2));
            boolean serverSpan = "SERVER_SPAN".equals(tracingMode);
            for (Observation observation : observations) {
                assertThat("valid trace ID", observation.traceId().matches("[0-9a-f]{32}"), is(true));
                assertThat(observation.traceId(), not("00000000000000000000000000000000"));
                assertThat("server span branch executed", observation.serverSpan(), is(serverSpan));
                assertThat("valid span ID", observation.spanId().matches("[0-9a-f]{16}"), is(true));
                assertThat(observation.spanId(), not("0000000000000000"));
                if (!serverSpan) {
                    assertThat("extracted W3C trace ID", observation.traceId(),
                               is(TracingRequestJfrJmhBenchmark.TRACE_ID));
                    assertThat("inbound branch retains remote span", observation.spanId(),
                               is(TracingRequestJfrJmhBenchmark.PARENT_SPAN_ID));
                }
            }
            if (serverSpan) {
                assertThat("independent root traces",
                           observations.stream().map(Observation::traceId).distinct().count(), is(2L));
            }
            if (!"OFF".equals(mode)) {
                Path file = directory.resolve(protocol + "-" + mode + "-" + tracingMode + ".jfr");
                state.dump(file);
                var events = RecordingFile.readAllEvents(file);
                var requests = named(events, HttpRequestJfrJmhBenchmark.REQUEST_EVENT).stream()
                        .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                        .toList();
                var traces = named(events, TracingRequestJfrJmhBenchmark.TRACE_EVENT);
                boolean expected = "ENABLED".equals(mode)
                        && Boolean.parseBoolean(System.getProperty("http.request.jfr.expectEvents", "true"));
                assertThat("request event count", requests, hasSize(expected ? 2 : 0));
                assertThat("trace event count", traces, hasSize(expected ? 2 : 0));
                assertThat("only the two selected Helidon events", events.stream()
                        .filter(event -> event.getEventType().getName().startsWith("io.helidon."))
                        .filter(event -> !List.of(HttpRequestJfrJmhBenchmark.REQUEST_EVENT,
                                                TracingRequestJfrJmhBenchmark.TRACE_EVENT)
                                .contains(event.getEventType().getName()))
                        .toList(), hasSize(0));
                // The two exchanges complete sequentially, so request start order matches handler observation order.
                var handlers = List.copyOf(observations);
                var unmatched = new ArrayList<>(traces);
                for (int i = 0; i < requests.size(); i++) {
                    RecordedEvent request = requests.get(i);
                    Observation handler = handlers.get(i);
                    long threadId = request.getThread().getJavaThreadId();
                    assertThat("request " + i + " uses its handler thread", threadId, is(handler.threadId()));
                    assertThat(request.getString("routeTemplate"), is("/benchmark"));
                    var enclosed = traces.stream()
                            .filter(trace -> trace.getThread().getJavaThreadId() == threadId)
                            .filter(trace -> !trace.getStartTime().isBefore(request.getStartTime()))
                            .filter(trace -> !trace.getEndTime().isAfter(request.getEndTime()))
                            .toList();
                    assertThat("request " + i + " contains exactly one trace event", enclosed, hasSize(1));
                    RecordedEvent trace = enclosed.getFirst();
                    assertThat("request " + i + " has its own trace event", unmatched.remove(trace), is(true));
                    assertThat(trace.getString("origin"), is(serverSpan ? "serverSpan" : "inboundContext"));
                    assertThat("request " + i + " records its handler's actual trace ID",
                               trace.getString("traceId"), is(handler.traceId()));
                }
                assertThat("every trace event belongs to a distinct request", unmatched, hasSize(0));
            }
        } finally {
            state.tearDown();
        }
    }

    private static List<RecordedEvent> named(List<RecordedEvent> events, String name) {
        return events.stream().filter(event -> name.equals(event.getEventType().getName())).toList();
    }

    private record Observation(String traceId, String spanId, boolean serverSpan, long threadId) {
    }
}
