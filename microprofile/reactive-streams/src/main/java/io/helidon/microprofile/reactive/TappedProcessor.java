/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.BufferedProcessor;

public class TappedProcessor extends BufferedProcessor<Object, Object> {

    private Optional<Function<Object, Object>> onNextFunction = Optional.empty();
    private Optional<Consumer<Throwable>> onErrorConsumer = Optional.empty();
    private Optional<Runnable> onCompleteRunnable = Optional.empty();
    private Optional<Consumer<Flow.Subscription>> onCancelConsumer = Optional.empty();

    private TappedProcessor() {
    }

    public static TappedProcessor create() {
        TappedProcessor processor = new TappedProcessor();
        return processor;
    }

    public TappedProcessor onNext(Function<Object, Object> function) {
        onNextFunction = Optional.ofNullable(function);
        return this;
    }

    public TappedProcessor onError(Consumer<Throwable> consumer) {
        onErrorConsumer = Optional.ofNullable(consumer);
        return this;
    }

    public TappedProcessor onComplete(Runnable runnable) {
        onCompleteRunnable = Optional.ofNullable(runnable);
        return this;
    }

    public TappedProcessor onCancel(Consumer<Flow.Subscription> consumer) {
        onCancelConsumer = Optional.ofNullable(consumer);
        return this;
    }

    @Override
    protected void hookOnNext(Object item) {
        submit(onNextFunction.map(f -> f.apply(item)).orElse(item));
    }

    @Override
    protected void hookOnError(Throwable error) {
        onErrorConsumer.ifPresent(c -> c.accept(error));
        super.hookOnError(error);
    }

    @Override
    protected void hookOnComplete() {
        onCompleteRunnable.ifPresent(Runnable::run);
        super.hookOnComplete();
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        onCancelConsumer.ifPresent(c -> c.accept(subscription));
        subscription.cancel();
    }
}
