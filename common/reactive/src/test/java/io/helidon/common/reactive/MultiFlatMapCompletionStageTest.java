/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

public class MultiFlatMapCompletionStageTest {

    private static ExecutorService exec;

    @BeforeAll
    static void beforeAll() {
        exec = Executors.newFixedThreadPool(5);
    }

    @AfterAll
    static void afterAll() {
        exec.shutdown();
    }

    @Test
    void singleThread() {
        List<Integer> result = Multi.just(1, 2, 3, 4)
                .flatMapCompletionStage(CompletableFuture::completedFuture)
                .collectList()
                .await(100, TimeUnit.MILLISECONDS);

        assertThat(result, Matchers.contains(1, 2, 3, 4));
    }

    @Test
    void voidCs() {
        Throwable result = Multi.just(1, 2, 3, 4)
                .flatMapCompletionStage(i -> CompletableFuture.<Void>completedFuture(null))
                .map(Throwable.class::cast)
                .onErrorResume(Function.identity())
                .first()
                .await(100, TimeUnit.MILLISECONDS);

        assertThat(result, Matchers.instanceOf(NullPointerException.class));
    }

    @Test
    void multipleVoidCs() {
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        Multi.just(1, 2, 3, 4)
                .flatMapCompletionStage(i -> CompletableFuture.completedFuture(i < 3 ? i : null))
                .subscribe(subscriber);

        subscriber.requestMax();
        subscriber.awaitDone(200, TimeUnit.MILLISECONDS);
        subscriber.assertValues(1,2);
        subscriber.assertError(NullPointerException.class);
    }

    @RepeatedTest(30)
    void multiThread() {
        List<Integer> result = Multi.just(10, 0, 8, 1)
                .flatMapCompletionStage(i -> i == 0
                        ? CompletableFuture.completedFuture(i)
                        : CompletableFuture.supplyAsync(() ->
                {
                    try {
                        Thread.sleep(i);
                        return i;
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }, exec))
                .collectList()
                .await(2, TimeUnit.SECONDS);

        assertThat(result, Matchers.contains(10, 0, 8, 1));
    }
}
