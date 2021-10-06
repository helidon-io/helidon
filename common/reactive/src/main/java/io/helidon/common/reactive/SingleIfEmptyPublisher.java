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
 *
 */
package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Executes given {@link java.lang.Runnable} when stream is finished without value(empty stream).
 *
 * @param <T> the item type
 */
final class SingleIfEmptyPublisher<T> extends CompletionSingle<T> {

    private final Single<T> source;
    private final Runnable ifEmpty;

    SingleIfEmptyPublisher(Single<T> source, Runnable ifEmpty) {
        this.source = source;
        this.ifEmpty = ifEmpty;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new MultiIfEmptyPublisher.IfEmptySubscriber<>(subscriber, ifEmpty));
    }
}
