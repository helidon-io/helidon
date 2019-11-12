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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Consumer;

public class ConsumableSubscriber<T> implements Subscriber<T> {

    private Consumer<T> onNext;
    private Subscription subscription;
    private Long chunkSize = 20L;
    private Long chunkPosition = 0L;

    public ConsumableSubscriber(Consumer<T> onNext) {
        this.onNext = onNext;
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        //First chunk request
        subscription.request(chunkSize);
    }

    @Override
    public void onNext(T o) {
        onNext.accept(o);
        incrementAndCheckChunkPosition();
    }

    @Override
    public void onError(Throwable t) {
        throw new RuntimeException(t);
    }

    @Override
    public void onComplete() {
    }

    private void incrementAndCheckChunkPosition() {
        chunkPosition++;
        if (chunkPosition >= chunkSize) {
            chunkPosition = 0L;
            subscription.request(chunkSize);
        }
    }
}
