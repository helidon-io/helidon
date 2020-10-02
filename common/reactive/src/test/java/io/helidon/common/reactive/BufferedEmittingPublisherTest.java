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

package io.helidon.common.reactive;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/**
 * The BufferedEmittingPublisherTest.
 */
public class BufferedEmittingPublisherTest {

    private static final double OTHER_THREAD_EXECUTION_RATIO = 0.8;
    private static final int BOUND = 5;
    private static final int ITERATION_COUNT = 1000;
    private final AtomicLong seq = new AtomicLong(0);
    private final AtomicLong check = new AtomicLong(0);

    @Test
    public void sanityPublisherCheck() throws Exception {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        CountDownLatch finishedLatch = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<Long>(){
            Subscription subscription;
            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(Long value) {
                if (!check.compareAndSet(value - 1, value)) {
                    throw new IllegalStateException("expected: " + (value - 1) + " but found: " + check.get());
                }
                if (ThreadLocalRandom.current().nextDouble(0, 1) < OTHER_THREAD_EXECUTION_RATIO) {
                    ForkJoinPool.commonPool().submit(() -> subscription.request(ThreadLocalRandom.current().nextLong(1, BOUND)));
                } else {
                    subscription.request(ThreadLocalRandom.current().nextLong(1, BOUND));
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted() && seq.get() < ITERATION_COUNT) {
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(0, 2));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted", e);
                }
                publisher.emit(seq.incrementAndGet());
            }
            finishedLatch.countDown();
        });

        try {
            if (!finishedLatch.await(10, TimeUnit.SECONDS)) {
                fail("Didn't finish in timely manner");
            }
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void testDoubleSubscribe() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        TestSubscriber<Long> subscriber1 = new TestSubscriber<>();
        TestSubscriber<Long> subscriber2 = new TestSubscriber<>();
        publisher.subscribe(subscriber1);
        publisher.subscribe(subscriber2);
        assertThat(subscriber1.isComplete(), is(equalTo(false)));
        assertThat(subscriber2.getLastError(), is(not(nullValue())));
        assertThat(subscriber2.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testNegativeSubscription() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        TestSubscriber<Long> subscriber = new TestSubscriber<Long>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(-1);
            }
        };
        publisher.subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(not(nullValue())));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void testError() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        TestSubscriber<Long> subscriber = new TestSubscriber<>();
        publisher.subscribe(subscriber);
        publisher.fail(new IllegalStateException("foo!"));
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(not(nullValue())));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testErrorBeforeSubscribe() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        TestSubscriber<Long> subscriber = new TestSubscriber<>();
        publisher.fail(new IllegalStateException("foo!"));
        publisher.subscribe(subscriber);
        subscriber.request1();
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(not(nullValue())));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testErrorBadOnError() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        TestSubscriber<Long> subscriber = new TestSubscriber<Long>() {
            @Override
            public void onError(Throwable throwable) {
                throw new UnsupportedOperationException("foo!");
            }
        };
        publisher.subscribe(subscriber);
        try {
            publisher.fail(new IllegalStateException("foo!"));
            fail("an exception should have been thrown");
        } catch(IllegalStateException ex) {
            assertThat(ex.getCause(), is(not(nullValue())));
            assertThat(ex.getCause(), is(instanceOf(UnsupportedOperationException.class)));
        }
    }

    @Test
    public void testComplete() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        TestSubscriber<Long> subscriber = new TestSubscriber<>();
        publisher.subscribe(subscriber);
        publisher.complete();
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(publisher.isCompleted(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
    }

    @Test
    public void testSubmitBadOnNext() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        TestSubscriber<Long> subscriber = new TestSubscriber<Long>() {
            @Override
            public void onNext(Long item) {
                throw new UnsupportedOperationException("foo!");
            }
        };
        publisher.subscribe(subscriber);
        subscriber.request1();
        publisher.emit(15L);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(not(nullValue())));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getLastError().getCause(), is(instanceOf(UnsupportedOperationException.class)));
    }

    @Test
    public void testRequiresMoreItems() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        TestSubscriber<Long> subscriber = new TestSubscriber<>();
        publisher.subscribe(subscriber);
        subscriber.request1();
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(publisher.hasRequests(), is(equalTo(true)));
    }

    @Test
    public void testHookOnRequested() {
        final AtomicLong requested = new AtomicLong();
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>(){};
        publisher.onRequest((n, demand) -> requested.set(n));
        TestSubscriber<Long> subscriber = new TestSubscriber<Long>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }
        };
        publisher.subscribe(subscriber);
        assertThat(requested.get(), is(equalTo(1L)));
    }

    @Test
    public void testHookOnCancel() {
        BufferedEmittingPublisher<Long> publisher = new BufferedEmittingPublisher<Long>();
        TestSubscriber<Long> subscriber = new TestSubscriber<Long>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
            }
        };
        publisher.subscribe(subscriber);
        assertThrows(IllegalStateException.class, () -> publisher.emit(0L));
    }
}
