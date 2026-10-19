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
import java.util.Optional;

import io.helidon.http.DirectHandler;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ServerStreamErrorLogTest {
    private static final int STREAM_ID = 1;

    @ParameterizedTest(name = "safe={0}, log-all-messages={1}, status={2}, warning={3}")
    @CsvSource({
            "true, false, 500, true",
            "false, false, 500, false",
            "false, true, 500, true",
            "true, false, 400, false"
    })
    void rejectedStreamHonorsMessageSafety(boolean safeMessage, boolean logAllMessages, int statusCode, boolean warning) {
        Status status = Status.create(statusCode);
        IllegalStateException originalCause = new IllegalStateException("original failure detail");
        RequestException exception = RequestException.builder()
                .type(statusCode == 500 ? DirectHandler.EventType.INTERNAL_ERROR : DirectHandler.EventType.BAD_REQUEST)
                .status(status)
                .message("request detail")
                .safeMessage(safeMessage)
                .cause(originalCause)
                .build();
        ListenerContext listener = mock(ListenerContext.class);
        when(listener.config()).thenReturn(ListenerConfig.builder()
                                                   .errorHandling(errors -> errors.logAllMessages(logAllMessages))
                                                   .build());
        when(listener.directHandlers()).thenReturn(DirectHandlers.create());
        ConnectionContext context = mock(ConnectionContext.class);
        when(context.listenerContext()).thenReturn(listener);
        when(context.router()).thenReturn(Router.empty());
        when(context.sniContext()).thenReturn(Optional.empty());

        Http2SubProtocolSelector.SubProtocolHandler handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.OPEN);
        doThrow(exception).when(handler).init();
        Http2SubProtocolSelector selector = (_, _, _, _, _, _, _, _, _, _) -> new SubProtocolResult(true, handler);
        Http2Config config = Http2Config.builder().initialWindowSize(8192).build();
        ConnectionFlowControl flowControl = ConnectionFlowControl.serverBuilder((_, _) -> { })
                .initialWindowSize(config.initialWindowSize())
                .maxFrameSize(config.maxFrameSize())
                .build();
        RecordingWriter writer = new RecordingWriter();
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream stream = new Http2ServerStream(context,
                                                        streams,
                                                        new Http2StreamAdmissionGate(),
                                                        mock(Http2ServerStream.LocallyResetStreamTracker.class),
                                                        HttpRouting.empty(),
                                                        config,
                                                        List.of(selector),
                                                        STREAM_ID,
                                                        Http2Settings.builder().build(),
                                                        Http2Settings.builder().build(),
                                                        writer,
                                                        flowControl,
                                                        new InboundDataBudget(1, 8192),
                                                        new Http2ConnectionChecks(config, mock(Http2Connection.class)));
        streams.put(new Http2Connection.StreamContext(STREAM_ID, 8192, stream));
        stream.prologue(HttpPrologue.create(Http2Connection.FULL_PROTOCOL,
                                            Http2Connection.PROTOCOL,
                                            Http2Connection.PROTOCOL_VERSION,
                                            Method.GET,
                                            "/",
                                            true));
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.add(Http2Headers.METHOD_NAME, Method.GET.text());
        requestHeaders.add(Http2Headers.PATH_NAME, "/");
        requestHeaders.add(Http2Headers.SCHEME_NAME, "https");
        requestHeaders.add(Http2Headers.AUTHORITY_NAME, "example.com");
        stream.headers(Http2Headers.create(requestHeaders), true);

        stream.run();
        streams.doMaintenance();

        verify(handler).init();
        if (warning) {
            verify(listener).logError(any(System.Logger.class),
                                      eq(System.Logger.Level.WARNING),
                                      eq("Internal server error"),
                                      same(exception));
        } else {
            verify(listener, never()).logError(any(System.Logger.class),
                                               eq(System.Logger.Level.WARNING),
                                               anyString(),
                                               any(Throwable.class));
        }
        assertThat("message safety must not change the error response", writer.headers.status(), is(status));
        assertThat("default include-entity=false must remain effective",
                   writer.headers.httpHeaders().contentLength().orElseThrow(), is(0L));
        assertThat(writer.headerWrites, is(1));
        assertThat(writer.headerFlags.endOfHeaders(), is(true));
        assertThat(writer.headerFlags.endOfStream(), is(true));
        assertThat("empty error responses need no DATA frame", writer.dataFrame, nullValue());
        assertThat("a completed request body needs no reset", writer.otherFrameWrites, is(0));
        assertThat(stream.streamState(), is(Http2StreamState.CLOSED));
        assertThat("rejected stream must be removed", streams.get(STREAM_ID), nullValue());
    }

    private static final class RecordingWriter implements Http2StreamWriter {
        private Http2Headers headers;
        private Http2Flag.HeaderFlags headerFlags;
        private Http2FrameData dataFrame;
        private int headerWrites;
        private int otherFrameWrites;

        @Override
        public void write(Http2FrameData frame) {
            otherFrameWrites++;
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            dataFrame = frame;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl) {
            headerWrites++;
            this.headers = headers;
            this.headerFlags = flags;
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame,
                                FlowControl.Outbound flowControl) {
            headerWrites++;
            this.headers = headers;
            this.headerFlags = flags;
            this.dataFrame = dataFrame;
            return 0;
        }
    }
}
