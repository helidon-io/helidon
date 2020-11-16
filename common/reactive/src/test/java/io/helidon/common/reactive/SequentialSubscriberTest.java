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
 *
 */

package io.helidon.common.reactive;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

public class SequentialSubscriberTest {
    int counter = 0;
    Optional<String> errorFound = Optional.empty();

    @Test
    void forceSignalOnMethodsSequentially() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            seqCallTest(3);
        }
    }

    void seqCallTest(long sleepMillis) throws InterruptedException {

        Flow.Subscriber<Object> sub = new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                enteringMethod();
                sleep(sleepMillis);
                leavingMethod();
            }


            @Override
            public void onNext(Object item) {
                enteringMethod();
                sleep(sleepMillis);
                leavingMethod();
            }

            @Override
            public void onError(Throwable throwable) {
                enteringMethod();
                sleep(sleepMillis);
                leavingMethod();
            }

            @Override
            public void onComplete() {
                enteringMethod();
                sleep(sleepMillis);
                leavingMethod();
            }
        };

        SequentialSubscriber<Object> seqSubscriber = SequentialSubscriber.create(sub);

        ExecutorService executorService = Executors.newFixedThreadPool(4);
        executorService.invokeAll(List.of(() -> {
                    seqSubscriber.onSubscribe(null);
                    return null;
                }, () -> {
                    seqSubscriber.onNext(null);
                    return null;
                }, () -> {
                    seqSubscriber.onError(null);
                    return null;
                }, () -> {
                    seqSubscriber.onComplete();
                    return null;
                }
        ));
        executorService.awaitTermination(10, TimeUnit.MILLISECONDS);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        assertFalse(errorFound.isPresent(), () -> errorFound.get());
    }

    private void sleep(long millis) {
        assertOnceInMethod();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            System.err.println(e);
        }
        assertOnceInMethod();
    }

    private void assertOnceInMethod() {
        try {
            enterLeaveLock.lock();
            if (1 != counter) {
                errorFound = Optional.of("Non-sequential call detected, counter should be 1 but is " + counter);
            }
        } finally {
            enterLeaveLock.unlock();
        }
    }

    ReentrantLock enterLeaveLock = new ReentrantLock();

    private void enteringMethod() {
        try {
            enterLeaveLock.lock();
//            System.out.println("Entering " + Thread.currentThread().getStackTrace()[2].getMethodName());
            counter++;
        } finally {
            enterLeaveLock.unlock();
        }
    }

    private void leavingMethod() {
        try {
            enterLeaveLock.lock();
//            System.out.println("Leaving " + Thread.currentThread().getStackTrace()[2].getMethodName());
            counter--;
        } finally {
            enterLeaveLock.unlock();
        }
    }
}