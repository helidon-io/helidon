/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Consumer;

import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

/**
 * A subscriber delegated java functions for each of the subscriber methods.
 */
final class FunctionalSubscriber<T> implements Subscriber<T> {

    private final Consumer<? super T> consumer;
    private final Consumer<? super Throwable> errorConsumer;
    private final Runnable completeConsumer;
    private final Consumer<? super Subscription> subscriptionConsumer;
    private Subscription subscription;

    FunctionalSubscriber(Consumer<? super T> consumer,
            Consumer<? super Throwable> errorConsumer,
            Runnable completeConsumer,
            Consumer<? super Subscription> subscriptionConsumer) {

        this.consumer = consumer;
        this.errorConsumer = errorConsumer;
        this.completeConsumer = completeConsumer;
        this.subscriptionConsumer = subscriptionConsumer;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (this.subscription == null) {
            this.subscription = subscription;
            if (subscriptionConsumer != null) {
                try {
                    subscriptionConsumer.accept(subscription);
                } catch (Throwable ex) {
                    subscription.cancel();
                    onError(ex);
                }
            } else {
                subscription.request(Long.MAX_VALUE);
            }
        }
    }

    @Override
    public void onComplete() {
        if (completeConsumer != null) {
            try {
                completeConsumer.run();
            } catch (Throwable t) {
                onError(t);
            }
        }
    }

    @Override
    public void onError(Throwable ex) {
        if (errorConsumer != null) {
            errorConsumer.accept(ex);
        }
    }

    @Override
    public void onNext(T x) {
        try {
            if (consumer != null) {
                consumer.accept(x);
            }
        } catch (Throwable t) {
            this.subscription.cancel();
            onError(t);
        }
    }
}
