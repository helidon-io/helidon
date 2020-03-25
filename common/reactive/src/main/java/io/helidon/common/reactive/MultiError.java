/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
 * Implementation of {@link Multi} that represents an error, raised during {@link Publisher#subscribe(Subscriber)} by invoking
 * {@link Subscriber#onError(java.lang.Throwable)}.
 *
 * @param <T> item type
 */
final class MultiError<T> implements Multi<T> {

    private final Throwable error;

    private MultiError(Throwable error) {
        this.error = Objects.requireNonNull(error, "error");
    }

    static <T> MultiError<T> create(Throwable error) {
        return new MultiError<T>(error);
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(EmptySubscription.INSTANCE);
        subscriber.onError(error);
    }

    /**
     * Returns the hosted {@code Throwable} instance.
     * @return the hosted {@code Throwable} instance
     */
    Throwable getError() {
        return error;
    }
}
