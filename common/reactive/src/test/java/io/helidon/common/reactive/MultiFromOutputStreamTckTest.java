/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.reactivestreams.tck.flow.support.Function;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * {@link MultiFromOutputStream} reactive streams tck test.
 */
public class MultiFromOutputStreamTckTest extends FlowPublisherVerification<ByteBuffer> {

    private static TidyTestExecutor executor;
    private static final TestEnvironment env = new TestEnvironment(150);

    public MultiFromOutputStreamTckTest() {
        super(env);
    }

    @Override
    public Flow.Publisher<ByteBuffer> createFlowPublisher(long l) {
        CountDownLatch countDownLatch = new CountDownLatch((int) l);
        MultiFromOutputStream osp = IoMulti.createOutputStream();
        executor.submit(() -> {
            for (long n = 0; n < l; n++) {
                final long fn = n;
                //stochastic test of write methods being thread-safe
                executor.submit(() -> {
                    try {
                        osp.write(("token" + fn).getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        // expected by some tests
                    }
                    countDownLatch.countDown();
                });
            }
            try {
                countDownLatch.await();
                osp.close();
            } catch (IOException | InterruptedException e) {
                // expected by some tests
            }
        });

        return osp;
    }

    @Override
    public Flow.Publisher<ByteBuffer> createFailedFlowPublisher() {
        MultiFromOutputStream osp = IoMulti.createOutputStream();
        osp.signalCloseComplete(new Exception("test"));
        return osp;
    }

    @Override
    public long maxElementsFromPublisher() {
        return Integer.MAX_VALUE - 1;
    }

    @Test
    public void stochastic_spec103_mustSignalOnMethodsSequentially() throws Throwable {
        final int iterations = 1000;
        final int elements = 100;

        stochasticTest(iterations, new Function<Integer, Void>() {
            @Override
            public Void apply(final Integer runNumber) throws Throwable {
                activePublisherTest(elements, true, new PublisherTestRun<ByteBuffer>() {
                    @Override
                    public void run(Publisher<ByteBuffer> pub) throws Throwable {
                        final TestEnvironment.Latch completionLatch = new TestEnvironment.Latch(env);

                        final AtomicInteger gotElements = new AtomicInteger(0);
                        pub.subscribe(new Subscriber<ByteBuffer>() {
                            private Subscription subs;

                            private ConcurrentAccessBarrier concurrentAccessBarrier = new ConcurrentAccessBarrier();

                            /**
                             * Concept wise very similar to a {@link org.reactivestreams.tck.TestEnvironment.Latch}, serves to protect
                             * a critical section from concurrent access, with the added benefit of Thread tracking and same-thread-access awareness.
                             *
                             * Since a <i>Synchronous</i> Publisher may choose to synchronously (using the same {@link Thread}) call
                             * {@code onNext} directly from either {@code subscribe} or {@code request} a plain Latch is not enough
                             * to verify concurrent access safety - one needs to track if the caller is not still using the calling thread
                             * to enter subsequent critical sections ("nesting" them effectively).
                             */
                            final class ConcurrentAccessBarrier {
                                private AtomicReference<Thread> currentlySignallingThread = new AtomicReference<Thread>(null);
                                private volatile String previousSignal = null;

                                public void enterSignal(String signalName) {
                                    if ((!currentlySignallingThread.compareAndSet(null, Thread.currentThread())) && !isSynchronousSignal()) {
                                        env.flop(String.format(
                                                "Illegal concurrent access detected (entering critical section)! " +
                                                        "%s emited %s signal, before %s finished its %s signal.",
                                                Thread.currentThread(), signalName, currentlySignallingThread.get(), previousSignal));
                                    }
                                    this.previousSignal = signalName;
                                }

                                public void leaveSignal(String signalName) {
                                    currentlySignallingThread.set(null);
                                    this.previousSignal = signalName;
                                }

                                private boolean isSynchronousSignal() {
                                    return (previousSignal != null) && Thread.currentThread().equals(currentlySignallingThread.get());
                                }

                            }

                            @Override
                            public void onSubscribe(Subscription s) {
                                final String signal = "onSubscribe()";
                                concurrentAccessBarrier.enterSignal(signal);

                                subs = s;
                                subs.request(1);

                                concurrentAccessBarrier.leaveSignal(signal);
                            }

                            @Override
                            public void onNext(ByteBuffer ignore) {
                                final String signal = String.format("onNext(%s)", ignore);
                                concurrentAccessBarrier.enterSignal(signal);

                                if (gotElements.incrementAndGet() <= elements) // requesting one more than we know are in the stream (some Publishers need this)
                                {
                                    subs.request(1);
                                }

                                concurrentAccessBarrier.leaveSignal(signal);
                            }

                            @Override
                            public void onError(Throwable t) {
                                final String signal = String.format("onError(%s)", t.getMessage());
                                concurrentAccessBarrier.enterSignal(signal);

                                // ignore value

                                concurrentAccessBarrier.leaveSignal(signal);
                            }

                            @Override
                            public void onComplete() {
                                final String signal = "onComplete()";
                                concurrentAccessBarrier.enterSignal(signal);

                                // entering for completeness

                                concurrentAccessBarrier.leaveSignal(signal);
                                completionLatch.close();
                            }
                        });

                        completionLatch.expectClose(
                                elements * env.defaultTimeoutMillis(),
                                String.format("Failed in iteration %d of %d. Expected completion signal after signalling %d elements (signalled %d), yet did not receive it",
                                        runNumber, iterations, elements, gotElements.get()));
                    }
                });
                return null;
            }
        });
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
