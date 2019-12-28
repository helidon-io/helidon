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
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;

/**
 * Implementation of {@link Single} that represents an error, raised during
 * {@link Publisher#subscribe(Subscriber)} by invoking
 * {@link Subscriber#onError(java.lang.Throwable)}.
 *
 * @param <T> item type
 */
final class SingleError<T> implements Single<T> {

    private final Throwable error;

    SingleError(Throwable error) {
        this.error = Objects.requireNonNull(error, "error cannot be null!");
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(EmptySubscription.INSTANCE);
        subscriber.onError(error);
    }
}
