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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;

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
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(ListenerConfig.create());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(listenerContext);

        return new Http2ServerStream(ctx,
                                     streams,
                                     streamId -> { },
                                     HttpRouting.empty(),
                                     config,
                                     List.of(),
                                     STREAM_ID,
                                     Http2Settings.builder().build(),
                                     Http2Settings.builder().build(),
                                     writer,
                                     flowControl,
                                     new Http2ConnectionChecks(config, mock(Http2Connection.class)));
    }

    private static Http2Headers responseHeaders() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.createCached(Http2Headers.STATUS_NAME, 200));
        return Http2Headers.create(headers);
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
