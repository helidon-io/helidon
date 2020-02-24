/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.reactive;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class CountingSubscriber implements Subscriber<Long> {
    private Subscription subscription;
    public AtomicLong sum = new AtomicLong(0);
    public AtomicLong requestCount = new AtomicLong(0);
    public CompletableFuture<AtomicLong> completed = new CompletableFuture<>();
    private AtomicBoolean expectError = new AtomicBoolean(false);
    private AtomicLong cancelAfter = new AtomicLong(0);

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
    }

    @Override
    public void onNext(Long item) {
        requestCount.incrementAndGet();
        sum.addAndGet(item);
        if (cancelAfter.get() != 0 && requestCount.get() > cancelAfter.get()) {
            cancel();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!expectError.get()) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void onComplete() {
        completed.complete(sum);
    }

    public void request(long n) {
        subscription.request(n);
    }

    public void cancel() {
        subscription.cancel();
    }

    public void cancelAfter(long max) {
        cancelAfter.set(max);
    }

    public AtomicLong getSum() {
        return sum;
    }

    public void expectRequestCount(int n) {
        assertEquals(n, requestCount.get(), String.format("Expected %d requests but only %d received.", n, (long) requestCount.get()));
    }

    public void expectSum(long n) {
        assertEquals(n, sum.get());
    }

    public void expectError() {
        expectError.set(true);
    }

    public void expectOnComplete() {
        try {
            request(1);
            completed.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail(e);
        }
    }
}
