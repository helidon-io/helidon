/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.reactive.BaseProcessor;
import io.helidon.common.reactive.SequentialSubscriber;
import io.helidon.microprofile.reactive.hybrid.HybridProcessor;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;
import io.helidon.microprofile.reactive.hybrid.HybridSubscription;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class SequentialProcessor<T, U> extends SequentialSubscriber<T> implements Processor<T, U> {
    private Flow.Processor<T, U> processor;

    private SequentialProcessor(Flow.Processor<T, U> processor) {
        super(processor);
        this.processor = processor;
    }

    static <T, U> SequentialProcessor<T, U> wrap(Processor<T, U> processor) {
        return new SequentialProcessor<>(HybridProcessor.from(processor));
    }

    static <T, U> SequentialProcessor<T, U> create() {
        return new SequentialProcessor<>(HybridProcessor.from(new EmptyProcessor<>()));
    }

    @Override
    public void subscribe(Subscriber<? super U> s) {
        processor.subscribe(HybridSubscriber.from(s));
    }

    @Override
    public void onSubscribe(Subscription s) {
        super.onSubscribe(HybridSubscription.from(s));
    }

    private static class EmptyProcessor<T, U> extends BaseProcessor<T, U> {

        @Override
        @SuppressWarnings("unchecked")
        protected void submit(T item) {
            super.getSubscriber().onNext((U) item);
        }
    }
}
