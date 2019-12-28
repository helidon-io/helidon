/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Testing implementation of {@link Flow.Subscriber}.
 */
public class TestingSubscriber<T> implements Flow.Subscriber<T> {

    volatile CountDownLatch onSubscribeLatch = new CountDownLatch(1);
    private Flow.Subscription subscription;
    volatile T lastOnNext;
    volatile CountDownLatch onNextLatch;
    Throwable lastOnError;
    boolean complete = false;

    public void request1() throws InterruptedException {
        onSubscribeLatch.await();
        lastOnNext = null;
        onNextLatch = new CountDownLatch(1);
        subscription.request(1);
    }

    public T getLastOnNext(long timeout, boolean expectedZero) throws InterruptedException {
        boolean await = onNextLatch.await(timeout, TimeUnit.MILLISECONDS);
        assertThat(await, is(expectedZero));

        return lastOnNext;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        onSubscribeLatch.countDown();
    }

    public Flow.Subscription getSubscription() {
        try {
            onSubscribeLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return subscription;
    }

    @Override
    public void onNext(T event) {
        lastOnNext = event;
        onNextLatch.countDown();
    }

    @Override
    public void onError(Throwable throwable) {
        lastOnError = throwable;
    }

    @Override
    public void onComplete() {
        complete = true;
    }

}
