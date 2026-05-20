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

import io.helidon.common.buffers.BufferData;
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
import io.helidon.webserver.Router;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ServerStreamTest {
    private static final int STREAM_ID = 1;

    @Test
    void testClosedSubProtocolInitCreditsQueuedData() {
        ClosingHandler handler = new ClosingHandler();
        Http2ServerStream stream = stream(handler);
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        Http2FrameHeader header = dataHeader(payload.length, 0);
        FlowControl.Inbound flowControl = stream.flowControl().inbound();
        int initialWindowSize = flowControl.getRemainingWindowSize();

        stream.headers(headers(), false);
        flowControl.decrementWindowSize(payload.length);
        stream.data(header, BufferData.create(payload), false);
        stream.run();

        assertThat(handler.dataCalls(), is(0));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        assertThat(flowControl.getRemainingWindowSize(), is(initialWindowSize));
    }

    @Test
    void testClosedSubProtocolInitCreditsBlockedProducerData() throws InterruptedException {
        ClosingHandler handler = new ClosingHandler();
        Http2ServerStream stream = stream(handler);
        FlowControl.Inbound flowControl = stream.flowControl().inbound();
        int initialWindowSize = flowControl.getRemainingWindowSize();
        byte[] payload = new byte[] {1};

        stream.headers(headers(), false);
        for (int i = 0; i < 32; i++) {
            flowControl.decrementWindowSize(payload.length);
            stream.data(dataHeader(payload.length, 0), BufferData.create(payload), false);
        }

        CountDownLatch producerStarted = new CountDownLatch(1);
        AtomicReference<Throwable> producerFailure = new AtomicReference<>();
        Thread blockedProducer = Thread.ofPlatform().start(() -> {
            try {
                flowControl.decrementWindowSize(payload.length);
                producerStarted.countDown();
                stream.data(dataHeader(payload.length, Http2Flag.END_OF_STREAM), BufferData.create(payload), true);
            } catch (Throwable t) {
                producerFailure.set(t);
            }
        });
        try {
            assertThat(producerStarted.await(10, TimeUnit.SECONDS), is(true));
            assertThat(awaitWaiting(blockedProducer), is(true));

            stream.run();
            blockedProducer.join(10_000);

            assertThat(blockedProducer.isAlive(), is(false));
            assertThat(producerFailure.get(), nullValue());
            assertThat(handler.dataCalls(), is(0));
            assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
            assertThat(flowControl.getRemainingWindowSize(), is(initialWindowSize));
        } finally {
            blockedProducer.interrupt();
            blockedProducer.join(10_000);
        }
    }

    @Test
    void testActiveSubProtocolDataCreditsWindow() {
        ActiveHandler handler = new ActiveHandler();
        Http2ServerStream stream = stream(handler);
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        Http2FrameHeader header = dataHeader(payload.length, 0);
        FlowControl.Inbound flowControl = stream.flowControl().inbound();
        int initialWindowSize = flowControl.getRemainingWindowSize();

        stream.headers(headers(), false);
        flowControl.decrementWindowSize(payload.length);
        stream.data(header, BufferData.create(payload), false);
        stream.run();

        assertThat(handler.dataCalls(), is(1));
        assertThat(handler.dataLength(), is(payload.length));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        assertThat(flowControl.getRemainingWindowSize(), is(initialWindowSize));
    }

    @Test
    void testRstStreamCreditsQueuedData() {
        ClosingHandler handler = new ClosingHandler();
        Http2ServerStream stream = stream(handler);
        FlowControl.Inbound flowControl = stream.flowControl().inbound();
        int initialWindowSize = flowControl.getRemainingWindowSize();
        byte[] payload = new byte[] {1};

        stream.headers(headers(), false);
        for (int i = 0; i < 32; i++) {
            flowControl.decrementWindowSize(payload.length);
            stream.data(dataHeader(payload.length, 0), BufferData.create(payload), false);
        }

        stream.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));

        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        assertThat(flowControl.getRemainingWindowSize(), is(initialWindowSize));
    }

    private static Http2ServerStream stream(Http2SubProtocolSelector.SubProtocolHandler handler) {
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.socketId()).thenReturn("socket");
        when(ctx.childSocketId()).thenReturn("child");
        ConnectionFlowControl flowControl = ConnectionFlowControl.serverBuilder((streamId, update) -> { }).build();
        Http2SubProtocolSelector selector = (connectionContext,
                                             prologue,
                                             headers,
                                             streamWriter,
                                             streamId,
                                             serverSettings,
                                             clientSettings,
                                             streamFlowControl,
                                             currentStreamState,
                                             router) -> new io.helidon.webserver.http2.spi.SubProtocolResult(true, handler);
        return new Http2ServerStream(ctx,
                                     new Http2ConnectionStreams(),
                                     ignored -> { },
                                     mock(HttpRouting.class),
                                     Http2Config.create(),
                                     List.of(selector),
                                     STREAM_ID,
                                     Http2Settings.create(),
                                     Http2Settings.create(),
                                     noOpWriter(),
                                     flowControl,
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

    private static Http2FrameHeader dataHeader(int length, int flags) {
        return Http2FrameHeader.create(length,
                                       Http2FrameTypes.DATA,
                                       Http2Flag.DataFlags.create(flags),
                                       STREAM_ID);
    }

    private static boolean awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.isAlive() && System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
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

    private static class ClosingHandler implements Http2SubProtocolSelector.SubProtocolHandler {
        private Http2StreamState streamState = Http2StreamState.OPEN;
        private int dataCalls;

        @Override
        public void init() {
            streamState = Http2StreamState.CLOSED;
        }

        @Override
        public Http2StreamState streamState() {
            return streamState;
        }

        @Override
        public void rstStream(Http2RstStream rstStream) {
        }

        @Override
        public void windowUpdate(Http2WindowUpdate update) {
        }

        @Override
        public void data(Http2FrameHeader header, BufferData data) {
            dataCalls++;
        }

        private int dataCalls() {
            return dataCalls;
        }
    }

    private static class ActiveHandler implements Http2SubProtocolSelector.SubProtocolHandler {
        private Http2StreamState streamState = Http2StreamState.OPEN;
        private int dataCalls;
        private int dataLength;

        @Override
        public void init() {
        }

        @Override
        public Http2StreamState streamState() {
            return streamState;
        }

        @Override
        public void rstStream(Http2RstStream rstStream) {
        }

        @Override
        public void windowUpdate(Http2WindowUpdate update) {
        }

        @Override
        public void data(Http2FrameHeader header, BufferData data) {
            dataCalls++;
            dataLength = data.available();
            streamState = Http2StreamState.CLOSED;
        }

        private int dataCalls() {
            return dataCalls;
        }

        private int dataLength() {
            return dataLength;
        }
    }
}
