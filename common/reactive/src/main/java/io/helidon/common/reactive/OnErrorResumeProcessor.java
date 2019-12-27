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

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Resume stream from supplied publisher if onError signal is intercepted.
 *
 * @param <T> item type
 */
public class OnErrorResumeProcessor<T> extends BufferedProcessor<T, T> implements Multi<T> {

    private Function<Throwable, T> supplier;
    private Function<Throwable, Flow.Publisher<T>> publisherSupplier;
    private AtomicReference<Optional<Flow.Subscription>> onErrorPublisherSubscription = new AtomicReference<>(Optional.empty());

    private OnErrorResumeProcessor() {
    }

    /**
     * Create new {@link OnErrorResumeProcessor} with supplier for item to submit after error is intercepted.
     *
     * @param supplier for item to submit after error is intercepted
     * @param <T>      item type
     * @return new {@link OnErrorResumeProcessor}
     */
    @SuppressWarnings("unchecked")
    public static <T> OnErrorResumeProcessor<T> resume(Function<Throwable, ?> supplier) {
        OnErrorResumeProcessor<T> processor = new OnErrorResumeProcessor<>();
        processor.supplier = (Function<Throwable, T>) supplier;
        return processor;
    }

    /**
     * Create new {@link OnErrorResumeProcessor} with supplier for {@link io.helidon.common.reactive.Flow.Publisher}
     * to resume stream after error is intercepted.
     *
     * @param supplier or {@link io.helidon.common.reactive.Flow.Publisher}
     *                 to resume stream after error is intercepted
     * @param <T>      item type
     * @return new {@link OnErrorResumeProcessor}
     */
    public static <T> OnErrorResumeProcessor<T> resumeWith(Function<Throwable, Flow.Publisher<T>> supplier) {
        OnErrorResumeProcessor<T> processor = new OnErrorResumeProcessor<>();
        processor.publisherSupplier = supplier;
        return processor;
    }

    @Override
    protected void tryRequest(Flow.Subscription subscription) {
        super.tryRequest(onErrorPublisherSubscription.get()
                .orElse(subscription));
    }

    @Override
    protected void hookOnNext(T item) {
        super.submit(item);
    }

    @Override
    public void onError(Throwable ex) {
        Objects.requireNonNull(ex);
        try {
            if (Objects.nonNull(supplier)) {

                submit(supplier.apply(ex));
                tryComplete();

            } else {
                publisherSupplier.apply(ex).subscribe(new Flow.Subscriber<T>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        Objects.requireNonNull(subscription);
                        onErrorPublisherSubscription.set(Optional.of(subscription));
                        if (getRequestedCounter().get() > 0) {
                            subscription.request(getRequestedCounter().get());
                        }
                    }

                    @Override
                    public void onNext(T t) {
                        submit(t);
                    }

                    @Override
                    public void onError(Throwable t) {
                        Objects.requireNonNull(t);
                        fail(t);
                    }

                    @Override
                    public void onComplete() {
                        OnErrorResumeProcessor.this.onComplete();
                        onErrorPublisherSubscription.set(Optional.empty());
                    }
                });
            }
        } catch (Throwable t) {
            onErrorPublisherSubscription.get().ifPresent(Flow.Subscription::cancel);
            fail(t);
        }
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        subscription.cancel();
        onErrorPublisherSubscription.get().ifPresent(Flow.Subscription::cancel);
    }
}
