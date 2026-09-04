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

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConnectionException;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.QuicConnectionImpl.ReassemblyBudget;
import io.helidon.quic.QuicInstance;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicTerminationTestSupport;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.ResetStreamFrame;
import io.helidon.quic.frame.StreamFrame;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuicReceiverStreamTest {
    private static final long STREAM_ID = 2;
    private static final int WINDOW_SIZE = 16 * 1024;
    private static final int MAX_SMALL_FRAGMENTS = 100;
    private static final int MAX_REASSEMBLY_NODES = 1024;

    @Test
    void rejectsNullReaderCollaboratorsWithoutChangingConnectionState() {
        QuicReceiverStreamImpl stream = receiver(connection());

        assertThrows(NullPointerException.class, () -> stream.connectReader(null));

        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        assertThrows(NullPointerException.class, () -> stream.disconnectReader(null));

        assertThat(reader.connected(), is(true));
        stream.disconnectReader(reader);
        assertThat(reader.connected(), is(false));
    }

    @Test
    void shouldCoalesceReadySmallFragmentsOutsideReassemblyBudget() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(0);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        int frameCount = MAX_REASSEMBLY_NODES + 1;

        for (int offset = 0; offset < frameCount; offset++) {
            stream.processIncomingFrame(frame(offset, 1));
        }

        assertThat(stream.dataReceived(), is((long) frameCount));
        assertThat(budget.retained(), is(0));

        AtomicInteger wakeups = new AtomicInteger();
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(wakeups::incrementAndGet));
        reader.start();

        assertThat(wakeups.get(), is(1));
        assertThat(reader.poll().orElseThrow().available(), is(frameCount));
        assertThat(reader.poll(), is(Optional.empty()));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldFlushFullSmallFragmentChunkBeforePendingTail() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(0);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        int fragmentSize = 256;
        int frameCount = WINDOW_SIZE / fragmentSize + 1;
        stream.updateMaxStreamData(WINDOW_SIZE + fragmentSize);

        for (int index = 0; index < frameCount; index++) {
            stream.processIncomingFrame(frame((long) index * fragmentSize, fragmentSize));
        }

        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        assertThat(reader.poll().orElseThrow().available(), is(WINDOW_SIZE));
        assertThat(reader.poll().orElseThrow().available(), is(fragmentSize));
        assertThat(reader.poll(), is(Optional.empty()));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldPeekPendingSmallFragmentsWithoutDuplication() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(0);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        byte[] data = {1, 2, 3};
        stream.processIncomingFrame(StreamFrame.create(STREAM_ID, 0, data.length, false, ByteBuffer.wrap(data)));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        BufferData firstPeek = reader.peek().orElseThrow();
        BufferData secondPeek = reader.peek().orElseThrow();
        BufferData polled = reader.poll().orElseThrow();

        assertThat(secondPeek, sameInstance(firstPeek));
        assertThat(polled, sameInstance(firstPeek));
        assertThat(polled.readBytes(), is(data));
        assertThat(reader.poll(), is(Optional.empty()));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldLimitOutOfOrderReassemblyIndependentlyOfReadyData() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(MAX_REASSEMBLY_NODES);
        QuicReceiverStreamImpl stream = receiver(connection(budget), Integer.MAX_VALUE);
        int orderedNodes = MAX_REASSEMBLY_NODES / 2;
        int lastOutOfOrderOffset = orderedNodes + MAX_REASSEMBLY_NODES;
        stream.updateMaxStreamData(lastOutOfOrderOffset + 2L);

        for (int offset = 0; offset < orderedNodes; offset++) {
            stream.processIncomingFrame(frame(offset, 1));
        }
        // Leave one byte missing so the remaining frames stay in the out-of-order flow.
        for (int offset = orderedNodes + 1; offset <= lastOutOfOrderOffset; offset++) {
            stream.processIncomingFrame(frame(offset, 1));
        }

        assertThat(budget.retained(), is(MAX_REASSEMBLY_NODES));
        QuicTransportException failure = assertThrows(QuicTransportException.class,
                                                       () -> stream.processIncomingFrame(
                                                               frame(lastOutOfOrderOffset + 1L, 1)));
        assertThat(failure.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(failure.sourceStreamId().orElseThrow(), is(STREAM_ID));
        assertThat(budget.retained(), is(MAX_REASSEMBLY_NODES));
    }

    @Test
    void shouldKeepReadyDataOutsideReassemblyBudget() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(0);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        stream.processIncomingFrame(frame(0, 1));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        assertThat(budget.retained(), is(0));
        reader.poll().orElseThrow();

        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldOwnBorrowedPayloadBeforeOutOfOrderRetention() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(2);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        byte[] laterBytes = {2, 3};
        byte[] firstBytes = {0, 1};

        stream.processIncomingFrame(StreamFrame.create(STREAM_ID,
                                                       2,
                                                       laterBytes.length,
                                                       false,
                                                       ByteBuffer.wrap(laterBytes)));
        laterBytes[0] = 9;
        laterBytes[1] = 9;
        stream.processIncomingFrame(StreamFrame.create(STREAM_ID,
                                                       0,
                                                       firstBytes.length,
                                                       false,
                                                       ByteBuffer.wrap(firstBytes)));
        firstBytes[0] = 9;
        firstBytes[1] = 9;
        assertThat(budget.retained(), is(0));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        assertThat(reader.poll().orElseThrow().readBytes(), is(new byte[] {0, 1, 2, 3}));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldExposeOwnedDecodedPayloadAsReadOnlyBuffer() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(1);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        byte[] encoded = {0x0a, (byte) STREAM_ID, 3, 1, 2, 3};
        StreamFrame frame = (StreamFrame) QuicFrame.decodeOwned(ByteBuffer.wrap(encoded));

        stream.processIncomingFrame(frame);
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();
        BufferData payload = reader.poll().orElseThrow();

        assertThat(payload.readBytes(), is(new byte[] {1, 2, 3}));
        assertThat(budget.retained(), is(0));
        assertThrows(UnsupportedOperationException.class, () -> payload.write(4));
        assertThrows(UnsupportedOperationException.class, () -> payload.readFrom(ByteBuffer.allocate(1)));
        assertThrows(UnsupportedOperationException.class, payload::capacity);
        assertThrows(UnsupportedOperationException.class, payload::reset);
    }

    @Test
    void shouldTransferOwnedSlicesThroughOrderedGapFill() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(2);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        stream.processIncomingFrame(StreamFrame.createOwned(STREAM_ID,
                                                            2,
                                                            4,
                                                            false,
                                                            ByteBuffer.wrap(new byte[] {2, 3, 4, 5})));
        stream.processIncomingFrame(StreamFrame.createOwned(STREAM_ID,
                                                            0,
                                                            3,
                                                            false,
                                                            ByteBuffer.wrap(new byte[] {0, 1, 2})));
        assertThat(budget.retained(), is(0));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        assertThat(reader.poll().orElseThrow().readBytes(), is(new byte[] {0, 1, 2, 3, 4, 5}));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldPreserveSmallAndLargeDeliveryOrder() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(0);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        byte[] large = new byte[400];
        large[0] = 2;

        stream.processIncomingFrame(StreamFrame.createOwned(STREAM_ID,
                                                            0,
                                                            1,
                                                            false,
                                                            ByteBuffer.wrap(new byte[] {1})));
        stream.processIncomingFrame(StreamFrame.createOwned(STREAM_ID,
                                                            1,
                                                            large.length,
                                                            false,
                                                            ByteBuffer.wrap(large)));
        stream.processIncomingFrame(StreamFrame.createOwned(STREAM_ID,
                                                            401,
                                                            1,
                                                            false,
                                                            ByteBuffer.wrap(new byte[] {3})));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        assertThat(reader.poll().orElseThrow().readBytes(), is(new byte[] {1}));
        BufferData largeData = reader.poll().orElseThrow();
        assertThat(largeData.available(), is(large.length));
        assertThat(largeData.read(), is(2));
        assertThat(reader.poll().orElseThrow().readBytes(), is(new byte[] {3}));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldFlushSmallDataBeforeEndOfStream() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(0);
        QuicConnectionImpl connection = connection(budget);
        QuicReceiverStreamImpl stream = receiver(connection);
        stream.processIncomingFrame(StreamFrame.createOwned(STREAM_ID,
                                                            0,
                                                            3,
                                                            true,
                                                            ByteBuffer.wrap(new byte[] {1, 2, 3})));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        assertThat(reader.poll().orElseThrow().readBytes(), is(new byte[] {1, 2, 3}));
        assertThat(reader.poll().orElseThrow(), is(QuicStreamReader.EOF));
        assertThat(budget.retained(), is(0));
    }

    @Test
    void shouldKeepPolledOwnedPayloadValidAfterConnectionTermination() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(1);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        byte[] bytes = {1, 2, 3};
        stream.processIncomingFrame(StreamFrame.createOwned(STREAM_ID,
                                                            0,
                                                            bytes.length,
                                                            false,
                                                            ByteBuffer.wrap(bytes)));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();
        BufferData payload = reader.poll().orElseThrow();

        stream.terminate(termination(new IllegalStateException("connection closed")));

        assertThat(payload.readBytes(), is(bytes));
        assertThat(budget.retained(), is(0));
        assertThat(budget.closed(), is(true));
    }

    @Test
    void shouldReleaseConnectionCreditOnlyWhenApplicationPollsData() throws Exception {
        QuicConnectionImpl connection = connection();
        QuicReceiverStreamImpl stream = receiver(connection);
        StreamFrame frame = frame(0, 4);

        stream.processIncomingFrame(frame);

        verify(connection).increaseReceivedData(4, frame.typeField());
        verify(connection, never()).increaseProcessedData(anyLong());

        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();
        BufferData data = reader.poll().orElseThrow();

        assertThat(data.available(), is(4));
        verify(connection).increaseProcessedData(4);
    }

    @Test
    void shouldMaintainConfiguredWindowFromProcessedOffset() throws Exception {
        QuicConnectionImpl connection = connection();
        QuicReceiverStreamImpl stream = receiver(connection);
        stream.processIncomingFrame(frame(0, WINDOW_SIZE / 2));
        stream.processIncomingFrame(frame(WINDOW_SIZE / 2, WINDOW_SIZE / 2));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        reader.poll().orElseThrow();

        verify(connection).requestSendMaxStreamData(STREAM_ID, WINDOW_SIZE + WINDOW_SIZE / 2);
        assertThat(stream.maxStreamData(), is((long) WINDOW_SIZE + WINDOW_SIZE / 2));
    }

    @Test
    void shouldReleaseBufferedConnectionCreditWhenReadingIsCancelled() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(MAX_REASSEMBLY_NODES);
        QuicConnectionImpl connection = connection(budget);
        QuicReceiverStreamImpl stream = receiver(connection);
        stream.processIncomingFrame(frame(0, WINDOW_SIZE / 2));
        assertThat(budget.retained(), is(0));

        stream.requestStopSending(0x10);
        stream.requestStopSending(0x10);

        verify(connection, times(1)).increaseProcessedData(WINDOW_SIZE / 2);
        assertThat(budget.retained(), is(0));
        assertThat(budget.closed(), is(true));
    }

    @Test
    void shouldReleaseReadyAndOutOfOrderOwnershipWhenReadingIsCancelled() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(2);
        QuicConnectionImpl connection = connection(budget);
        QuicReceiverStreamImpl stream = receiver(connection);
        stream.processIncomingFrame(frame(0, 2));
        stream.processIncomingFrame(frame(4, 2));
        assertThat(budget.retained(), is(1));

        stream.requestStopSending(0x10);

        verify(connection, times(1)).increaseProcessedData(6);
        assertThat(budget.retained(), is(0));
        assertThat(budget.closed(), is(true));
    }

    @Test
    void shouldReleaseConnectionCreditOnceWhenPolledThenCancelled() throws Exception {
        QuicConnectionImpl connection = connection();
        QuicReceiverStreamImpl stream = receiver(connection);
        stream.processIncomingFrame(frame(0, WINDOW_SIZE / 2));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        reader.poll().orElseThrow();
        stream.requestStopSending(0x10);

        verify(connection, times(1)).increaseProcessedData(WINDOW_SIZE / 2);
    }

    @Test
    void shouldReleaseLaterDataReceivedAfterReadingIsCancelled() throws Exception {
        QuicConnectionImpl connection = connection();
        QuicReceiverStreamImpl stream = receiver(connection);
        stream.processIncomingFrame(frame(0, WINDOW_SIZE / 4));

        stream.requestStopSending(0x10);
        stream.processIncomingFrame(frame(WINDOW_SIZE / 4, WINDOW_SIZE / 4));

        verify(connection, times(2)).increaseProcessedData(WINDOW_SIZE / 4);
    }

    @Test
    void shouldWakeReaderWhenReadingIsCancelled() {
        QuicConnectionImpl connection = connection();
        QuicReceiverStreamImpl stream = receiver(connection);
        AtomicInteger wakeups = new AtomicInteger();
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(wakeups::incrementAndGet));
        reader.start();

        stream.requestStopSending(0x10);

        assertThat(wakeups.get(), is(1));
        QuicStreamException failure = assertThrows(QuicStreamException.class, reader::poll);
        assertThat(failure.kind(), is(QuicStreamException.Kind.CLOSED));
        assertThat(failure.streamId(), is(2L));
    }

    @Test
    void shouldWakeReaderStartedAfterReadingIsCancelled() {
        QuicConnectionImpl connection = connection();
        QuicReceiverStreamImpl stream = receiver(connection);
        stream.requestStopSending(0x10);
        AtomicInteger wakeups = new AtomicInteger();

        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(wakeups::incrementAndGet));
        reader.start();

        assertThat(wakeups.get(), is(1));
        QuicStreamException failure = assertThrows(QuicStreamException.class, reader::poll);
        assertThat(failure.kind(), is(QuicStreamException.Kind.CLOSED));
        assertThat(failure.streamId(), is(2L));
    }

    @Test
    void shouldExposePeerResetAsTypedStreamFailure() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(MAX_REASSEMBLY_NODES);
        QuicReceiverStreamImpl stream = receiver(connection(budget));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();
        stream.processIncomingFrame(frame(0, 1));
        stream.processIncomingFrame(frame(2, 1));
        assertThat(budget.retained(), is(1));

        stream.processIncomingResetFrame(ResetStreamFrame.create(STREAM_ID, 0x10c, 3));

        QuicStreamException failure = assertThrows(QuicStreamException.class, reader::poll);
        assertThat(failure.kind(), is(QuicStreamException.Kind.RESET_BY_PEER));
        assertThat(failure.streamId(), is(STREAM_ID));
        assertThat(failure.errorCode().orElseThrow(), is(0x10cL));
        assertThat(budget.retained(), is(0));
        assertThat(budget.closed(), is(true));
    }

    @Test
    void shouldDiscardBufferedDataAndWakeReaderWhenConnectionTerminates() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(MAX_REASSEMBLY_NODES);
        QuicConnectionImpl connection = connection(budget);
        QuicReceiverStreamImpl stream = receiver(connection);
        AtomicInteger wakeups = new AtomicInteger();
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(wakeups::incrementAndGet));
        reader.start();
        stream.processIncomingFrame(frame(0, WINDOW_SIZE / 2));
        stream.processIncomingFrame(frame(WINDOW_SIZE / 2 + 1, 1));
        assertThat(budget.retained(), is(1));
        int wakeupsBeforeTermination = wakeups.get();
        QuicTermination termination = termination(new IllegalStateException("connection closed"));
        when(connection.termination()).thenReturn(Optional.of(termination));

        stream.terminate(termination);

        assertThat(wakeups.get(), is(wakeupsBeforeTermination + 1));
        QuicConnectionException failure = assertThrows(QuicConnectionException.class, reader::poll);
        assertThat(failure.getMessage(), is("connection closed"));
        assertThat(budget.retained(), is(0));
        assertThat(budget.closed(), is(true));
    }

    @Test
    void shouldWakeReaderStartedAfterConnectionTerminates() {
        QuicConnectionImpl connection = connection();
        QuicReceiverStreamImpl stream = receiver(connection);
        QuicTermination termination = termination(new IllegalStateException("connection closed"));
        when(connection.termination()).thenReturn(Optional.of(termination));
        stream.terminate(termination);
        AtomicInteger wakeups = new AtomicInteger();

        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(wakeups::incrementAndGet));
        reader.start();

        assertThat(wakeups.get(), is(1));
        QuicConnectionException failure = assertThrows(QuicConnectionException.class, reader::poll);
        assertThat(failure.getMessage(), is("connection closed"));
    }

    private static QuicTermination termination(Throwable closeCause) {
        return QuicTerminationTestSupport.local(QuicCloseCommand.transport(closeCause));
    }

    @Test
    void shouldReachTerminalReadStateWhenEndOfStreamIsPolled() throws Exception {
        TestReassemblyBudget budget = new TestReassemblyBudget(1);
        QuicConnectionImpl connection = connection(budget);
        QuicReceiverStreamImpl stream = receiver(connection);
        stream.processIncomingFrame(StreamFrame.create(STREAM_ID, 0, 0, true, ByteBuffer.allocate(0)));
        QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();

        assertThat(reader.peek().orElseThrow(), is(QuicStreamReader.EOF));
        assertThat(reader.poll().orElseThrow(), is(QuicStreamReader.EOF));

        assertThat(budget.retained(), is(0));
        assertThat(stream.receivingState(), is(QuicReceiverStream.ReceivingStreamState.DATA_READ));
        verify(connection).notifyTerminalState(STREAM_ID, QuicReceiverStream.ReceivingStreamState.DATA_READ);
    }

    private static QuicConnectionImpl connection() {
        return connection(new TestReassemblyBudget(MAX_REASSEMBLY_NODES));
    }

    private static QuicConnectionImpl connection(TestReassemblyBudget budget) {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.executor()).thenReturn(Runnable::run);
        when(connection.quicInstance()).thenReturn(instance);
        when(connection.quicConfig()).thenReturn(QuicConfig.builder()
                                                        .initialMaxStreamData(WINDOW_SIZE)
                                                        .buildPrototype());
        when(connection.newReassemblyBudget()).thenReturn(budget);
        when(connection.isClientConnection()).thenReturn(false);
        return connection;
    }

    private static QuicReceiverStreamImpl receiver(QuicConnectionImpl connection) {
        return receiver(connection, MAX_SMALL_FRAGMENTS);
    }

    private static QuicReceiverStreamImpl receiver(QuicConnectionImpl connection, int maxSmallFragments) {
        QuicReceiverStreamImpl stream = new QuicReceiverStreamImpl(connection, STREAM_ID, maxSmallFragments);
        stream.updateMaxStreamData(WINDOW_SIZE);
        return stream;
    }

    private static StreamFrame frame(long offset, int length) {
        return StreamFrame.create(STREAM_ID, offset, length, false, ByteBuffer.wrap(new byte[length]));
    }

    private static final class TestReassemblyBudget implements ReassemblyBudget {
        private final int capacity;
        private int retained;
        private boolean accepting = true;

        private TestReassemblyBudget(int capacity) {
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

        @Override
        public void close() {
            accepting = false;
        }

        private int retained() {
            return retained;
        }

        private boolean closed() {
            return !accepting;
        }
    }
}
