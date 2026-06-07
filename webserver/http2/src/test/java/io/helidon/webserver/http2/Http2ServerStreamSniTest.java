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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriAuthority;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniMatchType;
import io.helidon.webserver.http.DirectHandlers;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ServerStreamSniTest {
    private static final int STREAM_ID = 1;
    private static final HttpPrologue PROLOGUE = HttpPrologue.create(Http2Connection.FULL_PROTOCOL,
                                                                     Http2Connection.PROTOCOL,
                                                                     Http2Connection.PROTOCOL_VERSION,
                                                                     Method.GET,
                                                                     "/",
                                                                     true);

    @Test
    void missingAuthorityClosesStream() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        Http2ServerStream stream = stream(streams, writer);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(), false);

        stream.run();
        streams.doMaintenance(0);

        assertThat(writer.status, is(Status.BAD_REQUEST_400));
        assertThat(writer.rstStreamCount, is(1));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        assertThat(streams.get(STREAM_ID), is(nullValue()));
        assertDoesNotThrow(stream::checkDataReceivable);
    }

    @Test
    void rejectedStreamRestoresQueuedDataFlowControl() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        Http2ServerStream stream = stream(streams, writer, windowUpdates);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        byte[] entity = "hello".getBytes();
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(entity.length)), false);
        stream.flowControl().inbound().decrementWindowSize(entity.length);
        stream.data(Http2FrameHeader.create(entity.length,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(0),
                                            STREAM_ID),
                    BufferData.create(entity),
                    false);

        stream.run();

        assertThat(windowUpdates, hasItems(new WindowUpdate(STREAM_ID, entity.length),
                                           new WindowUpdate(0, entity.length)));
    }

    @Test
    void rejectedStreamRestoresRacingDataFlowControl() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        Http2ServerStream stream = stream(streams, writer, windowUpdates);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        byte[] entity = "hello".getBytes();
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(entity.length)), false);
        stream.checkDataReceivable();
        stream.flowControl().inbound().decrementWindowSize(entity.length);

        stream.run();

        stream.data(Http2FrameHeader.create(entity.length,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(0),
                                            STREAM_ID),
                    BufferData.create(entity),
                    false);

        assertThat(windowUpdates, hasItems(new WindowUpdate(STREAM_ID, entity.length),
                                           new WindowUpdate(0, entity.length)));
    }

    @Test
    void rejectedStreamAllowsRacingDataAfterConnectionPrecheck() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        List<WindowUpdate> windowUpdates = new ArrayList<>();
        Http2ServerStream stream = stream(streams, writer, windowUpdates);
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        byte[] entity = "hello".getBytes();
        stream.prologue(PROLOGUE);
        stream.headers(headersWithoutAuthority(String.valueOf(entity.length)), false);

        stream.run();

        assertDoesNotThrow(stream::checkDataReceivable);
        stream.flowControl().inbound().decrementWindowSize(entity.length);
        stream.data(Http2FrameHeader.create(entity.length,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(0),
                                            STREAM_ID),
                    BufferData.create(entity),
                    false);

        assertThat(windowUpdates, hasItems(new WindowUpdate(STREAM_ID, entity.length),
                                           new WindowUpdate(0, entity.length)));
    }

    @Test
    void invalidAuthoritySyntaxResetsStreamWithProtocolError() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        RecordingStreamWriter writer = new RecordingStreamWriter();
        Http2ServerStream stream = stream(streams, writer, parsingSniContext());
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(PROLOGUE);
        stream.headers(headersWithAuthority("bad authority"), true);

        stream.run();
        streams.doMaintenance(0);

        assertThat(writer.status, is(nullValue()));
        assertThat(writer.rstStreamCodes, hasItems(Http2ErrorCode.PROTOCOL));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        assertThat(streams.get(STREAM_ID), is(nullValue()));
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams, Http2StreamWriter writer) {
        return stream(streams, writer, new ArrayList<>());
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            SniContext sniContext) {
        return stream(streams, writer, new ArrayList<>(), sniContext);
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            List<WindowUpdate> windowUpdates) {
        return stream(streams, writer, windowUpdates, sniContext());
    }

    private static Http2ServerStream stream(Http2ConnectionStreams streams,
                                            Http2StreamWriter writer,
                                            List<WindowUpdate> windowUpdates,
                                            SniContext sniContext) {
        Http2Config config = Http2Config.builder()
                .initialWindowSize(8192)
                .maxFrameSize(16384)
                .build();
        ConnectionFlowControl flowControl = ConnectionFlowControl.serverBuilder((streamId, windowUpdate) ->
                        windowUpdates.add(new WindowUpdate(streamId, windowUpdate.windowSizeIncrement())))
                .initialWindowSize(config.initialWindowSize())
                .maxFrameSize(config.maxFrameSize())
                .build();
        return new Http2ServerStream(connectionContext(sniContext),
                                     streams,
                                     null,
                                     config,
                                     List.of(),
                                     STREAM_ID,
                                     Http2Settings.builder().build(),
                                     Http2Settings.builder().build(),
                                     writer,
                                     flowControl,
                                     new Http2ConnectionChecks(config, mock(Http2Connection.class)));
    }

    private static ConnectionContext connectionContext() {
        return connectionContext(sniContext());
    }

    private static ConnectionContext connectionContext(SniContext sniContext) {
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(ListenerConfig.create());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.sniContext()).thenReturn(Optional.of(sniContext));
        return ctx;
    }

    private static SniContext sniContext() {
        return new SniContext() {
            @Override
            public Optional<String> presentedHost() {
                return Optional.of("api.example.com");
            }

            @Override
            public Optional<String> matchedHost() {
                return Optional.of("api.example.com");
            }

            @Override
            public SniMatchType matchType() {
                return SniMatchType.EXACT;
            }

            @Override
            public AuthorityCheck checkAuthority(String authority) {
                return AuthorityCheck.ALLOWED;
            }
        };
    }

    private static SniContext parsingSniContext() {
        return new SniContext() {
            @Override
            public Optional<String> presentedHost() {
                return Optional.of("api.example.com");
            }

            @Override
            public Optional<String> matchedHost() {
                return Optional.of("api.example.com");
            }

            @Override
            public SniMatchType matchType() {
                return SniMatchType.EXACT;
            }

            @Override
            public AuthorityCheck checkAuthority(String authority) {
                UriAuthority.create(authority).host();
                return AuthorityCheck.ALLOWED;
            }
        };
    }

    private static Http2Headers headersWithoutAuthority() {
        return headersWithoutAuthority("1");
    }

    private static Http2Headers headersWithoutAuthority(String contentLength) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(Http2Headers.METHOD_NAME, Method.GET.text());
        headers.add(Http2Headers.PATH_NAME, "/");
        headers.add(Http2Headers.SCHEME_NAME, "https");
        headers.add(HeaderNames.CONTENT_LENGTH, contentLength);
        return Http2Headers.create(headers);
    }

    private static Http2Headers headersWithAuthority(String authority) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(Http2Headers.METHOD_NAME, Method.GET.text());
        headers.add(Http2Headers.PATH_NAME, "/");
        headers.add(Http2Headers.SCHEME_NAME, "https");
        headers.add(Http2Headers.AUTHORITY_NAME, authority);
        return Http2Headers.create(headers);
    }

    private record WindowUpdate(int streamId, int increment) {
    }

    private static final class RecordingStreamWriter implements Http2StreamWriter {
        private Status status;
        private int rstStreamCount;
        private final List<Http2ErrorCode> rstStreamCodes = new ArrayList<>();

        @Override
        public void write(Http2FrameData frame) {
            if (frame.header().type() == Http2FrameTypes.RST_STREAM.type()) {
                rstStreamCount++;
                rstStreamCodes.add(Http2RstStream.create(frame.data()).errorCode());
            }
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl) {
            this.status = headers.status();
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame,
                                FlowControl.Outbound flowControl) {
            this.status = headers.status();
            return 0;
        }
    }
}
