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

package io.helidon.webserver.observe.tracing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.context.Context;
import io.helidon.common.uri.UriPath;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.ComponentTracingConfig;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.tracing.spi.TracingSemanticConventionsProvider;
import io.helidon.webserver.spi.ServerFeature;

import jdk.jfr.Contextual;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestTraceContextEventTest {
    private static final String EVENT_NAME = "io.helidon.RequestTraceContext";
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final TracingConfig DISABLED_SERVER_SPAN = TracingConfig.builder()
            .addComponent(ComponentTracingConfig.builder("web-server")
                                  .addSpan(SpanTracingConfig.builder("HTTP Request").enabled(false).build())
                                  .build())
            .build();

    @TempDir
    Path directory;

    @Test
    void defaultEventCorrelatesExistingServerSpanOnRequestThread() throws IOException {
        Fixture fixture = fixture(TracingConfig.ENABLED, Optional.empty(), TRACE_ID);
        doAnswer(_ -> {
            assertThat(fixture.request.context().get(SpanContext.class).orElseThrow().traceId(), is(TRACE_ID));
            new DownstreamEvent().commit();
            return null;
        }).when(fixture.chain).proceed();

        var type = EventType.getEventType(RequestTraceContextEvent.class);
        assertThat(type.getField("traceId").getAnnotation(Contextual.class), notNullValue());
        assertThat(setting(type, "enabled"), is("true"));
        assertThat(setting(type, "threshold"), is("0 ms"));
        assertThat(setting(type, "stackTrace"), is("false"));
        List<RecordedEvent> events = record(fixture, false, false);
        List<RecordedEvent> contexts = contexts(events);
        assertThat(contexts, hasSize(1));
        RecordedEvent context = contexts.getFirst();
        assertThat(context.getString("traceId"), is(TRACE_ID));
        assertThat(context.getString("origin"), is("serverSpan"));
        assertThat(context.getStackTrace(), nullValue());
        RecordedEvent downstream = events.stream()
                .filter(event -> event.getEventType().getName().equals("io.helidon.test.TracingDownstream"))
                .findFirst().orElseThrow();
        assertThat(context.getThread().getJavaThreadId(), is(downstream.getThread().getJavaThreadId()));
        assertThat(context.getStartTime().isAfter(downstream.getStartTime()), is(false));
        assertThat(context.getEndTime().isBefore(downstream.getEndTime()), is(false));
        assertThat(context.getEventType().getFields().stream().map(field -> field.getName()).collect(Collectors.toSet()),
                   is(Set.of("startTime", "duration", "eventThread", "stackTrace", "traceId", "origin")));
        verify(fixture.span).end();
        verify(fixture.scope).close();
    }

    @Test
    void disabledServerSpanRecordsOnlyExistingInboundContext() throws IOException {
        var inbound = mock(SpanContext.class);
        when(inbound.traceId()).thenReturn(TRACE_ID);
        Fixture fixture = fixture(DISABLED_SERVER_SPAN, Optional.of(inbound), null);
        List<RecordedEvent> events = contexts(record(fixture, false, false));
        assertThat(events, hasSize(1));
        assertThat(events.getFirst().getString("traceId"), is(TRACE_ID));
        assertThat(events.getFirst().getString("origin"), is("inboundContext"));
        verify(fixture.tracer, never()).spanBuilder(anyString());
    }

    @Test
    void disabledServerSpanWithoutInboundContextDoesNotInventId() throws IOException {
        Fixture fixture = fixture(DISABLED_SERVER_SPAN, Optional.empty(), null);
        assertThat(contexts(record(fixture, false, false)), hasSize(0));
        verify(fixture.tracer, never()).spanBuilder(anyString());
        verify(fixture.chain).proceed();
    }

    @Test
    void absentAndNoOpIdsAreOmitted() throws IOException {
        for (String id : new String[] {null, "", " ", "no-op", "x".repeat(129)}) {
            assertThat("Trace ID " + id,
                       contexts(record(fixture(TracingConfig.ENABLED, Optional.empty(), id), false, false)),
                       hasSize(0));
        }
    }

    @Test
    void recordingCanDisableSupplementWithoutChangingTracing() throws IOException {
        Fixture fixture = fixture(TracingConfig.ENABLED, Optional.empty(), TRACE_ID);
        assertThat(contexts(record(fixture, true, false)), hasSize(0));
        verify(fixture.span).end();
        verify(fixture.scope).close();
    }

    @Test
    void exceptionalDownstreamCommitsContextAndPreservesSpanFailure() throws IOException {
        Fixture fixture = fixture(TracingConfig.ENABLED, Optional.empty(), TRACE_ID);
        var failure = new IllegalStateException("sensitive failure detail");
        doAnswer(_ -> {
            throw failure;
        }).when(fixture.chain).proceed();
        assertThat(contexts(record(fixture, false, true)), hasSize(1));
        verify(fixture.span).end(failure);
        verify(fixture.span, never()).end();
        verify(fixture.scope).close();
    }

    private static List<RecordedEvent> contexts(List<RecordedEvent> events) {
        return events.stream().filter(event -> event.getEventType().getName().equals(EVENT_NAME)).toList();
    }

    private static String setting(EventType type, String name) {
        return type.getSettingDescriptors().stream()
                .filter(setting -> setting.getName().equals(name))
                .findFirst().orElseThrow().getDefaultValue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Fixture fixture(TracingConfig config, Optional<SpanContext> inbound, String traceId) {
        var tracer = mock(Tracer.class);
        var semanticsProvider = mock(TracingSemanticConventionsProvider.class);
        var semantics = mock(TracingSemanticConventions.class);
        var span = mock(Span.class);
        var spanContext = mock(SpanContext.class);
        var scope = mock(Scope.class);
        Span.Builder builder = mock(Span.Builder.class, RETURNS_SELF);
        when(tracer.extract(any())).thenReturn(inbound);
        when(tracer.spanBuilder(anyString())).thenReturn(builder);
        when(builder.start()).thenReturn(span);
        when(span.context()).thenReturn(spanContext);
        when(spanContext.traceId()).thenReturn(traceId);
        when(span.activate()).thenReturn(scope);
        when(semanticsProvider.create(any(), anyString(), any(), any())).thenReturn(semantics);
        when(semantics.spanName()).thenReturn("sensitive-span-name");
        var observerConfig = TracingObserverConfig.builder().tracer(tracer).envConfig(config).buildPrototype();
        var observer = TracingObserver.create(observerConfig, () -> semanticsProvider);
        var featureContext = mock(ServerFeature.ServerFeatureContext.class);
        var socketBuilders = mock(ServerFeature.SocketBuilders.class);
        var routing = mock(HttpRouting.Builder.class);
        when(featureContext.sockets()).thenReturn(Set.of());
        when(featureContext.socket(DEFAULT_SOCKET_NAME)).thenReturn(socketBuilders);
        when(socketBuilders.httpRouting()).thenReturn(routing);
        observer.register(featureContext, List.of(), endpoint -> endpoint);
        var featureCaptor = ArgumentCaptor.forClass(HttpFeature.class);
        verify(routing).addFeature(featureCaptor.capture());
        featureCaptor.getValue().setup(routing);
        var filterCaptor = ArgumentCaptor.forClass(Filter.class);
        verify(routing).addFilter(filterCaptor.capture());
        var request = mock(RoutingRequest.class);
        var prologue = mock(HttpPrologue.class);
        when(request.context()).thenReturn(Context.create());
        when(request.prologue()).thenReturn(prologue);
        when(prologue.method()).thenReturn(Method.GET);
        when(prologue.uriPath()).thenReturn(UriPath.create("/private-account/secret?credential=secret"));
        return new Fixture(filterCaptor.getValue(), mock(FilterChain.class), request, mock(RoutingResponse.class),
                           tracer, span, scope);
    }

    private List<RecordedEvent> record(Fixture fixture, boolean disabled, boolean fails) throws IOException {
        Path file = directory.resolve("trace.jfr");
        try (var recording = new Recording()) {
            EventType.getEventType(RequestTraceContextEvent.class);
            recording.enable(DownstreamEvent.class);
            if (disabled) {
                recording.disable(EVENT_NAME);
            }
            recording.start();
            if (fails) {
                assertThrows(IllegalStateException.class, () -> fixture.filter.filter(fixture.chain,
                                                                                    fixture.request,
                                                                                    fixture.response));
            } else {
                fixture.filter.filter(fixture.chain, fixture.request, fixture.response);
            }
            recording.stop();
            recording.dump(file);
        }
        return RecordingFile.readAllEvents(file);
    }

    private record Fixture(Filter filter, FilterChain chain, RoutingRequest request, RoutingResponse response,
                           Tracer tracer, Span span, Scope scope) {
    }

    @Name("io.helidon.test.TracingDownstream")
    static final class DownstreamEvent extends Event {
    }
}
