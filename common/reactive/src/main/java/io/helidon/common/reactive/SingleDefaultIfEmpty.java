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

package io.helidon.common.reactive;

import java.util.concurrent.Flow;

/**
 * Signal an item if the source is empty.
 * @param <T> the element type
 */
final class SingleDefaultIfEmpty<T> extends CompletionSingle<T> {

    private final Single<T> source;

    private final T defaultItem;

    SingleDefaultIfEmpty(Single<T> source, T defaultItem) {
        this.source = source;
        this.defaultItem = defaultItem;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new DefaultIfEmptySubscriber<>(subscriber, defaultItem));
    }

    static final class DefaultIfEmptySubscriber<T> extends DeferredScalarSubscription<T>
    implements Flow.Subscriber<T> {

        private final T defaultItem;

        private Flow.Subscription upstream;

        DefaultIfEmptySubscriber(Flow.Subscriber<? super T> downstream, T defaultItem) {
            super(downstream);
            this.defaultItem = defaultItem;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            subscribeSelf();
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            upstream = SubscriptionHelper.CANCELED;
            complete(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error(throwable);
        }

        @Override
        public void onComplete() {
            if (upstream != SubscriptionHelper.CANCELED) {
                complete(defaultItem);
            }
        }

        @Override
        public void cancel() {
            super.cancel();
            upstream.cancel();
        }
    }
}
