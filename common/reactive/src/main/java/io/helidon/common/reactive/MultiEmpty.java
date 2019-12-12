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

import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;

/**
 * Implementation of {@link Multi} that represents the absence of a value by invoking {@link Subscriber#onComplete() } during
 * {@link Publisher#subscribe(Subscriber)}.
 */
final class MultiEmpty implements Multi<Object> {

    private static final MultiEmpty INSTANCE = new MultiEmpty();

    MultiEmpty() {
    }

    @Override
    public void subscribe(Subscriber<? super Object> subscriber) {
        subscriber.onSubscribe(EmptySubscription.INSTANCE);
        subscriber.onComplete();
    }

    @SuppressWarnings("unchecked")
    static <T> Multi<T> instance() {
        return (Multi<T>) INSTANCE;
    }
}
