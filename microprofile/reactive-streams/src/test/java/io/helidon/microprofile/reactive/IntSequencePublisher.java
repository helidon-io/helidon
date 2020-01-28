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

package io.helidon.microprofile.reactive;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.reactive.RequestedCounter;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class IntSequencePublisher implements Publisher<Integer>, Subscription {

    private boolean closed = false;
    private AtomicInteger sequence = new AtomicInteger(0);
    private Subscriber<? super Integer> subscriber;
    private RequestedCounter requestCounter = new RequestedCounter();
    private AtomicBoolean trampolineLock = new AtomicBoolean(false);


    public IntSequencePublisher() {
    }

    @Override
    public void subscribe(Subscriber<? super Integer> s) {
        subscriber = s;
        subscriber.onSubscribe(this);
    }

    @Override
    public void request(long n) {
        requestCounter.increment(n, subscriber::onError);
        trySubmit();
    }

    private void trySubmit() {
        if (!trampolineLock.getAndSet(true)) {
            try {
                while (requestCounter.tryDecrement() && !closed) {
                    subscriber.onNext(sequence.incrementAndGet());
                }
            } finally {
                trampolineLock.set(false);
            }
        }
    }

    @Override
    public void cancel() {
        closed = true;
        subscriber.onComplete();
    }
}
