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

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicInboundQueueTest {

    @Test
    void closeWaitsForOwnedOfferThenDrainsItExactlyOnce() throws Exception {
        AtomicInteger buffered = new AtomicInteger();
        CountDownLatch accountEntered = new CountDownLatch(1);
        CountDownLatch allowAccount = new CountDownLatch(1);
        QuicInboundQueue<Item> queue = new QuicInboundQueue<>(Item::bytes,
                                                              bytes -> {
                                                                  buffered.addAndGet(bytes);
                                                                  accountEntered.countDown();
                                                                  await(allowAccount);
                                                              },
                                                              bytes -> buffered.addAndGet(-bytes));
        Item item = new Item(17);
        CompletableFuture<Boolean> offer = CompletableFuture.supplyAsync(() -> queue.offer(item));
        assertThat(accountEntered.await(5, TimeUnit.SECONDS), is(true));

        CompletableFuture<Void> close = CompletableFuture.runAsync(queue::closeAndDrain);
        long closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!queue.isClosed() && System.nanoTime() < closeDeadline) {
            Thread.onSpinWait();
        }
        assertThat(queue.isClosed(), is(true));
        assertThat(close.isDone(), is(false));

        allowAccount.countDown();
        assertThat(offer.get(5, TimeUnit.SECONDS), is(true));
        close.get(5, TimeUnit.SECONDS);
        assertThat(buffered.get(), is(0));
        assertThat(queue.isEmpty(), is(true));
        assertThat(queue.isClosed(), is(true));
        assertThat(queue.offer(new Item(1)), is(false));
        assertThat(buffered.get(), is(0));
    }

    @Test
    void claimedItemAndCloseHaveSingleAccountingOwner() {
        AtomicInteger buffered = new AtomicInteger();
        QuicInboundQueue<Item> queue = queue(buffered);
        Item item = new Item(11);
        assertThat(queue.offer(item), is(true));

        Item claimed = queue.poll();
        assertThat(claimed, sameInstance(item));
        queue.closeAndDrain();
        assertThat(buffered.get(), is(11));

        queue.release(claimed.bytes());
        assertThat(buffered.get(), is(0));
        assertThat(queue.poll(), nullValue());
    }

    @Test
    void schedulingRollbackReleasesOnlyStillQueuedItem() {
        AtomicInteger buffered = new AtomicInteger();
        QuicInboundQueue<Item> queue = queue(buffered);
        Item first = new Item(5);
        Item rejected = new Item(7);
        assertThat(queue.offer(first), is(true));
        assertThat(queue.offer(rejected), is(true));

        assertThat(queue.removeAndRelease(rejected), is(true));
        assertThat(buffered.get(), is(5));
        assertThat(queue.removeAndRelease(rejected), is(false));

        Item claimed = queue.poll();
        assertThat(claimed, sameInstance(first));
        assertThat(queue.removeAndRelease(claimed), is(false));
        queue.release(claimed.bytes());
        queue.closeAndDrain();
        assertThat(buffered.get(), is(0));
    }

    @Test
    void failedQueueOfferRollsBackAccountingAndOfferOwnership() {
        AtomicInteger buffered = new AtomicInteger();
        AbstractQueue<Item> rejectingQueue = rejectingQueue(new IllegalStateException("rejected"));
        QuicInboundQueue<Item> queue = new QuicInboundQueue<>(rejectingQueue,
                                                              Item::bytes,
                                                              buffered::addAndGet,
                                                              bytes -> buffered.addAndGet(-bytes));

        assertThrows(IllegalStateException.class, () -> queue.offer(new Item(13)));
        assertThat(buffered.get(), is(0));
        queue.closeAndDrain();
        assertThat(queue.isClosed(), is(true));
    }

    @Test
    void closeDrainsRemainingItemsWhenOneReleaseCallbackFails() {
        AtomicInteger buffered = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        QuicInboundQueue<Item> queue = new QuicInboundQueue<>(Item::bytes,
                                                              buffered::addAndGet,
                                                              bytes -> {
                                                                  buffered.addAndGet(-bytes);
                                                                  if (releases.getAndIncrement() == 0) {
                                                                      throw new IllegalStateException("release failure");
                                                                  }
                                                              });
        assertThat(queue.offer(new Item(3)), is(true));
        assertThat(queue.offer(new Item(5)), is(true));

        assertThrows(IllegalStateException.class, queue::closeAndDrain);

        assertThat(releases.get(), is(2));
        assertThat(buffered.get(), is(0));
        assertThat(queue.isEmpty(), is(true));
    }

    @Test
    void failedOfferPreservesPrimaryFailureAndSuppressesRollbackFailure() {
        AtomicInteger buffered = new AtomicInteger();
        IllegalStateException primary = new IllegalStateException("offer failure");
        IllegalStateException rollback = new IllegalStateException("rollback failure");
        AbstractQueue<Item> rejectingQueue = rejectingQueue(primary);
        QuicInboundQueue<Item> queue = new QuicInboundQueue<>(rejectingQueue,
                                                              Item::bytes,
                                                              buffered::addAndGet,
                                                              bytes -> {
                                                                  buffered.addAndGet(-bytes);
                                                                  throw rollback;
                                                              });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                      () -> queue.offer(new Item(13)));

        assertThat(failure, sameInstance(primary));
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(failure.getSuppressed()[0], sameInstance(rollback));
        assertThat(buffered.get(), is(0));
        queue.closeAndDrain();
    }

    private static AbstractQueue<Item> rejectingQueue(RuntimeException failure) {
        return new AbstractQueue<>() {
            @Override
            public Iterator<Item> iterator() {
                return List.<Item>of().iterator();
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean offer(Item item) {
                throw failure;
            }

            @Override
            public Item poll() {
                return null;
            }

            @Override
            public Item peek() {
                return null;
            }
        };
    }

    private static QuicInboundQueue<Item> queue(AtomicInteger buffered) {
        return new QuicInboundQueue<>(Item::bytes,
                                      buffered::addAndGet,
                                      bytes -> buffered.addAndGet(-bytes));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test barrier");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test barrier", e);
        }
    }

    private record Item(int bytes) {
    }
}
