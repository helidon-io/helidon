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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ConsumableSubscriber<T> implements Subscriber<T> {

    private Consumer<T> onNext;
    private AtomicLong requestCount = new AtomicLong(1000);
    private Subscription subscription;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ConsumableSubscriber(Consumer<T> onNext) {
        this.onNext = onNext;
    }
    public ConsumableSubscriber(Consumer<T> onNext, long requestCount) {
        this.onNext = onNext;
        this.requestCount.set(requestCount);
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        //First chunk request
        subscription.request(requestCount.decrementAndGet());
    }

    @Override
    public void onNext(T o) {
        if (!closed.get()) {
            onNext.accept(o);
            if(0 == requestCount.decrementAndGet()){
                subscription.cancel();
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        throw new RuntimeException(t);
    }

    @Override
    public void onComplete() {
        closed.set(true);
        subscription.cancel();
    }
}
