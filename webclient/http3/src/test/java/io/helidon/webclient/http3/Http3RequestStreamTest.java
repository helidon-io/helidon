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

package io.helidon.webclient.http3;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webclient.api.ResolvedClientTarget;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState.RECV;
import static io.helidon.quic.stream.QuicSenderStream.SendingStreamState.DATA_RECVD;
import static io.helidon.quic.stream.QuicSenderStream.SendingStreamState.READY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http3RequestStreamTest {
    @Test
    void emptyGetSendsOneHeadersFrameWithFinAndCompletesOnce() throws Exception {
        try (Fixture fixture = new Fixture(Method.GET, new byte[0], false, false, false)) {
            fixture.execute(Runnable::run);
            assertThat(fixture.headers.get(), is(1));
            assertThat(fixture.fin.get(), is(1));
            assertThat(fixture.body.size(), is(0));
            assertThat(fixture.sent.get(), is(1));
            fixture.readResponse();
            Http3StreamedResponse response = fixture.result.get(5, TimeUnit.SECONDS);
            assertThat(response.status(), is(204));
            response.closeResource();
            response.closeResource();
            assertThat(fixture.finished.get(), is(1));
        }
    }

    @Test
    void nonemptyPostPreservesDataBytesAndOneFin() throws Exception {
        byte[] expected = new byte[65537];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = (byte) index;
        }
        try (Fixture fixture = new Fixture(Method.POST, expected, false, false, false)) {
            fixture.execute(Runnable::run);
            assertThat(fixture.headers.get(), is(1));
            assertThat(fixture.body.toByteArray(), is(expected));
            assertThat(fixture.fin.get(), is(1));
            fixture.readResponse();
            fixture.result.get(5, TimeUnit.SECONDS).closeResource();
            assertThat(fixture.finished.get(), is(1));
        }
    }

    @Test
    void completedContinueDecisionStillSendsBodyAndCompletedFinalDecisionStopsIt() throws Exception {
        byte[] body = new byte[] {1, 2, 3};
        for (boolean informational : List.of(false, true)) {
            try (Fixture fixture = new Fixture(Method.POST, body, true, true, informational)) {
                fixture.execute(Runnable::run);
                Http3StreamedResponse response = fixture.result.get(5, TimeUnit.SECONDS);
                assertThat(response.status(), is(204));
                assertThat(fixture.body.toByteArray(), is(informational ? body : new byte[0]));
                assertThat(fixture.fin.get(), is(informational ? 1 : 0));
                assertThat(fixture.sent.get(), is(1));
                response.closeResource();
                response.closeResource();
                assertThat(fixture.finished.get(), is(1));
                assertThat(fixture.result.isCompletedExceptionally(), is(false));
            }
        }
    }

    @Test
    void sessionCloseReleasesPendingExpectProducerWithoutSendingBody() throws Exception {
        try (Fixture fixture = new Fixture(Method.POST, new byte[] {1, 2, 3}, true, false, false)) {
            Thread producer = Thread.ofVirtual().unstarted(() -> fixture.execute(Runnable::run));
            try {
                producer.start();
                assertThat("Request HEADERS dispatched", fixture.headerDispatched.await(5, TimeUnit.SECONDS), is(true));
                fixture.request.sessionClosed(new IllegalStateException("session closed"), StreamOutcome.ERROR);
                producer.join(5000);
                assertThat("Expect producer finished", producer.isAlive(), is(false));
                assertThat(fixture.result.isCompletedExceptionally(), is(true));
                assertThat(fixture.body.size(), is(0));
                assertThat(fixture.finished.get(), is(1));
            } finally {
                fixture.request.finish(StreamOutcome.CANCELLED);
                producer.interrupt();
                producer.join(5000);
                assertThat("Expect producer reaped", producer.isAlive(), is(false));
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Http3QpackContext qpack = Http3QpackContext.create(0, 0, 16_384, _ -> { });
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final AtomicInteger headers = new AtomicInteger();
        private final AtomicInteger fin = new AtomicInteger();
        private final AtomicInteger sent = new AtomicInteger();
        private final AtomicInteger finished = new AtomicInteger();
        private final CountDownLatch headerDispatched = new CountDownLatch(1);
        private final AtomicReference<Runnable> readerTask = new AtomicReference<>();
        private final CompletableFuture<Http3StreamedResponse> result = new CompletableFuture<>();
        private final Http3RequestStream request;

        private Fixture(Method method, byte[] bytes, boolean expect, boolean earlyResponse, boolean informational) {
            Http3ExchangeClient.ConnectionSession session = mock(Http3ExchangeClient.ConnectionSession.class);
            QuicConnection connection = mock(QuicConnection.class);
            when(connection.termination()).thenReturn(Optional.empty());
            when(session.connection()).thenReturn(connection);
            when(session.resolvedTarget()).thenReturn(mock(ResolvedClientTarget.class));
            when(session.qpackContext()).thenReturn(qpack);
            QuicBidiStream stream = mock(QuicBidiStream.class);
            QuicStreamReader reader = mock(QuicStreamReader.class);
            QuicStreamWriter writer = mock(QuicStreamWriter.class);
            when(stream.connectReader(any(SequentialScheduler.class))).thenReturn(reader);
            when(stream.streamId()).thenReturn(0L);
            when(stream.receivingState()).thenReturn(RECV);
            when(stream.futureSendingCompletion()).thenReturn(CompletableFuture.completedFuture(DATA_RECVD));
            when(stream.whenStopSendingReceived()).thenReturn(new CompletableFuture<>());
            when(writer.sendingState()).thenReturn(READY);
            ArrayDeque<BufferData> received = new ArrayDeque<>();
            if (informational) {
                received.add(BufferData.create(Http3Protocol.encodeResponseHeaders(100, WritableHeaders.create())));
            }
            received.add(BufferData.create(Http3Protocol.encodeResponseHeaders(204, WritableHeaders.create())));
            received.add(QuicStreamReader.EOF);
            when(reader.poll()).thenAnswer(_ -> Optional.ofNullable(received.poll()));
            when(reader.peek()).thenAnswer(_ -> Optional.ofNullable(received.peek()));
            doAnswer(invocation -> {
                capture(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(writer).scheduleForWriting(any(BufferData.class), anyBoolean());
            when(writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                    .thenAnswer(invocation -> {
                        capture(invocation.getArgument(0), invocation.getArgument(1));
                        return CompletableFuture.completedFuture(null);
                    });
            ClientRequestHeaders requestHeaders = ClientRequestHeaders.create(WritableHeaders.create());
            Http3RequestBody requestBody = Http3RequestBody.create(bytes);
            Context context = Context.create();
            requestBody.prepare(requestHeaders, context, 16 * 1024);
            Http3ExchangeClient.RequestData data = new Http3ExchangeClient.RequestData(
                    URI.create("https://example.test/request"), method, requestHeaders, requestBody,
                    Duration.ofSeconds(1), Duration.ofSeconds(30), expect, false, context, sent::incrementAndGet);
            Http3FrameListener listener = Http3FrameListener.create(List.of());
            Executor responseExecutor = earlyResponse ? Runnable::run : readerTask::set;
            request = new Http3RequestStream(session, data, stream,
                    Http3MessageReader.response(stream, qpack, mock(SocketContext.class), method, 16_384, listener),
                    writer, listener, mock(StreamObservation.class), responseExecutor,
                    _ -> finished.incrementAndGet(), _ -> { });
        }

        @Override
        public void close() {
            request.finish(StreamOutcome.CANCELLED);
            qpack.close(new IllegalStateException("Request fixture closed"));
        }

        private void execute(Executor executor) {
            request.execute(executor, (response, failure) -> {
                if (failure == null) {
                    result.complete(response);
                } else {
                    result.completeExceptionally(failure);
                }
            });
        }

        private void readResponse() {
            readerTask.get().run();
        }

        private void capture(BufferData buffer, boolean last) {
            ByteBuffer encoded = ByteBuffer.wrap(buffer.readBytes());
            while (encoded.hasRemaining()) {
                long type = VariableLengthEncoder.decode(encoded);
                int size = Math.toIntExact(VariableLengthEncoder.decode(encoded));
                byte[] payload = new byte[size];
                encoded.get(payload);
                if (type == Http3Protocol.FRAME_HEADERS) {
                    headers.incrementAndGet();
                    headerDispatched.countDown();
                } else {
                    assertThat(type, is(Http3Protocol.FRAME_DATA));
                    body.writeBytes(payload);
                }
            }
            if (last) {
                fin.incrementAndGet();
            }
        }
    }
}
