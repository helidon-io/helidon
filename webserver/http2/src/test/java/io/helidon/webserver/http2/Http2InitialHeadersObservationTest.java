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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HttpPrologue;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.HttpTransportObserverSupport.ConnectionObservationContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class Http2InitialHeadersObservationTest {
    @ParameterizedTest
    @EnumSource(RequestForm.class)
    void initialRequestEndCompletesOnlyAfterResponseWrite(RequestForm form) {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        Http2Headers requestHeaders = Http2Headers.create(WritableHeaders.create());
        requestHeaders.method(Method.GET);
        requestHeaders.path("/");
        requestHeaders.scheme("http");
        requestHeaders.authority("localhost");
        if (form == RequestForm.H2C) {
            input.add(frameBytes(Http2Settings.create().toFrameData(null, 0, Http2Flag.SettingsFlags.create(0))));
        } else {
            BufferData headersData = BufferData.growing(512);
            requestHeaders.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                                 Http2HuffmanEncoder.create(),
                                 headersData);
            if (form == RequestForm.CONTINUATION) {
                byte[] firstByte = new byte[1];
                headersData.read(firstByte);
                input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(1,
                                                                                Http2FrameTypes.HEADERS,
                                                                                Http2Flag.HeaderFlags.create(
                                                                                        Http2Flag.END_OF_STREAM),
                                                                                1),
                                                        BufferData.create(firstByte))));
                input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                                Http2FrameTypes.CONTINUATION,
                                                                                Http2Flag.ContinuationFlags.create(
                                                                                        Http2Flag.END_OF_HEADERS),
                                                                                1),
                                                        headersData)));
            } else {
                input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                                Http2FrameTypes.HEADERS,
                                                                                Http2Flag.HeaderFlags.create(
                                                                                        Http2Flag.END_OF_HEADERS
                                                                                                | Http2Flag.END_OF_STREAM),
                                                                                1),
                                                        headersData)));
            }
        }

        List<StreamOutcome> outcomes = new ArrayList<>();
        var observation = mock(ConnectionObservation.class);
        when(observation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE)).thenReturn(outcomes::add);
        var context = mock(ConnectionContext.class, withSettings().extraInterfaces(ConnectionObservationContext.class));
        when(((ConnectionObservationContext) context).httpTransportObservation()).thenReturn(observation);
        when(context.router()).thenReturn(Router.empty());
        when(context.listenerContext()).thenReturn(mock(ListenerContext.class));
        when(context.dataWriter()).thenReturn(mock(DataWriter.class));
        when(context.dataReader()).thenReturn(DataReader.create(input::poll));
        when(context.sniContext()).thenReturn(Optional.empty());
        var peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(context.remotePeer()).thenReturn(peerInfo);
        when(context.proxyProtocolData()).thenReturn(Optional.empty());
        var executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).submit(any(Runnable.class));
        when(context.executor()).thenReturn(executor);
        Http2SubProtocolSelector selector = (_, _, _, writer, streamId, _, _, _, _, _) -> {
            var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
            when(handler.streamState()).thenReturn(Http2StreamState.CLOSED);
            doAnswer(_ -> {
                assertThat("Initial request completion does not complete the response", outcomes, is(List.of()));
                writer.writeHeaders(Http2Headers.create(WritableHeaders.create()).status(Status.OK_200),
                                    streamId,
                                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                    FlowControl.Outbound.NOOP);
                return null;
            }).when(handler).init();
            return new SubProtocolResult(true, handler);
        };
        var connection = new Http2Connection(context, Http2Config.create(), List.of(selector));
        if (form == RequestForm.H2C) {
            connection.upgradeConnectionData(HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", Method.GET, "/", false),
                                             requestHeaders);
        }

        assertThrows(CloseConnectionException.class, () -> connection.handle(mock(Limit.class)));

        verify(observation, times(1)).streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE);
        assertThat("One completed exchange after the response write", outcomes, is(List.of(StreamOutcome.COMPLETED)));
    }

    private static byte[] frameBytes(Http2FrameData frame) {
        return BufferData.create(frame.header().write(), frame.data()).readBytes();
    }

    private enum RequestForm {
        HEADERS,
        CONTINUATION,
        H2C
    }
}
