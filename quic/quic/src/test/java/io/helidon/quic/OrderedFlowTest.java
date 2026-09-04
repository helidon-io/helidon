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
import java.util.ArrayList;
import java.util.NoSuchElementException;

import io.helidon.quic.OrderedFlow.CryptoDataFlow;
import io.helidon.quic.OrderedFlow.ReassemblyBudget;
import io.helidon.quic.OrderedFlow.StreamDataFlow;
import io.helidon.quic.frame.CryptoFrame;
import io.helidon.quic.frame.StreamFrame;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderedFlowTest {
    private static final long STREAM_ID = 8;

    @Test
    void shouldDeliverInOrderFrameWithoutReassemblyCapacity() {
        TestBudget budget = new TestBudget(0);
        StreamDataFlow flow = StreamDataFlow.create(budget, STREAM_ID);

        StreamFrame frame = flow.receive(streamFrame(0, 1)).orElseThrow();

        assertThat(frame.offset(), is(0L));
        assertThat(flow.offset(), is(1L));
        assertThat(flow.isEmpty(), is(true));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldRetainOnePermitForDuplicateAndMergedFrames() {
        TestBudget budget = new TestBudget(2);
        StreamDataFlow flow = StreamDataFlow.create(budget, STREAM_ID);

        flow.receive(streamFrame(10, 2));
        flow.receive(streamFrame(10, 1));

        assertThat(flow.size(), is(1));
        assertThat(flow.buffered(), is(2L));
        assertThat(budget.retained(), is(1));

        flow.receive(streamFrame(14, 2));
        flow.receive(streamFrame(10, 6));

        assertThat(flow.size(), is(1));
        assertThat(flow.buffered(), is(6L));
        assertThat(budget.retained(), is(1));
    }

    @Test
    void shouldReleasePermitWhenHeadIsSlicedAndPolled() {
        TestBudget budget = new TestBudget(2);
        StreamDataFlow flow = StreamDataFlow.create(budget, STREAM_ID);

        flow.receive(streamFrame(2, 4));
        StreamFrame direct = flow.receive(streamFrame(0, 3)).orElseThrow();

        assertThat(direct.offset(), is(0L));
        assertThat(flow.size(), is(1));
        assertThat(flow.buffered(), is(3L));
        assertThat(budget.retained(), is(1));
        budget.rejectAcquisitions();

        StreamFrame polled = flow.poll().orElseThrow();

        assertThat(polled.offset(), is(3L));
        assertThat(polled.dataLength(), is(3));
        assertThat(flow.size(), is(0));
        assertThat(flow.buffered(), is(0L));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldReleaseCoveredNodesWhenDirectFrameAdvancesOffset() {
        TestBudget budget = new TestBudget(2);
        StreamDataFlow flow = StreamDataFlow.create(budget, STREAM_ID);
        flow.receive(streamFrame(2, 1));
        flow.receive(streamFrame(4, 1));
        budget.rejectAcquisitions();

        StreamFrame direct = flow.receive(streamFrame(0, 5)).orElseThrow();

        assertThat(direct.offset(), is(0L));
        assertThat(flow.offset(), is(5L));
        assertThat(flow.size(), is(0));
        assertThat(flow.buffered(), is(0L));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldRejectStreamGrowthWithoutMutatingTheQueue() {
        TestBudget budget = new TestBudget(2);
        StreamDataFlow flow = StreamDataFlow.create(budget, STREAM_ID);
        flow.receive(streamFrame(2, 1));
        flow.receive(streamFrame(4, 1));

        // A duplicate does not require another retained node at the limit.
        flow.receive(streamFrame(2, 1));
        QuicTransportException failure = assertThrows(QuicTransportException.class,
                                                       () -> flow.receive(streamFrame(6, 1)));

        assertThat(failure.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(failure.keySpace().orElseThrow(), is(QuicTLSEngine.KeySpace.ONE_RTT));
        assertThat(failure.sourceStreamId().orElseThrow(), is(STREAM_ID));
        assertThat(flow.size(), is(2));
        assertThat(flow.buffered(), is(2L));
        assertThat(budget.retained(), is(2));
    }

    @Test
    void shouldMapCryptoBudgetExhaustionToCryptoBufferExceeded() {
        TestBudget budget = new TestBudget(1);
        CryptoDataFlow flow = CryptoDataFlow.create(budget, HANDSHAKE);
        flow.receive(cryptoFrame(2, 1));

        QuicTransportException failure = assertThrows(QuicTransportException.class,
                                                       () -> flow.receive(cryptoFrame(4, 1)));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED.code()));
        assertThat(failure.keySpace().orElseThrow(), is(HANDSHAKE));
        assertThat(flow.size(), is(1));
        assertThat(flow.buffered(), is(1L));
        assertThat(budget.retained(), is(1));
    }

    @Test
    void shouldClearByteAndPermitAccountingIdempotently() {
        TestBudget budget = new TestBudget(2);
        StreamDataFlow flow = StreamDataFlow.create(budget, STREAM_ID);
        flow.receive(streamFrame(2, 2));
        flow.receive(streamFrame(6, 2));

        flow.clear();
        flow.clear();

        assertThat(flow.size(), is(0));
        assertThat(flow.buffered(), is(0L));
        assertThat(budget.retained(), is(0));

        flow.receive(streamFrame(10, 1));

        assertThat(flow.size(), is(1));
        assertThat(budget.retained(), is(1));
    }

    @Test
    void shouldRejectNullFrameWithoutChangingFlow() {
        TestBudget budget = new TestBudget(1);
        StreamDataFlow flow = StreamDataFlow.create(budget, STREAM_ID);

        assertThrows(NullPointerException.class, () -> flow.receive(null));

        assertThat(flow.offset(), is(0L));
        assertThat(flow.isEmpty(), is(true));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldExposeContiguousFramesWithoutResultWrappers() {
        TestBudget budget = new TestBudget(1);
        StreamDataFlow flow = StreamDataFlow.create(budget, STREAM_ID);
        var delivered = new ArrayList<StreamFrame>();

        flow.receive(streamFrame(2, 2));
        boolean frameAvailable = flow.receiveAvailable(streamFrame(0, 2));
        while (flow.hasAvailable()) {
            delivered.add(flow.takeAvailable());
            flow.pollAvailable();
        }

        assertThat(frameAvailable, is(true));
        assertThat(delivered.size(), is(2));
        assertThat(delivered.get(0).offset(), is(0L));
        assertThat(delivered.get(1).offset(), is(2L));
        assertThat(flow.isEmpty(), is(true));
        assertThat(budget.retained(), is(0));
        assertThrows(NullPointerException.class, () -> flow.receiveAvailable(null));
        assertThrows(NoSuchElementException.class, flow::takeAvailable);
        assertThat(flow.offset(), is(4L));
    }

    private static StreamFrame streamFrame(long offset, int length) {
        return StreamFrame.create(STREAM_ID, offset, length, false, ByteBuffer.allocate(length));
    }

    private static CryptoFrame cryptoFrame(long offset, int length) {
        return CryptoFrame.create(offset, length, ByteBuffer.allocate(length));
    }

    private static final class TestBudget implements ReassemblyBudget {
        private final int capacity;
        private int retained;
        private boolean accepting = true;

        private TestBudget(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public boolean tryAcquire() {
            if (!accepting || retained == capacity) {
                return false;
            }
            retained++;
            return true;
        }

        @Override
        public void release(int count) {
            if (count < 0 || count > retained) {
                throw new IllegalStateException("Invalid release: " + count);
            }
            retained -= count;
        }

        private int retained() {
            return retained;
        }

        private void rejectAcquisitions() {
            accepting = false;
        }
    }
}
