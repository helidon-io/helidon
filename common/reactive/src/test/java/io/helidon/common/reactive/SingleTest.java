/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link Single} test.
 */
public class SingleTest {

    static final String TEST_PAYLOAD = "test-payload";

    @Test
    public void testJust() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testJustCanceledSubscription() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
                subscription.request(Long.MAX_VALUE);
            }
        };
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testJustNegativeSubscription() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(-1);
            }
        };
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testJustNegativeCanceledSubscription() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
                subscription.request(-1);
            }
        };
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testJustDoubleSubscriptionRequest() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(1);
                subscription.request(1);
            }
        };
        Single.<String>just("foo").subscribe(subscriber);
        subscriber.assertResult("foo");
    }

    @Test
    public void testEmpty() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<>();
        Single.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testEmptyCanceledSubscription() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<Object>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.cancel();
            }
        };
        Single.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testError() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<>();
        Single.<Object>error(new Exception("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(notNullValue()));
        assertThat(subscriber.getLastError().getMessage(), is(equalTo("foo")));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNever() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<>();
        Single.<Object>never().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNeverIsNotSingleton() throws InterruptedException, TimeoutException, ExecutionException {
        CompletableFuture<Void> cf1 = new CompletableFuture<>();
        CompletableFuture<Void> cf2 = new CompletableFuture<>();
        Single<Object> never1 = Single.never();
        Single<Object> never2 = Single.never();

        never1.onCancel(() -> cf1.complete(null));
        never2.onCancel(() -> cf2.complete(null));
        never1.cancel();

        cf1.get(100, TimeUnit.MILLISECONDS);
        assertThat("First Single.never should be cancelled!", cf1.isDone());
        assertThat("Other Single.never should NOT be cancelled!", !cf2.isDone());
    }

    @Test
    public void testMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("foo").map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("FOO"));
    }

    @Test
    public void testMapNullMapper() {
        try {
            Single.just("foo").map(null);
            fail("NullPointerException should be thrown");
        } catch (NullPointerException ex) {
        }
    }

    @Test
    public void testMapBadMapperNullValue() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("bar").map((s) -> (String) null).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(NullPointerException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testErrorMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>error(new IllegalStateException("foo!")).map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testEmptyMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>empty().map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNeverMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>never().map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testFromPublisher() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.create(new TestPublisher<>("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testFromPublisherMoreThanOne() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.create(new TestPublisher<>("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testFromSingle() {
        Single<String> single = Single.just("foo");
        assertThat(Single.create(single), is(equalTo(single)));
    }

    @Test
    public void testFlatMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("f.o.o")
                .flatMap((str) -> new TestPublisher<>(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("f", "o", "o"));
    }

    @Test
    public void testEmptyFlatMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>empty()
                .flatMap((str) -> new TestPublisher<>(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testErrorFlatMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>error(new IllegalStateException("foo!"))
                .flatMap((str) -> new TestPublisher<>(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNeverFlatMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>never()
                .flatMap((str) -> new TestPublisher<>(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testFlatMapNullMapper() {
        try {
            Single.just("bar").flatMap(null);
            fail("NullPointerException should have been thrown");
        } catch (NullPointerException ex) {
        }
    }

    @Test
    public void testFlatMapMapperNullValue() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("bar").flatMap((s) -> (Multi<String>) null).subscribe(subscriber);
        subscriber.assertFailure(NullPointerException.class);
    }

    @Test
    public void testBadSingleToFuture() throws InterruptedException, TimeoutException {
        Single<String> single = new CompletionSingle<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                throw new IllegalStateException("foo!");
            }
        };
        try {
            single.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    public void testEmptyToFuture() throws InterruptedException, TimeoutException {
        try {
            Single.<Object>empty().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    public void testToFutureDoubleOnError() throws InterruptedException, TimeoutException {
        Single<String> single = new CompletionSingle<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        subscriber.onError(new IllegalStateException("foo!"));
                        subscriber.onError(new IllegalStateException("foo!"));
                    }

                    @Override
                    public void cancel() {
                    }
                });
            }
        };
        try {
            single.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    public void testToFutureDoubleOnNext() throws InterruptedException, ExecutionException {
        Single<String> single = new CompletionSingle<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        subscriber.onNext("foo");
                        subscriber.onNext("bar");
                        subscriber.onComplete();
                    }

                    @Override
                    public void cancel() {
                    }
                });
            }
        };
        Future<String> future = single.toStage().toCompletableFuture();
        assertThat(future.isDone(), is(equalTo(true)));
        assertThat(future.get(), is(equalTo("foo")));
    }

    @Test
    public void testToFutureCancel() throws InterruptedException, ExecutionException {
        Future<String> future = Single.just("foo").toStage().toCompletableFuture();
        assertThat(future.cancel(true), is(equalTo(false)));
        assertThat(future.get(), is(equalTo("foo")));
    }

    @Test
    public void testNeverToFutureCancel() throws InterruptedException, ExecutionException {
        Future<String> future = Single.<String>never().toStage().toCompletableFuture();
        assertThat(future.cancel(true), is(equalTo(true)));
        assertThat(future.isCancelled(), is(equalTo(true)));
    }

    @Test
    public void testNeverToFutureDoubleCancel() throws InterruptedException, ExecutionException {
        Future<String> future = Single.<String>never().toStage().toCompletableFuture();
        assertThat(future.cancel(true), is(equalTo(true)));
        assertThat(future.cancel(true), is(equalTo(true)));
        assertThat(future.isCancelled(), is(equalTo(true)));
    }

    @Test
    public void testToFutureDoubleOnSubscribe() throws InterruptedException, ExecutionException {
        TestSubscription subscription1 = new TestSubscription();
        TestSubscription subscription2 = new TestSubscription();
        Single<String> single = new CompletionSingle<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(subscription1);
                subscriber.onSubscribe(subscription2);
            }
        };
        Future<String> future = single.toStage().toCompletableFuture();
        assertThat(future.isDone(), is(equalTo(false)));
        assertThat(subscription1.canceled, is(equalTo(true)));
        assertThat(subscription2.canceled, is(equalTo(true)));
    }

    @Test
    void exceptionThrowingMapper() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("foo").<String>map(s -> {
            throw new IllegalStateException("bar!");
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    void testOnCompleteResume() {
        List<Integer> result = Single.just(1)
                .onCompleteResume(4)
                .collectList()
                .await(100, TimeUnit.MILLISECONDS);

        assertThat(result, is(equalTo(List.of(1, 4))));
    }

    @Test
    void testOnCompleteResumeWith() {
        List<Integer> result = Single.just(1)
                .onCompleteResumeWith(Multi.just(4, 5, 6))
                .collectList()
                .await(100, TimeUnit.MILLISECONDS);

        assertThat(result, is(equalTo(List.of(1, 4, 5, 6))));
    }

    @Test
    void testOnCompleteResumeWithFirst() {
        Integer result = Single.<Integer>empty()
                .onCompleteResume(1)
                .first()
                .await(100, TimeUnit.MILLISECONDS);

        assertThat(result, is(equalTo(1)));
    }

    @Test
    void testSingleOnComplete() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> onCompleteFuture = new CompletableFuture<>();

        assertThat(Single.just(TEST_PAYLOAD)
                .onComplete(() -> onCompleteFuture.complete(null))
                .await(100, TimeUnit.MILLISECONDS), is(equalTo(TEST_PAYLOAD)));
        onCompleteFuture.get(100, TimeUnit.MILLISECONDS);
    }

    @Test
    void testSingleOnCancel() throws InterruptedException {
        CountDownLatch onCancelCnt = new CountDownLatch(5);
        CountDownLatch onCompleteCnt = new CountDownLatch(2);
        CountDownLatch onErrCnt = new CountDownLatch(1);
        Single.just(TEST_PAYLOAD)
                .onComplete(onCompleteCnt::countDown)
                .map(String::toUpperCase)
                .onCancel(onCancelCnt::countDown)
                .onCancel(onCancelCnt::countDown)
                .map(String::toUpperCase)
                .onCancel(onCancelCnt::countDown)
                .map(String::toLowerCase)
                .onCancel(onCancelCnt::countDown)
                .onCancel(onCancelCnt::countDown)
                .onError(throwable -> onErrCnt.countDown())
                .onComplete(onCompleteCnt::countDown)
                .cancel();

        onCancelCnt.await(100, TimeUnit.MILLISECONDS);
        assertThat("At least one test onCancel callback was not called",
                onCancelCnt.getCount(), is(equalTo(0L)));

        onCompleteCnt.await(5, TimeUnit.MILLISECONDS);
        assertThat("OnError callback was not expected",
                onCompleteCnt.getCount(), is(equalTo(2L)));

        onErrCnt.await(5, TimeUnit.MILLISECONDS);
        assertThat("",
                onErrCnt.getCount(), is(equalTo(1L)));
    }

    @Test
    void testForSingle() {
        AtomicInteger onCancelCnt = new AtomicInteger(0);
        AtomicInteger onCompleteCnt = new AtomicInteger(0);
        AtomicInteger onErrorCnt = new AtomicInteger(0);
        CompletableFuture<String> result = new CompletableFuture<>();

        Single.just(TEST_PAYLOAD)
                .onCancel(onCancelCnt::incrementAndGet)
                .onComplete(onCompleteCnt::incrementAndGet)
                .onError(t -> onErrorCnt.incrementAndGet())
                .forSingle(result::complete);

        assertThat(Single.create(result).await(300, TimeUnit.MILLISECONDS), is(TEST_PAYLOAD));
        assertThat(onCancelCnt.get(), is(0));
        assertThat(onErrorCnt.get(), is(0));
        assertThat(onCompleteCnt.get(), is(1));
    }

    @Test
    void testForSingleErrorAwait() throws InterruptedException {
        AtomicInteger onCancelCnt = new AtomicInteger(0);
        AtomicInteger onCompleteCnt = new AtomicInteger(0);
        CountDownLatch onErrorCnt = new CountDownLatch(1);
        CompletableFuture<String> result = new CompletableFuture<>();

        RuntimeException testException = new RuntimeException("BOOM!!!");

        CompletionException actualException = assertThrows(CompletionException.class,
                () -> Single.<String>error(testException)
                        .onCancel(onCancelCnt::incrementAndGet)
                        .onComplete(onCompleteCnt::incrementAndGet)
                        .onError(t -> onErrorCnt.countDown())
                        .forSingle(result::complete)
                        .await(300, TimeUnit.MILLISECONDS));

        assertThat(onErrorCnt.await(300, TimeUnit.MILLISECONDS), is(true));
        assertThat(actualException.getCause(), is(testException));
        assertThat(result.isDone(), is(false));
        assertThat(onCancelCnt.get(), is(0));
        assertThat(onCompleteCnt.get(), is(0));
    }

    @Test
    void testForSingleErrorNoAwait() throws InterruptedException {
        AtomicInteger onCancelCnt = new AtomicInteger(0);
        AtomicInteger onCompleteCnt = new AtomicInteger(0);
        CountDownLatch onErrorCnt = new CountDownLatch(1);
        CompletableFuture<String> result = new CompletableFuture<>();

        RuntimeException testException = new RuntimeException("BOOM!!!");

        Single.<String>error(testException)
                .onCancel(onCancelCnt::incrementAndGet)
                .onComplete(onCompleteCnt::incrementAndGet)
                .onError(t -> onErrorCnt.countDown())
                .forSingle(result::complete);

        assertThat(onErrorCnt.await(300, TimeUnit.MILLISECONDS), is(true));
        assertThat(result.isDone(), is(false));
        assertThat(onCancelCnt.get(), is(0));
        assertThat(onCompleteCnt.get(), is(0));
    }

    @Test
    void testForSingleException() {
        AtomicInteger onCancelCnt = new AtomicInteger(0);
        AtomicInteger onCompleteCnt = new AtomicInteger(0);
        AtomicInteger onErrorCnt = new AtomicInteger(0);
        CompletableFuture<Throwable> result = new CompletableFuture<>();

        RuntimeException testException = new RuntimeException("BOOM!!!");

        Single.just(TEST_PAYLOAD)
                .onCancel(onCancelCnt::incrementAndGet)
                .onComplete(onCompleteCnt::incrementAndGet)
                .onError(t -> onErrorCnt.incrementAndGet())
                .forSingle(s -> {
                    throw testException;
                })
                .exceptionallyAccept(result::complete);

        assertThat(Single.create(result).await(300, TimeUnit.MILLISECONDS).getCause(), is(testException));
        assertThat(onCancelCnt.get(), is(0));
        assertThat(onErrorCnt.get(), is(0));
        assertThat(onCompleteCnt.get(), is(1));
    }

    @Test
    void testTraditionalExceptionallyWithFunction() {
        CompletableFuture<Throwable> exceptionallyFuture = new CompletableFuture<>();

        RuntimeException testException = new RuntimeException("BOOM!!!");
        Single.error(testException)
                .exceptionally(value -> {
                    exceptionallyFuture.complete(value);
                    return null;
                })
                .await(300, TimeUnit.MILLISECONDS);

        assertThat(Single.create(exceptionallyFuture).await(300, TimeUnit.MILLISECONDS), is(testException));
    }

    @Test
    void testExceptionallyWithConsumer() {
        CompletableFuture<Throwable> exceptionallyFuture = new CompletableFuture<>();

        RuntimeException testException = new RuntimeException("BOOM!!!");
        Single.error(testException)
                .exceptionallyAccept(exceptionallyFuture::complete)
                .await(300, TimeUnit.MILLISECONDS);

        assertThat(Single.create(exceptionallyFuture).await(300, TimeUnit.MILLISECONDS), is(testException));
    }

    @Test
    void testExceptionallyWithoutException() {
        CompletableFuture<Throwable> exceptionallyFuture = new CompletableFuture<>();

        String result = Single.just(TEST_PAYLOAD)
                .exceptionallyAccept(exceptionallyFuture::complete)
                .await(300, TimeUnit.MILLISECONDS);

        assertThat(result, is(TEST_PAYLOAD));
    }

    private static class SingleTestSubscriber<T> extends TestSubscriber<T> {

        @Override
        public void onSubscribe(Subscription subscription) {
            super.onSubscribe(subscription);
            requestMax();
        }
    }

}
