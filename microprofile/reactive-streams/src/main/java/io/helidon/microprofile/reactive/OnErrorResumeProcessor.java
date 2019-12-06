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

import java.util.Objects;
import java.util.function.Function;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.RSCompatibleProcessor;

import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class OnErrorResumeProcessor<T> extends RSCompatibleProcessor<T, T> {


    private Function<Throwable, T> supplier;
    private Function<Throwable, Publisher<T>> publisherSupplier;

    private OnErrorResumeProcessor() {
    }

    @SuppressWarnings("unchecked")
    static <T> OnErrorResumeProcessor<T> resume(Function<Throwable, ?> supplier) {
        OnErrorResumeProcessor<T> processor = new OnErrorResumeProcessor<>();
        processor.supplier = (Function<Throwable, T>) supplier;
        return processor;
    }

    static <T> OnErrorResumeProcessor<T> resumeWith(Function<Throwable, Graph> supplier) {
        OnErrorResumeProcessor<T> processor = new OnErrorResumeProcessor<>();
        processor.publisherSupplier = throwable -> GraphBuilder.create().from(supplier.apply(throwable)).getPublisher();
        return processor;
    }

    @Override
    public void onError(Throwable ex) {
        Objects.requireNonNull(ex);
        try {
            if (Objects.nonNull(supplier)) {

                submit(supplier.apply(ex));
                tryComplete();

            } else {
                publisherSupplier.apply(ex).subscribe(new Subscriber<T>() {
                    private Subscription subscription;

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        Objects.requireNonNull(subscription);
                        this.subscription = subscription;
                        subscription.request(getRequestedCounter().get());
                    }

                    @Override
                    public void onNext(T t) {
                        submit(t);
                        subscription.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        Objects.requireNonNull(t);
                        superError(t);
                    }

                    @Override
                    public void onComplete() {
                        OnErrorResumeProcessor.this.onComplete();
                    }
                });
            }
        } catch (Throwable t) {
            superError(t);
        }
    }

    private void superError(Throwable t) {
        super.onError(t);
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        subscription.cancel();
    }
}
