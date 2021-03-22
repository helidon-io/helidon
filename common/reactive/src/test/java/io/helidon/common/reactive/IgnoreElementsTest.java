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
 *
 */

package io.helidon.common.reactive;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class IgnoreElementsTest {
    @Test
    void multiIgnoreTriggerSubscription() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        Multi.just(1, 1, 1)
                .peek(i -> latch.countDown())
                .ignoreElements(); // should trigger subscription on its own

        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void singleIgnoreTriggerSubscription() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Single.just(1)
                .peek(i -> latch.countDown())
                .ignoreElement(); // should trigger subscription on its own

        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void multiIgnorePeek() {
        List<Integer> result = new ArrayList<>(3);
        Multi.just(1, 2, 3)
                .peek(result::add)
                .ignoreElements()
                .await(200, TimeUnit.MILLISECONDS);

        assertThat(result, Matchers.contains(1, 2, 3));
    }

    @Test
    void singleIgnorePeek() {
        AtomicInteger result = new AtomicInteger(0);
        Single.just(3)
                .peek(result::set)
                .ignoreElement()
                .await(200, TimeUnit.MILLISECONDS);

        assertThat(result.get(), Matchers.is(3));
    }

    @Test
    void completePropagation() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Void> multiOnComplete = new CompletableFuture<>();
        CompletableFuture<Void> singleOnComplete = new CompletableFuture<>();
        CompletableFuture<Void> thenAccept = new CompletableFuture<>();
        Multi.just(1, 2, 3)
                .onComplete(() -> multiOnComplete.complete(null))
                .ignoreElements()
                .onComplete(() -> singleOnComplete.complete(null))
                .ignoreElement()
                .thenAccept(unused -> thenAccept.complete(null));

        multiOnComplete.get(200, TimeUnit.MILLISECONDS);
        singleOnComplete.get(200, TimeUnit.MILLISECONDS);
        thenAccept.get(200, TimeUnit.MILLISECONDS);
    }

    @Test
    void errorPropagation() throws ExecutionException, InterruptedException, TimeoutException {
        Exception exception = new Exception("Boom!!!");
        CompletableFuture<Throwable> multiOnError = new CompletableFuture<>();
        CompletableFuture<Throwable> singleOnError = new CompletableFuture<>();
        CompletableFuture<Throwable> exceptionallyAccept = new CompletableFuture<>();
        Multi.concat(Multi.just(1, 2, 3), Single.error(exception))
                .onError(multiOnError::complete)
                .ignoreElements()
                .onError(singleOnError::complete)
                .ignoreElement()
                .exceptionallyAccept(exceptionallyAccept::complete);

        assertThat(multiOnError.get(200, TimeUnit.MILLISECONDS), Matchers.is(exception));
        assertThat(singleOnError.get(200, TimeUnit.MILLISECONDS), Matchers.is(exception));
        assertThat(exceptionallyAccept.get(200, TimeUnit.MILLISECONDS).getCause(), Matchers.is(exception));
    }
}
