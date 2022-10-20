/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.isOneOf;

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

    @Test
    public void testLastDelay() {
        List<Long> lastDelayCalls = new ArrayList<>();
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> {
                    lastDelayCalls.add(lastDelay);
                    return Optional.of(lastDelay + 1);
                })
                .build();


        Request req = new Request(3, new RetryException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        result.await(1, TimeUnit.SECONDS);
        assertThat(req.call.get(), is(4));

        assertThat("Last delay should increase", lastDelayCalls, contains(0L, 1L, 2L));
    }

    @Test
    public void testMultiLastDelay() throws InterruptedException {
        List<Long> lastDelayCalls = new ArrayList<>();
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> {
                    lastDelayCalls.add(lastDelay);
                    return Optional.of(lastDelay + 1);
                })
                .build();

        AtomicInteger count = new AtomicInteger();

        TestSubscriber ts = new TestSubscriber();

        Multi<Integer> multi = retry.invokeMulti(() -> {
            if (count.getAndIncrement() < 3) {
                return Multi.error(new RetryException());
            } else {
                return Multi.just(0, 1, 2);
            }
        });

        multi.subscribe(ts);
        ts.request(2);

        ts.cdl.await(1, TimeUnit.SECONDS);

        assertThat("Last delay should increase", lastDelayCalls, contains(0L, 1L, 2L));
    }

    @Test
    public void testExceptionCause() {
        AtomicBoolean isTimeout = new AtomicBoolean(false);
        AtomicBoolean isRuntime = new AtomicBoolean(false);

        Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder().build())
                .overallTimeout(Duration.ofSeconds(1))
                .build().invoke(() -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(2000);
                        throw new RuntimeException("Hello");
                    } catch (InterruptedException e) {
                        // falls through
                    }
                }))
                .exceptionallyAccept(t -> {
                    isTimeout.set(t instanceof TimeoutException);
                    isRuntime.set(t.getCause() instanceof RuntimeException);
                })
                .await();

        assertThat("Must be a TimeoutException", isTimeout.get(), is(true));
        assertThat("Must be a RuntimeException", isRuntime.get(), is(true));
    }

    @Test
    void testRetryCancel() {
        AtomicBoolean cancelCalled = new AtomicBoolean();
        Retry retry = Retry.builder().build();
        Single<Void> single = retry.invoke(() ->
                new CompletableFuture<>() {
                    @Override
                    public boolean cancel(boolean b) {
                        cancelCalled.set(true);
                        return super.cancel(b);
                    }
                });
        single.cancel();
        assertThat("Cancel must be called", cancelCalled.get(), is(true));
    }

    @Test
    void testRetryConfig() {
        ConfigSource configSource = ConfigSources.classpath("application.yaml").build();
        Config config = Config.create(() -> configSource);

        Retry.Builder retry1 = Retry.builder()
                .config(config.get("retry1"));
        assertThat(retry1.name(), is("MyRetry1"));
        assertThat(retry1.cancelSource(), is(false));
        assertThat(retry1.overallTimeout(), is(Duration.ofSeconds(2)));
        assertThat(retry1.retryPolicy(), instanceOf(Retry.DelayingRetryPolicy.class));
        Retry.DelayingRetryPolicy policy1 = (Retry.DelayingRetryPolicy) retry1.retryPolicy();
        assertThat(policy1.calls(), is(6));
        assertThat(policy1.delay(), is(Duration.ofMillis(400)));
        assertThat(policy1.delayFactor(), is(4.0));

        Retry.Builder retry2 = Retry.builder()
                .config(config.get("retry2"));
        assertThat(retry2.name(), Matchers.is("MyRetry2"));
        assertThat(retry2.cancelSource(), Matchers.is(false));
        assertThat(retry2.overallTimeout(), is(Duration.ofSeconds(2)));
        assertThat(retry2.retryPolicy(), instanceOf(Retry.JitterRetryPolicy.class));
        Retry.JitterRetryPolicy policy2 = (Retry.JitterRetryPolicy) retry2.retryPolicy();
        assertThat(policy2.calls(), is(6));
        assertThat(policy2.delay(), is(Duration.ofMillis(400)));
    }

    @Test
    void testFibonacciRetryPolicy() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.FibonacciRetryPolicy.builder()
                        .calls(10)
                        .maxDelay(Duration.ofMillis(100))
                        .jitter(0)
                        .build())
                .overallTimeout(Duration.ofMillis(500))
                .build();


        Request req = new Request(8, new TerminalException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        int count = result.await(1, TimeUnit.SECONDS);
        assertThat(count, is(9));
    }

    @Test
    void testExponentialRetryPolicy() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.ExponentialRetryPolicy.builder()
                        .calls(5)
                        .maxDelay(Duration.ofMillis(50))
                        .jitter(0)
                        .factor(3)
                        .build())
                .overallTimeout(Duration.ofMillis(500))
                .build();


        Request req = new Request(3, new TerminalException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        int count = result.await(1, TimeUnit.SECONDS);
        assertThat(count, is(4));
    }

    @Test
    void testExponentialRetrySequence(){
        Retry.ExponentialRetryPolicy policy = Retry.ExponentialRetryPolicy.builder()
                .calls(10)
                .jitter(0)
                .factor(2)
                .build();

        long[] result = new long[10];
        for (int i = 0; i < 10; i++){
            Optional<Long> nextDelay = policy.nextDelayMillis(0, 0, i);
            result[i]=nextDelay.get();
        }
        assertThat(result, is(new long[]{2, 4, 8, 16, 32, 64, 128, 256, 512, 1024}));
    }

    @Test
    void testFibonacciRetrySequence(){
        Retry.FibonacciRetryPolicy policy = Retry.FibonacciRetryPolicy.builder()
                .initialDelay(Duration.ofMillis(1))
                .calls(10)
                .jitter(0)
                .build();

        long[] result = new long[10];
        for (int i = 0; i < 10; i++){
            Optional<Long> nextDelay = policy.nextDelayMillis(0, 0, i);
            result[i]=nextDelay.get();
        }
        assertThat(result, is(new long[]{1, 2, 3, 5, 8, 13, 21, 34, 55, 89}));
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
