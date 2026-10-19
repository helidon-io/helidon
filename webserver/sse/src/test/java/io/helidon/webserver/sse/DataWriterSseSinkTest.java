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

package io.helidon.webserver.sse;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.SinkProviderContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataWriterSseSinkTest {

    @Test
    void shouldWriteReconnectDelayWithOtherFields() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SseEvent event = SseEvent.builder()
                .comment("first\r\nsecond")
                .id("event1")
                .name("update")
                .reconnectDelay(Duration.ofSeconds(2))
                .data("hello\r\nworld".getBytes(StandardCharsets.UTF_8))
                .build();

        try (DataWriterSseSink sink = createSink(output)) {
            sink.emit(event);
            assertThat(output.toString(StandardCharsets.UTF_8),
                       equalTo(":first\n:second\nid:event1\nretry:2000\nevent:update\ndata:hello\ndata:world\n\n"));
        }
    }

    @Test
    void shouldOmitUnsetReconnectDelay() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SseEvent event = SseEvent.create("hello".getBytes(StandardCharsets.UTF_8));

        assertThat(event.reconnectMillis(), is(Optional.empty()));
        try (DataWriterSseSink sink = createSink(output)) {
            sink.emit(event);
            assertThat(output.toString(StandardCharsets.UTF_8), equalTo("data:hello\n\n"));
        }
    }

    @Test
    void shouldWriteZeroReconnectDelay() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SseEvent event = SseEvent.builder()
                .reconnectDelay(Duration.ZERO)
                .data("hello".getBytes(StandardCharsets.UTF_8))
                .build();

        try (DataWriterSseSink sink = createSink(output)) {
            sink.emit(event);
            assertThat(output.toString(StandardCharsets.UTF_8), equalTo("retry:0\ndata:hello\n\n"));
        }
    }

    @Test
    void shouldWriteReconnectDelayInWholeMilliseconds() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataWriterSseSink sink = createSink(output)) {
            sink.emit(SseEvent.builder().reconnectDelay(Duration.ofNanos(1_999_999)).build());
            sink.emit(SseEvent.builder().reconnectDelay(Duration.ofNanos(999_999)).build());
            assertThat(output.toString(StandardCharsets.UTF_8), equalTo("retry:1\n\nretry:0\n\n"));
        }
    }

    @Test
    void shouldWriteReconnectDelayWithoutIntegerTruncation() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SseEvent event = SseEvent.builder()
                .reconnectDelay(Duration.ofMillis(3_000_000_000L))
                .build();

        try (DataWriterSseSink sink = createSink(output)) {
            sink.emit(event);
            assertThat(output.toString(StandardCharsets.UTF_8), equalTo("retry:3000000000\n\n"));
        }
    }

    @Test
    void shouldWriteRetryOnlyEventWithoutRepeatingDelayOnNextEvent() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataWriterSseSink sink = createSink(output)) {
            sink.emit(SseEvent.builder().reconnectDelay(Duration.ofSeconds(2)).build());
            sink.emit(SseEvent.create("next".getBytes(StandardCharsets.UTF_8)));
            assertThat(output.toString(StandardCharsets.UTF_8), equalTo("retry:2000\n\ndata:next\n\n"));
        }
    }

    private static DataWriterSseSink createSink(OutputStream output) {
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.closeRunnable()).thenReturn(() -> { });
        when(response.outputStream()).thenReturn(output);
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        return new DataWriterSseSink(context);
    }
}
