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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.helidon.quic.stream.QuicSenderStream.SendingStreamState;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamWriterQueueTest {
    @Test
    void completesEachDispatchAtItsOwnConsumedOffset() throws Exception {
        TestQueue queue = new TestQueue();
        queue.updateMaxStreamData(16);

        CompletableFuture<Void> first = queue.submitAndGetDispatchCompletion(ByteBuffer.wrap(new byte[4]), false);
        CompletableFuture<Void> second = queue.submitAndGetDispatchCompletion(ByteBuffer.wrap(new byte[3]), false);

        assertThat(first.isDone(), equalTo(false));
        assertThat(second.isDone(), equalTo(false));

        assertThat(queue.poll(2).remaining(), equalTo(2));
        queue.markDispatched(2, false);
        assertThat(first.isDone(), equalTo(false));
        assertThat(second.isDone(), equalTo(false));

        assertThat(queue.poll(2).remaining(), equalTo(2));
        assertThat(first.isDone(), equalTo(false));
        queue.markDispatched(4, false);
        assertThat(first.isDone(), equalTo(true));
        assertThat(second.isDone(), equalTo(false));

        assertThat(queue.poll(3).remaining(), equalTo(3));
        queue.markDispatched(7, false);
        assertThat(second.isDone(), equalTo(true));
    }

    @Test
    void keepsDispatchPendingUntilFlowControlAllowsConsumption() throws Exception {
        TestQueue queue = new TestQueue();
        CompletableFuture<Void> dispatch = queue.submitAndGetDispatchCompletion(ByteBuffer.wrap(new byte[3]), false);

        assertThat(queue.poll(3), nullValue());
        assertThat(dispatch.isDone(), equalTo(false));

        queue.updateMaxStreamData(3);
        assertThat(queue.poll(3).remaining(), equalTo(3));
        assertThat(dispatch.isDone(), equalTo(false));
        queue.markDispatched(3, false);
        assertThat(dispatch.isDone(), equalTo(true));
    }

    @Test
    void emptyNonFinalSubmissionActsAsDispatchBarrier() throws Exception {
        TestQueue queue = new TestQueue();
        queue.updateMaxStreamData(3);
        queue.submit(ByteBuffer.wrap(new byte[3]), false);

        CompletableFuture<Void> barrier = queue.submitAndGetDispatchCompletion(ByteBuffer.allocate(0), false);

        assertThat(barrier.isDone(), equalTo(false));
        assertThat(queue.poll(2).remaining(), equalTo(2));
        queue.markDispatched(2, false);
        assertThat(barrier.isDone(), equalTo(false));
        assertThat(queue.poll(1).remaining(), equalTo(1));
        queue.markDispatched(3, false);
        assertThat(barrier.isDone(), equalTo(true));
        assertThat(queue.hasPendingZeroLengthEndOfStream(), equalTo(false));
    }

    @Test
    void failsPendingDispatchWhenQueueCloses() throws Exception {
        TestQueue queue = new TestQueue();
        CompletableFuture<Void> dispatch = queue.submitAndGetDispatchCompletion(ByteBuffer.wrap(new byte[3]), false);
        IllegalStateException failure = new IllegalStateException("test close");

        queue.close(failure);

        CompletionException exception = assertThrows(CompletionException.class, dispatch::join);
        assertThat(exception.getCause(), sameInstance(failure));
    }

    @Test
    void failsReceiptSubmittedAfterQueueCloses() throws Exception {
        TestQueue queue = new TestQueue();
        IllegalStateException failure = new IllegalStateException("test close");
        queue.close(failure);

        CompletableFuture<Void> dispatch = queue.submitAndGetDispatchCompletion(ByteBuffer.allocate(0), true);

        CompletionException exception = assertThrows(CompletionException.class, dispatch::join);
        assertThat(exception.getCause(), sameInstance(failure));
    }

    @Test
    void acceptsOrdinaryTerminalSubmissionAfterQueueCloses() {
        TestQueue queue = new TestQueue();
        queue.close(new IllegalStateException("test close"));

        queue.submit(ByteBuffer.allocate(0), true);
        assertThat(queue.poll(0).remaining(), equalTo(0));
    }

    @Test
    void ignoresOrdinarySubmissionButFailsReceiptAfterReset() throws Exception {
        TestQueue queue = new TestQueue();
        CompletableFuture<Void> pending = queue.submitAndGetDispatchCompletion(ByteBuffer.wrap(new byte[1]), false);

        queue.markReset(0x10c);

        CompletionException pendingFailure = assertThrows(CompletionException.class, pending::join);
        QuicStreamException reset = (QuicStreamException) pendingFailure.getCause();
        assertThat(reset.kind(), equalTo(QuicStreamException.Kind.RESET_LOCALLY));
        assertThat(reset.errorCode().orElseThrow(), equalTo(0x10cL));
        queue.submit(ByteBuffer.allocate(0), true);
        CompletableFuture<Void> dispatch = queue.submitAndGetDispatchCompletion(ByteBuffer.allocate(0), true);

        assertThrows(CompletionException.class, dispatch::join);
    }

    @Test
    void preservesStopSendingForPendingDispatchFailure() {
        TestQueue queue = new TestQueue();
        CompletableFuture<Void> pending = queue.submitAndGetDispatchCompletion(ByteBuffer.wrap(new byte[1]), false);

        queue.receiveStopSending(0x10c);
        queue.markReset(0x10c);

        CompletionException pendingFailure = assertThrows(CompletionException.class, pending::join);
        QuicStreamException stopSending = (QuicStreamException) pendingFailure.getCause();
        assertThat(stopSending.kind(), equalTo(QuicStreamException.Kind.STOP_SENDING));
        assertThat(stopSending.errorCode().orElseThrow(), equalTo(0x10cL));
    }

    @Test
    void dispatchesZeroLengthFinAtDefaultZeroCredit() throws Exception {
        TestQueue queue = new TestQueue();

        CompletableFuture<Void> dispatch = queue.submitAndGetDispatchCompletion(ByteBuffer.allocate(0), true);

        assertThat(dispatch.isDone(), equalTo(false));
        assertThat(queue.poll(1).remaining(), equalTo(0));
        assertThat(dispatch.isDone(), equalTo(false));
        queue.markDispatched(0, true);
        assertThat(dispatch.isDone(), equalTo(true));
    }

    @Test
    void doesNotConsumeNonEmptyDataAtDefaultZeroCredit() throws Exception {
        TestQueue queue = new TestQueue();

        CompletableFuture<Void> dispatch = queue.submitAndGetDispatchCompletion(ByteBuffer.wrap(new byte[1]), true);

        assertThat(queue.poll(1), nullValue());
        assertThat(dispatch.isDone(), equalTo(false));
    }

    private static final class TestQueue extends StreamWriterQueue {
        private TestQueue() {
            super(16);
        }

        @Override
        protected void logDebug(String format, Object... arguments) {
        }

        @Override
        protected void wakeupConsumer() {
        }

        @Override
        protected void wakeupProducer() {
        }

        @Override
        protected void switchState(SendingStreamState dataSent) {
        }

        @Override
        protected long streamId() {
            return 0;
        }

        private boolean receiveStopSending(long errorCode) {
            return stopSending(errorCode);
        }
    }
}
