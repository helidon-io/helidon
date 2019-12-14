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

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class FromCompletionStagePublisher<T> implements Publisher<T> {

    private CompletionStage<?> completionStage;
    private volatile boolean nullable;
    private volatile boolean cancelled = false;
    private AtomicBoolean registered = new AtomicBoolean(false);
    private Subscriber<? super T> subscriber;

    public FromCompletionStagePublisher(CompletionStage<?> completionStage, boolean nullable) {
        this.nullable = nullable;
        Objects.requireNonNull(completionStage);
        this.completionStage = completionStage;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void subscribe(Subscriber<? super T> subscriber) {
        this.subscriber = subscriber;
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                registerEmitWhenCompleteOnceAction();
            }

            @Override
            public void cancel() {
                cancelled = true;
                //registerEmitWhenCompleteOnceAction();
            }
        });
    }

    private void registerEmitWhenCompleteOnceAction() {
        if (!registered.getAndSet(true)) {
            completionStage.whenComplete((item, throwable) -> {
                if (Objects.isNull(throwable)) {
                    emit((T) item);
                } else {
                    subscriber.onError(throwable);
                }
            });
        }
    }

    private void emit(T item) {
        if (!cancelled) {
            if (Objects.nonNull(item)) {
                subscriber.onNext(item);
                subscriber.onComplete();
            } else {
                if (nullable) {
                    subscriber.onComplete();
                } else {
                    subscriber.onError(new NullPointerException("Null in non nullable completion stage."));
                }
            }
        }
    }
}
