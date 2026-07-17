/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionStreamTest {

    private static final int SETTINGS_MAX_CONCURRENT_STREAMS = 50;
    private static final int STREAM_ID = 1;
    private static final Http2ServerStream.LocallyResetStreamTracker NO_OP_RESET_TRACKER = new NoOpResetTracker();

    @Test
    void concurrentModification() throws InterruptedException {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        for (int i = 1; i < SETTINGS_MAX_CONCURRENT_STREAMS * 2; i += 2) {
            Http2ServerStream s = mockStream(i);
            streams.put(new Http2Connection.StreamContext(i, 8192, s));
            streams.activate(i);
        }

        CountDownLatch removed = new CountDownLatch(streams.contexts().size());
        for (Http2Connection.StreamContext ctx : streams.contexts()) {
            Thread.ofVirtual().start(() -> {
                streams.remove(ctx.stream().streamId());
                removed.countDown();
            });
        }

        assertThat(removed.await(10, TimeUnit.SECONDS), Matchers.is(true));
        streams.doMaintenance();

        assertThat(streams.contexts().size(), Matchers.is(0));
        assertThat(streams.size(), Matchers.is(0));
    }

    @Test
    void queuedRemovalStopsCountingStreamAsActive() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream s = mockStream(1);

        streams.put(new Http2Connection.StreamContext(1, 8192, s));
        streams.activate(1);
        streams.remove(1);

        assertThat(streams.size(), Matchers.is(0));
        assertThat(streams.isEmpty(), Matchers.is(true));
    }

    @Test
    void deactivatedStreamRemainsAvailableUntilRemoved() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream s = mockStream(1);
        Http2Connection.StreamContext ctx = new Http2Connection.StreamContext(1, 8192, s);

        streams.put(ctx);
        streams.activate(1);
        streams.deactivate(1);

        assertThat(streams.size(), Matchers.is(0));
        assertThat(streams.isEmpty(), Matchers.is(true));
        assertThat(streams.get(1), sameInstance(ctx));

        streams.doMaintenance();
        assertThat(streams.get(1), sameInstance(ctx));

        streams.remove(1);
        streams.doMaintenance();
        assertThat(streams.get(1), nullValue());
    }

    @Test
    void duplicateRemovalDoesNotChangeActiveCount() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream first = mockStream(1);
        Http2ServerStream second = mockStream(3);

        streams.put(new Http2Connection.StreamContext(1, 8192, first));
        streams.activate(1);
        streams.remove(1);
        streams.doMaintenance();
        streams.remove(1);
        streams.put(new Http2Connection.StreamContext(3, 8192, second));
        streams.activate(3);

        assertThat(streams.size(), Matchers.is(1));
        assertThat(streams.isEmpty(), Matchers.is(false));
    }

    @Test
    void idleStreamDoesNotCountAsActive() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream s = mockStream(1);

        streams.put(new Http2Connection.StreamContext(1, 8192, s));

        assertThat(streams.size(), Matchers.is(0));
        assertThat(streams.isEmpty(), Matchers.is(true));
    }

    @Test
    void terminalHeadersDeactivateAfterConnectionWriterCallback() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter();
        Http2ServerStream stream = stream(streams, writer);

        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        streams.activate(STREAM_ID);
        assertThat(streams.isActive(STREAM_ID), Matchers.is(true));
        stream.closeFromRemote();
        assertThat(streams.isActive(STREAM_ID), Matchers.is(true));

        stream.writeHeaders(responseHeaders(), true);

        assertThat(writer.hasPendingTerminalCallback(), Matchers.is(true));
        assertThat(streams.isActive(STREAM_ID), Matchers.is(true));

        writer.completeTerminalWrite();

        assertThat(streams.isActive(STREAM_ID), Matchers.is(false));
    }

    @Test
    void terminalTrailersDeactivateAfterConnectionWriterCallback() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingConnectionWriter writer = new RecordingConnectionWriter();
        Http2ServerStream stream = stream(streams, writer);

        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        streams.activate(STREAM_ID);
        assertThat(streams.isActive(STREAM_ID), Matchers.is(true));
        stream.closeFromRemote();
        assertThat(streams.isActive(STREAM_ID), Matchers.is(true));
        stream.writeHeaders(responseHeaders(), false);

        stream.writeTrailers(responseHeaders());

        assertThat(writer.hasPendingTerminalCallback(), Matchers.is(true));
        assertThat(streams.isActive(STREAM_ID), Matchers.is(true));

        writer.completeTerminalWrite();

        assertThat(streams.isActive(STREAM_ID), Matchers.is(false));
    }

    @Test
    void resetRestoresOnlyConnectionCreditForQueuedData() {
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        ConnectionFlowControl flowControl = ConnectionFlowControl.serverBuilder(
                        (streamId, update) -> windowUpdates.add(new WindowUpdate(streamId,
                                                                                 update.windowSizeIncrement())))
                .initialWindowSize(65536)
                .maxFrameSize(16384)
                .build();
        Http2ServerStream stream = stream(new Http2ConnectionStreams(), new RecordingConnectionWriter(), flowControl);
        stream.headers(Http2Headers.create(WritableHeaders.create()), false);

        for (int i = 0; i < 2; i++) {
            Http2FrameHeader header = Http2FrameHeader.create(4096,
                                                              Http2FrameTypes.DATA,
                                                              Http2Flag.DataFlags.create(0),
                                                              STREAM_ID);
            stream.flowControl().inbound().decrementWindowSize(header.length());
            stream.data(header, BufferData.create(new byte[header.length()]), false);
        }

        stream.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));

        assertThat(windowUpdates, Matchers.empty());

        for (int i = 0; i < 2; i++) {
            flowControl.decrementInboundConnectionWindowSize(16384);
            flowControl.incrementInboundConnectionWindowSize(16384);
        }

        assertThat(windowUpdates, Matchers.is(List.of(new WindowUpdate(0, 40960))));
    }

    @Test
    void asynchronousSubProtocolCloseWakesStreamTask() throws InterruptedException {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        AsyncSubProtocolHandler handler = new AsyncSubProtocolHandler();
        Http2SubProtocolSelector selector = (ctx,
                                             prologue,
                                             headers,
                                             streamWriter,
                                             streamId,
                                             serverSettings,
                                             clientSettings,
                                             flowControl,
                                             currentStreamState,
                                             router) -> new SubProtocolResult(true, handler);
        Http2ServerStream stream = stream(streams, new RecordingConnectionWriter(), List.of(selector));
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        streams.activate(STREAM_ID);
        stream.headers(Http2Headers.create(WritableHeaders.create()), false);
        Http2FrameHeader header = Http2FrameHeader.create(1,
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                          STREAM_ID);
        stream.data(header, BufferData.create(new byte[1]), true);
        Thread task = Thread.startVirtualThread(stream);

        assertThat(handler.dataReceived.await(10, TimeUnit.SECONDS), Matchers.is(true));
        handler.close();
        task.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(task.isAlive(), Matchers.is(false));
        assertThat(stream.streamState(), Matchers.is(Http2StreamState.CLOSED));
        assertThat(streams.isActive(STREAM_ID), Matchers.is(false));
        streams.doMaintenance();
        assertThat(streams.get(STREAM_ID), nullValue());
    }

    private static Http2ServerStream mockStream(int streamId) {
        Http2ServerStream s = mock(Http2ServerStream.class);
        when(s.streamId()).thenReturn(streamId);
        return s;
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams, Http2ConnectionWriter writer) {
        Http2Config config = Http2Config.builder()
                .initialWindowSize(8192)
                .maxFrameSize(16384)
                .build();
        ConnectionFlowControl flowControl = ConnectionFlowControl.serverBuilder((streamId, windowUpdate) -> { })
                .initialWindowSize(config.initialWindowSize())
                .maxFrameSize(config.maxFrameSize())
                .build();
        return stream(streams, writer, flowControl, List.of());
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2ConnectionWriter writer,
                                            ConnectionFlowControl flowControl) {
        return stream(streams, writer, flowControl, List.of());
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2ConnectionWriter writer,
                                            List<Http2SubProtocolSelector> subProtocols) {
        Http2Config config = Http2Config.builder()
                .initialWindowSize(8192)
                .maxFrameSize(16384)
                .build();
        ConnectionFlowControl flowControl = ConnectionFlowControl.serverBuilder((streamId, windowUpdate) -> { })
                .initialWindowSize(config.initialWindowSize())
                .maxFrameSize(config.maxFrameSize())
                .build();
        return stream(streams, writer, flowControl, subProtocols);
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2ConnectionWriter writer,
                                            ConnectionFlowControl flowControl,
                                            List<Http2SubProtocolSelector> subProtocols) {
        Http2Config config = Http2Config.builder()
                .initialWindowSize(8192)
                .maxFrameSize(16384)
                .build();
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(ListenerConfig.create());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(listenerContext);

        return new Http2ServerStream(ctx,
                                     streams,
                                     NO_OP_RESET_TRACKER,
                                     HttpRouting.empty(),
                                     config,
                                     subProtocols,
                                     STREAM_ID,
                                     Http2Settings.builder().build(),
                                     Http2Settings.builder().build(),
                                     writer,
                                     flowControl,
                                     new Http2ServerStream.InboundDataBudget(1024,
                                                                             2L * config.initialWindowSize()),
                                     new Http2ConnectionChecks(config, mock(Http2Connection.class)));
    }

    private static final class AsyncSubProtocolHandler implements Http2SubProtocolSelector.SubProtocolHandler {
        private final CountDownLatch dataReceived = new CountDownLatch(1);
        private volatile Http2StreamState state = Http2StreamState.OPEN;
        private volatile Runnable closeListener = () -> { };

        @Override
        public void init() {
        }

        @Override
        public Http2StreamState streamState() {
            return state;
        }

        @Override
        public void onStreamClosed(Runnable listener) {
            closeListener = listener;
        }

        @Override
        public void rstStream(Http2RstStream rstStream) {
        }

        @Override
        public void windowUpdate(Http2WindowUpdate update) {
        }

        @Override
        public void data(Http2FrameHeader header, BufferData data) {
            state = Http2StreamState.HALF_CLOSED_REMOTE;
            dataReceived.countDown();
        }

        private void close() {
            state = Http2StreamState.CLOSED;
            closeListener.run();
        }
    }

    private record WindowUpdate(int streamId, int increment) { }

    private static Http2Headers responseHeaders() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.createCached(Http2Headers.STATUS_NAME, 200));
        return Http2Headers.create(headers);
    }

    private static final class NoOpResetTracker implements Http2ServerStream.LocallyResetStreamTracker {
        @Override
        public void add(int streamId, Http2ServerStream.LocallyResetStreamState streamState) {
        }

        @Override
        public void localComplete(int streamId) {
        }

        @Override
        public void remoteComplete(int streamId) {
        }
    }

    private static final class RecordingConnectionWriter extends Http2ConnectionWriter {
        private Runnable terminalCallback;

        private RecordingConnectionWriter() {
            super(mock(SocketContext.class), mock(DataWriter.class), List.of());
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
                                FlowControl.Outbound flowControl,
                                Runnable onEndStreamFrameWritten) {
            if (flags.endOfStream()) {
                terminalCallback = onEndStreamFrameWritten;
            }
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

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame,
                                FlowControl.Outbound flowControl,
                                Runnable onEndStreamFrameWritten) {
            terminalCallback = onEndStreamFrameWritten;
            return 0;
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
        }

        @Override
        public int writeData(Http2FrameData frame,
                             FlowControl.Outbound flowControl,
                             Runnable onEndStreamFrameWritten) {
            terminalCallback = onEndStreamFrameWritten;
            return 0;
        }

        @Override
        public void write(Http2FrameData frame) {
        }

        private void completeTerminalWrite() {
            assertThat(terminalCallback, Matchers.notNullValue());
            terminalCallback.run();
            terminalCallback = null;
        }

        private boolean hasPendingTerminalCallback() {
            return terminalCallback != null;
        }
    }
}
