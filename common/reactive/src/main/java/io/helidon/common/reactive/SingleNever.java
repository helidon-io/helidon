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

import io.helidon.common.reactive.Flow.Subscriber;

/**
 * Implementation of {@link Single} that never invokes
 * {@link Subscriber#onComplete()} or
 * {@link Subscriber#onError(java.lang.Throwable)}.
 */
final class SingleNever implements Single<Object> {

    /**
     * Singleton instance.
     */
    private static final SingleNever INSTANCE = new SingleNever();

    private SingleNever() {
    }

    @Override
    public void subscribe(Subscriber<? super Object> actual) {
        actual.onSubscribe(EmptySubscription.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    static <T> Single<T> instance() {
        return (Single<T>) INSTANCE;
    }
}
