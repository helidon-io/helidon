/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test
public class EmittingPublisherTckTest extends FlowPublisherVerification<Integer> {

    private static TidyTestExecutor executor;

    public EmittingPublisherTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Publisher<Integer> createFlowPublisher(long l) {
        AtomicLong counter = new AtomicLong(l);
        CountDownLatch completeLatch = new CountDownLatch((int) l);
        EmittingPublisher<Integer> osp = EmittingPublisher.create();
        osp.onRequest((r, demand) -> {
            boolean accepted = true;
            for (long n = 0; n < r && n <= l && accepted; n++) {
                final long fn = n;
                //stochastic test of emit methods being thread-safe
                accepted = executor.submitAwaitable(() -> {
                    long cnt = counter.getAndDecrement();
                    if (cnt > 0) {
                        osp.emit((int) fn);
                        completeLatch.countDown();
                        if (cnt == 1) {
                            executor.submit(() -> {
                                try {
                                    completeLatch.await(2, TimeUnit.SECONDS);
                                    osp.complete();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                });
            }
        });


        return osp;
    }

    @Override
    public Flow.Publisher<Integer> createFailedFlowPublisher() {
        BufferedEmittingPublisher<Integer> emitter = BufferedEmittingPublisher.create();
        emitter.fail(new RuntimeException());
        return emitter;
    }

    @Override
    public long maxElementsFromPublisher() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void required_spec102_maySignalLessThanRequestedAndTerminateSubscription() throws Throwable {
        for (int i = 0; i < 1000; i++) {
            super.required_spec102_maySignalLessThanRequestedAndTerminateSubscription();
        }
    }

    @Override
    public void stochastic_spec103_mustSignalOnMethodsSequentially() throws Throwable {
        for (int i = 0; i < 100; i++) {
            super.stochastic_spec103_mustSignalOnMethodsSequentially();
        }
    }

    @BeforeClass
    public void beforeClass() {
        executor = new TidyTestExecutor();
    }

    @AfterClass
    public void afterClass() {
        executor.shutdownNow();
    }

    @AfterMethod
    public void tearDown() throws InterruptedException {
        executor.awaitAllFinished();
    }
}
