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

package io.helidon.quic.stream;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.QuicConnectionImpl.ReassemblyBudget;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.ThreadParams;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Benchmarks server ready-stream publication and consumption.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuicReadyStreamQueueJmhBenchmark {
    private static final int BATCH_STREAM_COUNT = 64;
    private static final int CONCURRENT_THREAD_COUNT = 8;

    /**
     * Publishes and consumes one batch on each of eight independent connections.
     *
     * @param state thread-local queue state
     * @return stream ID checksum
     */
    @Benchmark
    @Threads(CONCURRENT_THREAD_COUNT)
    @OperationsPerInvocation(BATCH_STREAM_COUNT)
    public long independentConnectionCycle(ThreadQueueState state) {
        return state.cycle();
    }

    /**
     * Models ID-set publication, stream lookup, and ready-queue insertion, then consumes one batch per connection.
     *
     * @param state thread-local queue state
     * @return stream ID checksum
     */
    @Benchmark
    @Threads(CONCURRENT_THREAD_COUNT)
    @OperationsPerInvocation(BATCH_STREAM_COUNT)
    public long idLookupNotificationCycle(ThreadQueueState state) {
        return state.idLookupCycle();
    }

    /**
     * Models direct sender publication with its ownership and state guards, then consumes one batch per connection.
     *
     * @param state thread-local queue state
     * @return stream ID checksum
     */
    @Benchmark
    @Threads(CONCURRENT_THREAD_COUNT)
    @OperationsPerInvocation(BATCH_STREAM_COUNT)
    public long directSenderNotificationCycle(ThreadQueueState state) {
        return state.directSenderCycle();
    }

    /**
     * Measures coalesced writer notifications from eight response workers sharing one connection.
     *
     * @param state shared ready queue with one pending stream per worker
     * @param cursor worker-local stream selection
     */
    @Benchmark
    @Threads(CONCURRENT_THREAD_COUNT)
    public void duplicateNotificationConcurrent(DuplicateQueueState state, ProducerCursor cursor) {
        state.ready.add(state.senders[cursor.senderIndex()]);
    }

    /**
     * Queue state local to one benchmark worker and one simulated connection.
     */
    @State(Scope.Thread)
    public static class ThreadQueueState {
        private QuicConnectionImpl connection;
        private QuicConnectionStreams.ReadyStreamCollection ready;
        private QuicBidiStreamImpl[] senders;
        private QuicSenderStreamImpl[] senderParts;
        private ConcurrentHashMap<Long, QuicBidiStreamImpl> streamsById;

        /**
         * Creates the reusable stream set and queue.
         */
        @Setup(Level.Trial)
        public void setUp() {
            connection = connection();
            ready = QuicConnectionStreams.serverReadyStreams();
            senders = senders(connection, BATCH_STREAM_COUNT);
            senderParts = new QuicSenderStreamImpl[senders.length];
            streamsById = new ConcurrentHashMap<>();
            for (int i = 0; i < senders.length; i++) {
                QuicBidiStreamImpl sender = senders[i];
                senderParts[i] = sender.senderPart();
                streamsById.put(sender.streamId(), sender);
            }
        }

        private long cycle() {
            for (int i = 0; i < senders.length; i++) {
                ready.add(senders[(i * 37) & (BATCH_STREAM_COUNT - 1)]);
            }
            return drain();
        }

        private long idLookupCycle() {
            for (int i = 0; i < senders.length; i++) {
                long streamId = senders[(i * 37) & (BATCH_STREAM_COUNT - 1)].streamId();
                for (long id : Set.of(streamId)) {
                    QuicStream stream = streamsById.get(id);
                    if (stream instanceof QuicSenderStream sender) {
                        ready.add(sender);
                    } else {
                        throw new IllegalStateException("Stream is not a sender: " + streamId);
                    }
                }
            }
            return drain();
        }

        private long directSenderCycle() {
            for (int i = 0; i < senderParts.length; i++) {
                QuicSenderStreamImpl sender = senderParts[(i * 37) & (BATCH_STREAM_COUNT - 1)];
                if (sender.connection() != connection) {
                    throw new IllegalStateException("Sender belongs to another connection");
                }
                if (sender.sendingState().isSending()) {
                    ready.add(sender);
                }
            }
            return drain();
        }

        private long drain() {
            long checksum = 0;
            for (int i = 0; i < senders.length; i++) {
                QuicStream stream = ready.poll();
                if (stream == null) {
                    throw new IllegalStateException("Ready-stream queue became empty during a complete batch");
                }
                checksum += stream.streamId();
            }
            if (!ready.isEmpty() || checksum != 8064) {
                throw new IllegalStateException("Ready-stream queue retained unexpected batch state: " + checksum);
            }
            return checksum;
        }
    }

    /**
     * One pending bidirectional response stream per concurrent notification worker.
     */
    @State(Scope.Benchmark)
    public static class DuplicateQueueState {
        private QuicConnectionStreams.ReadyStreamCollection ready;
        private QuicBidiStreamImpl[] senders;

        /**
         * Creates the shared queue and marks every worker's stream ready.
         */
        @Setup(Level.Trial)
        public void setUp() {
            ready = QuicConnectionStreams.serverReadyStreams();
            senders = senders(CONCURRENT_THREAD_COUNT);
            for (QuicBidiStreamImpl sender : senders) {
                ready.add(sender);
            }
        }
    }

    /**
     * Producer-local selection which avoids shared benchmark bookkeeping.
     */
    @State(Scope.Thread)
    public static class ProducerCursor {
        private int next;

        /**
         * Assigns a distinct initial stream residue to each producer.
         *
         * @param threadParams JMH worker metadata
         */
        @Setup(Level.Trial)
        public void setUp(ThreadParams threadParams) {
            next = threadParams.getThreadIndex() & (CONCURRENT_THREAD_COUNT - 1);
        }

        private int senderIndex() {
            return next;
        }
    }

    private static QuicBidiStreamImpl[] senders(int count) {
        return senders(connection(), count);
    }

    private static QuicConnectionImpl connection() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        ReassemblyBudget reassemblyBudget = mock(ReassemblyBudget.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.newReassemblyBudget()).thenReturn(reassemblyBudget);
        return connection;
    }

    private static QuicBidiStreamImpl[] senders(QuicConnectionImpl connection, int count) {
        QuicBidiStreamImpl[] senders = new QuicBidiStreamImpl[count];
        for (int i = 0; i < senders.length; i++) {
            senders[i] = new QuicBidiStreamImpl(connection, i * 4L, 100, 1 << 16);
        }
        return senders;
    }
}
