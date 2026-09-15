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

package io.helidon.http.http3;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class Http3FrameListenerUtilTest {
    private static final SocketContext CONTEXT = Http3TestSocketContext.INSTANCE;
    private static final long STREAM_ID = 12;
    private static final Headers HEADERS = WritableHeaders.create().add(HeaderNames.CONTENT_TYPE, "text/plain");
    private static final byte[] DATA = new byte[] {0x01, 0x02};

    @Test
    void shouldDispatchDecodedHeadersOnlyToEnabledDelegates() {
        RecordingListener disabled = new RecordingListener(false, false);
        RecordingListener rawOnly = new RecordingListener(false, true);
        RecordingListener enabled = new RecordingListener(true, false);
        Http3FrameListener listener = Http3FrameListener.create(List.of(disabled, rawOnly, enabled));

        listener.requestHeaders(CONTEXT, STREAM_ID, "GET", "https", "example.test", "/path", HEADERS);
        listener.responseHeaders(CONTEXT, STREAM_ID, 200, HEADERS);
        listener.trailers(CONTEXT, STREAM_ID, HEADERS);

        assertThat(disabled.events, is(List.of()));
        assertThat(rawOnly.events, is(List.of()));
        assertThat(enabled.events, is(List.of("request headers", "response headers", "trailers")));
    }

    @Test
    void shouldRecheckDelegateEnablementBeforeEachDecodedCallback() {
        RecordingListener first = new RecordingListener(true, false);
        RecordingListener second = new RecordingListener(false, false);
        Http3FrameListener listener = Http3FrameListener.create(List.of(first, second));

        assertThat(listener.enabled(), is(true));
        listener.requestHeaders(CONTEXT, STREAM_ID, "GET", "https", "example.test", "/path", HEADERS);
        first.enabled = false;
        second.enabled = true;
        listener.responseHeaders(CONTEXT, STREAM_ID, 200, HEADERS);
        listener.trailers(CONTEXT, STREAM_ID, HEADERS);
        second.enabled = false;

        assertThat(listener.enabled(), is(false));
        listener.requestHeaders(CONTEXT, STREAM_ID, "GET", "https", "example.test", "/path", HEADERS);
        listener.responseHeaders(CONTEXT, STREAM_ID, 200, HEADERS);
        listener.trailers(CONTEXT, STREAM_ID, HEADERS);

        assertThat(first.events, is(List.of("request headers")));
        assertThat(second.events, is(List.of("response headers", "trailers")));
    }

    @Test
    void shouldKeepRawCallbacksIndependentOfMetadataEnablement() {
        RecordingListener metadata = new RecordingListener(true, false);
        RecordingListener rawOnly = new RecordingListener(false, true);
        RecordingListener disabled = new RecordingListener(false, false);
        Http3FrameListener listener = Http3FrameListener.create(List.of(metadata, rawOnly, disabled));

        assertThat(listener.enabled(), is(true));
        assertThat(listener.rawDataEnabled(), is(true));
        listener.frameHeader(CONTEXT, STREAM_ID, Http3Protocol.FRAME_DATA, DATA.length, 2);
        listener.frameData(CONTEXT, STREAM_ID, DATA.length, true);
        listener.streamType(CONTEXT, STREAM_ID, Http3StreamType.CONTROL, 1);
        listener.streamData(CONTEXT, STREAM_ID, "stream data", DATA.length);
        listener.rawFrameHeader(CONTEXT, STREAM_ID, DATA);
        listener.rawFrameData(CONTEXT, STREAM_ID, DATA, true);
        listener.rawStreamData(CONTEXT, STREAM_ID, "stream data", DATA);

        assertThat(metadata.events, is(List.of("frame header", "frame data", "stream type", "stream data")));
        assertThat(rawOnly.events, is(List.of("raw frame header", "raw frame data", "raw stream data")));
        assertThat(disabled.events, is(List.of()));

        metadata.enabled = false;
        rawOnly.rawDataEnabled = false;
        assertThat(listener.enabled(), is(false));
        assertThat(listener.rawDataEnabled(), is(false));
        listener.rawFrameData(CONTEXT, STREAM_ID, DATA, true);
        assertThat(rawOnly.events, is(List.of("raw frame header", "raw frame data", "raw stream data")));

        disabled.rawDataEnabled = true;
        assertThat(listener.enabled(), is(false));
        assertThat(listener.rawDataEnabled(), is(true));
        listener.rawFrameData(CONTEXT, STREAM_ID, DATA, true);
        assertThat(disabled.events, is(List.of("raw frame data")));
    }

    private static final class RecordingListener implements Http3FrameListener {
        private final List<String> events = new ArrayList<>();
        private boolean enabled;
        private boolean rawDataEnabled;

        private RecordingListener(boolean enabled, boolean rawDataEnabled) {
            this.enabled = enabled;
            this.rawDataEnabled = rawDataEnabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean rawDataEnabled() {
            return rawDataEnabled;
        }

        @Override
        public void frameHeader(SocketContext context,
                                long streamId,
                                long frameType,
                                long frameLength,
                                int encodedLength) {
            assertThat(frameType, is(Http3Protocol.FRAME_DATA));
            assertThat(frameLength, is((long) DATA.length));
            assertThat(encodedLength, is(2));
            recordEvent(context, streamId, "frame header");
        }

        @Override
        public void rawFrameHeader(SocketContext context, long streamId, byte[] data) {
            assertThat(data, sameInstance(DATA));
            recordEvent(context, streamId, "raw frame header");
        }

        @Override
        public void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
            assertThat(byteCount, is(DATA.length));
            assertThat(last, is(true));
            recordEvent(context, streamId, "frame data");
        }

        @Override
        public void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
            assertThat(data, sameInstance(DATA));
            assertThat(last, is(true));
            recordEvent(context, streamId, "raw frame data");
        }

        @Override
        public void streamType(SocketContext context, long streamId, Http3StreamType streamType, int encodedLength) {
            assertThat(streamType, is(Http3StreamType.CONTROL));
            assertThat(encodedLength, is(1));
            recordEvent(context, streamId, "stream type");
        }

        @Override
        public void streamData(SocketContext context, long streamId, String label, int byteCount) {
            assertThat(label, is("stream data"));
            assertThat(byteCount, is(DATA.length));
            recordEvent(context, streamId, "stream data");
        }

        @Override
        public void rawStreamData(SocketContext context, long streamId, String label, byte[] data) {
            assertThat(label, is("stream data"));
            assertThat(data, sameInstance(DATA));
            recordEvent(context, streamId, "raw stream data");
        }

        @Override
        public void requestHeaders(SocketContext context,
                                   long streamId,
                                   String method,
                                   String scheme,
                                   String authority,
                                   String path,
                                   Headers headers) {
            assertThat(method, is("GET"));
            assertThat(scheme, is("https"));
            assertThat(authority, is("example.test"));
            assertThat(path, is("/path"));
            assertThat(headers, sameInstance(HEADERS));
            recordEvent(context, streamId, "request headers");
        }

        @Override
        public void responseHeaders(SocketContext context, long streamId, int status, Headers headers) {
            assertThat(status, is(200));
            assertThat(headers, sameInstance(HEADERS));
            recordEvent(context, streamId, "response headers");
        }

        @Override
        public void trailers(SocketContext context, long streamId, Headers trailers) {
            assertThat(trailers, sameInstance(HEADERS));
            recordEvent(context, streamId, "trailers");
        }

        private void recordEvent(SocketContext context, long streamId, String event) {
            assertThat(context, sameInstance(CONTEXT));
            assertThat(streamId, is(STREAM_ID));
            events.add(event);
        }
    }
}
