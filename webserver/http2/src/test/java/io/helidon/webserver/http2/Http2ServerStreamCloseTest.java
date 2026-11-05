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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
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
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ServerStreamCloseTest {
    private static final int STREAM_ID = 1;

    @Test
    void testStreamEventAfterRemoteEndRunsOnExistingStreamThread() throws Exception {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.HALF_CLOSED_REMOTE);
        var notifier = new AtomicReference<Runnable>();
        var closeListener = new AtomicReference<Runnable>();
        registerCallbacks(handler, notifier, closeListener);
        var streamThread = new AtomicReference<Thread>();
        var endReceived = new CountDownLatch(1);
        doAnswer(_ -> {
            streamThread.set(Thread.currentThread());
            return null;
        }).when(handler).init();
        doAnswer(invocation -> {
            Http2FrameHeader header = invocation.getArgument(0);
            assertThat(header.flags(Http2FrameTypes.DATA).endOfStream(), is(true));
            endReceived.countDown();
            return null;
        }).when(handler).data(any(), any());
        doAnswer(_ -> {
            assertThat(Thread.currentThread(), sameInstance(streamThread.get()));
            closeListener.get().run();
            return null;
        }).when(handler).streamEvent();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), true);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        try {
            assertThat("remote end was delivered", endReceived.await(5, TimeUnit.SECONDS), is(true));
            notifier.get().run();
            thread.join(5_000);
            assertThat("existing runner stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), nullValue());
            assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
            notifier.get().run();
            verify(handler).streamEvent();
            verify(handler).close();
            verify(handler).streamFinished();
        } finally {
            stream.abortConnection();
            thread.join(5_000);
        }
    }

    @Test
    void testStreamEventsCoalesceAndNotificationDuringCallbackRemainsPending() throws Exception {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.OPEN);
        var notifier = new AtomicReference<Runnable>();
        var closeListener = new AtomicReference<Runnable>();
        registerCallbacks(handler, notifier, closeListener);
        var initialized = new CountDownLatch(1);
        var releaseInit = new CountDownLatch(1);
        var streamThread = new AtomicReference<Thread>();
        var events = new AtomicInteger();
        doAnswer(_ -> {
            streamThread.set(Thread.currentThread());
            initialized.countDown();
            awaitIgnoringInterrupts(releaseInit);
            return null;
        }).when(handler).init();
        doAnswer(_ -> {
            assertThat(Thread.currentThread(), sameInstance(streamThread.get()));
            if (events.incrementAndGet() == 1) {
                notifier.get().run();
            } else {
                closeListener.get().run();
            }
            return null;
        }).when(handler).streamEvent();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        try {
            assertThat("initialization started after notifier registration", initialized.await(5, TimeUnit.SECONDS), is(true));
            for (int i = 0; i < 10_000; i++) {
                notifier.get().run();
            }
            releaseInit.countDown();
            thread.join(5_000);
            assertThat("existing runner stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), nullValue());
            assertThat(events.get(), is(2));
            verify(handler, never()).data(any(), any());
        } finally {
            releaseInit.countDown();
            stream.abortConnection();
            thread.join(5_000);
        }
    }

    @Test
    void testResetDiscardsPendingStreamEventWithoutReopening() throws Exception {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.OPEN);
        var notifier = new AtomicReference<Runnable>();
        var closeListener = new AtomicReference<Runnable>();
        registerCallbacks(handler, notifier, closeListener);
        var eventStarted = new CountDownLatch(1);
        var releaseEvent = new CountDownLatch(1);
        var initialized = new CountDownLatch(1);
        doAnswer(_ -> {
            initialized.countDown();
            return null;
        }).when(handler).init();
        doAnswer(_ -> {
            eventStarted.countDown();
            awaitIgnoringInterrupts(releaseEvent);
            return null;
        }).when(handler).streamEvent();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        try {
            assertThat("handler initialized", initialized.await(5, TimeUnit.SECONDS), is(true));
            notifier.get().run();
            assertThat("event started", eventStarted.await(5, TimeUnit.SECONDS), is(true));
            notifier.get().run();
            stream.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
            notifier.get().run();
            releaseEvent.countDown();
            thread.join(5_000);
            assertThat("existing runner stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), nullValue());
            assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
            verify(handler).streamEvent();
            verify(handler).close();
            verify(handler).streamFinished();
        } finally {
            releaseEvent.countDown();
            stream.abortConnection();
            thread.join(5_000);
        }
    }

    @Test
    void testFailingStreamEventClosesHandlerAndStopsRunner() throws Exception {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.OPEN);
        var notifier = new AtomicReference<Runnable>();
        registerCallbacks(handler, notifier, new AtomicReference<>());
        var initialized = new CountDownLatch(1);
        doAnswer(_ -> {
            initialized.countDown();
            return null;
        }).when(handler).init();
        var expected = new IllegalStateException("test stream event failure");
        doThrow(expected).when(handler).streamEvent();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        try {
            assertThat("handler initialized", initialized.await(5, TimeUnit.SECONDS), is(true));
            notifier.get().run();
            thread.join(5_000);
            assertThat("existing runner stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), sameInstance(expected));
            assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
            notifier.get().run();
            verify(handler).streamEvent();
            verify(handler).close();
            verify(handler).streamFinished();
        } finally {
            stream.abortConnection();
            thread.join(5_000);
        }
    }

    @Test
    void testSubProtocolClosedWhenStreamExits() {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.CLOSED);
        var runner = Thread.currentThread();
        var finishThread = new AtomicReference<Thread>();
        doAnswer(_ -> {
            finishThread.set(Thread.currentThread());
            return null;
        }).when(handler).streamFinished();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);

        stream.run();
        stream.abortConnection();

        verify(handler).close();
        verify(handler, never()).rstStream(any());
        verify(handler).streamFinished();
        assertThat(finishThread.get(), sameInstance(runner));
        var order = inOrder(handler);
        order.verify(handler).close();
        order.verify(handler).streamFinished();
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
            verify(handler, never()).rstStream(any());
            verify(handler).streamFinished();
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
        var selectionThread = new AtomicReference<Thread>();
        var finishThread = new AtomicReference<Thread>();
        Http2SubProtocolSelector selector = (_, _, _, _, _, _, _, _, _, _) -> {
            selectionThread.set(Thread.currentThread());
            entered.countDown();
            awaitIgnoringInterrupts(release);
            return new SubProtocolResult(true, handler);
        };
        doAnswer(_ -> {
            finishThread.set(Thread.currentThread());
            return null;
        }).when(handler).streamFinished();
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
            verify(handler).rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
            verify(handler).close();
            verify(handler).streamFinished();
            assertThat(finishThread.get(), sameInstance(selectionThread.get()));
            var order = inOrder(handler);
            order.verify(handler).rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
            order.verify(handler).close();
            order.verify(handler).streamFinished();
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
        doThrow(new IllegalStateException("test application cleanup failure")).when(handler).streamFinished();
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
            stream.abortConnection();
            stream.run();
            verify(handler, never()).rstStream(any());
            verify(handler).streamFinished();
        } finally {
            thread.interrupt();
            thread.join(5_000);
        }
    }

    @Test
    void testBlockingStreamFinishedDoesNotBlockConnectionAbort() throws Exception {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.OPEN);
        var initialized = new CountDownLatch(1);
        var finishEntered = new CountDownLatch(1);
        var releaseFinish = new CountDownLatch(1);
        var runner = new AtomicReference<Thread>();
        var finishThread = new AtomicReference<Thread>();
        doAnswer(_ -> {
            runner.set(Thread.currentThread());
            initialized.countDown();
            return null;
        }).when(handler).init();
        doAnswer(_ -> {
            finishThread.set(Thread.currentThread());
            finishEntered.countDown();
            awaitIgnoringInterrupts(releaseFinish);
            return null;
        }).when(handler).streamFinished();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        var abortReturned = new CountDownLatch(1);
        Thread abortThread = null;
        try {
            assertThat("handler initialized", initialized.await(5, TimeUnit.SECONDS), is(true));
            stream.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
            assertThat("application cleanup started", finishEntered.await(5, TimeUnit.SECONDS), is(true));
            abortThread = Thread.ofPlatform().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(() -> {
                stream.abortConnection();
                abortReturned.countDown();
            });
            assertThat("connection cleanup returned while application cleanup blocked",
                       abortReturned.await(5, TimeUnit.SECONDS), is(true));
            assertThat("application cleanup remains blocked", thread.isAlive(), is(true));
            releaseFinish.countDown();
            thread.join(5_000);
            assertThat("stream stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), nullValue());
            verify(handler).rstStream(any());
            verify(handler).close();
            verify(handler).streamFinished();
            assertThat(finishThread.get(), sameInstance(runner.get()));
            var order = inOrder(handler);
            order.verify(handler).close();
            order.verify(handler).streamFinished();
        } finally {
            releaseFinish.countDown();
            stream.abortConnection();
            thread.join(5_000);
            if (abortThread != null) {
                abortThread.join(5_000);
                assertThat("connection cleanup thread stopped", abortThread.isAlive(), is(false));
            }
            assertThat("stream cleanup thread stopped", thread.isAlive(), is(false));
        }
    }

    @Test
    void testLocalProtocolResetRetainsHandlerForStreamFinished() throws Exception {
        var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.OPEN);
        var initialized = new CountDownLatch(1);
        var releaseInit = new CountDownLatch(1);
        var runner = new AtomicReference<Thread>();
        var finishThread = new AtomicReference<Thread>();
        doAnswer(_ -> {
            runner.set(Thread.currentThread());
            initialized.countDown();
            awaitIgnoringInterrupts(releaseInit);
            return null;
        }).when(handler).init();
        doAnswer(_ -> {
            finishThread.set(Thread.currentThread());
            return null;
        }).when(handler).streamFinished();
        var stream = stream(handler);
        stream.prologue(HttpPrologue.create("HTTP/2.0", "HTTP", "2.0", Method.POST, "/service/method", false));
        stream.headers(headers(), false);
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(stream);
        try {
            assertThat("handler initialized", initialized.await(5, TimeUnit.SECONDS), is(true));
            stream.windowUpdate(new Http2WindowUpdate(0));
            verify(handler).rstStream(any());
            releaseInit.countDown();
            thread.join(5_000);
            assertThat("stream stopped", thread.isAlive(), is(false));
            assertThat(failure.get(), nullValue());
            stream.abortConnection();
            verify(handler).close();
            verify(handler).streamFinished();
            assertThat(finishThread.get(), sameInstance(runner.get()));
            var order = inOrder(handler);
            order.verify(handler).close();
            order.verify(handler).streamFinished();
            assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        } finally {
            releaseInit.countDown();
            stream.abortConnection();
            thread.join(5_000);
            assertThat("stream cleanup thread stopped", thread.isAlive(), is(false));
        }
    }

    private static void registerCallbacks(Http2SubProtocolSelector.SubProtocolHandler handler,
                                          AtomicReference<Runnable> notifier,
                                          AtomicReference<Runnable> closeListener) {
        doAnswer(invocation -> {
            notifier.set(invocation.getArgument(0));
            return null;
        }).when(handler).onStreamEvent(any());
        doAnswer(invocation -> {
            closeListener.set(invocation.getArgument(0));
            return null;
        }).when(handler).onStreamClosed(any());
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
