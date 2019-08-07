/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;

/**
 * Fixed items publisher implementation.
 */
final class FixedItemsPublisher<T> implements Publisher<T> {

    private final Queue<T> queue;
    private final SingleSubscriberHolder<T> subscriber;
    private final RequestedCounter requested;
    private final AtomicBoolean publishing;

    FixedItemsPublisher(Collection<T> items) {
        queue = new LinkedList<>(items);
        subscriber = new SingleSubscriberHolder<>();
        requested = new RequestedCounter();
        publishing = new AtomicBoolean(false);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> s) {
        if (subscriber.register(s)) {

            // prevent onNext from inside of onSubscribe
            publishing.set(true);

            try {
                s.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                        requested.increment(n, t -> tryComplete(t));
                        tryPublish();
                    }

                    @Override
                    public void cancel() {
                        subscriber.cancel();
                    }
                });
            } finally {
                publishing.set(false);
            }

            // give onNext a chance in case request has been invoked in
            // onSubscribe
            tryPublish();
        }
    }

    private void tryPublish() {
        boolean immediateRetry = true;
        while (immediateRetry) {
            immediateRetry = false;

            // Publish, if can
            if (!subscriber.isClosed()
                    && requested.get() > 0
                    && publishing.compareAndSet(false, true)) {
                try {
                    Flow.Subscriber<? super T> sub = this.subscriber.get();
                    while (!subscriber.isClosed()
                            && requested.tryDecrement()
                            && !queue.isEmpty()) {
                        T item = queue.poll();
                        if (item != null) {
                            sub.onNext(item);
                        }
                    }
                    if (queue.isEmpty()) {
                        tryComplete();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    tryComplete(e);
                } catch (Exception e) {
                    tryComplete(e);
                } finally {
                    // give a chance to some other thread to publish
                    publishing.set(false);
                }
            }
        }
    }

    private void tryComplete() {
        subscriber.close(Subscriber::onComplete);
    }

    private void tryComplete(Throwable t) {
        subscriber.close(sub -> sub.onError(t));
    }
}
