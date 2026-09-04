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

package io.helidon.quic;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import io.helidon.common.buffers.BufferData;
import io.helidon.quic.QuicConnectionImpl.ReassemblyBudget;
import io.helidon.quic.QuicTransportParameters.ParameterId;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.stream.QuicConnectionStreams;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicStream;
import io.helidon.quic.stream.QuicStreamReader;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Measures initial remote STREAM creation and publication through the real QUIC stream manager.
 *
 * <p>The publish-then-process cells reproduce the former connection sequence: create and publish the remote stream,
 * then apply its first STREAM frame. The process-then-publish cells use the combined production path which applies the
 * first frame before publishing the stream. Invocation setup creates a fresh real {@link QuicConnectionStreams} and
 * real stream implementation around a mocked connection owner, so connection construction and Mockito setup are not
 * part of the timed method.</p>
 *
 * <p>This benchmark is intended for the relative manager-path comparison. It does not measure absolute connection
 * ingress latency or the request-worker scheduling effect covered by the HTTP/3 initial-request handoff benchmark.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuicInitialRemoteStreamJmhBenchmark {
    private static final int CONCURRENT_CONNECTIONS = 8;

    /**
     * Measures one connection publishing a new stream before applying its first STREAM frame.
     *
     * @param state per-worker stream-manager state
     * @return payload bytes received by the target stream
     * @throws QuicTransportException if the real stream manager rejects the frame
     */
    @Benchmark
    @Threads(1)
    public long publishThenProcess(InitialStreamState state) throws QuicTransportException {
        return state.publishThenProcess();
    }

    /**
     * Measures concurrent independent connections publishing a new stream before applying its first STREAM frame.
     *
     * @param state per-worker stream-manager state
     * @return payload bytes received by the target stream
     * @throws QuicTransportException if the real stream manager rejects the frame
     */
    @Benchmark
    @Threads(CONCURRENT_CONNECTIONS)
    public long publishThenProcessConcurrent(InitialStreamState state) throws QuicTransportException {
        return state.publishThenProcess();
    }

    /**
     * Measures one connection applying a first STREAM frame before publishing the new stream.
     *
     * @param state per-worker stream-manager state
     * @return payload bytes received by the target stream
     * @throws QuicTransportException if the real stream manager rejects the frame
     */
    @Benchmark
    @Threads(1)
    public long processThenPublish(InitialStreamState state) throws QuicTransportException {
        return state.processThenPublish();
    }

    /**
     * Measures concurrent independent connections applying a first STREAM frame before publishing the new stream.
     *
     * @param state per-worker stream-manager state
     * @return payload bytes received by the target stream
     * @throws QuicTransportException if the real stream manager rejects the frame
     */
    @Benchmark
    @Threads(CONCURRENT_CONNECTIONS)
    public long processThenPublishConcurrent(InitialStreamState state) throws QuicTransportException {
        return state.processThenPublish();
    }

    /**
     * Fresh stream-manager state for each measured invocation.
     */
    @State(Scope.Thread)
    public static class InitialStreamState {
        private static final long STREAM_ID = 0;
        private static final int PAYLOAD_SIZE = 64;
        private static final ReassemblyBudget REASSEMBLY_BUDGET = new ReassemblyBudget() {
            @Override
            public boolean tryAcquire() {
                return true;
            }

            @Override
            public void release(int count) {
            }

            @Override
            public void close() {
            }
        };

        private byte[] payload;
        private QuicConfig config;
        private QuicTransportParameters localParameters;
        private QuicConnectionImpl connection;
        private QuicConnectionStreams streams;
        private StreamFrame frame;
        private Predicate<QuicReceiverStream> listener;
        private QuicReceiverStream publishedStream;
        private HandoffOrder order;
        private int publicationCount;
        private long visibleAtPublication;

        /**
         * Creates immutable input and transport configuration shared by the invocation fixtures.
         */
        @Setup(Level.Trial)
        public void setUpTrial() {
            payload = new byte[PAYLOAD_SIZE];
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (i * 31 + 7);
            }
            config = QuicConfig.builder()
                    .initialMaxStreamData(PAYLOAD_SIZE * 2L)
                    .maxBidiStreams(1)
                    .buildPrototype();
            localParameters = QuicTransportParameters.create();
            localParameters.intParameter(ParameterId.initial_max_stream_data_bidi_remote, PAYLOAD_SIZE * 2L);
            localParameters.intParameter(ParameterId.initial_max_streams_bidi, 1);
        }

        /**
         * Creates a fresh mocked connection owner and real stream manager outside the timed method.
         */
        @Setup(Level.Invocation)
        public void setUpInvocation() {
            if (streams != null || frame != null || order != null || publishedStream != null) {
                throw new IllegalStateException("Initial remote STREAM benchmark retained an earlier invocation");
            }
            connection = mock(QuicConnectionImpl.class);
            QuicInstance instance = mock(QuicInstance.class);
            when(instance.executor()).thenReturn(Runnable::run);
            when(connection.quicConfig()).thenReturn(config);
            when(connection.quicInstance()).thenReturn(instance);
            when(connection.isClientConnection()).thenReturn(false);
            when(connection.peerTransportParameters()).thenReturn(Optional.empty());
            when(connection.localTransportParameters()).thenReturn(Optional.of(localParameters));
            when(connection.newReassemblyBudget()).thenReturn(REASSEMBLY_BUDGET);

            streams = QuicConnectionStreams.create(connection);
            streams.newLocalTransportParameters(localParameters);
            listener = stream -> {
                publicationCount++;
                publishedStream = stream;
                visibleAtPublication = stream.dataReceived();
                return true;
            };
            if (!streams.addRemoteStreamListener(listener)) {
                throw new IllegalStateException("Could not install the initial remote STREAM benchmark listener");
            }
            frame = StreamFrame.create(STREAM_ID,
                                       0,
                                       payload.length,
                                       false,
                                       ByteBuffer.wrap(payload));
        }

        /**
         * Drains the real receiver and verifies publication timing and invocation-local cleanup.
         */
        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            QuicConnectionStreams currentStreams = streams;
            QuicReceiverStream currentStream = publishedStream;
            Predicate<QuicReceiverStream> currentListener = listener;
            HandoffOrder currentOrder = order;
            connection = null;
            streams = null;
            frame = null;
            listener = null;
            publishedStream = null;
            order = null;
            try {
                if (currentStreams == null || currentStream == null || currentListener == null || currentOrder == null) {
                    throw new IllegalStateException("Initial remote STREAM benchmark invocation was not completed");
                }
                if (publicationCount != 1) {
                    throw new IllegalStateException("Unexpected remote stream publications: " + publicationCount);
                }
                long expectedVisible = currentOrder == HandoffOrder.PROCESS_THEN_PUBLISH ? payload.length : 0;
                if (visibleAtPublication != expectedVisible) {
                    throw new IllegalStateException("Unexpected bytes visible at publication: expected=" + expectedVisible
                                                            + ", actual=" + visibleAtPublication);
                }
                if (currentStream.dataReceived() != payload.length
                        || currentStreams.findStream(STREAM_ID).orElseThrow() != currentStream) {
                    throw new IllegalStateException("Initial STREAM payload was not retained by the published stream");
                }
                drainAndValidate(currentStream);
                if (!currentStreams.removeRemoteStreamListener(currentListener)) {
                    throw new IllegalStateException("Initial remote STREAM benchmark listener was not removed");
                }
                int[] latePublications = new int[1];
                Predicate<QuicReceiverStream> lateListener = ignored -> {
                    latePublications[0]++;
                    return true;
                };
                if (!currentStreams.addRemoteStreamListener(lateListener)) {
                    throw new IllegalStateException("Could not install the cleanup probe listener");
                }
                currentStreams.removeRemoteStreamListener(lateListener);
                if (latePublications[0] != 0) {
                    throw new IllegalStateException("Initial remote STREAM remained queued after being claimed");
                }
            } finally {
                publicationCount = 0;
                visibleAtPublication = 0;
            }
        }

        private long publishThenProcess() throws QuicTransportException {
            begin(HandoffOrder.PUBLISH_THEN_PROCESS);
            QuicStream stream = streams.ensureRemoteStream(STREAM_ID, frame.typeField()).orElseThrow();
            streams.processIncomingFrame(stream, frame);
            return ((QuicReceiverStream) stream).dataReceived();
        }

        private long processThenPublish() throws QuicTransportException {
            begin(HandoffOrder.PROCESS_THEN_PUBLISH);
            streams.processInitialRemoteStreamFrame(frame);
            return publishedStream.dataReceived();
        }

        private void begin(HandoffOrder requestedOrder) {
            if (order != null) {
                throw new IllegalStateException("Initial remote STREAM benchmark invocation was already started");
            }
            order = requestedOrder;
        }

        private void drainAndValidate(QuicReceiverStream stream) {
            QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
            }));
            try {
                reader.start();
                BufferData data = reader.poll().orElseThrow();
                if (!Arrays.equals(payload, data.readBytes()) || reader.poll().isPresent()) {
                    throw new IllegalStateException("Initial remote STREAM benchmark payload was not drained exactly once");
                }
            } finally {
                stream.disconnectReader(reader);
            }
        }
    }

    private enum HandoffOrder {
        PUBLISH_THEN_PROCESS,
        PROCESS_THEN_PUBLISH
    }
}
