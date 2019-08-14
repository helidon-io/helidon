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
 * Processor of {@link Publisher} to {@link Single} that only processes the first
 * item and maps it to a different type.
 *
 * @param <T> subscribed type
 * @param <U> published type
 */
final class SingleMapperProcessor<T, U> implements Processor<T, U>, Single<U> {

    private Publisher<? extends U> delegate;
    private Subscriber<? super U> subscriber;
    private volatile boolean subcribed;
    private final Mapper<T, U> mapper;

    SingleMapperProcessor(Mapper<T, U> mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper cannot be null!");
        }
        this.mapper = mapper;
    }

    @Override
    public void onNext(T item) {
        if (delegate == null) {
            delegate = Single.just(mapper.map(item));
            doSusbcribe();
        }
    }

    @Override
    public void onError(Throwable ex) {
        if (delegate == null) {
            delegate = Single.<U>error(ex);
            doSusbcribe();
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(1);
    }

    @Override
    public void onComplete() {
        if (delegate == null) {
            delegate = Single.empty();
            doSusbcribe();
        }
    }

    private void doSusbcribe() {
        if (!subcribed && subscriber != null) {
            delegate.subscribe(subscriber);
            subcribed = true;
        }
    }

    @Override
    public void subscribe(Subscriber<? super U> subscriber) {
        this.subscriber = subscriber;
        if (delegate != null) {
            doSusbcribe();
        }
    }
}
