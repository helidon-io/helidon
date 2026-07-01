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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class Http2ServerStreamInboundDataTest {

    @Test
    void frameBudgetIsConnectionWideNonBlockingAndPreservesFrames() throws InterruptedException {
        var budget = new Http2ServerStream.InboundDataBudget(2, 100);
        var queue = new Http2ServerStream.InboundDataQueue(budget);
        var peerQueue = new Http2ServerStream.InboundDataQueue(budget);
        Http2FrameHeader firstHeader = header(8);
        BufferData firstData = BufferData.create(new byte[8]);
        Http2FrameHeader secondHeader = header(12);
        BufferData secondData = BufferData.create(new byte[12]);
        Http2FrameHeader thirdHeader = header(4);
        BufferData thirdData = BufferData.create(new byte[4]);

        assertThat(queue.offer(firstHeader, firstData), is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));
        assertThat(peerQueue.offer(secondHeader, secondData),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));
        assertThat(queue.offer(thirdHeader, thirdData),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.BUDGET_EXHAUSTED));

        Http2ServerStream.DataFrame first = queue.take();
        assertThat(first.header(), sameInstance(firstHeader));
        assertThat(first.data(), sameInstance(firstData));
        queue.complete(first, () -> { });
        assertThat(budget.availableFrames(), is(1));
        assertThat(budget.availableBytes(), is(88L));
        assertThat(queue.offer(thirdHeader, thirdData), is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));

        Http2ServerStream.DataFrame second = peerQueue.take();
        assertThat(second.header(), sameInstance(secondHeader));
        assertThat(second.data(), sameInstance(secondData));
        peerQueue.complete(second, () -> { });
        Http2ServerStream.DataFrame third = queue.take();
        assertThat(third.header(), sameInstance(thirdHeader));
        assertThat(third.data(), sameInstance(thirdData));
        queue.complete(third, () -> { });
        assertThat(budget.availableFrames(), is(2));
        assertThat(budget.availableBytes(), is(100L));
    }

    @Test
    void byteBudgetIsWeightedAndReleasedForPeers() {
        var budget = new Http2ServerStream.InboundDataBudget(10, 8);
        var queue = new Http2ServerStream.InboundDataQueue(budget);
        var peerQueue = new Http2ServerStream.InboundDataQueue(budget);
        Http2FrameHeader fullBudget = header(8);
        Http2FrameHeader peerFrame = header(1);
        assertThat(queue.offer(fullBudget, BufferData.create(new byte[8])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));

        assertThat(peerQueue.offer(peerFrame, BufferData.create(new byte[1])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.BUDGET_EXHAUSTED));
        assertThat(queue.abortAndDrain(), is(8L));
        assertThat(peerQueue.offer(peerFrame, BufferData.create(new byte[1])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));

        assertThat(peerQueue.abortAndDrain(), is(1L));
        assertThat(budget.availableFrames(), is(10));
        assertThat(budget.availableBytes(), is(8L));
    }

    @Test
    void abortOwnsQueuedAndInFlightCredit() throws InterruptedException {
        var budget = new Http2ServerStream.InboundDataBudget(2, 20);
        var queue = new Http2ServerStream.InboundDataQueue(budget);
        Http2FrameHeader first = header(8);
        Http2FrameHeader second = header(12);
        assertThat(queue.offer(first, BufferData.create(new byte[8])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));
        assertThat(queue.offer(second, BufferData.create(new byte[12])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));

        Http2ServerStream.DataFrame inFlight = queue.take();
        assertThat(queue.abortAndDrain(), is(20L));
        assertThat(budget.availableFrames(), is(2));
        assertThat(budget.availableBytes(), is(20L));

        AtomicBoolean consumed = new AtomicBoolean();
        queue.complete(inFlight, () -> consumed.set(true));
        assertThat(consumed.get(), is(false));
        assertThat(queue.abortAndDrain(), is(0L));
        assertThat(queue.offer(first, BufferData.create(new byte[8])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.CLOSED));
        assertThat(queue.take(), nullValue());
    }

    @Test
    void completionCallbackDoesNotHoldQueueLock() throws InterruptedException {
        var budget = new Http2ServerStream.InboundDataBudget(1, 8);
        var queue = new Http2ServerStream.InboundDataQueue(budget);
        assertThat(queue.offer(header(8), BufferData.create(new byte[8])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));
        Http2ServerStream.DataFrame frame = queue.take();
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        CountDownLatch completionFinished = new CountDownLatch(1);
        CountDownLatch abortFinished = new CountDownLatch(1);
        AtomicReference<Long> discarded = new AtomicReference<>();
        AtomicBoolean callbackInterrupted = new AtomicBoolean();
        Thread completion = Thread.ofVirtual().start(() -> {
            queue.complete(frame, () -> {
                callbackStarted.countDown();
                try {
                    releaseCallback.await();
                } catch (InterruptedException e) {
                    callbackInterrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            });
            completionFinished.countDown();
        });

        assertThat(callbackStarted.await(5, TimeUnit.SECONDS), is(true));
        Thread abort = Thread.ofVirtual().start(() -> {
            discarded.set(queue.abortAndDrain());
            abortFinished.countDown();
        });
        try {
            assertThat("abort is not blocked by the completion callback",
                       abortFinished.await(5, TimeUnit.SECONDS),
                       is(true));
            assertThat(discarded.get(), is(0L));
            assertThat(completionFinished.getCount(), is(1L));
            assertThat(budget.availableFrames(), is(1));
            assertThat(budget.availableBytes(), is(8L));
        } finally {
            releaseCallback.countDown();
            completion.join();
            abort.join();
        }
        assertThat(callbackInterrupted.get(), is(false));
    }

    @Test
    void terminalIsDeliveredOnceAndAbortWakesWaiter() throws InterruptedException {
        var queue = new Http2ServerStream.InboundDataQueue(new Http2ServerStream.InboundDataBudget(1, 1));
        queue.finish();

        Http2ServerStream.DataFrame terminal = queue.take();
        assertThat(terminal.flowControlLength(), is(0));
        assertThat(terminal.header().flags(Http2FrameTypes.DATA).endOfStream(), is(true));

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Http2ServerStream.DataFrame> result = new AtomicReference<>();
        AtomicReference<InterruptedException> failure = new AtomicReference<>();
        Thread waiter = Thread.ofVirtual().start(() -> {
            started.countDown();
            try {
                result.set(queue.take());
            } catch (InterruptedException e) {
                failure.set(e);
            } finally {
                completed.countDown();
            }
        });

        assertThat(started.await(5, TimeUnit.SECONDS), is(true));
        assertThat(completed.await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(queue.abortAndDrain(), is(0L));
        assertThat(completed.await(5, TimeUnit.SECONDS), is(true));
        waiter.join();
        assertThat(failure.get(), nullValue());
        assertThat(result.get(), nullValue());
    }

    @Test
    void connectionWideAbortDrainsStreamsAndWakesWorkers() throws InterruptedException {
        var budget = new Http2ServerStream.InboundDataBudget(2, 20);
        var firstQueue = new Http2ServerStream.InboundDataQueue(budget);
        var secondQueue = new Http2ServerStream.InboundDataQueue(budget);
        assertThat(firstQueue.offer(header(8), BufferData.create(new byte[8])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));
        assertThat(secondQueue.offer(header(12), BufferData.create(new byte[12])),
                   is(Http2ServerStream.InboundDataQueue.OfferResult.ACCEPTED));

        CountDownLatch framesTaken = new CountDownLatch(2);
        CountDownLatch workersExited = new CountDownLatch(2);
        AtomicReference<Http2ServerStream.DataFrame> firstResult = new AtomicReference<>();
        AtomicReference<Http2ServerStream.DataFrame> secondResult = new AtomicReference<>();
        Thread firstWorker = Thread.ofVirtual().start(() -> {
            try {
                firstQueue.take();
                framesTaken.countDown();
                firstResult.set(firstQueue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                workersExited.countDown();
            }
        });
        Thread secondWorker = Thread.ofVirtual().start(() -> {
            try {
                secondQueue.take();
                framesTaken.countDown();
                secondResult.set(secondQueue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                workersExited.countDown();
            }
        });

        assertThat("both workers retained one frame", framesTaken.await(5, TimeUnit.SECONDS), is(true));
        assertThat("workers wait for more DATA", workersExited.await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(firstQueue.abortAndDrain(), is(8L));
        assertThat(secondQueue.abortAndDrain(), is(12L));

        assertThat("workers exited", workersExited.await(5, TimeUnit.SECONDS), is(true));
        firstWorker.join();
        secondWorker.join();
        assertThat(firstResult.get(), nullValue());
        assertThat(secondResult.get(), nullValue());
        assertThat(budget.availableFrames(), is(2));
        assertThat(budget.availableBytes(), is(20L));
    }

    private Http2FrameHeader header(int length) {
        return Http2FrameHeader.create(length,
                                       Http2FrameTypes.DATA,
                                       Http2Flag.DataFlags.create(0),
                                       1);
    }
}
