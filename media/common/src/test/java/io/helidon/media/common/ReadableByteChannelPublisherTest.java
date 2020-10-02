/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.media.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import io.helidon.common.LazyValue;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.RetrySchema;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link io.helidon.media.common.ReadableByteChannelPublisher}.
 */
public class ReadableByteChannelPublisherTest {

    private static final int TEST_DATA_SIZE = 250 * 1024;

    @Test
    void allData() throws Exception {
        PeriodicalChannel pc = new PeriodicalChannel(i -> 256, TEST_DATA_SIZE);
        CountingOnNextDelegatingPublisher publisher = new CountingOnNextDelegatingPublisher(
                new ReadableByteChannelPublisher(pc, RetrySchema.constant(5)));
        // assert
        byte[] bytes = ContentReaders.readBytes(publisher).get(5, TimeUnit.SECONDS);
        assertThat(bytes.length, is(TEST_DATA_SIZE));
        assertByteSequence(bytes);
        assertThat(pc.threads.size(), is(1));
        assertThat(pc.isOpen(), is(false));
        assertThat("Publisher did not concatenate read results to minimize output chunks!",
                   pc.readMethodCallCounter > (publisher.onNextCounter() * 2),
                   is(true));

    }

    @Test
    void chunky() throws Exception {
        PeriodicalChannel pc = createChannelWithNoAvailableData(25, 3);
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, RetrySchema.constant(2));
        // assert
        byte[] bytes = ContentReaders.readBytes(publisher).get(5, TimeUnit.SECONDS);
        assertThat(bytes.length, is(TEST_DATA_SIZE));
        assertByteSequence(bytes);
        assertThat(pc.threads.size(), is(2));
        assertThat(pc.isOpen(), is(false));

        LazyValue<ScheduledExecutorService> executor = publisher.executor();
        assertThat("Executor should have been used", executor.isLoaded(), is(true));
        assertThat("Executor should have been shut down", executor.get().isShutdown(), is(true));
    }

    @Test
    void chunkyNoDelay() throws Exception {
        PeriodicalChannel pc = createChannelWithNoAvailableData(10, 3);
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, RetrySchema.constant(0));
        // assert
        byte[] bytes = ContentReaders.readBytes(publisher).get(5, TimeUnit.SECONDS);
        assertThat(bytes.length, is(TEST_DATA_SIZE));
        assertByteSequence(bytes);
        assertThat(pc.threads.size(), is(1));
        assertThat(pc.isOpen(), is(false));
    }

    @Test
    void onClosedChannel() throws Exception {
        PeriodicalChannel pc = new PeriodicalChannel(i -> 1024, TEST_DATA_SIZE);
        pc.close();
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, RetrySchema.constant(0));
        // assert
        try {
            ContentReaders.readBytes(publisher).get(5, TimeUnit.SECONDS);
            fail("Did not throw expected ExecutionException!");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(ClosedChannelException.class));
        }

        LazyValue<ScheduledExecutorService> executor = publisher.executor();
        assertThat("Executor should have not been used", executor.isLoaded(), is(false));
    }

    @Test
    @Disabled("This test uses a sleep, so could cause issues on slow environments")
    void onClosedInProgress() throws Exception {
        PeriodicalChannel pc = createChannelWithNoAvailableData(5, 2);

        RetrySchema schema = RetrySchema.constant(TimeUnit.SECONDS.toMillis(2));
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, schema);

        // start reading (this will cause 2 second delay)
        Single<byte[]> data = ContentReaders.readBytes(publisher);
        // run the stream
        data.thenRun(() -> {
        });
        Thread.sleep(1000);
        // immediately close the channel, so we fail reading
        pc.close();

        CompletionException c = assertThrows(CompletionException.class, () -> data.await(5, TimeUnit.SECONDS));
        assertThat(c.getCause(), instanceOf(ClosedChannelException.class));

        LazyValue<ScheduledExecutorService> executor = publisher.executor();
        assertThat("Executor should have been used", executor.isLoaded(), is(true));
        assertThat("Executor should have been shut down", executor.get().isShutdown(), is(true));
    }

    @Test
    void testCancelled() throws Exception {
        PeriodicalChannel pc = createChannelWithNoAvailableData(5, 1);

        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, RetrySchema.constant(100));

        AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();
        final CountDownLatch onNextCalled = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean completeCalled = new AtomicBoolean();

        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscriptionRef.set(subscription);
                subscription.request(1);
            }

            @Override
            public void onNext(DataChunk item) {
                onNextCalled.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
            }

            @Override
            public void onComplete() {
                completeCalled.set(true);
            }
        });

        onNextCalled.await(5, TimeUnit.SECONDS);
        subscriptionRef.get().cancel();

        assertThat("Should not complete", completeCalled.get(), is(false));
        assertThat("Exception should be null", failure.get(), is(nullValue()));

        LazyValue<ScheduledExecutorService> executor = publisher.executor();
        assertThat("Executor should have been used", executor.isLoaded(), is(true));
        assertThat("Executor should have been shut down", executor.get().isShutdown(), is(true));
    }

    @Test
    void negativeDelay() throws Exception {
        PeriodicalChannel pc = createChannelWithNoAvailableData(10, 1);

        RetrySchema schema = (i, delay) -> i >= 3 ? -10 : 0;
        ReadableByteChannelPublisher publisher = new ReadableByteChannelPublisher(pc, schema);

        // assert
        try {
            ContentReaders.readBytes(publisher).get(5, TimeUnit.SECONDS);
            fail("Did not throw expected ExecutionException!");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(TimeoutException.class));
        }
    }

    private static PeriodicalChannel createChannelWithNoAvailableData(int hasDataCount, int noDataCount) {

        return new PeriodicalChannel(i -> {
            int subIndex = i % (hasDataCount + noDataCount);
            return subIndex < hasDataCount ? 512 : 0;
        }, TEST_DATA_SIZE);
    }

    private void assertByteSequence(byte[] bytes) {
        assertThat(bytes, notNullValue());
        int index = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != PeriodicalChannel.SEQUENCE[index]) {
                fail("Invalid (unexpected) byte in an array on position: " + i);
            }
            index++;
            if (index == PeriodicalChannel.SEQUENCE.length) {
                index = 0;
            }
        }
    }

    private static class CountingOnNextSubscriber implements Subscriber<DataChunk> {

        private final Subscriber<? super DataChunk> delegate;
        private volatile int onNextCount;

        CountingOnNextSubscriber(Subscriber<? super DataChunk> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(DataChunk item) {
            onNextCount++;
            delegate.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }

    static class CountingOnNextDelegatingPublisher implements Publisher<DataChunk> {

        private final Publisher<DataChunk> delegate;
        private CountingOnNextSubscriber subscriber;

        CountingOnNextDelegatingPublisher(Publisher<DataChunk> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void subscribe(Subscriber<? super DataChunk> subscriber) {
            if (this.subscriber == null) {
                this.subscriber = new CountingOnNextSubscriber(subscriber);
            }
            delegate.subscribe(this.subscriber);
        }

        int onNextCounter() {
            return subscriber == null ? 0 : subscriber.onNextCount;
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
