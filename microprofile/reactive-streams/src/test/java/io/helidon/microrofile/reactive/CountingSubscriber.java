/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microrofile.reactive;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.microprofile.reactive.ExceptionUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class CountingSubscriber implements Subscriber<Integer> {
    private Subscription subscription;
    public AtomicInteger sum = new AtomicInteger(0);
    public AtomicInteger requestCount = new AtomicInteger(0);
    public CompletableFuture<AtomicInteger> completed = new CompletableFuture<>();

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
    }

    @Override
    public void onNext(Integer item) {
        System.out.println("Received: " + item);
        requestCount.incrementAndGet();
        sum.addAndGet((int) item);
    }

    @Override
    public void onError(Throwable throwable) {
        ExceptionUtils.throwUncheckedException(throwable);
    }

    @Override
    public void onComplete() {
        completed.complete(sum);
    }

    public void request(long n) {
        System.out.println("Requested: " + n);
        subscription.request(n);
    }

    public void cancel() {
        subscription.cancel();
    }

    public AtomicInteger getSum() {
        return sum;
    }

    public void expectRequestCount(int n) {
        assertEquals(n, requestCount.get());
    }

    public void expectSum(int n) {
        assertEquals(n, sum.get());
    }
}
