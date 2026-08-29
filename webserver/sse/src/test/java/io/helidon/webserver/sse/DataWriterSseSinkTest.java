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
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HeaderNames;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.SinkProviderContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class DataWriterSseSinkTest {

    @Test
    void shouldCloseEntityOutputStreamBeforeCommit() {
        AtomicBoolean outputStreamClosed = new AtomicBoolean();
        AtomicBoolean committedAfterFinalBytes = new AtomicBoolean();
        ByteArrayOutputStream entityOutputStream = new ByteArrayOutputStream() {
            @Override
            public void close() {
                writeBytes("[final]".getBytes(StandardCharsets.UTF_8));
                outputStreamClosed.set(true);
            }
        };

        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ServerRequest request = mock(ServerRequest.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.serverRequest()).thenReturn(request);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return Optional.of(entityOutputStream);
        });
        when(context.closeRunnable()).thenReturn(() -> {
            String entity = entityOutputStream.toString(StandardCharsets.UTF_8);
            committedAfterFinalBytes.set(outputStreamClosed.get() && entity.endsWith("[final]"));
        });
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(request.headers()).thenReturn(ServerRequestHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        enableBuffering(listenerContext);

        try (DataWriterSseSink sink = new DataWriterSseSink(context)) {
            sink.emit(SseEvent.create("hello".getBytes(StandardCharsets.UTF_8)));
        }

        assertThat(committedAfterFinalBytes.get(), is(true));
        assertThat(entityOutputStream.toString(StandardCharsets.UTF_8), equalTo("data:hello\n\n[final]"));
    }

    @Test
    void shouldNotApplyContentEncodingToProtocolEntityOutputStream() {
        ByteArrayOutputStream entityOutputStream = new ByteArrayOutputStream();
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ServerRequest request = mock(ServerRequest.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.serverRequest()).thenReturn(request);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return Optional.of(entityOutputStream);
        });
        when(context.closeRunnable()).thenReturn(() -> { });
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(request.headers()).thenReturn(ServerRequestHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        enableBuffering(listenerContext);

        try (DataWriterSseSink sink = new DataWriterSseSink(context)) {
            sink.emit(SseEvent.create("hello".getBytes(StandardCharsets.UTF_8)));
        }

        verifyZeroInteractions(contentEncodingContext);
        assertThat(entityOutputStream.toString(StandardCharsets.UTF_8), equalTo("data:hello\n\n"));
    }

    @Test
    void shouldPrepareResponseHeadersBeforeRequestingProtocolStream() {
        AtomicBoolean headersPrepared = new AtomicBoolean();
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            headersPrepared.set(responseHeaders.contains(HeaderNames.CONTENT_TYPE)
                                        && responseHeaders.contains(HeaderNames.CACHE_CONTROL)
                                        && responseHeaders.contains(HeaderNames.DATE));
            invocation.<Runnable>getArgument(0).run();
            return Optional.of(new ByteArrayOutputStream());
        });
        when(context.closeRunnable()).thenReturn(() -> { });
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(responseHeaders);
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        enableBuffering(listenerContext);

        try (DataWriterSseSink _ = new DataWriterSseSink(context)) {
            // Close the protocol stream before committing the response.
        }

        assertThat("SSE headers when requesting the protocol stream", headersPrepared.get(), is(true));
        assertThat("SSE Date header is changing", responseHeaders.get(HeaderNames.DATE).changing(), is(true));
    }

    @Test
    void shouldFlushProtocolHeadersBeforeFirstEvent() {
        AtomicBoolean entityStreamCreated = new AtomicBoolean();
        AtomicBoolean headersFlushedAfterStreamCreation = new AtomicBoolean();
        ByteArrayOutputStream entityOutputStream = new ByteArrayOutputStream();
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            entityStreamCreated.set(true);
            return Optional.of(entityOutputStream);
        });
        doAnswer(_ -> {
            headersFlushedAfterStreamCreation.set(entityStreamCreated.get());
            return null;
        }).when(context).flushHeaders();
        when(context.closeRunnable()).thenReturn(() -> { });
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        enableBuffering(listenerContext);

        try (DataWriterSseSink _ = new DataWriterSseSink(context)) {
            assertThat("protocol headers flushed after entity stream creation",
                       headersFlushedAfterStreamCreation.get(),
                       is(true));
            assertThat("entity bytes before first event", entityOutputStream.size(), is(0));
        }
    }

    @Test
    void shouldNotReplayBufferedProtocolEventAfterFailedFlushCleanup() {
        AtomicInteger writes = new AtomicInteger();
        AtomicBoolean responseCommitted = new AtomicBoolean();
        AtomicBoolean entityStreamClosed = new AtomicBoolean();
        ByteArrayOutputStream entityBytes = new ByteArrayOutputStream();
        OutputStream entityOutputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[] {(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                writes.incrementAndGet();
                entityBytes.write(bytes, offset, Math.min(length, 5));
                throw new IOException("flow-control timeout");
            }

            @Override
            public void close() {
                entityStreamClosed.set(true);
            }
        };
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return Optional.of(entityOutputStream);
        });
        when(context.closeRunnable()).thenReturn(() -> responseCommitted.set(true));
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        enableBuffering(listenerContext);

        assertThrows(UncheckedIOException.class, () -> {
            try (DataWriterSseSink sink = new DataWriterSseSink(context)) {
                sink.emit(SseEvent.create("hello".getBytes(StandardCharsets.UTF_8)));
            }
        });

        assertThat("failed cleanup must not replay buffered event bytes", writes.get(), is(1));
        assertThat("failed cleanup must not commit the protocol response", responseCommitted.get(), is(false));
        assertThat("failed cleanup must close the decorated protocol stream", entityStreamClosed.get(), is(true));
        assertThat(entityBytes.toString(StandardCharsets.UTF_8), equalTo("data:"));
    }

    @Test
    void shouldNotReplayBufferedProtocolEventAfterHttp2TimeoutCleanup() {
        AtomicInteger writes = new AtomicInteger();
        AtomicBoolean responseCommitted = new AtomicBoolean();
        AtomicBoolean entityStreamClosed = new AtomicBoolean();
        ByteArrayOutputStream entityBytes = new ByteArrayOutputStream();
        OutputStream entityOutputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[] {(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) {
                if (writes.incrementAndGet() == 1) {
                    entityBytes.write(bytes, offset, Math.min(length, 5));
                    throw new Http2Exception(Http2ErrorCode.FLOW_CONTROL, "Flow control update wait time-out.");
                }
                entityBytes.write(bytes, offset, length);
            }

            @Override
            public void close() {
                entityStreamClosed.set(true);
            }
        };
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return Optional.of(entityOutputStream);
        });
        when(context.closeRunnable()).thenReturn(() -> responseCommitted.set(true));
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        enableBuffering(listenerContext);

        try (DataWriterSseSink sink = new DataWriterSseSink(context)) {
            Http2Exception failure = assertThrows(Http2Exception.class,
                                                  () -> sink.emit(SseEvent.create("hello".getBytes(StandardCharsets.UTF_8))));
            Http2Exception repeatedFailure = assertThrows(Http2Exception.class,
                                                          () -> sink.emit(SseEvent.create("again".getBytes(StandardCharsets.UTF_8))));

            assertThat(failure.code(), is(Http2ErrorCode.FLOW_CONTROL));
            assertThat("subsequent emits must report the original transport failure",
                       repeatedFailure,
                       sameInstance(failure));
        }

        assertThat("failed cleanup must not replay buffered event bytes", writes.get(), is(1));
        assertThat("failed cleanup must not commit the protocol response", responseCommitted.get(), is(false));
        assertThat("failed cleanup must close the decorated protocol stream", entityStreamClosed.get(), is(true));
        assertThat(entityBytes.toString(StandardCharsets.UTF_8), equalTo("data:"));
    }

    @Test
    void shouldWriteSmallProtocolEventOnce() {
        AtomicInteger writes = new AtomicInteger();
        ByteArrayOutputStream entityOutputStream = new ByteArrayOutputStream() {
            @Override
            public void write(byte[] bytes, int offset, int length) {
                writes.incrementAndGet();
                super.write(bytes, offset, length);
            }
        };
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return Optional.of(entityOutputStream);
        });
        when(context.closeRunnable()).thenReturn(() -> { });
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        enableBuffering(listenerContext);

        try (DataWriterSseSink sink = new DataWriterSseSink(context)) {
            sink.emit(SseEvent.create("hello".getBytes(StandardCharsets.UTF_8)));
            assertThat("one protocol write per small event", writes.get(), is(1));
            assertThat(entityOutputStream.toString(StandardCharsets.UTF_8), equalTo("data:hello\n\n"));
        }
    }

    @Test
    void shouldStreamProtocolEventLargerThanConfiguredBuffer() {
        AtomicInteger writes = new AtomicInteger();
        ByteArrayOutputStream entityOutputStream = new ByteArrayOutputStream() {
            @Override
            public void write(byte[] bytes, int offset, int length) {
                writes.incrementAndGet();
                super.write(bytes, offset, length);
            }
        };
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        String payload = "x".repeat(64);

        when(context.serverResponse()).thenReturn(response);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return Optional.of(entityOutputStream);
        });
        when(context.closeRunnable()).thenReturn(() -> { });
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);
        writeBufferSize(listenerContext, 8);

        try (DataWriterSseSink sink = new DataWriterSseSink(context)) {
            sink.emit(SseEvent.create(payload.getBytes(StandardCharsets.UTF_8)));
            assertThat("event larger than the configured buffer must stream", writes.get(), greaterThan(1));
            assertThat(entityOutputStream.toString(StandardCharsets.UTF_8), equalTo("data:" + payload + "\n\n"));
        }
    }

    @Test
    void shouldValidateResponseBeforeCreatingProtocolStream() {
        AtomicBoolean entityStreamCreated = new AtomicBoolean();
        SinkProviderContext context = mock(SinkProviderContext.class);
        ServerResponse response = mock(ServerResponse.class);
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);

        when(context.serverResponse()).thenReturn(response);
        when(context.connectionContext()).thenReturn(connectionContext);
        when(context.entityOutputStream(any())).thenAnswer(invocation -> {
            when(response.status()).thenReturn(Status.UNAUTHORIZED_401);
            invocation.<Runnable>getArgument(0).run();
            entityStreamCreated.set(true);
            return Optional.of(new ByteArrayOutputStream());
        });
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(connectionContext.listenerContext()).thenReturn(listenerContext);

        assertThrows(IllegalStateException.class, () -> new DataWriterSseSink(context));
        assertThat("entity stream created after invalid response", entityStreamCreated.get(), is(false));
    }

    private static void enableBuffering(ListenerContext listenerContext) {
        writeBufferSize(listenerContext, 4096);
    }

    private static void writeBufferSize(ListenerContext listenerContext, int size) {
        ListenerConfig config = mock(ListenerConfig.class);
        when(config.writeBufferSize()).thenReturn(size);
        when(listenerContext.config()).thenReturn(config);
    }
}
