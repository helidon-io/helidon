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

package io.helidon.microrofile.reactive;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IntSequencePublisher implements Publisher<Integer>, Subscription {

    private AtomicBoolean closed = new AtomicBoolean(false);
    private AtomicInteger sequence = new AtomicInteger(0);
    private Subscriber<? super Integer> subscriber;

    public IntSequencePublisher() {
    }

    @Override
    public void subscribe(Subscriber<? super Integer> s) {
        subscriber = s;
        subscriber.onSubscribe(this);
    }

    @Override
    public void request(long n) {
        for (long i = 0; i <= n
                && !closed.get(); i++) {
            subscriber.onNext(sequence.incrementAndGet());
        }
    }

    @Override
    public void cancel() {
        closed.set(true);
    }
}
