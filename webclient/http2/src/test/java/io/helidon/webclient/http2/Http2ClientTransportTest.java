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

package io.helidon.webclient.http2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameListener;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.HttpTransportObserverSupport.ConnectionObservationContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class Http2ClientTransportTest {
    private static final Http2StreamConfig STREAM_CONFIG = new Http2StreamConfig() {
        @Override
        public boolean priorKnowledge() {
            return true;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Duration readTimeout() {
            return Duration.ofSeconds(1);
        }
    };

    @Test
    void responseHeadersWaitForSuccessfulRequestEnd() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), false);
        stream.inboundHeaders(responseHeaders(), true);
        assertThat(fixture.outcomes, is(List.of()));

        stream.writeData(BufferData.empty(), true);
        stream.close();

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.COMPLETED)));
        assertThat(fixture.events, is(List.of("protocol:http/2", "stream:LOCAL", "closed:COMPLETED")));
    }

    @Test
    void terminalDataCompletesBeforeApplicationReadsIt() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), true);
        stream.inboundHeaders(responseHeaders(), false);
        var frame = dataFrame(stream.streamId(), true);

        fixture.connection.handle(frame.header(), frame.data());

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.COMPLETED)));
        assertThat(stream.read().available(), is(1));
        stream.close();
        assertThat(fixture.outcomes, is(List.of(StreamOutcome.COMPLETED)));
    }

    @Test
    void nonOkGrpcTrailersCompleteBeforeApplicationReadsThem() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), true);
        stream.inboundHeaders(responseHeaders(), false);

        stream.inboundHeaders(Http2Headers.create(WritableHeaders.create().add(HeaderNames.create("grpc-status"), "7")), true);

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.COMPLETED)));
        assertThat(stream.trailers().isDone(), is(false));
        stream.close();
        assertThat(fixture.outcomes, is(List.of(StreamOutcome.COMPLETED)));
    }

    @Test
    void directSubclassPublishesWithoutConnectionCreateStream() {
        var fixture = new Fixture();
        var stream = fixture.subclassStream();

        stream.writeHeaders(requestHeaders(), true);
        stream.inboundHeaders(responseHeaders(), true);

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.COMPLETED)));
    }

    @Test
    void remoteResetPublishesBeforeSubclassCanCloseConnection() {
        var fixture = new Fixture();
        var stream = fixture.subclassStream();
        stream.closeOnReset = true;
        stream.writeHeaders(requestHeaders(), true);
        var frame = new Http2RstStream(Http2ErrorCode.CANCEL)
                .toFrameData(Http2Settings.create(), stream.streamId(), Http2Flag.NoFlags.create());

        fixture.connection.handle(frame.header(), frame.data());

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.RESET)));
        assertThat(fixture.events,
                   is(List.of("protocol:http/2", "stream:LOCAL", "closed:RESET", "connection:LOCAL_CLOSE")));
    }

    @Test
    void refusedStreamIsRejected() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), true);

        stream.rstStream(new Http2RstStream(Http2ErrorCode.REFUSED_STREAM));

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.REJECTED)));
    }

    @Test
    void localCancellationWritesReset() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), true);

        stream.cancel();
        stream.close();

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.RESET)));
    }

    @Test
    void closeBeforeResetPreservesResetOutcome() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), true);

        stream.resetAndClose(Http2ErrorCode.PROTOCOL);

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.RESET)));
    }

    @Test
    void bareCloseCancelsPendingExchange() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), true);

        stream.close();

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.CANCELLED)));
    }

    @Test
    void failedRequestWriteClosesStreamAsError() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        var failure = new UncheckedIOException(new IOException("Write failed"));
        doThrow(failure).when(fixture.writer).writeNow(any(BufferData.class));

        assertThat(assertThrows(UncheckedIOException.class, () -> stream.writeHeaders(requestHeaders(), true)),
                   sameInstance(failure));

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.ERROR)));
        assertThat(fixture.connectionOutcome.get(), is(ConnectionOutcome.ERROR));
    }

    @Test
    void failedTerminalRequestDataCannotComplete() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), false);
        stream.inboundHeaders(responseHeaders(), true);
        var failure = new UncheckedIOException(new IOException("Write failed"));
        doThrow(failure).when(fixture.writer).writeNow(any(BufferData.class));

        assertThrows(UncheckedIOException.class, () -> stream.writeData(BufferData.empty(), true));

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.ERROR)));
    }

    @Test
    void failedResetWriteReportsErrorInsteadOfCancellation() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), true);
        doThrow(new UncheckedIOException(new IOException("Reset write failed")))
                .when(fixture.writer).writeNow(any(BufferData.class));

        stream.resetAndClose(Http2ErrorCode.PROTOCOL);

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.ERROR)));
        assertThat(fixture.connectionOutcome.get(), is(ConnectionOutcome.ERROR));
    }

    @Test
    void failedPingWriteClassifiesPhysicalClosure() {
        var fixture = new Fixture();
        doThrow(new UncheckedIOException(new IOException("PING write failed")))
                .when(fixture.writer).writeNow(any(BufferData.class));

        assertThat(fixture.connection.closed(Http2ClientProtocolConfig.builder().ping(true).build()), is(true));
        fixture.connection.closeNow();

        assertThat(fixture.events, is(List.of("protocol:http/2", "connection:ERROR")));
    }

    @Test
    void timedOutPingWriteClassifiesPhysicalClosure() {
        var fixture = new Fixture();
        doThrow(new UncheckedIOException(new SocketTimeoutException("PING write timed out")))
                .when(fixture.writer).writeNow(any(BufferData.class));

        assertThat(fixture.connection.closed(Http2ClientProtocolConfig.builder().ping(true).build()), is(true));
        fixture.connection.closeNow();

        assertThat(fixture.events, is(List.of("protocol:http/2", "connection:TIMEOUT")));
    }

    @Test
    void pingTimeoutClassifiesPhysicalClosure() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), true);

        assertThat(fixture.connection.ping(Http2ClientProtocolConfig.builder()
                                                  .pingTimeout(Duration.ofMillis(1))
                                                  .build()),
                   is(false));
        fixture.connection.closeNow();

        assertThat(fixture.connectionOutcome.get(), is(ConnectionOutcome.TIMEOUT));
        assertThat(fixture.outcomes, is(List.of(StreamOutcome.ERROR)));
    }

    @Test
    void pollingTimeoutDoesNotEndGrpcExchange() {
        var fixture = new Fixture();
        var stream = fixture.subclassStream();
        stream.writeHeaders(requestHeaders(), true);

        assertThrows(StreamTimeoutException.class, () -> stream.readHeaders(Duration.ofMillis(1)));
        assertThat(fixture.outcomes, is(List.of()));

        stream.inboundHeaders(responseHeaders(), true);
        assertThat(fixture.outcomes, is(List.of(StreamOutcome.COMPLETED)));
    }

    @Test
    void physicalCloseSettlesPendingStreamAndSuppressesLateEvents() {
        var fixture = new Fixture();
        var stream = fixture.stream();
        stream.writeHeaders(requestHeaders(), false);
        fixture.connection.transportFailed(new IOException("Connection failed"));

        fixture.connection.closeNow();
        stream.remoteReset(Http2ErrorCode.CANCEL);
        stream.close();

        assertThat(fixture.outcomes, is(List.of(StreamOutcome.ERROR)));
        assertThat(fixture.events,
                   is(List.of("protocol:http/2", "stream:LOCAL", "closed:ERROR", "connection:ERROR")));
    }

    private static Http2Headers requestHeaders() {
        return Http2Headers.create(WritableHeaders.create()
                                           .add(Http2Headers.METHOD_NAME, "POST")
                                           .add(Http2Headers.PATH_NAME, "/")
                                           .add(Http2Headers.SCHEME_NAME, "http")
                                           .add(Http2Headers.AUTHORITY_NAME, "localhost"));
    }

    private static Http2Headers responseHeaders() {
        return Http2Headers.create(WritableHeaders.create()).status(Status.OK_200);
    }

    private static Http2FrameData dataFrame(int streamId, boolean endOfStream) {
        return new Http2FrameData(Http2FrameHeader.create(1,
                                                         Http2FrameTypes.DATA,
                                                         Http2Flag.DataFlags.create(endOfStream ? Http2Flag.END_OF_STREAM : 0),
                                                         streamId),
                                  BufferData.create(new byte[1]));
    }

    private static final class Fixture {
        private final List<String> events = new ArrayList<>();
        private final List<StreamOutcome> outcomes = new ArrayList<>();
        private final AtomicReference<ConnectionOutcome> connectionOutcome = new AtomicReference<>(ConnectionOutcome.LOCAL_CLOSE);
        private final DataWriter writer = mock(DataWriter.class);
        private final HelidonSocket socket = mock(HelidonSocket.class);
        private final Http2ClientConfig config = Http2ClientConfig.create();
        private final Http2ClientConnection connection;

        private Fixture() {
            ConnectionObservation observation = HttpTransportObserver.compose(List.of((_, _, _) -> new ConnectionObservation() {
                @Override
                public HandshakeObservation handshakeStarted() {
                    return HandshakeObservation.noop();
                }

                @Override
                public void protocolSelected(String protocol) {
                    events.add("protocol:" + protocol);
                }

                @Override
                public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                    assertThat(direction, is(Direction.BIDIRECTIONAL));
                    events.add("stream:" + initiator);
                    return outcome -> {
                        outcomes.add(outcome);
                        events.add("closed:" + outcome);
                    };
                }

                @Override
                public void close(ConnectionOutcome outcome) {
                    events.add("connection:" + outcome);
                }
            })).connectionOpened(Role.CLIENT, HttpTransportObserver.TRANSPORT_TCP, Handshake.NONE);
            var physical = mock(ClientConnection.class, withSettings().extraInterfaces(ConnectionObservationContext.class));
            var context = (ConnectionObservationContext) physical;
            when(context.httpTransportObservation()).thenReturn(observation);
            doAnswer(invocation -> {
                connectionOutcome.set(invocation.getArgument(0));
                return null;
            }).when(context).httpTransportOutcome(any());
            AtomicBoolean closed = new AtomicBoolean();
            doAnswer(_ -> {
                if (closed.compareAndSet(false, true)) {
                    observation.close(connectionOutcome.get());
                }
                return null;
            }).when(physical).closeResource();
            when(physical.helidonSocket()).thenReturn(socket);
            when(physical.writer()).thenReturn(writer);
            when(physical.reader()).thenReturn(DataReader.create(() -> BufferData.EMPTY_BYTES));
            var client = mock(Http2ClientImpl.class);
            when(client.clientConfig()).thenReturn(config);
            when(client.protocolConfig()).thenReturn(config.protocolConfig());
            when(client.sendListener()).thenReturn(Http2FrameListener.create(List.of()));
            when(client.recvListener()).thenReturn(Http2FrameListener.create(List.of()));
            connection = new Http2ClientConnection(client, physical);
            var settings = Http2Settings.create().toFrameData(null, 0, Http2Flag.SettingsFlags.create(0));
            connection.handle(settings.header(), settings.data());
        }

        private Http2ClientStream stream() {
            return connection.createStream(STREAM_CONFIG);
        }

        private ClosingSubclass subclassStream() {
            return new ClosingSubclass(connection, socket, config);
        }
    }

    private static final class ClosingSubclass extends Http2ClientStream {
        private final Http2ClientConnection connection;
        private boolean closeOnReset;

        private ClosingSubclass(Http2ClientConnection connection, HelidonSocket socket, Http2ClientConfig config) {
            super(connection,
                  Http2Settings.create(),
                  socket,
                  STREAM_CONFIG,
                  config,
                  connection.streamIdSequence(),
                  Http2FrameListener.create(List.of()),
                  Http2FrameListener.create(List.of()));
            this.connection = connection;
        }

        @Override
        public boolean rstStream(Http2RstStream rstStream) {
            if (closeOnReset) {
                connection.closeNow();
            }
            return super.rstStream(rstStream);
        }
    }
}
