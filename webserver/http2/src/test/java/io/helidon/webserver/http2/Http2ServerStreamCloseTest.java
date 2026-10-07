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

package io.helidon.webserver.http2;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ServerStreamCloseTest {
    private static final int STREAM_ID = 1;

    @Test
    void testSubProtocolClosedWhenStreamExits() {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.CLOSED);
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);

        stream.run();
        stream.abortConnection();

        verify(handler).close();
    }

    @Test
    void testConnectionCloseDuringSubProtocolInitDoesNotReopenStream() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.OPEN);
        doAnswer(_ -> {
            entered.countDown();
            awaitIgnoringInterrupts(release);
            return null;
        }).when(handler).init();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        try {
            assertThat("handler initialization started", entered.await(5, TimeUnit.SECONDS), is(true));
            stream.abortConnection();
            verify(handler).close();
            release.countDown();
            thread.join(5_000);

            assertThat("stream stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), nullValue());
            assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
            verify(handler, times(1)).close();
        } finally {
            release.countDown();
            thread.interrupt();
            thread.join(5_000);
        }
    }

    @Test
    void testConnectionCloseDuringSubProtocolSelectionClosesWithoutInit() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        Http2SubProtocolSelector selector = (_, _, _, _, _, _, _, _, _, _) -> {
            entered.countDown();
            awaitIgnoringInterrupts(release);
            return new SubProtocolResult(true, handler);
        };
        var stream = stream(selector, noOpWriter());
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        try {
            assertThat("handler selection started", entered.await(5, TimeUnit.SECONDS), is(true));
            stream.abortConnection();
            release.countDown();
            thread.join(5_000);

            assertThat("stream stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), nullValue());
            assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
            verify(handler, never()).init();
            verify(handler).close();
        } finally {
            release.countDown();
            thread.interrupt();
            thread.join(5_000);
        }
    }

    @Test
    void testFailingSubProtocolCloseDoesNotPreventTransportCleanup() throws Exception {
        var entered = new CountDownLatch(1);
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.OPEN);
        doAnswer(_ -> {
            entered.countDown();
            return null;
        }).when(handler).init();
        doThrow(new IllegalStateException("test cleanup failure")).when(handler).close();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        try {
            assertThat("handler initialization started", entered.await(5, TimeUnit.SECONDS), is(true));
            stream.abortConnection();
            thread.join(5_000);

            assertThat("stream stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), nullValue());
            assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
            verify(handler).close();
        } finally {
            thread.interrupt();
            thread.join(5_000);
        }
    }

    private static Http2ServerStream stream(Http2SubProtocolSelector.SubProtocolHandler handler) {
        return stream(handler, noOpWriter());
    }

    private static Http2ServerStream stream(Http2SubProtocolSelector.SubProtocolHandler handler,
                                            Http2StreamWriter writer) {
        Http2SubProtocolSelector selector = (_, _, _, _, _, _, _, _, _, _) -> new SubProtocolResult(true, handler);
        return stream(selector, writer);
    }

    private static Http2ServerStream stream(Http2SubProtocolSelector selector, Http2StreamWriter writer) {
        ConnectionContext ctx = mock(ConnectionContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(ListenerConfig.create());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.socketId()).thenReturn("socket");
        when(ctx.childSocketId()).thenReturn("child");
        ConnectionFlowControl flowControl = ConnectionFlowControl.serverBuilder((_, _) -> { }).build();
        return new Http2ServerStream(ctx,
                                     new Http2ConnectionStreams(),
                                     new Http2StreamAdmissionGate(),
                                     mock(Http2ServerStream.LocallyResetStreamTracker.class),
                                     mock(HttpRouting.class),
                                     Http2Config.create(),
                                     List.of(selector),
                                     STREAM_ID,
                                     Http2Settings.create(),
                                     Http2Settings.create(),
                                     writer,
                                     flowControl,
                                     new Http2ServerStream.InboundDataBudget(1024, 131070),
                                     mock(Http2ConnectionChecks.class));
    }

    private static Http2Headers headers() {
        Http2Headers headers = Http2Headers.create(WritableHeaders.create());
        headers.method(Method.POST);
        headers.path("/service/method");
        headers.scheme("http");
        headers.authority("localhost");
        return headers;
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    assertThat("callback released", latch.await(10, TimeUnit.SECONDS), is(true));
                    return;
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Http2StreamWriter noOpWriter() {
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                return 0;
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                return 0;
            }
        };
    }

}
