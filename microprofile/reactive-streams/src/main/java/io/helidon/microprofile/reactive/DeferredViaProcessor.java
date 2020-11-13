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
 *
 */

package io.helidon.microprofile.reactive;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Multi;

/**
 * Lets the downstream subscribe to the Processor first, then
 * the processor is subscribed to the source once.
 * @param <T> the element type of the upstream and the inlet of the Processor
 * @param <R> the outlet type of the Processor and the result type of the sequence
 */
final class DeferredViaProcessor<T, R> implements Multi<R> {

    private final Multi<T> source;

    private final Flow.Processor<T, R> processor;

    private final AtomicBoolean once;

    DeferredViaProcessor(Multi<T> source, Flow.Processor<T, R> processor) {
        this.source = source;
        this.processor = processor;
        this.once = new AtomicBoolean();
    }

    @Override
    public void subscribe(
            Flow.Subscriber<? super R> subscriber) {

        processor.subscribe(subscriber);

        if (!once.get() && once.compareAndSet(false, true)) {
            source.subscribe(processor);
        }
    }
}
