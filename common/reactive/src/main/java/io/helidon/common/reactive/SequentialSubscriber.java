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

import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Wrapper {@link Flow.Subscriber} ensuring {@code OnSubscribe}, {@code onNext}, {@code onError}
 * and {@code onComplete} to be signaled serially.
 *
 * @param <T> Type of the item
 * @see <a href="https://github.com/reactive-streams/reactive-streams-jvm#1.3">
 * https://github.com/reactive-streams/reactive-streams-jvm#1.3</a>
 */
public class SequentialSubscriber<T> implements Flow.Subscriber<T> {

    private Flow.Subscriber<T> subscriber;
    private ReentrantLock seqLock = new ReentrantLock();
    private LinkedList<Runnable> queue = new LinkedList<>();
    private volatile boolean done;
    private boolean draining;

    /**
     * Provides protection from untrusted publishers, enforces rule 1.3 by serializing all parallel calls.
     *
     * @param subscriber to be protected
     */
    protected SequentialSubscriber(Flow.Subscriber<T> subscriber) {
        this.subscriber = subscriber;
    }

    /**
     * Wrapper {@link Flow.Subscriber} ensuring {@code OnSubscribe}, {@code onNext}, {@code onError}
     * and {@code onComplete} to be signaled serially.
     *
     * @param subscriber {@link Flow.Subscriber} to be wrapped.
     * @param <T>        item type
     * @return new {@link SequentialSubscriber}
     */
    public static <T> SequentialSubscriber<T> create(Flow.Subscriber<T> subscriber) {
        return new SequentialSubscriber<>(subscriber);
    }

    private AtomicBoolean subscribedAlready = new AtomicBoolean(false);

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        Objects.requireNonNull(subscription);
        if (subscribedAlready.getAndSet(true)) {
            subscription.cancel();
            return;
        }
        boolean cancel;
        if (!done) {
            try {
                seqLock.lock();
                if (done) {
                    cancel = true;
                } else {
                    if (draining) {
                        queue.addFirst(() -> subscriber.onSubscribe(subscription));
                        return;
                    }
                    draining = true;
                    cancel = false;
                }
            } finally {
                seqLock.unlock();
            }
        } else {
            cancel = true;
        }
        if (cancel) {
            subscription.cancel();
        } else {
            subscriber.onSubscribe(subscription);
            drainQueue();
        }
    }

    @Override
    public void onNext(T item) {
        Objects.requireNonNull(item);
        if (done) return;
        try {
            seqLock.lock();
            if (done) return;
            if (draining) {
                queue.add(() -> submit(item));
                return;
            }
            draining = true;
        } finally {
            seqLock.unlock();
        }
        submit(item);
        drainQueue();
    }

    @Override
    public void onError(Throwable throwable) {
        Objects.requireNonNull(throwable);
        if (done) return;
        try {
            seqLock.lock();
            if (done) return;
            done = true;
            if (draining) {
                queue = new ReadOnlySignalQueue(queue, () -> subscriber.onError(throwable));
                return;
            }
            draining = true;
        } finally {
            seqLock.unlock();
        }
        subscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
        if (done) return;
        try {
            seqLock.lock();
            if (done) return;
            done = true;
            if (draining) {
                queue = new ReadOnlySignalQueue(queue, () -> subscriber.onComplete());
                return;
            }
            draining = true;
        } finally {
            seqLock.unlock();
        }
        subscriber.onComplete();
    }

    private void drainQueue() {
        while (true) {
            Runnable job;
            try {
                seqLock.lock();
                if (queue.isEmpty()) {
                    draining = false;
                    return;
                }
                job = queue.removeFirst();
            } finally {
                seqLock.unlock();
            }
            // Synchronized and out of lock call
            job.run();
        }
    }

    private void submit(T item) {
        try {
            subscriber.onNext(item);
        } catch (Throwable ex) {
            this.onError(ex);
        }
    }

    private class ReadOnlySignalQueue extends LinkedList<Runnable> {

        ReadOnlySignalQueue(Collection<Runnable> unfinishedQueue, Runnable single) {
            super();
            super.addAll(unfinishedQueue);
            super.add(single);
        }

        @Override
        public boolean add(Runnable runnable) {
            return false;
        }
    }

}
