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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3StreamSupportTest {
    private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());

    @Test
    void shouldReadEntityAndTrailersUsingTrailerAwareReader() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200,
                                                                  WritableHeaders.create()
                                                                          .add(HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                                                   "text/plain")));
        byte[] dataFrame = Http3Protocol.encodeDataFrame("hello trailer-aware reader".getBytes(StandardCharsets.UTF_8));
        byte[] trailersFrame = Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                        .add(HeaderValues.create("x-trailer", "done")));

        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(dataFrame),
                                                                      BufferData.create(trailersFrame)));
        Http3MessageReader reader = responseReader(stream);

        Http3MessageReader.ResponseHead responseHead = reader.readResponseHead(_ -> {
        });
        assertThat(responseHead.status().code(), equalTo(200));
        assertThat(responseHead.headers().first(HeaderNames.CONTENT_TYPE).orElseThrow(), equalTo("text/plain"));

        String entity = readEntityWithTrailers(reader, 8);
        assertThat(entity, equalTo("hello trailer-aware reader"));
        assertThat(reader.trailers().first(HeaderNames.create("x-trailer")).orElseThrow(), equalTo("done"));
    }

    @Test
    void shouldExposeStrictReadOnlyEntityRangeWithOneOutstandingBuffer() {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8));
        byte[] trailersFrame = Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                        .add(HeaderValues.create("x-end", "yes")));
        byte[] message = concat(headersFrame, dataFrame, trailersFrame);
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.createReadOnly(message,
                                                                                                 0,
                                                                                                 message.length)));
        Http3MessageReader reader = responseReader(stream);
        reader.readResponseHead(_ -> {
        });

        BufferData body = reader.readEntityBufferWithTrailers(4);

        assertThat(body.available(), is(4));
        assertThrows(IndexOutOfBoundsException.class, () -> body.get(4));
        assertThrows(UnsupportedOperationException.class, () -> body.write(1));
        assertThat(body.readString(4), is("body"));
        body.rewind();
        assertThrows(IndexOutOfBoundsException.class, () -> body.get(4));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> reader.readEntityBufferWithTrailers(4));
        assertThat(failure.getMessage(), is("Previous HTTP/3 entity buffer has not been consumed"));
        assertThat(body.readString(4), is("body"));

        assertThat(reader.readEntityBufferWithTrailers(4).available(), is(0));
        assertThat(reader.trailers().first(HeaderNames.create("x-end")).orElseThrow(), is("yes"));
        assertThat(reader.messageComplete(), is(true));
    }

    @Test
    void shouldDefensivelyCopyRawInboundFrameData() {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8));
        byte[] trailersFrame = Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                        .add(HeaderValues.create("x-end", "yes")));
        byte[] message = concat(headersFrame, dataFrame, trailersFrame);
        List<byte[]> loggedData = new ArrayList<>();
        Http3FrameListener listener = new Http3FrameListener() {
            private long currentFrameType;

            @Override
            public boolean rawDataEnabled() {
                return true;
            }

            @Override
            public void frameHeader(SocketContext context,
                                    long streamId,
                                    long frameType,
                                    long frameLength,
                                    int encodedLength) {
                currentFrameType = frameType;
            }

            @Override
            public void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
                if (currentFrameType == Http3Protocol.FRAME_DATA) {
                    loggedData.add(data.clone());
                }
                Arrays.fill(data, (byte) 0);
            }
        };
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.createReadOnly(message,
                                                                                                 0,
                                                                                                 message.length)));
        Http3MessageReader reader = responseReader(stream, listener);

        assertThat(reader.readResponseHead(_ -> {
        }).status().code(), is(200));
        assertThat(readEntityWithTrailers(reader, 4), is("body"));

        assertThat(concat(loggedData.toArray(byte[][]::new)), is("body".getBytes(StandardCharsets.UTF_8)));
        assertThat(reader.trailers().first(HeaderNames.create("x-end")).orElseThrow(), is("yes"));
    }

    @Test
    void shouldBatchSingleByteEntityReads() throws Exception {
        byte[] entity = new byte[20 * 1024];
        for (int i = 0; i < entity.length; i++) {
            entity[i] = (byte) i;
        }
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame(entity);
        byte[] message = concat(headersFrame, dataFrame);
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.createReadOnly(message,
                                                                                                 0,
                                                                                                 message.length)));
        Http3MessageReader reader = responseReader(stream);
        reader.readResponseHead(_ -> {
        });
        byte[] actual = new byte[entity.length];

        try (InputStream inputStream = reader.inputStreamWithTrailers()) {
            for (int i = 0; i < actual.length; i++) {
                int next = inputStream.read();
                if (next == -1) {
                    throw new AssertionError("HTTP/3 entity ended after " + i + " bytes");
                }
                actual[i] = (byte) next;
            }
            assertThat(inputStream.read(), is(-1));
        }

        assertThat(actual, is(entity));
        assertThat(stream.pollCount(), is(2));
    }

    @Test
    void shouldReadFragmentedFramesAndExposeTrailers() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200,
                                                                  WritableHeaders.create()
                                                                          .add(HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                                                   "application/json")));
        byte[] dataFrame = Http3Protocol.encodeDataFrame("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        byte[] trailersFrame = Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                        .add(HeaderValues.create("x-end", "yes")));

        List<BufferData> fragmented = new ArrayList<>();
        fragmented.addAll(splitBytes(headersFrame, 2));
        fragmented.addAll(splitBytes(dataFrame, 1));
        fragmented.addAll(splitBytes(trailersFrame, 3));

        FakeReceiverStream stream = FakeReceiverStream.create(fragmented);
        Http3MessageReader reader = responseReader(stream);

        Http3MessageReader.ResponseHead responseHead = reader.readResponseHead(_ -> {
        });
        assertThat(responseHead.status().code(), equalTo(200));
        assertThat(responseHead.headers().first(HeaderNames.CONTENT_TYPE).orElseThrow(), equalTo("application/json"));

        String entity = readEntityWithTrailers(reader, 4);
        assertThat(entity, equalTo("{\"ok\":true}"));
        assertThat(reader.trailers().first(HeaderNames.create("x-end")).orElseThrow(), equalTo("yes"));
        assertThat(stream.disconnected(), is(true));
    }

    @Test
    void shouldExposeTrailerAwareInputStream() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200,
                                                                  WritableHeaders.create()
                                                                          .add(HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                                                   "text/plain")));
        byte[] dataFrame = Http3Protocol.encodeDataFrame("hello streamed trailers".getBytes(StandardCharsets.UTF_8));
        byte[] trailersFrame = Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                        .add(HeaderValues.create("x-trailer", "done")));

        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(dataFrame),
                                                                      BufferData.create(trailersFrame)));
        Http3MessageReader reader = responseReader(stream);

        Http3MessageReader.ResponseHead responseHead = reader.readResponseHead(_ -> {
        });
        assertThat(responseHead.status().code(), equalTo(200));
        assertThat(responseHead.headers().first(HeaderNames.CONTENT_TYPE).orElseThrow(), equalTo("text/plain"));

        Headers[] trailers = new Headers[1];
        AtomicInteger entityFullyRead = new AtomicInteger();
        try (InputStream inputStream = reader.inputStreamWithTrailers(headers -> trailers[0] = headers,
                                                                     entityFullyRead::incrementAndGet,
                                                                     _ -> {
                                                                     })) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8),
                       equalTo("hello streamed trailers"));
            assertThat(inputStream.read(), equalTo(-1));
        }

        assertThat(entityFullyRead.get(), equalTo(1));
        assertThat(trailers[0].first(HeaderNames.create("x-trailer")).orElseThrow(), equalTo("done"));
        assertThat(stream.disconnected(), is(true));
    }

    @Test
    void shouldPollStreamOnlyWhenMessageConsumerNeedsData() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame("payload".getBytes(StandardCharsets.UTF_8));
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(dataFrame)));
        Http3MessageReader reader = responseReader(stream);

        for (int i = 0; i < 1_000; i++) {
            stream.reader.scheduler.runOrSchedule();
        }

        assertThat("Reader wakeups must coalesce without prefetching transport buffers", stream.pollCount(), is(0));

        reader.readResponseHead(_ -> {
        });
        assertThat("Reading response headers should consume only their transport buffer", stream.pollCount(), is(1));

        try (InputStream inputStream = reader.inputStreamWithTrailers()) {
            assertThat("Creating the entity stream must not prefetch body data", stream.pollCount(), is(1));
            assertThat(inputStream.read(), is((int) 'p'));
            assertThat("The first entity read should obtain one body transport buffer", stream.pollCount(), is(2));
        }
    }

    @Test
    void shouldCompleteReadyEndOfStreamBeforeDisconnectingReader() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(204, WritableHeaders.create());
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame)));
        Http3MessageReader reader = responseReader(stream);

        reader.readResponseHead(_ -> {
        });
        assertThat(reader.messageComplete(), is(true));

        reader.close();

        assertThat("Completing a message must consume the transport EOF", stream.pollCount(), is(2));
        assertThat(stream.disconnected(), is(true));
    }

    @Test
    void shouldRejectEntityReadsAfterMessageReaderCloses() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame("buffered".getBytes(StandardCharsets.UTF_8));
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(dataFrame)));
        Http3MessageReader reader = responseReader(stream);
        reader.readResponseHead(_ -> {
        });
        InputStream inputStream = reader.inputStreamWithTrailers();

        reader.close();

        IOException failure = assertThrows(IOException.class, inputStream::read);
        assertThat(failure.getMessage(), equalTo("HTTP/3 entity stream is closed."));
        assertThat(stream.disconnected(), is(true));
    }

    @Test
    void shouldWakeBlockedEntityStreamReadWithIOExceptionWhenReaderCloses() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        FakeReceiverStream stream = new FakeReceiverStream(List.of(BufferData.create(headersFrame)));
        Http3MessageReader reader = responseReader(stream);
        reader.readResponseHead(_ -> {
        });
        InputStream inputStream = reader.inputStreamWithTrailers();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread readThread = Thread.ofPlatform().start(() -> {
            try {
                inputStream.read();
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (readThread.getState() != Thread.State.WAITING
                    && readThread.isAlive()
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(readThread.getState(), is(Thread.State.WAITING));
        } finally {
            reader.close();
        }

        readThread.join(5_000);
        assertThat(readThread.isAlive(), is(false));
        assertThat(failure.get(), instanceOf(IOException.class));
        assertThat(failure.get().getMessage(), equalTo("HTTP/3 entity stream is closed."));
        assertThat(failure.get().getCause(), instanceOf(IllegalStateException.class));
    }

    @Test
    void shouldTimeoutWhileWaitingForResponseHeaders() {
        FakeReceiverStream stream = new FakeReceiverStream(List.of());
        Http3MessageReader reader = Http3MessageReader.response(stream,
                                                                qpackContext(0, 0),
                                                                Http3TestSocketContext.INSTANCE,
                                                                Method.GET,
                                                                16_384,
                                                                Http3MessageReader.ResponseOptions.create(
                                                                        true,
                                                                        Duration.ofMillis(50),
                                                                        NO_OP_FRAME_LISTENER));
        reader.activateReadTimeout();

        UncheckedIOException failure = assertThrows(UncheckedIOException.class,
                                                     () -> reader.readResponseHead(_ -> {
                                                     }));

        assertThat(failure.getCause(), instanceOf(Http3ReadTimeoutException.class));
        reader.close();
    }

    @Test
    void shouldTimeoutWhileWaitingForResponseEntityData() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame(new byte[] {1});
        FakeReceiverStream stream = new FakeReceiverStream(List.of(BufferData.create(headersFrame),
                                                                   BufferData.create(dataFrame)));
        Http3MessageReader reader = Http3MessageReader.response(stream,
                                                                qpackContext(0, 0),
                                                                Http3TestSocketContext.INSTANCE,
                                                                Method.GET,
                                                                16_384,
                                                                Http3MessageReader.ResponseOptions.create(
                                                                        true,
                                                                        Duration.ofMillis(50),
                                                                        NO_OP_FRAME_LISTENER));
        reader.activateReadTimeout();
        reader.readResponseHead(_ -> {
        });

        try (InputStream inputStream = reader.inputStreamWithTrailers()) {
            assertThat(inputStream.read(), is(1));
            assertThrows(SocketTimeoutException.class, inputStream::read);
        }
    }

    @Test
    void shouldTimeoutPartialRequestHeaders() {
        FakeReceiverStream stream = new FakeReceiverStream(
                List.of(BufferData.create(new byte[] {(byte) Http3Protocol.FRAME_HEADERS})));
        Http3MessageReader reader = Http3MessageReader.request(stream,
                                                               qpackContext(0, 0),
                                                               Http3TestSocketContext.INSTANCE,
                                                               16_384,
                                                               true,
                                                               Duration.ofMillis(50),
                                                               NO_OP_FRAME_LISTENER);
        reader.activateReadTimeout();

        UncheckedIOException failure = assertThrows(UncheckedIOException.class, reader::readRequestHead);

        assertThat(failure.getCause(), instanceOf(Http3ReadTimeoutException.class));
        reader.close();
    }

    @Test
    void shouldTimeoutStalledRequestEntityAfterProgress() {
        byte[] dataFrame = Http3Protocol.encodeDataFrame("partial".getBytes(StandardCharsets.UTF_8));
        FakeReceiverStream stream = new FakeReceiverStream(
                List.of(BufferData.create(requestHeadersFrame()),
                        BufferData.create(Arrays.copyOf(dataFrame, dataFrame.length - 1))));
        Http3MessageReader reader = Http3MessageReader.request(stream,
                                                               qpackContext(0, 0),
                                                               Http3TestSocketContext.INSTANCE,
                                                               16_384,
                                                               true,
                                                               Duration.ofMillis(50),
                                                               NO_OP_FRAME_LISTENER);
        reader.activateReadTimeout();
        reader.readRequestHead();

        assertThat(reader.readEntityDataWithTrailers(8).length, equalTo(6));
        UncheckedIOException failure = assertThrows(UncheckedIOException.class,
                                                     () -> reader.readEntityDataWithTrailers(8));

        assertThat(failure.getCause(), instanceOf(Http3ReadTimeoutException.class));
        reader.close();
    }

    @Test
    void shouldTimeoutBlockedRequestQpackSection() {
        Http3QpackContext peerEncoder = qpackContext(0, 0);
        List<byte[]> encoderInstructions = new ArrayList<>();
        peerEncoder.encoderInstructionsSender(encoderInstructions::add);
        peerEncoder.peerSettings(128, 1);
        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create("x-dynamic", "value"));
        URI uri = URI.create("https://localhost/test");
        Http3Protocol.encodeRequestHeaders(peerEncoder, 0, uri, "GET", headers);
        Http3QpackContext primingDecoder = qpackContext(128, 1);
        primingDecoder.decoderInstructionsSender(peerEncoder::onDecoderStreamData);
        for (byte[] instruction : encoderInstructions) {
            primingDecoder.onEncoderStreamData(instruction);
        }
        byte[] requestFrame = Http3Protocol.encodeRequestHeaders(peerEncoder, 0, uri, "GET", headers);
        Http3QpackContext localDecoder = qpackContext(128, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        localDecoder.decoderInstructionsSender(decoderInstructions::add);
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(requestFrame)));
        Http3MessageReader reader = Http3MessageReader.request(stream,
                                                               localDecoder,
                                                               Http3TestSocketContext.INSTANCE,
                                                               16_384,
                                                               true,
                                                               Duration.ofMillis(50),
                                                               NO_OP_FRAME_LISTENER);
        reader.activateReadTimeout();

        UncheckedIOException failure = assertThrows(UncheckedIOException.class, reader::readRequestHead);

        assertThat(failure.getCause(), instanceOf(Http3ReadTimeoutException.class));
        assertThat(decoderInstructions, hasSize(1));
        assertThat(decoderInstructions.getFirst(), equalTo(new byte[] {0x40}));
        reader.close();
    }

    @Test
    void shouldTranslateInterruptedQpackWaitAtEntityInputStreamBoundary() throws Exception {
        Http3QpackContext peerEncoder = qpackContext(0, 0);
        List<byte[]> encoderInstructions = new ArrayList<>();
        peerEncoder.encoderInstructionsSender(encoderInstructions::add);
        peerEncoder.peerSettings(128, 1);
        WritableHeaders<?> trailers = WritableHeaders.create()
                .add(HeaderValues.create("x-dynamic", "value"));
        Http3Protocol.encodeHeadersFrame(peerEncoder, 0, trailers);
        Http3QpackContext primingDecoder = qpackContext(128, 1);
        primingDecoder.decoderInstructionsSender(peerEncoder::onDecoderStreamData);
        for (byte[] instruction : encoderInstructions) {
            primingDecoder.onEncoderStreamData(instruction);
        }
        byte[] trailersFrame = Http3Protocol.encodeHeadersFrame(peerEncoder, 0, trailers);
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        Http3QpackContext localDecoder = qpackContext(128, 1);
        localDecoder.decoderInstructionsSender(_ -> {
        });
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(trailersFrame)));
        Http3MessageReader reader = responseReader(stream, localDecoder, 16_384, NO_OP_FRAME_LISTENER);
        reader.readResponseHead(_ -> {
        });
        InputStream inputStream = reader.inputStreamWithTrailers();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread readThread = Thread.ofPlatform().start(() -> {
            try {
                inputStream.read();
            } catch (Throwable t) {
                failure.set(t);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (readThread.getState() != Thread.State.WAITING
                    && readThread.isAlive()
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(readThread.getState(), is(Thread.State.WAITING));

            readThread.interrupt();
            readThread.join(5_000);

            assertThat(readThread.isAlive(), is(false));
            assertThat(interrupted.get(), is(true));
            assertThat(failure.get(), instanceOf(IOException.class));
            assertThat(failure.get().getMessage(), equalTo("Interrupted while waiting for QPACK dynamic entries"));
            assertThat(failure.get().getCause(), instanceOf(Http3QpackContext.DecodingInterruptedException.class));
            assertThat(failure.get().getCause().getCause(), instanceOf(InterruptedException.class));
        } finally {
            readThread.interrupt();
            reader.close();
            readThread.join(5_000);
        }
    }

    @Test
    void shouldNotTranslateFrameListenerFailureWhenReaderCloses() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame("payload".getBytes(StandardCharsets.UTF_8));
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(dataFrame)));
        AtomicReference<Http3MessageReader> readerReference = new AtomicReference<>();
        IllegalStateException listenerFailure = new IllegalStateException("listener failed");
        Http3FrameListener listener = new Http3FrameListener() {
            @Override
            public void frameHeader(SocketContext context,
                                    long streamId,
                                    long frameType,
                                    long frameLength,
                                    int encodedLength) {
                if (frameType == Http3Protocol.FRAME_DATA) {
                    readerReference.get().close();
                    throw listenerFailure;
                }
            }
        };
        Http3MessageReader reader = responseReader(stream, listener);
        readerReference.set(reader);
        reader.readResponseHead(_ -> {
        });
        InputStream inputStream = reader.inputStreamWithTrailers();

        IllegalStateException failure = assertThrows(IllegalStateException.class, inputStream::read);

        assertThat(failure, sameInstance(listenerFailure));
    }

    @Test
    void shouldWakeBlockedMessageReadWhenReaderCloses() throws Exception {
        FakeReceiverStream stream = new FakeReceiverStream(List.of());
        Http3MessageReader reader = responseReader(stream);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread readThread = Thread.ofPlatform().start(() -> {
            try {
                reader.readResponseHead(_ -> {
                });
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (readThread.getState() != Thread.State.WAITING
                    && readThread.isAlive()
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(readThread.getState(), is(Thread.State.WAITING));
        } finally {
            reader.close();
        }

        readThread.join(5_000);
        assertThat(readThread.isAlive(), is(false));
        assertThat(failure.get(), instanceOf(IllegalStateException.class));
        assertThat(failure.get().getMessage(), equalTo("HTTP/3 stream reader is closed."));
    }

    @Test
    void shouldSerializeCloseWithInFlightBufferRead() throws Exception {
        BlockingReadBuffer buffer = new BlockingReadBuffer(
                BufferData.create(Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create())));
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(buffer));
        Http3MessageReader reader = responseReader(stream);
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        Thread readThread = Thread.ofPlatform().start(() -> {
            try {
                reader.readResponseHead(_ -> {
                });
            } catch (Throwable t) {
                readFailure.set(t);
            }
        });
        assertThat(buffer.readStarted.await(5, TimeUnit.SECONDS), is(true));

        Thread closeThread = Thread.ofPlatform().start(reader::close);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (closeThread.getState() != Thread.State.WAITING
                && closeThread.isAlive()
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat("Close should wait until the current buffer operation releases ownership",
                   closeThread.getState(),
                   is(Thread.State.WAITING));

        buffer.allowRead.countDown();
        readThread.join(5_000);
        closeThread.join(5_000);

        assertThat(readThread.isAlive(), is(false));
        assertThat(closeThread.isAlive(), is(false));
        assertThat(readFailure.get(), anyOf(nullValue(),
                                            instanceOf(CancellationException.class),
                                            instanceOf(IllegalStateException.class)));
        assertThat(stream.disconnected(), is(true));
    }

    @Test
    void shouldSerializeCloseWithTransportPollPublication() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame)));
        Http3MessageReader reader = responseReader(stream);
        CountDownLatch pollStarted = new CountDownLatch(1);
        CountDownLatch allowPoll = new CountDownLatch(1);
        stream.reader.blockNextPoll(pollStarted, allowPoll);
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        Thread readThread = Thread.ofPlatform().start(() -> {
            try {
                reader.readResponseHead(_ -> {
                });
            } catch (Throwable t) {
                readFailure.set(t);
            }
        });
        assertThat(pollStarted.await(5, TimeUnit.SECONDS), is(true));

        Thread closeThread = Thread.ofPlatform().start(reader::close);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (closeThread.getState() != Thread.State.WAITING
                && closeThread.isAlive()
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat("Close must not inspect EOF before the polled buffer is published",
                   closeThread.getState(),
                   is(Thread.State.WAITING));

        allowPoll.countDown();
        readThread.join(5_000);
        closeThread.join(5_000);

        assertThat(readThread.isAlive(), is(false));
        assertThat(closeThread.isAlive(), is(false));
        assertThat(readFailure.get(), anyOf(nullValue(),
                                            instanceOf(CancellationException.class),
                                            instanceOf(IllegalStateException.class)));
        assertThat(stream.disconnected(), is(true));
    }

    @Test
    void shouldPreserveQueuedEndOfStreamWhenReaderCloses() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame("complete".getBytes(StandardCharsets.UTF_8));
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(dataFrame)));
        Http3MessageReader reader = responseReader(stream);
        reader.readResponseHead(_ -> {
        });
        InputStream inputStream = reader.inputStreamWithTrailers();

        assertThat(new String(inputStream.readNBytes(8), StandardCharsets.UTF_8), equalTo("complete"));
        inputStream.close();

        assertThat(reader.messageComplete(), is(true));
    }

    @Test
    void shouldNotifyFrameListenerWhileReadingMessage() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame("hello".getBytes(StandardCharsets.UTF_8));
        byte[] trailersFrame = Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                        .add(HeaderValues.create("x-trailer", "done")));
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(dataFrame),
                                                                      BufferData.create(trailersFrame)));
        AtomicInteger frameHeaders = new AtomicInteger();
        AtomicInteger frameData = new AtomicInteger();
        List<Boolean> finalChunks = new ArrayList<>();
        Headers[] loggedTrailers = new Headers[1];
        Http3FrameListener listener = new Http3FrameListener() {
            @Override
            public void frameHeader(SocketContext context,
                                    long streamId,
                                    long frameType,
                                    long frameLength,
                                    int encodedLength) {
                assertThat(context, is(Http3TestSocketContext.INSTANCE));
                assertThat(streamId, is(stream.streamId()));
                frameHeaders.incrementAndGet();
            }

            @Override
            public void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
                assertThat(context, is(Http3TestSocketContext.INSTANCE));
                assertThat(streamId, is(stream.streamId()));
                frameData.incrementAndGet();
                finalChunks.add(last);
            }

            @Override
            public void trailers(SocketContext context, long streamId, Headers trailers) {
                assertThat(context, is(Http3TestSocketContext.INSTANCE));
                assertThat(streamId, is(stream.streamId()));
                loggedTrailers[0] = trailers;
            }
        };
        Http3MessageReader reader = responseReader(stream, listener);

        reader.readResponseHead(_ -> {
        });
        assertThat(readEntityWithTrailers(reader, 2), equalTo("hello"));

        assertThat(frameHeaders.get(), is(3));
        assertThat(frameData.get(), is(5));
        assertThat(finalChunks, equalTo(List.of(true, false, false, true, true)));
        assertThat(loggedTrailers[0].first(HeaderNames.create("x-trailer")).orElseThrow(), equalTo("done"));
    }

    @Test
    void shouldTrackSplitOutboundFramesWithoutRawPayloadCopies() throws Exception {
        RecordingFrameListener listener = new RecordingFrameListener(false);
        FakeSenderStream stream = new FakeSenderStream();
        QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream,
                                                                   Http3TestSocketContext.INSTANCE,
                                                                   listener);

        writer.queueForWriting(BufferData.create(new byte[] {0x40}));
        writer.queueForWriting(BufferData.create(new byte[] {0x00, (byte) 0x80}));
        writer.queueForWriting(BufferData.create(new byte[] {0x00, 0x00}));
        writer.queueForWriting(BufferData.create(new byte[] {0x03, 'a'}));
        writer.queueForWriting(BufferData.create(new byte[] {'b', 'c', 0x01}));
        writer.scheduleForWriting(BufferData.create(new byte[] {0x02, 'x', 'y'}), true);

        assertThat(listener.frameTypes, equalTo(List.of(Http3Protocol.FRAME_DATA, Http3Protocol.FRAME_HEADERS)));
        assertThat(listener.frameLengths, equalTo(List.of(3L, 2L)));
        assertThat(listener.headerLengths, equalTo(List.of(6, 2)));
        assertThat(listener.frameDataLengths, equalTo(List.of(1, 2, 2)));
        assertThat(listener.finalChunks, equalTo(List.of(false, true, true)));
        assertThat(listener.rawHeaders, equalTo(List.of()));
        assertThat(listener.rawFrameData, equalTo(List.of()));
    }

    @Test
    void shouldPreserveSplitNonMinimalOutboundFrameHeaderBytes() throws Exception {
        RecordingFrameListener listener = new RecordingFrameListener(true);
        FakeSenderStream stream = new FakeSenderStream();
        QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream,
                                                                   Http3TestSocketContext.INSTANCE,
                                                                   listener);
        byte[] header = new byte[] {0x40, 0x00, (byte) 0x80, 0x00, 0x00, 0x03};

        writer.queueForWriting(BufferData.create(new byte[] {header[0]}));
        writer.queueForWriting(BufferData.create(new byte[] {header[1], header[2], header[3]}));
        writer.scheduleForWriting(BufferData.create(new byte[] {header[4], header[5], 'a', 'b', 'c'}), true);

        assertThat(listener.rawHeaders.size(), is(1));
        assertThat(listener.rawHeaders.getFirst(), equalTo(header));
        assertThat(concat(listener.rawFrameData.toArray(byte[][]::new)), equalTo("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldKeepOutboundFrameStateWhileListenerIsDisabled() throws Exception {
        RecordingFrameListener listener = new RecordingFrameListener(false);
        listener.enabled(false);
        FakeSenderStream stream = new FakeSenderStream();
        QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream,
                                                                   Http3TestSocketContext.INSTANCE,
                                                                   listener);

        writer.queueForWriting(BufferData.create(new byte[] {0x00, 0x03, 'a'}));
        listener.enabled(true);
        writer.scheduleForWriting(BufferData.create(new byte[] {'b', 'c', 0x01, 0x00}), true);

        assertThat(listener.frameTypes, equalTo(List.of(Http3Protocol.FRAME_HEADERS)));
        assertThat(listener.frameLengths, equalTo(List.of(0L)));
        assertThat(listener.frameDataLengths, equalTo(List.of(2, 0)));
        assertThat(listener.finalChunks, equalTo(List.of(true, true)));
    }

    @Test
    void shouldBoundUnsafeOutboundPayloadChunks() throws Exception {
        RecordingFrameListener listener = new RecordingFrameListener(true);
        FakeSenderStream stream = new FakeSenderStream();
        QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream,
                                                                   Http3TestSocketContext.INSTANCE,
                                                                   listener);
        byte[] payload = new byte[20 * 1024];

        writer.scheduleForWriting(BufferData.create(Http3Protocol.encodeDataFrame(payload)), true);

        assertThat(listener.rawFrameData.stream().map(it -> it.length).toList(),
                   equalTo(List.of(8 * 1024, 8 * 1024, 4 * 1024)));
        assertThat(listener.finalChunks, equalTo(List.of(false, false, true)));
    }

    @Test
    void shouldPreserveSplitNonMinimalInboundFrameHeaderBytes() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataHeader = new byte[] {0x40, 0x00, 0x40, 0x03};
        byte[] dataFrame = concat(dataHeader, "abc".getBytes(StandardCharsets.UTF_8));
        byte[] trailersFrame = Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                        .add(HeaderValues.create("x-end", "yes")));
        byte[] message = concat(headersFrame, dataFrame, trailersFrame);
        RecordingFrameListener listener = new RecordingFrameListener(true);
        FakeReceiverStream stream = FakeReceiverStream.create(splitBytes(message, 1));
        Http3MessageReader reader = responseReader(stream, listener);

        reader.readResponseHead(_ -> {
        });
        assertThat(readEntityWithTrailers(reader, 2), equalTo("abc"));

        assertThat(listener.frameTypes,
                   equalTo(List.of(Http3Protocol.FRAME_HEADERS,
                                   Http3Protocol.FRAME_DATA,
                                   Http3Protocol.FRAME_HEADERS)));
        assertThat(listener.rawHeaders.get(1), equalTo(dataHeader));
        assertThat(listener.headerLengths.get(1), is(dataHeader.length));
    }

    @Test
    void shouldRecheckRawLoggingAfterReadingInboundHeader() throws Exception {
        byte[] leadingHeader = new byte[] {0x40, 0x21, 0x40, 0x00};
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        List<byte[]> rawHeaders = new ArrayList<>();
        List<Integer> frameDataLengths = new ArrayList<>();
        List<byte[]> rawFrameData = new ArrayList<>();
        Http3FrameListener listener = new Http3FrameListener() {
            private boolean rawDataEnabled;

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
                rawDataEnabled = true;
            }

            @Override
            public void rawFrameHeader(SocketContext context, long streamId, byte[] data) {
                rawHeaders.add(data);
            }

            @Override
            public void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
                frameDataLengths.add(byteCount);
            }

            @Override
            public void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
                rawFrameData.add(data);
            }
        };
        byte[] message = concat(leadingHeader, headersFrame);
        FakeReceiverStream stream = FakeReceiverStream.create(splitBytes(message, 1));
        Http3MessageReader reader = responseReader(stream, listener);

        reader.readResponseHead(_ -> {
        });

        assertThat(rawHeaders.getFirst(), equalTo(leadingHeader));
        assertThat(frameDataLengths.getFirst(), is(0));
        assertThat(rawFrameData.getFirst(), equalTo(new byte[0]));
    }

    @Test
    void shouldNotifyListenerForEmptyDataFrame() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] emptyDataFrame = new byte[] {0x00, 0x00};
        byte[] dataFrame = Http3Protocol.encodeDataFrame(new byte[] {'x'});
        RecordingFrameListener listener = new RecordingFrameListener(true);
        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(emptyDataFrame),
                                                                      BufferData.create(dataFrame)));
        Http3MessageReader reader = responseReader(stream, listener);
        reader.readResponseHead(_ -> {
        });
        listener.frameDataLengths.clear();
        listener.finalChunks.clear();
        listener.rawFrameData.clear();

        assertThat(readEntityWithTrailers(reader, 2), equalTo("x"));

        assertThat(listener.frameDataLengths, equalTo(List.of(0, 1)));
        assertThat(listener.finalChunks, equalTo(List.of(true, true)));
        assertThat(listener.rawFrameData.stream().map(it -> it.length).toList(), equalTo(List.of(0, 1)));
    }

    @Test
    void shouldRejectSecondEntityStreamAfterTrailerAwareInputStream() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        byte[] dataFrame = Http3Protocol.encodeDataFrame("hello".getBytes(StandardCharsets.UTF_8));

        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(dataFrame)));
        Http3MessageReader reader = responseReader(stream);
        reader.readResponseHead(_ -> {
        });

        reader.inputStreamWithTrailers();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       reader::inputStreamWithTrailers);
        assertThat(exception.getMessage(), equalTo("HTTP/3 entity stream has already been requested."));
    }

    @Test
    void shouldCancelDynamicSectionRejectedAtEndOfStream() throws Exception {
        DynamicResponse dynamicResponse = dynamicResponse();
        Http3QpackContext localDecoder = qpackContext(128, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        localDecoder.decoderInstructionsSender(decoderInstructions::add);
        for (byte[] instruction : dynamicResponse.encoderInstructions()) {
            localDecoder.onEncoderStreamData(instruction);
        }
        decoderInstructions.clear();

        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(dynamicResponse.headersFrame())));
        Http3MessageReader reader = responseReader(stream, localDecoder, 1, NO_OP_FRAME_LISTENER);

        Http3ProtocolException failure = assertThrows(Http3ProtocolException.class,
                                                      () -> reader.readResponseHead(_ -> {
                                                      }));
        assertThat(failure.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));

        reader.close();

        assertThat(decoderInstructions, hasSize(1));
        assertThat(decoderInstructions.getFirst(), equalTo(new byte[] {0x40}));
    }

    @Test
    void shouldCancelBlockedDynamicSectionOnRemoteReset() throws Exception {
        DynamicResponse dynamicResponse = dynamicResponse();
        Http3QpackContext localDecoder = qpackContext(128, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        localDecoder.decoderInstructionsSender(decoderInstructions::add);
        FakeReceiverStream stream = FakeReceiverStream.create(
                List.of(BufferData.create(dynamicResponse.headersFrame())));
        Http3MessageReader reader = responseReader(stream, localDecoder, 16_384, NO_OP_FRAME_LISTENER);
        CompletableFuture<Http3MessageReader.ResponseHead> decoded = new CompletableFuture<>();
        Thread decodeThread = Thread.ofPlatform().start(() -> {
            try {
                decoded.complete(reader.readResponseHead(_ -> {
                }));
            } catch (Throwable t) {
                decoded.completeExceptionally(t);
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (decodeThread.isAlive()
                && decodeThread.getState() != Thread.State.WAITING
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(decodeThread.getState(), is(Thread.State.WAITING));

        stream.reset();
        stream.reset();

        assertThrows(CancellationException.class, decoded::join);
        assertThat(decoderInstructions, hasSize(1));
        assertThat(decoderInstructions.getFirst(), equalTo(new byte[] {0x40}));

        for (byte[] instruction : dynamicResponse.encoderInstructions()) {
            localDecoder.onEncoderStreamData(instruction);
        }

        assertThat(decoderInstructions.stream()
                           .filter(instruction -> Arrays.equals(instruction, new byte[] {(byte) 0x80}))
                           .count(),
                   equalTo(0L));
        reader.close();
    }

    @Test
    void shouldTimeoutBlockedDynamicSection() {
        DynamicResponse dynamicResponse = dynamicResponse();
        Http3QpackContext localDecoder = qpackContext(128, 1);
        List<byte[]> decoderInstructions = new ArrayList<>();
        localDecoder.decoderInstructionsSender(decoderInstructions::add);
        FakeReceiverStream stream = FakeReceiverStream.create(
                List.of(BufferData.create(dynamicResponse.headersFrame())));
        Http3MessageReader reader = Http3MessageReader.response(stream,
                                                                localDecoder,
                                                                Http3TestSocketContext.INSTANCE,
                                                                Method.GET,
                                                                16_384,
                                                                Http3MessageReader.ResponseOptions.create(
                                                                        true,
                                                                        Duration.ofMillis(50),
                                                                        NO_OP_FRAME_LISTENER));
        reader.activateReadTimeout();

        UncheckedIOException failure = assertThrows(UncheckedIOException.class,
                                                     () -> reader.readResponseHead(_ -> {
                                                     }));

        assertThat(failure.getCause(), instanceOf(SocketTimeoutException.class));
        assertThat(decoderInstructions, hasSize(1));
        assertThat(decoderInstructions.getFirst(), equalTo(new byte[] {0x40}));
        reader.close();
    }

    @Test
    void shouldRejectDataBeforeHeadersOnRequestStream() {
        byte[] dataFrame = Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8));
        byte[] headersFrame = requestHeadersFrame();

        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(dataFrame),
                                                                      BufferData.create(headersFrame)));
        Http3MessageReader reader = requestReader(stream);

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class, reader::readRequestHead);

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectCancelPushOnRequestStream() throws Exception {
        byte[] headersFrame = requestHeadersFrame();
        byte[] cancelPushFrame = cancelPushFrame(0);

        FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                      BufferData.create(cancelPushFrame)));
        Http3MessageReader reader = requestReader(stream);
        reader.readRequestHead();

        Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                        () -> reader.readEntityDataWithTrailers(1));

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectSettingsAndGoAwayOnResponseStream() throws Exception {
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        BufferData settingsFrame = BufferData.create(VariableLengthEncoder.encodedSize(Http3Protocol.FRAME_SETTINGS)
                                                              + VariableLengthEncoder.encodedSize(0));
        VariableLengthEncoder.encode(settingsFrame, Http3Protocol.FRAME_SETTINGS);
        VariableLengthEncoder.encode(settingsFrame, 0);
        for (byte[] unexpectedFrame : List.of(settingsFrame.readBytes(),
                                              Http3Protocol.goAwayFrame(Http3GoAway.requestStream(0)))) {
            FakeReceiverStream stream = FakeReceiverStream.create(List.of(BufferData.create(headersFrame),
                                                                          BufferData.create(unexpectedFrame)));
            Http3MessageReader reader = responseReader(stream);
            reader.readResponseHead(_ -> {
            });

            Http3ProtocolException exception = assertThrows(Http3ProtocolException.class,
                                                            () -> reader.readEntityDataWithTrailers(1));

            assertThat(exception.errorCode(), equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
            assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectReservedHttp2FrameTypesOnMessageStreams() throws Exception {
        byte[] requestHeaders = requestHeadersFrame();
        byte[] responseHeaders = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
        for (long frameType : List.of(0x02L, 0x06L, 0x08L, 0x09L)) {
            BufferData frame = BufferData.create(VariableLengthEncoder.encodedSize(frameType)
                                                         + VariableLengthEncoder.encodedSize(0));
            VariableLengthEncoder.encode(frame, frameType);
            VariableLengthEncoder.encode(frame, 0);
            byte[] reservedFrame = frame.readBytes();

            Http3MessageReader requestReader = requestReader(FakeReceiverStream.create(
                    List.of(BufferData.create(reservedFrame), BufferData.create(requestHeaders))));
            Http3ProtocolException requestFailure =
                    assertThrows(Http3ProtocolException.class, requestReader::readRequestHead);
            assertThat("request frame type " + frameType,
                       requestFailure.errorCode(),
                       equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
            assertThat("request frame type " + frameType,
                       requestFailure.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));

            Http3MessageReader responseReader = responseReader(FakeReceiverStream.create(
                    List.of(BufferData.create(responseHeaders), BufferData.create(reservedFrame))));
            responseReader.readResponseHead(_ -> {
            });
            Http3ProtocolException responseFailure =
                    assertThrows(Http3ProtocolException.class,
                                 () -> responseReader.readEntityDataWithTrailers(1));
            assertThat("response frame type " + frameType,
                       responseFailure.errorCode(),
                       equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
            assertThat("response frame type " + frameType,
                       responseFailure.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    private static DynamicResponse dynamicResponse() {
        Http3QpackContext peerEncoder = qpackContext(0, 0);
        List<byte[]> encoderInstructions = new ArrayList<>();
        peerEncoder.encoderInstructionsSender(encoderInstructions::add);
        peerEncoder.peerSettings(128, 1);
        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create("x-dynamic", "value"));
        Http3Protocol.encodeResponseHeaders(peerEncoder, 0, 200, headers);
        Http3QpackContext primingDecoder = qpackContext(128, 1);
        primingDecoder.decoderInstructionsSender(peerEncoder::onDecoderStreamData);
        for (byte[] instruction : encoderInstructions) {
            primingDecoder.onEncoderStreamData(instruction);
        }
        return new DynamicResponse(Http3Protocol.encodeResponseHeaders(peerEncoder, 0, 200, headers),
                                   List.copyOf(encoderInstructions));
    }

    private static Http3QpackContext qpackContext(long maxTableCapacity, long blockedStreams) {
        return Http3QpackContext.create(maxTableCapacity, blockedStreams, 16_384, _ -> {
        });
    }

    private static Http3MessageReader responseReader(FakeReceiverStream stream) {
        return responseReader(stream, qpackContext(0, 0), 16_384, NO_OP_FRAME_LISTENER);
    }

    private static Http3MessageReader responseReader(FakeReceiverStream stream, Http3FrameListener listener) {
        return responseReader(stream, qpackContext(0, 0), 16_384, listener);
    }

    private static Http3MessageReader responseReader(FakeReceiverStream stream,
                                                     Http3QpackContext qpackContext,
                                                     int maxHeadersSize,
                                                     Http3FrameListener listener) {
        return Http3MessageReader.response(stream,
                                           qpackContext,
                                           Http3TestSocketContext.INSTANCE,
                                           Method.GET,
                                           maxHeadersSize,
                                           true,
                                           listener);
    }

    private static Http3MessageReader requestReader(FakeReceiverStream stream) {
        return Http3MessageReader.request(stream,
                                          qpackContext(0, 0),
                                          Http3TestSocketContext.INSTANCE,
                                          16_384,
                                          true,
                                          NO_OP_FRAME_LISTENER);
    }

    private static String readEntityWithTrailers(Http3MessageReader reader, int estimate) {
        BufferData output = BufferData.growing(128);
        while (true) {
            byte[] part = reader.readEntityDataWithTrailers(estimate);
            if (part.length == 0) {
                return new String(output.readBytes(), StandardCharsets.UTF_8);
            }
            output.write(part);
        }
    }

    private static List<BufferData> splitBytes(byte[] bytes, int chunkSize) {
        List<BufferData> buffers = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += chunkSize) {
            int end = Math.min(offset + chunkSize, bytes.length);
            buffers.add(BufferData.create(Arrays.copyOfRange(bytes, offset, end)));
        }
        return buffers;
    }

    private static byte[] concat(byte[]... arrays) {
        int length = Arrays.stream(arrays).mapToInt(it -> it.length).sum();
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    private static byte[] requestHeadersFrame() {
        return Http3Protocol.encodeRequestHeaders(qpackContext(0, 0),
                                                  0,
                                                  URI.create("https://example.com/hello"),
                                                  "GET",
                                                  WritableHeaders.create());
    }

    private static byte[] cancelPushFrame(long pushId) {
        BufferData payload = BufferData.create(VariableLengthEncoder.encodedSize(pushId));
        VariableLengthEncoder.encode(payload, pushId);
        BufferData frame = BufferData.create(VariableLengthEncoder.encodedSize(Http3Protocol.FRAME_CANCEL_PUSH)
                                                     + VariableLengthEncoder.encodedSize(payload.available())
                                                     + payload.available());
        VariableLengthEncoder.encode(frame, Http3Protocol.FRAME_CANCEL_PUSH);
        VariableLengthEncoder.encode(frame, payload.available());
        frame.write(payload);
        return frame.readBytes();
    }

    private record DynamicResponse(byte[] headersFrame, List<byte[]> encoderInstructions) {
    }

    private static final class RecordingFrameListener implements Http3FrameListener {
        private final boolean rawDataEnabled;
        private final List<Long> frameTypes = new ArrayList<>();
        private final List<Long> frameLengths = new ArrayList<>();
        private final List<Integer> headerLengths = new ArrayList<>();
        private final List<Integer> frameDataLengths = new ArrayList<>();
        private final List<Boolean> finalChunks = new ArrayList<>();
        private final List<byte[]> rawHeaders = new ArrayList<>();
        private final List<byte[]> rawFrameData = new ArrayList<>();
        private boolean enabled = true;

        private RecordingFrameListener(boolean rawDataEnabled) {
            this.rawDataEnabled = rawDataEnabled;
        }

        @Override
        public boolean rawDataEnabled() {
            return enabled && rawDataEnabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void frameHeader(SocketContext context,
                                long streamId,
                                long frameType,
                                long frameLength,
                                int encodedLength) {
            frameTypes.add(frameType);
            frameLengths.add(frameLength);
            headerLengths.add(encodedLength);
        }

        @Override
        public void rawFrameHeader(SocketContext context, long streamId, byte[] data) {
            rawHeaders.add(data);
        }

        @Override
        public void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
            frameDataLengths.add(byteCount);
            finalChunks.add(last);
        }

        @Override
        public void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
            rawFrameData.add(data);
        }

        private void enabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private static final class FakeSenderStream implements QuicSenderStream {
        private FakeStreamWriter writer;

        @Override
        public SendingStreamState sendingState() {
            return SendingStreamState.SEND;
        }

        @Override
        public QuicStreamWriter connectWriter(SequentialScheduler scheduler) {
            if (writer != null && writer.connected()) {
                throw new IllegalStateException("Writer already connected.");
            }
            writer = new FakeStreamWriter(this, scheduler);
            return writer;
        }

        @Override
        public void disconnectWriter(QuicStreamWriter writer) {
            if (this.writer != writer || !this.writer.connected()) {
                throw new IllegalStateException("Writer is not connected.");
            }
            this.writer.disconnect();
        }

        @Override
        public void reset(long errorCode) {
            // No-op for test harness.
        }

        @Override
        public long dataSent() {
            return 0;
        }

        @Override
        public long sndErrorCode() {
            return -1;
        }

        @Override
        public boolean stopSendingReceived() {
            return false;
        }

        @Override
        public CompletionStage<Long> whenStopSendingReceived() {
            return new CompletableFuture<>();
        }

        @Override
        public CompletableFuture<SendingStreamState> futureSendingCompletion() {
            return new CompletableFuture<>();
        }

        @Override
        public long streamId() {
            return 0;
        }

        @Override
        public StreamMode mode() {
            return StreamMode.WRITE_ONLY;
        }

        @Override
        public boolean isClientInitiated() {
            return true;
        }

        @Override
        public boolean isServerInitiated() {
            return false;
        }

        @Override
        public boolean isBidirectional() {
            return false;
        }

        @Override
        public boolean isLocalInitiated() {
            return true;
        }

        @Override
        public boolean isRemoteInitiated() {
            return false;
        }

        @Override
        public int type() {
            return 0x02;
        }

        @Override
        public StreamState state() {
            return sendingState();
        }
    }

    private static final class FakeStreamWriter extends QuicStreamWriter {
        private final FakeSenderStream stream;
        private boolean connected = true;

        private FakeStreamWriter(FakeSenderStream stream, SequentialScheduler scheduler) {
            super(scheduler);
            this.stream = stream;
        }

        @Override
        public QuicSenderStream.SendingStreamState sendingState() {
            return stream.sendingState();
        }

        @Override
        public void scheduleForWriting(BufferData buffer, boolean last) {
            // No-op for test harness.
        }

        @Override
        public CompletableFuture<Void> scheduleForWritingAndGetDispatchCompletion(BufferData buffer, boolean last) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void queueForWriting(BufferData buffer) {
            // No-op for test harness.
        }

        @Override
        public long credit() {
            return Long.MAX_VALUE;
        }

        @Override
        public void reset(long errorCode) {
            // No-op for test harness.
        }

        @Override
        public Optional<QuicSenderStream> stream() {
            return connected ? Optional.of(stream) : Optional.empty();
        }

        @Override
        public boolean connected() {
            return connected;
        }

        private void disconnect() {
            connected = false;
        }
    }

    private static final class BlockingReadBuffer implements BufferData {
        private final BufferData delegate;
        private final CountDownLatch readStarted = new CountDownLatch(1);
        private final CountDownLatch allowRead = new CountDownLatch(1);
        private final AtomicBoolean blockFirstRead = new AtomicBoolean(true);

        private BlockingReadBuffer(BufferData delegate) {
            this.delegate = delegate;
        }

        @Override
        public BufferData reset() {
            delegate.reset();
            return this;
        }

        @Override
        public BufferData rewind() {
            delegate.rewind();
            return this;
        }

        @Override
        public BufferData clear() {
            delegate.clear();
            return this;
        }

        @Override
        public void writeTo(OutputStream out) {
            delegate.writeTo(out);
        }

        @Override
        public int readFrom(InputStream in) {
            return delegate.readFrom(in);
        }

        @Override
        public int readFrom(ByteBuffer buffer) {
            return delegate.readFrom(buffer);
        }

        @Override
        public int read() {
            if (blockFirstRead.compareAndSet(true, false)) {
                readStarted.countDown();
                try {
                    if (!allowRead.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release buffer read.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to release buffer read.", e);
                }
            }
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int position, int length) {
            return delegate.read(bytes, position, length);
        }

        @Override
        public String readString(int length, Charset charset) {
            return delegate.readString(length, charset);
        }

        @Override
        public boolean consumed() {
            return delegate.consumed();
        }

        @Override
        public BufferData write(int value) {
            delegate.write(value);
            return this;
        }

        @Override
        public int writeTo(ByteBuffer writeBuffer, int length) {
            return delegate.writeTo(writeBuffer, length);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            delegate.write(bytes, offset, length);
        }

        @Override
        public void write(BufferData toWrite, int length) {
            delegate.write(toWrite, length);
        }

        @Override
        public String debugDataBinary() {
            return delegate.debugDataBinary();
        }

        @Override
        public String debugDataHex(boolean fullBuffer) {
            return delegate.debugDataHex(fullBuffer);
        }

        @Override
        public int available() {
            return delegate.available();
        }

        @Override
        public void skip(int length) {
            delegate.skip(length);
        }

        @Override
        public int indexOf(byte aByte) {
            return delegate.indexOf(aByte);
        }

        @Override
        public int lastIndexOf(byte aByte, int length) {
            return delegate.lastIndexOf(aByte, length);
        }

        @Override
        public BufferData trim(int length) {
            delegate.trim(length);
            return this;
        }

        @Override
        public int capacity() {
            return delegate.capacity();
        }

        @Override
        public int get(int index) {
            return delegate.get(index);
        }
    }

    private static final class FakeReceiverStream implements QuicReceiverStream {
        private final List<BufferData> buffers;
        private final long dataReceived;
        private FakeStreamReader reader;
        private boolean disconnected;
        private ReceivingStreamState receivingState = ReceivingStreamState.RECV;

        private FakeReceiverStream(List<BufferData> buffers) {
            this.buffers = buffers;
            this.dataReceived = buffers.stream()
                    .filter(it -> it != QuicStreamReader.EOF)
                    .mapToLong(BufferData::available)
                    .sum();
        }

        private static FakeReceiverStream create(List<BufferData> buffers) {
            List<BufferData> input = new ArrayList<>(buffers.size() + 1);
            input.addAll(buffers);
            input.add(QuicStreamReader.EOF);
            return new FakeReceiverStream(input);
        }

        @Override
        public ReceivingStreamState receivingState() {
            return disconnected ? ReceivingStreamState.DATA_READ : receivingState;
        }

        @Override
        public QuicStreamReader connectReader(SequentialScheduler scheduler) {
            if (reader != null && reader.connected()) {
                throw new IllegalStateException("Reader already connected.");
            }
            reader = new FakeStreamReader(this, scheduler, buffers);
            return reader;
        }

        @Override
        public void disconnectReader(QuicStreamReader reader) {
            if (this.reader != reader || this.reader == null || !this.reader.connected()) {
                throw new IllegalStateException("Reader is not connected.");
            }
            disconnected = true;
            this.reader.disconnect();
        }

        @Override
        public void requestStopSending(long errorCode) {
            // No-op for test harness.
        }

        @Override
        public long dataReceived() {
            return dataReceived;
        }

        @Override
        public long maxStreamData() {
            return dataReceived;
        }

        @Override
        public long rcvErrorCode() {
            return -1;
        }

        @Override
        public long streamId() {
            return 0;
        }

        @Override
        public StreamMode mode() {
            return StreamMode.READ_ONLY;
        }

        @Override
        public boolean isClientInitiated() {
            return true;
        }

        @Override
        public boolean isServerInitiated() {
            return false;
        }

        @Override
        public boolean isBidirectional() {
            return false;
        }

        @Override
        public boolean isLocalInitiated() {
            return false;
        }

        @Override
        public boolean isRemoteInitiated() {
            return true;
        }

        @Override
        public int type() {
            return 0x02;
        }

        @Override
        public QuicStream.StreamState state() {
            return receivingState();
        }

        private boolean disconnected() {
            return disconnected;
        }

        private int pollCount() {
            return reader == null ? 0 : reader.pollCount();
        }

        private void reset() {
            receivingState = ReceivingStreamState.RESET_RECVD;
            reader.scheduler.runOrSchedule();
        }
    }

    private static final class FakeStreamReader extends QuicStreamReader {
        private final FakeReceiverStream stream;
        private final SequentialScheduler scheduler;
        private final List<BufferData> buffers;
        private int index;
        private boolean connected = true;
        private boolean started;
        private int pollCount;
        private CountDownLatch pollStarted;
        private CountDownLatch allowPoll;

        private FakeStreamReader(FakeReceiverStream stream,
                                 SequentialScheduler scheduler,
                                 List<BufferData> buffers) {
            super(scheduler);
            this.stream = stream;
            this.scheduler = scheduler;
            this.buffers = buffers;
        }

        @Override
        public QuicReceiverStream.ReceivingStreamState receivingState() {
            return stream.receivingState();
        }

        @Override
        public Optional<BufferData> poll() {
            ensureConnected();
            if (!started || index >= buffers.size()) {
                return Optional.empty();
            }
            pollCount++;
            BufferData buffer = buffers.get(index++);
            CountDownLatch pollStarted = this.pollStarted;
            if (pollStarted != null) {
                this.pollStarted = null;
                pollStarted.countDown();
                try {
                    if (!allowPoll.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to complete transport poll.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to complete transport poll.", e);
                }
            }
            return Optional.of(buffer);
        }

        @Override
        public Optional<BufferData> peek() {
            ensureConnected();
            if (!started || index >= buffers.size()) {
                return Optional.empty();
            }
            return Optional.of(buffers.get(index));
        }

        @Override
        public Optional<QuicReceiverStream> stream() {
            return connected ? Optional.of(stream) : Optional.empty();
        }

        @Override
        public boolean connected() {
            return connected;
        }

        @Override
        public boolean started() {
            return started;
        }

        @Override
        public void start() {
            started = true;
            scheduler.runOrSchedule();
        }

        private void disconnect() {
            connected = false;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("Reader not connected.");
            }
        }

        private int pollCount() {
            return pollCount;
        }

        private void blockNextPoll(CountDownLatch pollStarted, CountDownLatch allowPoll) {
            this.pollStarted = pollStarted;
            this.allowPoll = allowPoll;
        }
    }
}
