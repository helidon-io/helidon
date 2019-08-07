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

import java.util.Objects;
import java.util.concurrent.CompletionStage;

import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

/**
 * Mono backed by a {@link CompletionStage}.
 */
final class MonoFromCompletionStage<T> implements Mono<T> {

    private final CompletionStage<? extends T> future;
    private Subscriber<? super T> subscriber;
    private volatile boolean requested;

    MonoFromCompletionStage(CompletionStage<? extends T> future) {
        this.future = Objects.requireNonNull(future, "future");
    }

    private void submit(T item) {
        subscriber.onNext(item);
        subscriber.onComplete();
    }

    private <U extends T> U raiseError(Throwable error) {
        subscriber.onError(error);
        return null;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if (this.subscriber != null) {
            throw new IllegalStateException("Already subscribed to");
        }
        this.subscriber = subscriber;
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                if (n > 0 && !requested) {
                    future.exceptionally(MonoFromCompletionStage.this::raiseError);
                    future.thenAccept(MonoFromCompletionStage.this::submit);
                    requested = true;
                }
            }

            @Override
            public void cancel() {
            }
        });
    }
}
