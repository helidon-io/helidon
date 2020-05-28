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
 */
package io.helidon.common.reactive;

import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Decorated publisher that allows subscribing to individual events with java functions.
 *
 * @param <T> item type
 */
public interface Subscribable<T> extends Publisher<T> {

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     */
    default void subscribe(Consumer<? super T> consumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, null, null, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer      onNext delegate function
     * @param errorConsumer onError delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, null, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer         onNext delegate function
     * @param errorConsumer    onError delegate function
     * @param completeConsumer onComplete delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer, Runnable completeConsumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, completeConsumer, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer             onNext delegate function
     * @param errorConsumer        onError delegate function
     * @param completeConsumer     onComplete delegate function
     * @param subscriptionConsumer onSusbcribe delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer, Runnable completeConsumer,
                           Consumer<? super Flow.Subscription> subscriptionConsumer) {

        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, completeConsumer, subscriptionConsumer));
    }

    /**
     * Signals the default item if the upstream is empty.
     *
     * @param defaultItem the item to signal if the upstream is empty
     * @return Subscribable
     * @throws NullPointerException if {@code defaultItem} is {@code null}
     */
    Subscribable<T> defaultIfEmpty(T defaultItem);

    /**
     * Transform item with supplied function and flatten resulting {@link Flow.Publisher} to downstream.
     *
     * @param mapper {@link Function} receiving item as parameter and returning {@link Flow.Publisher}
     * @param <U>    output item type
     * @return Subscribable
     */
    <U> Subscribable<U> flatMap(Function<? super T, ? extends Publisher<? extends U>> mapper);

    /**
     * Transform item with supplied function and flatten resulting {@link Iterable} to downstream.
     *
     * @param mapper {@link Function} receiving item as parameter and returning {@link Iterable}
     * @param <U>    output item type
     * @return Subscribable
     */
    <U> Subscribable<U> flatMapIterable(Function<? super T, ? extends Iterable<? extends U>> mapper);

    /**
     * Map this {@link Subscribable} instance to a new {@link Subscribable}
     * of another type using the given {@link Function}.
     *
     * @param <U>    mapped item type
     * @param mapper mapper
     * @return Subscribable
     * @throws NullPointerException if mapper is {@code null}
     */
    <U> Subscribable<U> map(Function<? super T, ? extends U> mapper);

    /**
     * Re-emit the upstream's signals to the downstream on the given executor's thread.
     *
     * @param executor the executor to signal the downstream from.
     * @return Subscribable
     * @throws NullPointerException if {@code executor} is {@code null}
     */
    Subscribable<T> observeOn(Executor executor);

    /**
     * Executes given {@link java.lang.Runnable} when a cancel signal is received.
     *
     * @param onCancel {@link java.lang.Runnable} to be executed.
     * @return Subscribable
     */
    Subscribable<T> onCancel(Runnable onCancel);

    /**
     * Executes given {@link java.lang.Runnable} when onComplete signal is received.
     *
     * @param onComplete {@link java.lang.Runnable} to be executed.
     * @return Subscribable
     */
    Subscribable<T> onComplete(Runnable onComplete);

    /**
     * Executes given {@link java.lang.Runnable} when onError signal is received.
     *
     * @param onErrorConsumer {@link java.util.function.Consumer} to be executed.
     * @return Subscribable
     */
    Subscribable<T> onError(Consumer<? super Throwable> onErrorConsumer);

    /**
     * Resume stream from supplied publisher if onError signal is intercepted.
     *
     * @param onError supplier of new stream publisher
     * @return Subscribable
     */
    Subscribable<T> onErrorResumeWith(Function<? super Throwable, ? extends Flow.Publisher<? extends T>> onError);

    /**
     * {@link java.util.function.Function} providing one item to be submitted as onNext in case of onError signal is received.
     *
     * @param onError Function receiving {@link java.lang.Throwable} as argument and producing one item to resume stream with.
     * @return Subscribable
     */
    Subscribable<T> onErrorResume(Function<? super Throwable, ? extends T> onError);

    /**
     * Resume stream from single item if onComplete signal is
     * intercepted. Effectively do an {@code append} to the stream.
     *
     * @param item one item to resume stream with
     * @return Subscribable
     */
    Subscribable<T> onCompleteResume(T item);

    /**
     * Resume stream from supplied publisher if onComplete signal is intercepted.
     *
     * @param publisher new stream publisher
     * @return Subscribable
     */
    Subscribable<T> onCompleteResumeWith(Flow.Publisher<? extends T> publisher);

    /**
     * Executes given {@link java.lang.Runnable} when any of signals onComplete,
     * onCancel or onError is received.
     *
     * @param onTerminate {@link java.lang.Runnable} to be executed.
     * @return Subscribable
     */
    Subscribable<T> onTerminate(Runnable onTerminate);

    /**
     * Invoke provided consumer for every item in stream.
     *
     * @param consumer consumer to be invoked
     * @return Subscribable
     */
    Subscribable<T> peek(Consumer<? super T> consumer);

    /**
     * Retry a failing upstream at most the given number of times before giving up.
     *
     * @param count the number of times to retry; 0 means no retry at all
     * @return Subscribable
     * @throws IllegalArgumentException if {@code count} is negative
     * @see #retryWhen(BiFunction)
     */
    Subscribable<T> retry(long count);

    /**
     * Retry a failing upstream if the predicate returns true.
     *
     * @param predicate the predicate that receives the latest failure {@link Throwable}
     *                  the number of times the retry happened so far (0-based) and
     *                  should return {@code true} to retry the upstream again or
     *                  {@code false} to signal the latest failure
     * @return Subscribable
     * @throws NullPointerException if {@code predicate} is {@code null}
     * @see #retryWhen(BiFunction)
     */
    Subscribable<T> retry(BiPredicate<? super Throwable, ? super Long> predicate);

    /**
     * Retry a failing upstream when the given function returns a publisher that
     * signals an item.
     * <p>
     * If the publisher returned by the function completes, the repetition stops
     * and this Subscribable is completed.
     * If the publisher signals an error, the repetition stops
     * and this Subscribable will signal this error.
     * </p>
     *
     * @param whenFunction the function that receives the latest failure {@link Throwable}
     *                     the number of times the retry happened so far (0-based) and
     *                     should return a {@link Flow.Publisher} that should signal an item
     *                     to retry again, complete to stop and complete this Subscribable
     *                     or signal an error to have this Subscribable emit that error as well.
     * @param <U>          the element type of the retry-signal sequence
     * @return Subscribable
     * @throws NullPointerException if {@code whenFunction} is {@code null}
     */
    <U> Subscribable<T> retryWhen(BiFunction<? super Throwable,
            ? super Long, ? extends Flow.Publisher<U>> whenFunction);

    /**
     * Relay upstream items until the other source signals an item or completes.
     *
     * @param other the other sequence to signal the end of the main sequence
     * @param <U>   the element type of the other sequence
     * @return Subscribable
     * @throws NullPointerException if {@code other} is {@code null}
     */
    <U> Subscribable<T> takeUntil(Flow.Publisher<U> other);

    /**
     * Signals a {@link java.util.concurrent.TimeoutException} if the upstream doesn't
     * signal the next item, error or completion within the specified time.
     *
     * @param timeout  the time to wait for the upstream to signal
     * @param unit     the time unit
     * @param executor the executor to use for waiting for the upstream signal
     * @return Subscribable
     * @throws NullPointerException if {@code unit} or {@code executor} is {@code null}
     */
    Subscribable<T> timeout(long timeout, TimeUnit unit, ScheduledExecutorService executor);
}
