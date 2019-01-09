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

/**
 * The OriginThreadPublisherTest.
 */
public class OriginThreadPublisherTest {
/*
    private static final double OTHER_THREAD_EXECUTION_RATIO = 0.8;
    private static final int BOUND = 5;
    private static final int ITERATION_COUNT = 1000;
    private final AtomicLong seq = new AtomicLong(0);
    private final AtomicLong check = new AtomicLong(0);


    @Test
    public void sanityPublisherCheck() throws Exception {

        UnboundedSemaphore semaphore = new UnboundedSemaphore();
        OriginThreadPublisher<Long> publisher = new OriginThreadPublisher<>(semaphore);
        CountDownLatch finishedLatch = new CountDownLatch(1);

        JdkFlowAdapter.flowPublisherToFlux(publisher).subscribe(new BaseSubscriber<>() {
            public Subscription subscription;

            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            protected void hookOnNext(Long value) {
                if (!check.compareAndSet(value - 1, value)) {
                    throw new IllegalStateException("expected: " + (value - 1) + " but found: " + check.get());
                }
                if (ThreadLocalRandom.current().nextDouble(0, 1) < OTHER_THREAD_EXECUTION_RATIO) {
                    ForkJoinPool.commonPool().submit(() -> subscription.request(ThreadLocalRandom.current().nextLong(1, BOUND)));
                } else {
                    subscription.request(ThreadLocalRandom.current().nextLong(1, BOUND));
                }
            }
        });

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted() && seq.get() < ITERATION_COUNT) {
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(0, 2));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted", e);
                }
                publisher.submit(seq.incrementAndGet());
            }
            finishedLatch.countDown();
        });

        try {
            if (!finishedLatch.await(10, TimeUnit.SECONDS)) {
                fail("Didn't finished in timely manner");
            }
        } finally {
            executorService.shutdown();
        }
    }*/
}
