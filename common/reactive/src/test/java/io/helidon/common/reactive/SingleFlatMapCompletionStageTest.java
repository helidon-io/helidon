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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

public class SingleFlatMapCompletionStageTest {

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
        Integer result = Single.just(1)
                .flatMapCompletionStage(CompletableFuture::completedFuture)
                .await(100, TimeUnit.MILLISECONDS);

        assertThat(result, Matchers.equalTo(1));
    }

    @Test
    void voidCs() {
        TestSubscriber<Void> subscriber = new TestSubscriber<>();

        Single.just(1)
                .flatMapCompletionStage(i -> CompletableFuture.<Void>completedFuture(null))
                .subscribe(subscriber);

        subscriber.requestMax();
        subscriber.assertError(NullPointerException.class);
    }

    @Test
    void multiThread() {
        Integer result = Single.just(10)
                .flatMapCompletionStage(i -> CompletableFuture.supplyAsync(() ->
                {
                    try {
                        Thread.sleep(i);
                        return i;
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }, exec))
                .await(2, TimeUnit.SECONDS);

        assertThat(result, Matchers.equalTo(10));
    }
}
