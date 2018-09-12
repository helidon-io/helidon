/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;

import io.netty.buffer.Unpooled;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * The WrappedOriginThreadPublisherTckTest.
 */
public class WrappedOriginThreadPublisherTckTest extends PublisherVerification<DataChunk> {

    private static final int DEFAULT_TIMEOUT_MILLIS = 200;
    private static final Logger LOGGER = Logger.getLogger(WrappedOriginThreadPublisherTckTest.class.getName());

    public WrappedOriginThreadPublisherTckTest() {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS, true));
    }

    private static final ExecutorService service = Executors.newCachedThreadPool();

    @Override
    public Publisher<DataChunk> createPublisher(long elements) {
        LOGGER.fine("creating publisher");

        UnboundedSemaphore semaphore = new UnboundedSemaphore();
        WrappedOriginThreadPublisher publisher = new WrappedOriginThreadPublisher(semaphore);

        CountDownLatch started = new CountDownLatch(1);

        service.submit(() -> {
            LOGGER.fine("Sending thread started");
            started.countDown();
            for (int i = 0; i < elements; i++) {
                while (!Thread.currentThread().isInterrupted()) {
                    long prev = semaphore.tryAcquire();
                    if (prev > 0) {
                        break;
                    }
                    // actively loop
                }
                LOGGER.fine("Sending data");
                publisher.submit(Unpooled.wrappedBuffer(new byte[] {}));
            }

            publisher.complete();

            LOGGER.fine("Sending thread ended");
        });

        try {
            started.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return publisher;
    }

    @AfterClass
    public void shutdownExecutor() throws Exception {
        service.submit(() -> {
            try {
                Thread.sleep(DEFAULT_TIMEOUT_MILLIS * 10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            service.shutdown();
        });
    }

    @Override
    public Publisher<DataChunk> createFailedPublisher() {
        return new WrappedOriginThreadPublisher(new UnboundedSemaphore()) {
            @Override
            public void subscribe(Subscriber<? super DataChunk> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {

                    }

                    @Override
                    public void cancel() {

                    }
                });
                subscriber.onError(new Exception("using failed publisher"));
            }
        };
    }

    @Override @Test(enabled = false)
    public void optional_spec111_multicast_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingOneByOne() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.optional_spec111_multicast_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingOneByOne();
    }

    @Override @Test(enabled = false)
    public void optional_spec111_multicast_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingManyUpfront() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.optional_spec111_multicast_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingManyUpfront();
    }

    @Override @Test(enabled = false)
    public void untested_spec108_possiblyCanceledSubscriptionShouldNotReceiveOnErrorOrOnCompleteSignals() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.untested_spec108_possiblyCanceledSubscriptionShouldNotReceiveOnErrorOrOnCompleteSignals();
    }

    @Override @Test(enabled = false)
    public void untested_spec107_mustNotEmitFurtherSignalsOnceOnErrorHasBeenSignalled() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.untested_spec107_mustNotEmitFurtherSignalsOnceOnErrorHasBeenSignalled();
    }

    @Override @Test(enabled = false)
    public void untested_spec109_subscribeShouldNotThrowNonFatalThrowable() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.untested_spec109_subscribeShouldNotThrowNonFatalThrowable();
    }

    @Override @Test(enabled = false)
    public void optional_spec111_maySupportMultiSubscribe() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.optional_spec111_maySupportMultiSubscribe();
    }

    @Override @Test(enabled = false)
    public void optional_spec111_multicast_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingManyUpfrontAndCompleteAsExpected() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.optional_spec111_multicast_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingManyUpfrontAndCompleteAsExpected();
    }

    @Override @Test(enabled = false)
    public void untested_spec304_requestShouldNotPerformHeavyComputations() throws Exception {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.untested_spec304_requestShouldNotPerformHeavyComputations();
    }

    @Override @Test(enabled = false)
    public void untested_spec305_cancelMustNotSynchronouslyPerformHeavyComputation() throws Exception {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.untested_spec305_cancelMustNotSynchronouslyPerformHeavyComputation();
    }

    @Override @Test(enabled = false)
    public void untested_spec106_mustConsiderSubscriptionCancelledAfterOnErrorOrOnCompleteHasBeenCalled() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.untested_spec106_mustConsiderSubscriptionCancelledAfterOnErrorOrOnCompleteHasBeenCalled();
    }

    @Override @Test(enabled = false)
    public void untested_spec110_rejectASubscriptionRequestIfTheSameSubscriberSubscribesTwice() throws Throwable {
        // skipped because the junit like testng report would report this as failed
        // ignoring the fact that the test is programmatically skipped
        super.untested_spec110_rejectASubscriptionRequestIfTheSameSubscriberSubscribesTwice();
    }
}
