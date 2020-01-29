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

package io.helidon.common.reactive;

import java.util.concurrent.Flow;
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

    private SequentialSubscriber(Flow.Subscriber<T> subscriber) {
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

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        seqLock(() -> subscriber.onSubscribe(subscription));
    }

    @Override
    public void onNext(T item) {
        seqLock(() -> subscriber.onNext(item));
    }

    @Override
    public void onError(Throwable throwable) {
        seqLock(() -> subscriber.onError(throwable));
    }

    @Override
    public void onComplete() {
        seqLock(() -> subscriber.onComplete());
    }

    /**
     * OnSubscribe, onNext, onError and onComplete signaled to a Subscriber MUST be signaled serially.
     * https://github.com/reactive-streams/reactive-streams-jvm#1.3
     */
    private void seqLock(Runnable runnable) {
        try {
            seqLock.lock();
            runnable.run();
        } finally {
            seqLock.unlock();
        }
    }
}
