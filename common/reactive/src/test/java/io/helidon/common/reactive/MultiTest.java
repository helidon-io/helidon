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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.mapper.Mapper;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link MultiTest} test.
 */
public class MultiTest {

    @Test
    public void testJust() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testJustCollection() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just(List.of("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testEmpty() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testError() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>error(new Exception("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(notNullValue()));
        assertThat(subscriber.getLastError().getMessage(), is(equalTo("foo")));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNever() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>never().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapper() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("FOO", "BAR"));
    }

    @Test
    public void testErrorCollect() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>error(new Exception("foo")).collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(notNullValue()));
        assertThat(subscriber.getLastError().getMessage(), is(equalTo("foo")));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectList() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems().get(0), hasItems("foo", "bar"));
    }

    @Test
    public void testFromPublisher() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.from(new TestPublisher<>("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testFromMulti() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.from(Multi.<String>just("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testFirst() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").first().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems().get(0), is(equalTo("foo")));
    }

    @Test
    public void testErrorFirst() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>error(new IllegalStateException("foo!")).first().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorBadCollectMethod() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
                throw new IllegalStateException("foo!");
            }

            @Override
            public List<String> value() {
                return Collections.emptyList();
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorBadValueMethod() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
            }

            @Override
            public List<String> value() {
                throw new IllegalStateException("foo!");
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorBadNullValue() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
            }

            @Override
            public List<String> value() {
                return null;
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNeverMap() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
                subscription.request(1);
            }
        };
        Multi.<String>never().map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapBadMapper() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").map(new Mapper<String, String>() {
            @Override
            public String map(String item) {
                throw new IllegalStateException("foo!");
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapBadMapperNullValue() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").map((Mapper<String, String>) item -> null).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(NullPointerException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    void testPeekInt() {
        AtomicInteger sum1 = new AtomicInteger();
        AtomicInteger sum2 = new AtomicInteger();
        Multi.just(1, 2, 3)
                .peek(sum1::addAndGet)
                .forEach(sum2::addAndGet);

        assertThat(sum1.get(), is(equalTo(1 + 2 + 3)));
        assertThat(sum1.get(), is(equalTo(sum2.get())));
    }

    @Test
    void testPeekString() {
        StringBuilder sbBefore = new StringBuilder();
        AtomicInteger sum = new AtomicInteger();
        Multi.just("1", "2", "3")
                .peek(sbBefore::append)
                .map(Integer::parseInt)
                .forEach(sum::addAndGet);
        assertThat(sbBefore.toString(), is(equalTo("123")));
        assertThat(sum.get(), is(equalTo(1 + 2 + 3)));
    }

    @Test
    void testFilter() {
        StringBuilder sbBefore = new StringBuilder();
        AtomicInteger sum = new AtomicInteger();
        Multi.just("1", "2", "3")
                .peek(sbBefore::append)
                .map(Integer::parseInt)
                .filter(i -> i != 2)
                .forEach(sum::addAndGet);
        assertThat(sbBefore.toString(), is(equalTo("123")));
        assertThat(sum.get(), is(equalTo(1 + 3)));
    }

    @Test
    void testLimit() {
        final List<Integer> TEST_DATA = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 9);
        final long TEST_LIMIT = 3;
        final int EXPECTED_SUM = 1 + 2 + 3;

        AtomicInteger multiSum1 = new AtomicInteger();
        AtomicInteger multiSum2 = new AtomicInteger();

        Multi.just(TEST_DATA)
                .limit(TEST_LIMIT)
                .peek(multiSum1::addAndGet)
                .forEach(multiSum2::addAndGet);

        AtomicInteger streamSum1 = new AtomicInteger();
        AtomicInteger streamSum2 = new AtomicInteger();

        TEST_DATA.stream()
                .limit(TEST_LIMIT)
                .peek(streamSum1::addAndGet)
                .forEach(streamSum2::addAndGet);

        assertThat(multiSum2.get(), is(equalTo(EXPECTED_SUM)));
        assertThat(streamSum1.get(), is(equalTo(EXPECTED_SUM)));
        assertThat(streamSum2.get(), is(equalTo(EXPECTED_SUM)));
    }

    @Test
    void testSkip() throws ExecutionException, InterruptedException {
        final List<Integer> TEST_DATA = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 9);
        final long TEST_SKIP = 3;
        final List<Integer> EXPECTED = Arrays.asList(4, 5, 6, 7, 9);

        List<Integer> result = Multi.just(TEST_DATA)
                .skip(TEST_SKIP)
                .collectList().get();

        assertThat(result, is(equalTo(EXPECTED)));
    }

    @Test
    void testTakeWhile() throws ExecutionException, InterruptedException {
        final List<Integer> TEST_DATA = Arrays.asList(1, 2, 3, 4, 3, 2, 1, 0);
        final List<Integer> EXPECTED = Arrays.asList(1, 2, 3);

        List<Integer> result = Multi.just(TEST_DATA)
                .takeWhile(i -> i < 4)
                .collectList().get();

        assertThat(result, is(equalTo(EXPECTED)));
    }

    @Test
    void testDropWhile() throws ExecutionException, InterruptedException {
        final List<Integer> TEST_DATA = Arrays.asList(1, 2, 3, 4, 3, 2, 1, 0);
        final List<Integer> EXPECTED = Arrays.asList(4, 3, 2, 1, 0);

        List<Integer> result = Multi.just(TEST_DATA)
                .dropWhile(i -> i < 4)
                .collectList().get();

        assertThat(result, is(equalTo(EXPECTED)));
    }


    @Test
    void testOnErrorResumeWith() throws ExecutionException, InterruptedException, TimeoutException {
        List<Integer> result = Multi.<Integer>error(new RuntimeException())
                .onErrorResumeWith(throwable -> Multi.just(1, 2, 3))
                .collectList()
                .get(100, TimeUnit.MILLISECONDS);

        assertThat(result, is(equalTo(List.of(1, 2, 3))));
    }

    @Test
    void testFlatMap() throws ExecutionException, InterruptedException {
        final List<String> TEST_DATA = Arrays.asList("abc", "xyz");
        final List<String> EXPECTED = Arrays.asList("a", "b", "c", "x", "y", "z");
        List<String> result = Multi.just(TEST_DATA)
                .flatMap(s -> Multi.just(s.chars().mapToObj(Character::toString).collect(Collectors.toList())))
                .collectList()
                .get();
        assertThat(result, is(equalTo(EXPECTED)));
    }

    @Test
    void testFlatMapIterable() throws ExecutionException, InterruptedException {
        final List<String> TEST_DATA = Arrays.asList("abc", "xyz");
        final List<String> EXPECTED = Arrays.asList("a", "b", "c", "x", "y", "z");
        List<String> result = Multi.just(TEST_DATA)
                .flatMapIterable(s -> s.chars().mapToObj(Character::toString).collect(Collectors.toList()))
                .collectList()
                .get();
        assertThat(result, is(equalTo(EXPECTED)));
    }

    @Test
    void testOnErrorResume() throws ExecutionException, InterruptedException, TimeoutException {
        Integer result = Multi.<Integer>error(new RuntimeException())
                .onErrorResume(throwable -> 1)
                .first()
                .get(100, TimeUnit.MILLISECONDS);

        assertThat(result, is(equalTo(1)));
    }

    @Test
    void testOnError() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Throwable> beenError = new CompletableFuture<>();
        Multi.<Integer>error(new RuntimeException())
                .onError(beenError::complete)
                .collectList();

        beenError.get(1, TimeUnit.SECONDS);
    }

    @Test
    void testOnComplete() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        Multi.just(1, 2, 3)
                .onComplete(() -> completed.complete(null))
                .collectList()
                .get(100, TimeUnit.MILLISECONDS);

        completed.get(1, TimeUnit.SECONDS);
    }

    @Test
    void testOnTerminate() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        CompletableFuture<Throwable> beenError = new CompletableFuture<>();

        Multi.just(1, 2, 3)
                .onTerminate(() -> completed.complete(null))
                .first()
                .get(100, TimeUnit.MILLISECONDS);

        Multi.<Integer>error(new RuntimeException())
                .onTerminate(() -> beenError.complete(null))
                .first();

        beenError.get(1, TimeUnit.SECONDS);
        completed.get(1, TimeUnit.SECONDS);
    }

    @Test
    void testConcat() throws ExecutionException, InterruptedException {
        final List<Integer> TEST_DATA_1 = Arrays.asList(1, 2, 3, 4, 3, 2, 1, 0);
        final List<Integer> TEST_DATA_2 = Arrays.asList(11, 12, 13, 14, 13, 12, 11, 10);
        final List<Integer> EXPECTED = Stream.concat(TEST_DATA_1.stream(), TEST_DATA_2.stream())
                .collect(Collectors.toList());

        List<Integer> result = Multi
                .concat(Multi.from(TEST_DATA_1), Multi.just(TEST_DATA_2))
                .collectList()
                .get();

        assertThat(result, is(equalTo(EXPECTED)));
    }

    @Test
    void testConcatWithError() throws ExecutionException, InterruptedException {
        Assertions.assertThrows(Exception.class, () -> Multi.concat(Multi.just(1, 2, 3), Multi.error(new Exception()))
                .collectList()
                .get());
        Assertions.assertThrows(Exception.class, () -> Multi.concat(Multi.error(new Exception()), Multi.just(1, 2, 3))
                .collectList()
                .get());
    }

    @Test
    void distinct() throws ExecutionException, InterruptedException {
        final List<Integer> TEST_DATA = Arrays.asList(1, 2, 1, 2, 3, 2, 1, 3);
        final List<Integer> EXPECTED = Arrays.asList(1, 2, 3);

        List<Integer> result = Multi.just(TEST_DATA)
                .distinct()
                .collectList().get();

        assertThat(result, is(equalTo(EXPECTED)));
    }

    private static class MultiTestSubscriber<T> extends TestSubscriber<T> {

        @Override
        public void onSubscribe(Subscription subscription) {
            super.onSubscribe(subscription);
            requestMax();
        }
    }
}
