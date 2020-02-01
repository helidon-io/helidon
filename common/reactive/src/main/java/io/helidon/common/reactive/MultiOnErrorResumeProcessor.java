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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Resume stream from supplied publisher if onError signal is intercepted.
 *
 * @param <T> item type
 */
public class MultiOnErrorResumeProcessor<T> implements Flow.Processor<T, T>, Flow.Subscription, Multi<T> {

    private Function<Throwable, Flow.Publisher<T>> publisherSupplier;
    private AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private AtomicReference<Flow.Subscriber<? super T>> subscriber = new AtomicReference<>();
    private RequestedCounter requestedCounter = new RequestedCounter();
    private Optional<Throwable> error = Optional.empty();
    private boolean done;

    private MultiOnErrorResumeProcessor() {
    }

    /**
     * Create new {@link MultiOnErrorResumeProcessor} with supplier for item to submit after error is intercepted.
     *
     * @param supplier for item to submit after error is intercepted
     * @param <T>      item type
     * @return new {@link MultiOnErrorResumeProcessor}
     */
    @SuppressWarnings("unchecked")
    public static <T> MultiOnErrorResumeProcessor<T> resume(Function<Throwable, ?> supplier) {
        MultiOnErrorResumeProcessor<T> processor = new MultiOnErrorResumeProcessor<>();
        processor.publisherSupplier = t -> IterablePublisher.create(List.of(((Function<Throwable, T>) supplier).apply(t)));
        return processor;
    }

    /**
     * Create new {@link MultiOnErrorResumeProcessor} with supplier for {@link java.util.concurrent.Flow.Publisher}
     * to resume stream after error is intercepted.
     *
     * @param supplier or {@link java.util.concurrent.Flow.Publisher}
     *                 to resume stream after error is intercepted
     * @param <T>      item type
     * @return new {@link MultiOnErrorResumeProcessor}
     */
    public static <T> MultiOnErrorResumeProcessor<T> resumeWith(Function<Throwable, Flow.Publisher<T>> supplier) {
        MultiOnErrorResumeProcessor<T> processor = new MultiOnErrorResumeProcessor<>();
        processor.publisherSupplier = supplier;
        return processor;
    }


    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        this.subscriber.set(SequentialSubscriber.create(subscriber));
        if (Objects.nonNull(this.subscription.get())) {
            this.subscriber.get().onSubscribe(this);
            if (done) {
                tryComplete();
            }
        }
        error.ifPresent(this::fail);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription.set(subscription);
        if (Objects.nonNull(subscriber.get())) {
            subscriber.get().onSubscribe(this);
        }
    }

    @Override
    public void request(long n) {
        requestedCounter.increment(n, this::onError);
        subscription.get().request(n);
    }

    @Override
    public void cancel() {
        subscription.get().cancel();
    }

    @Override
    public void onNext(T item) {
        if (requestedCounter.tryDecrement()) {
            subscriber.get().onNext(item);
        } else {
            onError(new IllegalStateException("Not enough request to submit item"));
        }
    }

    @Override
    public void onError(Throwable ex) {
        Objects.requireNonNull(ex);
        try {
            publisherSupplier.apply(ex).subscribe(SequentialSubscriber.create(new Flow.Subscriber<T>() {

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    Objects.requireNonNull(s);
                    subscription.set(s);
                    if (requestedCounter.get() > 0) {
                        s.request(requestedCounter.get());
                    }
                }

                @Override
                public void onNext(T t) {
                    subscriber.get().onNext(t);
                }

                @Override
                public void onError(Throwable t) {
                    fail(t);
                }

                @Override
                public void onComplete() {
                    MultiOnErrorResumeProcessor.this.onComplete();
                }
            }));

        } catch (Throwable t) {
            fail(t);
        }
    }

    private void fail(Throwable t) {
        if (Objects.nonNull(subscriber.get())) {
            subscriber.get().onError(t);
        } else {
            this.error = Optional.of(t);
        }
    }

    @Override
    public void onComplete() {
        done = true;
        tryComplete();
    }

    private void tryComplete() {
        if (Objects.nonNull(subscriber.get())) {
            subscriber.get().onComplete();
        }
    }
}
