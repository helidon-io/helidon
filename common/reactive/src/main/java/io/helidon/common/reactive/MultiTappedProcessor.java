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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Processor executing provided functions on passing signals onNext, onError, onComplete, onCancel.
 *
 * @param <R> Type of the processed items.
 */
public class MultiTappedProcessor<R> extends BaseProcessor<R, R> implements Multi<R> {

    private Optional<BiFunction<Flow.Subscriber<? super R>, Long, Long>> onRequestFunction = Optional.empty();
    private Optional<Function<Flow.Subscriber<? super R>, Flow.Subscriber<? super R>>> whenSubscribeFunction = Optional.empty();
    private Optional<Function<R, R>> onNextFunction = Optional.empty();
    private Optional<Consumer<Throwable>> onErrorConsumer = Optional.empty();
    private Optional<Runnable> onCompleteRunnable = Optional.empty();
    private Optional<Consumer<Flow.Subscription>> onCancelConsumer = Optional.empty();

    /**
     * Create new processor with no functions to execute when signals are intercepted.
     */
    protected MultiTappedProcessor() {
    }

    @Override
    protected void submit(R item) {
        getSubscriber().onNext(item);
    }

    /**
     * Create new processor with no functions to execute when signals are intercepted.
     *
     * @param <R> Type of the processed items.
     * @return Brand new {@link io.helidon.common.reactive.MultiTappedProcessor}
     */
    public static <R> MultiTappedProcessor<R> create() {
        return new MultiTappedProcessor<>();
    }

    /**
     * Set {@link java.util.function.Function} to be executed when onNext signal is intercepted.
     *
     * @param function Function to be invoked.
     * @return This {@link io.helidon.common.reactive.MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onNext(Function<R, R> function) {
        onNextFunction = Optional.ofNullable(function);
        return this;
    }

    /**
     * Set {@link java.util.function.Function} to be executed when subscribe signal is intercepted.
     *
     * @param function Function to be invoked.
     * @return This {@link io.helidon.common.reactive.MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> whenSubscribe(Function<Flow.Subscriber<? super R>, Flow.Subscriber<? super R>> function) {
        whenSubscribeFunction = Optional.ofNullable(function);
        return this;
    }

    /**
     * Set {@link java.util.function.Consumer} to be executed when onError signal is intercepted.
     *
     * @param consumer Consumer to be executed when onError signal is intercepted,
     *                 argument is intercepted {@link Throwable}.
     * @return This {@link io.helidon.common.reactive.MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onError(Consumer<Throwable> consumer) {
        onErrorConsumer = Optional.ofNullable(consumer);
        return this;
    }

    /**
     * Set {@link Runnable} to be executed when onComplete signal is intercepted.
     *
     * @param runnable {@link Runnable} to be executed.
     * @return This {@link io.helidon.common.reactive.MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onComplete(Runnable runnable) {
        onCompleteRunnable = Optional.ofNullable(runnable);
        return this;
    }

    /**
     * Set {@link java.util.function.Function} to be executed when request signal is intercepted.
     *
     * @param function Function to be invoked.
     * @return This {@link io.helidon.common.reactive.MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onRequest(BiFunction<Flow.Subscriber<? super R>, Long, Long> function) {
        onRequestFunction = Optional.ofNullable(function);
        return this;
    }

    /**
     * Set consumer to be executed when onCancel signal is intercepted.
     *
     * @param consumer Consumer to be executed when onCancel signal is intercepted,
     *                 argument is intercepted {@link java.util.concurrent.Flow.Subscription}.
     * @return This {@link io.helidon.common.reactive.MultiTappedProcessor}
     */
    public MultiTappedProcessor<R> onCancel(Consumer<Flow.Subscription> consumer) {
        onCancelConsumer = Optional.ofNullable(consumer);
        return this;
    }

    @Override
    public void request(long n) {
        super.request(onRequestFunction.map(r -> r.apply(super.getSubscriber(), n)).orElse(n));
    }

    @Override
    public void cancel() {
        onCancelConsumer.ifPresent(c -> c.accept(super.getSubscription()));
        super.cancel();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> s) {
        Flow.Subscriber<? super R> subscriber = s;
        if (whenSubscribeFunction.isPresent()) {
            subscriber = whenSubscribeFunction.map(f -> f.apply(s)).get();
        }
        super.subscribe(subscriber);
    }

    @Override
    public void onNext(R item) {
        R value;
        try {
            value = onNextFunction.map(f -> f.apply(item)).orElse(item);
        } catch (Throwable t) {
            super.onError(t);
            return;
        }
        super.onNext(value);
    }

    @Override
    public void onError(Throwable error) {
        try {
            onErrorConsumer.ifPresent(c -> c.accept(error));
        } catch (Throwable t) {
            error.addSuppressed(t);
        }
        super.onError(error);
    }

    @Override
    public void onComplete() {
        try {
            onCompleteRunnable.ifPresent(Runnable::run);
            super.onComplete();
        } catch (Throwable t) {
            super.onError(t);
        }
    }
}
