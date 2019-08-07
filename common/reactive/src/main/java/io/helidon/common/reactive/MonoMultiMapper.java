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

import io.helidon.common.reactive.Flow.Processor;
import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

/**
 * Processor of {@link Mono} to {@link Publisher} that expands the first (and
 * only) item to a publisher.
 *
 * @param <T> subscribed type
 * @param <U> published type
 */
public abstract class MonoMultiMapper<T, U> implements Processor<T, U> {

    private Throwable error;
    private Publisher<? extends U> delegate;
    private Subscription subscription;
    private Subscriber<? super U> subscriber;
    private volatile boolean subcribed;

    /**
     * Map a given item to multiple items.
     * @param item input item to map
     * @return Publisher of the mapped items
     */
    public abstract Publisher<U> mapNext(T item);

    @Override
    public final void onNext(T item) {
        if (delegate == null) {
            delegate = mapNext(item);
            doSusbcribe();
        }
        subscription.cancel();
    }

    @Override
    public final void onError(Throwable ex) {
        if (delegate == null) {
            error = ex;
            delegate = Mono.<U>error(error);
            doSusbcribe();
        }
    }

    @Override
    public final void onSubscribe(Subscription s) {
        this.subscription = s;
        s.request(1);
    }

    @Override
    public final void onComplete() {
    }

    private void doSusbcribe() {
        if (!subcribed && subscriber != null) {
            delegate.subscribe(subscriber);
            subcribed = true;
        }
    }

    @Override
    public final void subscribe(Subscriber<? super U> subscriber) {
        this.subscriber = subscriber;
        if (delegate != null) {
            doSusbcribe();
        }
    }
}
