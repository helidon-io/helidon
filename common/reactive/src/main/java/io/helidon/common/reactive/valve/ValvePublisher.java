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

package io.helidon.common.reactive.valve;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.reactive.Flow;

/**
 * The ValvePublisher provides {@link io.helidon.common.reactive.Flow.Publisher} based API for the {@link Valve}.
 * This publisher accepts only a single subscriber.
 *
 * @param <T> the type of items to be published
 */
class ValvePublisher<T> implements Flow.Publisher<T> {

    private final Valve<T> valve;
    private final ReentrantReadWriteLock.WriteLock pausableFeederNullLock = new ReentrantReadWriteLock().writeLock();

    private volatile Flow.Subscriber<? super T> singleSubscriber;
    private volatile PausableFeeder pausableFeeder;

    /**
     * Creates a {@link io.helidon.common.reactive.Flow.Publisher} wrapping a provided {@link Valve}.
     * Depending on the Valve implementation, only the first {@link io.helidon.common.reactive.Flow.Subscriber}
     * (subscribed to any number of such created publishers for a single {@link Valve} instance) that calls
     * {@link io.helidon.common.reactive.Flow.Subscription#request(long)} will be able to consume the produced items.
     *
     * @param valve the valve to wrap
     */
    ValvePublisher(Valve<T> valve) {
        this.valve = valve;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {

        synchronized (this) {
            if (this.singleSubscriber != null) {
                subscriber.onError(new IllegalStateException("Multiple subscribers aren't allowed!"));
                return;
            }
            this.singleSubscriber = subscriber;
        }

        this.singleSubscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {

                if (n <= 0) {
                    subscriber.onError(new IllegalArgumentException("Requested illegal item count: " + 0));
                    return;
                }

                if (pausableFeeder != null) {
                    // a standard release
                    pausableFeeder.release(n);
                } else {
                    try {
                        pausableFeederNullLock.lock();

                        if (pausableFeeder == null) {
                            // the first item is always emitted, as such we set one less
                            pausableFeeder = new PausableFeeder(n - 1, valve);

                            handleValve();
                        } else {
                            // pausableFeeder actually is not null, do a standard release
                            pausableFeeder.release(n);
                        }
                    } finally {
                        pausableFeederNullLock.unlock();
                    }
                }
            }

            @Override
            public void cancel() {
                valve.pause();
            }
        });
    }

    private void handleValve() {
        valve.handle((data) -> {
                         singleSubscriber.onNext(data);
                         pausableFeeder.acquire();
                     },
                     throwable -> singleSubscriber.onError(new IllegalStateException(
                             "Valve to Publisher in an error.",
                             throwable)),
                     singleSubscriber::onComplete);
    }

    private static class PausableFeeder {
        private final Pausable pausable;
        private final ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();

        private volatile long count;

        PausableFeeder(long count, Pausable pausable) {
            this.count = count;
            this.pausable = pausable;
        }

        private void acquire() {
            try {
                lock.lock();

                count = count == Long.MAX_VALUE
                        ? count
                        : count == 0 ? 0 : count - 1;

                if (count == 0) {
                    pausable.pause();
                }
            } finally {
                lock.unlock();
            }
        }

        private void release(long n) {
            try {
                lock.lock();

                long r = count + n;
                // HD 2-12 Overflow iff both arguments have the opposite sign of the result; inspired by Math.addExact(long, long)
                count = r == Long.MAX_VALUE || ((count ^ r) & (n ^ r)) < 0
                        // unbounded reached
                        ? Long.MAX_VALUE
                        : count + n;

                pausable.resume();
            } finally {
                lock.unlock();
            }
        }
    }
}
