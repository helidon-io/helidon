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

package io.helidon.common.reactive;

import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Processor executing provided functions on passing signals onNext, onError, onComplete, onCancel.
 *
 * @param <R> Type of the processed items.
 */
public class MultiTappedProcessor<R> extends BufferedProcessor<R, R> implements Multi<R> {

    private Optional<Function<R, R>> onNextFunction = Optional.empty();
    private Optional<Consumer<Throwable>> onErrorConsumer = Optional.empty();
    private Optional<Runnable> onCompleteRunnable = Optional.empty();
    private Optional<Consumer<Flow.Subscription>> onCancelConsumer = Optional.empty();

    private MultiTappedProcessor() {
    }

    /**
     * Create new processor with no functions to execute when signals are intercepted.
     *
     * @param <R> Type of the processed items.
     * @return Brand new {@link MultiTappedProcessor}
     */
    public static <R> MultiTappedProcessor<R> create() {
        return new MultiTappedProcessor<>();
    }

    /**
     * Set {@link java.util.function.Function} to be executed when onNext signal is intercepted.
     *
     * @param function Function to be invoked.
     * @return This {@link MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onNext(Function<R, R> function) {
        onNextFunction = Optional.ofNullable(function);
        return this;
    }

    /**
     * Set {@link java.util.function.Consumer} to be executed when onError signal is intercepted.
     *
     * @param consumer Consumer to be executed when onError signal is intercepted,
     *                 argument is intercepted {@link java.lang.Throwable}.
     * @return This {@link MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onError(Consumer<Throwable> consumer) {
        onErrorConsumer = Optional.ofNullable(consumer);
        return this;
    }

    /**
     * Set {@link java.lang.Runnable} to be executed when onComplete signal is intercepted.
     *
     * @param runnable {@link java.lang.Runnable} to be executed.
     * @return This {@link MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onComplete(Runnable runnable) {
        onCompleteRunnable = Optional.ofNullable(runnable);
        return this;
    }

    /**
     * Set consumer to be executed when onCancel signal is intercepted.
     *
     * @param consumer Consumer to be executed when onCancel signal is intercepted,
     *                 argument is intercepted {@link java.util.concurrent.Flow.Subscription}.
     * @return This {@link MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onCancel(Consumer<Flow.Subscription> consumer) {
        onCancelConsumer = Optional.ofNullable(consumer);
        return this;
    }

    @Override
    protected void hookOnNext(R item) {
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
