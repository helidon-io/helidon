/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

import io.helidon.common.reactive.RetrySchema;
import io.helidon.webserver.utils.CollectingSubscriber;

import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ReadableByteChannelPublisher}.
 */
public class ReadableByteChannelPublisherTest {

    private static final long TEST_DATA_SIZE = 250 * 1024;

    @Test
    public void allData() throws Exception {
        PeriodicalChannel pc = new PeriodicalChannel(i -> 256, TEST_DATA_SIZE);
        CollectingSubscriber subscriber = new CollectingSubscriber();
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, RetrySchema.constant(5));
        ForkJoinPool.commonPool().submit(() -> subscriber.subscribeOn(publisher));
        // assert
        byte[] bytes = subscriber.result().get(5, TimeUnit.SECONDS);
        assertEquals(TEST_DATA_SIZE, bytes.length);
        assertByteSequence(bytes);
        assertEquals(1, pc.threads.size());
        assertFalse(pc.isOpen());
        assertTrue(pc.readMethodCallCounter > (subscriber.onNextCounter() * 2),
                "Publisher did not concatenate read results to minimize output chunks!");
    }

    @Test
    public void chunky() throws Exception {
        PeriodicalChannel pc = createChannelWithNoAvailableData(25, 3);
        CollectingSubscriber subscriber = new CollectingSubscriber(1);
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, RetrySchema.constant(2));
        ForkJoinPool.commonPool().submit(() -> subscriber.subscribeOn(publisher));
        // assert
        byte[] bytes = subscriber.result().get(5, TimeUnit.SECONDS);
        assertEquals(TEST_DATA_SIZE, bytes.length);
        assertByteSequence(bytes);
        assertEquals(2, pc.threads.size());
        assertFalse(pc.isOpen());
    }

    @Test
    public void chunkyNoDelay() throws Exception {
        PeriodicalChannel pc = createChannelWithNoAvailableData(10, 3);
        CollectingSubscriber subscriber = new CollectingSubscriber(Long.MAX_VALUE);
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, RetrySchema.constant(0));
        ForkJoinPool.commonPool().submit(() -> subscriber.subscribeOn(publisher));
        // assert
        byte[] bytes = subscriber.result().get(5, TimeUnit.SECONDS);
        assertEquals(TEST_DATA_SIZE, bytes.length);
        assertByteSequence(bytes);
        assertEquals(1, pc.threads.size());
        assertFalse(pc.isOpen());
    }

    @Test
    public void onClosedChannel() throws Exception {
        PeriodicalChannel pc = new PeriodicalChannel(i -> 1024, TEST_DATA_SIZE);
        pc.close();
        CollectingSubscriber subscriber = new CollectingSubscriber(Long.MAX_VALUE);
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, RetrySchema.constant(0));
        ForkJoinPool.commonPool().submit(() -> subscriber.subscribeOn(publisher));
        // assert
        try {
            subscriber.result().get(5, TimeUnit.SECONDS);
            throw new AssertionError("Did not throw expected ExecutionException!");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), IsInstanceOf.instanceOf(ClosedChannelException.class));
        }
    }

    @Test
    public void negativeDelay() throws Exception {
        PeriodicalChannel pc = createChannelWithNoAvailableData(10, 1);
        CollectingSubscriber subscriber = new CollectingSubscriber(Long.MAX_VALUE);
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, (i, delay) -> i >= 3 ? -10 : 0);
        ForkJoinPool.commonPool().submit(() -> subscriber.subscribeOn(publisher));
        // assert
        try {
            subscriber.result().get(5, TimeUnit.SECONDS);
            throw new AssertionError("Did not throw expected ExecutionException!");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), IsInstanceOf.instanceOf(TimeoutException.class));
        }
    }

    private static PeriodicalChannel createChannelWithNoAvailableData(int hasDataCount, int noDataCount) {
        return new PeriodicalChannel(i -> {
            int subIndex = i % (hasDataCount + noDataCount);
            return subIndex < hasDataCount ? 512 : 0;
        }, TEST_DATA_SIZE);
    }

    private void assertByteSequence(byte[] bytes) {
        assertNotNull(bytes);
        int index = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != PeriodicalChannel.SEQUENCE[index]) {
                throw new AssertionError("Invalid (unexpected) byte in an array on position: " + i);
            }
            index++;
            if (index == PeriodicalChannel.SEQUENCE.length) {
                index = 0;
            }
        }
    }

    static class PeriodicalChannel implements ReadableByteChannel {

        static final byte[] SEQUENCE = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.US_ASCII);

        private boolean open = true;
        private int pointer = 0;

        private final IntFunction<Integer> maxChunkSize;
        private final long size;

        long count;
        int readMethodCallCounter = 0;
        final Set<Thread> threads = new HashSet<>();

        PeriodicalChannel(IntFunction<Integer> maxChunkSize, long size) {
            this.maxChunkSize = maxChunkSize;
            this.size = size;
        }

        @Override
        public synchronized int read(ByteBuffer dst) throws IOException {
            threads.add(Thread.currentThread());
            readMethodCallCounter++;
            if (!open) {
                throw new ClosedChannelException();
            }
            if (dst == null || dst.remaining() == 0) {
                return 0;
            }
            if (count >= size) {
                return -1;
            }
            // Do read
            int chunkSizeLimit = maxChunkSize.apply(readMethodCallCounter);
            int writeCounter = 0;
            while (count < size && writeCounter < chunkSizeLimit && dst.remaining() > 0) {
                count++;
                writeCounter++;
                dst.put(pick());
            }
            return writeCounter;
        }

        private byte pick() {
            byte result = SEQUENCE[pointer++];
            if (pointer >= SEQUENCE.length) {
                pointer = 0;
            }
            return result;
        }

        @Override
        public synchronized boolean isOpen() {
            return open;
        }

        @Override
        public synchronized void close() throws IOException {
            this.open = false;
        }
    }
}
