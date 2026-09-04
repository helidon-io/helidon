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

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks concurrent retirement and MAX_STREAMS limit advancement.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class QuicMaxStreamsJmhBenchmark {
    /**
     * Retires one remotely initiated stream and attempts to advertise the resulting credit.
     *
     * @param state shared stream-credit state
     * @param blackhole result consumer
     */
    @Benchmark
    public void retireAndAdvance(SharedCreditState state, Blackhole blackhole) {
        QuicConnectionStreams.RemoteStreamCredit streamCredit = state.streamCredit;
        streamCredit.streamRetired();
        long nextLimit = streamCredit.nextLimit(true);
        boolean advanced = streamCredit.tryAdvanceLimit(nextLimit);
        blackhole.consume(nextLimit);
        blackhole.consume(advanced);
    }

    /**
     * Exercises a stale limit proposal which must leave the advertised limit unchanged.
     *
     * @param state shared stream-credit state
     * @param blackhole result consumer
     */
    @Benchmark
    public void staleLimit(SharedCreditState state, Blackhole blackhole) {
        QuicConnectionStreams.RemoteStreamCredit streamCredit = state.streamCredit;
        long currentLimit = streamCredit.currentLimit();
        boolean advanced = streamCredit.tryAdvanceLimit(currentLimit);
        long nextLimit = streamCredit.nextLimit(false);
        blackhole.consume(advanced);
        blackhole.consume(nextLimit);
    }

    /**
     * Polls both stream directions when neither has credit to advertise.
     *
     * @param state two-direction polling state
     * @param blackhole result consumer
     */
    @Benchmark
    public void pollTwoDirectionsNoCredit(TwoDirectionPollingState state, Blackhole blackhole) {
        boolean uniCreditAvailable = state.noCreditUni.nextLimit(false) > 0;
        boolean bidiCreditAvailable = state.noCreditBidi.nextLimit(false) > 0;
        blackhole.consume(uniCreditAvailable);
        blackhole.consume(bidiCreditAvailable);
    }

    /**
     * Polls both stream directions when each has pending credit below the batching threshold.
     *
     * @param state two-direction polling state
     * @param blackhole result consumer
     */
    @Benchmark
    public void pollTwoDirectionsSubThreshold(TwoDirectionPollingState state, Blackhole blackhole) {
        boolean uniCreditAvailable = state.subThresholdUni.nextLimit(false) > 0;
        boolean bidiCreditAvailable = state.subThresholdBidi.nextLimit(false) > 0;
        blackhole.consume(uniCreditAvailable);
        blackhole.consume(bidiCreditAvailable);
    }

    /**
     * One tracker shared by all benchmark threads.
     */
    @State(Scope.Benchmark)
    public static class SharedCreditState {
        /** Initial remotely initiated stream limit. */
        @Param({"64"})
        public long initialLimit;

        private QuicConnectionStreams.RemoteStreamCredit streamCredit;

        /**
         * Creates an initialized credit tracker for the trial.
         */
        @Setup(Level.Trial)
        public void setUp() {
            streamCredit = new QuicConnectionStreams.RemoteStreamCredit();
            streamCredit.initialize(initialLimit);
        }
    }

    /**
     * Two independent direction trackers for the steady negative and pending-credit polling paths.
     */
    @State(Scope.Benchmark)
    public static class TwoDirectionPollingState {
        /** Initial remotely initiated stream limit. */
        @Param({"64"})
        public long initialLimit;

        private QuicConnectionStreams.RemoteStreamCredit noCreditUni;
        private QuicConnectionStreams.RemoteStreamCredit noCreditBidi;
        private QuicConnectionStreams.RemoteStreamCredit subThresholdUni;
        private QuicConnectionStreams.RemoteStreamCredit subThresholdBidi;

        /**
         * Initializes each direction and retires one stream for the sub-threshold pair.
         */
        @Setup(Level.Trial)
        public void setUp() {
            if ((initialLimit >> 2) <= 1) {
                throw new IllegalArgumentException("initialLimit must be at least 8");
            }
            noCreditUni = new QuicConnectionStreams.RemoteStreamCredit();
            noCreditBidi = new QuicConnectionStreams.RemoteStreamCredit();
            subThresholdUni = new QuicConnectionStreams.RemoteStreamCredit();
            subThresholdBidi = new QuicConnectionStreams.RemoteStreamCredit();
            noCreditUni.initialize(initialLimit);
            noCreditBidi.initialize(initialLimit);
            subThresholdUni.initialize(initialLimit);
            subThresholdBidi.initialize(initialLimit);
            subThresholdUni.streamRetired();
            subThresholdBidi.streamRetired();
        }
    }
}
