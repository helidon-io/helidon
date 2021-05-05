/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

/**
 * Create a Single for each incoming subscriber via a supplier callback.
 * @param <T> the element type of the sequence
 */
final class SingleDefer<T> extends CompletionSingle<T> {

    private final Supplier<? extends Single<? extends T>> supplier;

    SingleDefer(Supplier<? extends Single<? extends T>> supplier) {
        this.supplier = supplier;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Single<? extends T> publisher;

        try {
            publisher = Objects.requireNonNull(supplier.get(),
                    "The supplier returned a null Flow.Publisher");
        } catch (Throwable ex) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            subscriber.onError(ex);
            return;
        }

        publisher.subscribe(subscriber);
    }
}
