/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Supplier;

/**
 * Implementation of {@link Single} that represents a non {@code null} value.
 *
 * @param <T> item type
 */
final class SingleDeferredJust<T> extends CompletionSingle<T> {

    private final Supplier<? extends T> supplier;

    SingleDeferredJust(Supplier<? extends T> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier cannot be null!");
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new SingleDeferredSubscription<>(supplier, subscriber));
    }
}
