/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.reactive;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Flow;

public class OfPublisher implements Flow.Publisher<Object> {
    private Iterable<?> iterable;
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private AtomicBoolean completed = new AtomicBoolean(false);

    public OfPublisher(Iterable<?> iterable) {
        this.iterable = iterable;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Object> subscriber) {
        final Iterator<?> iterator;
        try {
            iterator = iterable.iterator();
        } catch (Throwable t) {
            subscriber.onError(t);
            return;
        }
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (n <= 0) {
                    // https://github.com/reactive-streams/reactive-streams-jvm#3.9
                    subscriber.onError(new IllegalArgumentException("non-positive subscription request"));
                }
                for (long i = 0; i < n; i++) {
                    if (iterator.hasNext() && !cancelled.get()) {
                        Object next = iterator.next();
                        Objects.requireNonNull(next);
                        subscriber.onNext(next);
                    } else {
                        if (!completed.getAndSet(true)) {
                            subscriber.onComplete();
                        }
                        break;
                    }
                }
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });
    }

}
