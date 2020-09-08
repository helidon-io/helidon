/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryTest {
    @Test
    void testRetry() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(50))
                                     .jitter(Duration.ofMillis(50))
                                     .build())
                .overallTimeout(Duration.ofMillis(500))
                .build();

        Request req = new Request(3, new TerminalException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, TerminalException.class);
        assertThat(req.call.get(), is(3));

        req = new Request(2, new TerminalException(), new RetryException());
        result = retry.invoke(req::invoke);
        int count = result.await(1, TimeUnit.SECONDS);
        assertThat(count, is(3));
    }

    @Test
    void testRetryOn() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(100))
                                     .jitter(Duration.ofMillis(50))
                                     .build())
                .overallTimeout(Duration.ofMillis(500))
                .addApplyOn(RetryException.class)
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, RetryException.class);
        assertThat(req.call.get(), is(3));

        req = new Request(2, new RetryException(), new TerminalException());
        result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, TerminalException.class);
        assertThat(req.call.get(), is(2));

        req = new Request(2, new RetryException(), new RetryException());
        result = retry.invoke(req::invoke);
        int count = result.await(1, TimeUnit.SECONDS);
        assertThat(count, is(3));
    }

    @Test
    void testAbortOn() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(100))
                                     .jitter(Duration.ofMillis(50))
                                     .build())
                .overallTimeout(Duration.ofMillis(50000))
                .addSkipOn(TerminalException.class)
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, RetryException.class);
        assertThat(req.call.get(), is(3));

        req = new Request(2, new RetryException(), new TerminalException());
        result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, TerminalException.class);
        assertThat(req.call.get(), is(2));

        req = new Request(2, new RetryException(), new RetryException());
        result = retry.invoke(req::invoke);
        int count = result.await(1, TimeUnit.SECONDS);
        assertThat(count, is(3));
    }

    @Test
    void testTimeout() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(100))
                                     .jitter(Duration.ZERO)
                                     .build())
                .overallTimeout(Duration.ofMillis(50))
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, TimeoutException.class);
        // first time: immediate call
        // second time: delayed invocation or timeout in very slow system
        // third attempt to retry fails on timeout
        assertThat("Should have been called twice", req.call.get(), isOneOf(1, 2));
    }

    @Test
    void testMultiRetriesNoFailure() throws InterruptedException {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.noDelay(3))
                .build();

        Multi<Integer> multi = retry.invokeMulti(() -> Multi.just(0, 1, 2));

        TestSubscriber ts = new TestSubscriber();
        multi.subscribe(ts);
        ts.request(100);

        ts.cdl.await(1, TimeUnit.SECONDS);

        assertThat("Should be completed", ts.completed.get(), is(true));
        assertThat("Should not be failed", ts.failed.get(), is(false));
        assertThat(ts.values, contains(0, 1, 2));
    }

    @Test
    void testMultiRetries() throws InterruptedException {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.noDelay(3))
                .build();

        AtomicInteger count = new AtomicInteger();

        Multi<Integer> multi = retry.invokeMulti(() -> {
            if (count.getAndIncrement() < 2) {
                return Multi.error(new RetryException());
            } else {
                return Multi.just(0, 1, 2);
            }
        });

        TestSubscriber ts = new TestSubscriber();
        multi.subscribe(ts);
        ts.request(100);

        ts.cdl.await(1, TimeUnit.SECONDS);

        assertThat("Should be completed", ts.completed.get(), is(true));
        assertThat("Should not be failed", ts.failed.get(), is(false));
        assertThat(ts.values, contains(0, 1, 2));
    }

    @Test
    void testMultiRetriesRead() throws InterruptedException {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.noDelay(3))
                .build();

        AtomicInteger count = new AtomicInteger();

        TestSubscriber ts = new TestSubscriber();

        Multi<Integer> multi = retry.invokeMulti(() -> {
            if (count.getAndIncrement() == 0) {
                //return new PartialPublisher();
                return Multi.concat(Multi.just(0), Multi.error(new RetryException()));
            } else {
                return Multi.just(0, 1, 2);
            }
        });

        multi.subscribe(ts);
        ts.request(2);

        ts.cdl.await(1, TimeUnit.SECONDS);

        assertThat("Should be failed", ts.failed.get(), is(true));
        assertThat(ts.throwable.get(), instanceOf(RetryException.class));
        assertThat("Should not be completed", ts.completed.get(), is(false));

    }

    private static class PartialPublisher implements Flow.Publisher<Integer> {
        @Override
        public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onNext(1);
                    subscriber.onError(new RetryException());
                }

                @Override
                public void cancel() {

                }
            });
        }
    }

    private static class TestSubscriber implements Flow.Subscriber<Integer> {
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicReference<Throwable> throwable = new AtomicReference<>();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final List<Integer> values = new LinkedList<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final CountDownLatch cdl = new CountDownLatch(1);

        private Flow.Subscription subscription;

        void request(long n) {
            subscription.request(n);
        }

        void cancel() {
            subscription.cancel();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(Integer item) {
            values.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            failed.set(true);
            this.throwable.set(throwable);
            finish();
        }

        @Override
        public void onComplete() {
            this.completed.set(true);
            finish();
        }

        private void finish() {
            if (finished.compareAndSet(false, true)) {
                cdl.countDown();
            }
        }
    }

    private static class Request {
        private final AtomicInteger call = new AtomicInteger();
        private final int failures;
        private final RuntimeException first;
        private final RuntimeException second;

        private Request(int failures, RuntimeException first, RuntimeException second) {
            this.failures = failures;
            this.first = first;
            this.second = second;
        }

        CompletionStage<Integer> invoke() {
            //failures 1
            // call
            int now = call.incrementAndGet();
            if (now <= failures) {
                if (now == 1) {
                    throw first;
                } else if (now == 2) {
                    throw second;
                } else {
                    throw first;
                }
            }
            return Single.just(now);
        }
    }

    private static class RetryException extends RuntimeException {
    }

    private static class TerminalException extends RuntimeException {
    }
}