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
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.HeaderNames;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.SinkProviderContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

        try (DataWriterSseSink _ = new DataWriterSseSink(context)) {
            // Close the protocol stream before committing the response.
        }

        assertThat("SSE headers when requesting the protocol stream", headersPrepared.get(), is(true));
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
}
