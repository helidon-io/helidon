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

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RSCompatibleProcessor<T, U> extends BaseProcessor<T, U> {

    private static final int BACK_PRESSURE_BUFFER_SIZE = 1024;

    private boolean rsCompatible = false;
    private ReferencedSubscriber<? super U> referencedSubscriber;
    private BlockingQueue<U> buffer = new ArrayBlockingQueue<U>(BACK_PRESSURE_BUFFER_SIZE);

    public void setRSCompatible(boolean rsCompatible) {
        this.rsCompatible = rsCompatible;
    }

    public boolean isRsCompatible() {
        return rsCompatible;
    }

    @Override
    public void request(long n) {
        if (rsCompatible && n <= 0) {
            // https://github.com/reactive-streams/reactive-streams-jvm#3.9
            onError(new IllegalArgumentException("non-positive subscription request"));
        }
        super.request(n);
    }

    @Override
    protected void tryRequest(Flow.Subscription subscription) {
        if (rsCompatible && !getSubscriber().isClosed() && !buffer.isEmpty()) {
            try {
                submit(buffer.take());
            } catch (InterruptedException e) {
                onError(e);
            }
        } else {
            super.tryRequest(subscription);
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super U> s) {
        referencedSubscriber = ReferencedSubscriber.create(s);
        super.subscribe(referencedSubscriber);
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        if (rsCompatible) {
            subscription.cancel();
            referencedSubscriber.releaseReference();
        }
    }

    @Override
    public void onNext(T item) {
        if (rsCompatible) {
            // https://github.com/reactive-streams/reactive-streams-jvm#2.13
            Objects.requireNonNull(item);
            try {
                hookOnNext(item);
            } catch (Throwable ex) {
                onError(ex);
            }
        } else {
            super.onNext(item);
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        if (rsCompatible) {
            // https://github.com/reactive-streams/reactive-streams-jvm#2.13
            Objects.requireNonNull(s);
            // https://github.com/reactive-streams/reactive-streams-jvm#2.5
            if (Objects.nonNull(super.getSubscription())) {
                s.cancel();
            }
        }
        super.onSubscribe(s);
    }

    @Override
    protected void notEnoughRequest(U item) {
        if (rsCompatible) {
            if (!buffer.offer(item)) {
                onError(new BackPressureOverflowException(BACK_PRESSURE_BUFFER_SIZE));
            }
        } else {
            super.notEnoughRequest(item);
        }
    }

    @Override
    protected void submit(U item) {
        super.submit(item);
    }

    @Override
    public void onComplete() {
        if (buffer.isEmpty()) {
            super.onComplete();
        }
    }

    @Override
    public void onError(Throwable ex) {
        if (rsCompatible) {
            // https://github.com/reactive-streams/reactive-streams-jvm#2.13
            Objects.requireNonNull(ex);
        }
        super.onError(ex);
    }

    public static class ReferencedSubscriber<T> implements Flow.Subscriber<T> {

        private Flow.Subscriber<T> subscriber;
        private WeakReference<Flow.Subscriber<T>> subscriberWeakReference;

        private ReferencedSubscriber(Flow.Subscriber<T> subscriber) {
            //Objects.requireNonNull(subscriber);
            this.subscriber = subscriber;
        }

        public static <T> ReferencedSubscriber<T> create(Flow.Subscriber<T> subscriber) {
            return new ReferencedSubscriber<>(subscriber);
        }

        public void releaseReference() {
            this.subscriberWeakReference = new WeakReference<>(this.subscriber);
            this.subscriber = null;
        }

        private Flow.Subscriber<T> getSubscriberReference() {
            if (Objects.nonNull(subscriber)) {
                return subscriber;
            } else if (Objects.nonNull(subscriberWeakReference)) {
                return subscriberWeakReference.get();
            }
            return null;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(T item) {
            getSubscriberReference().onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            getSubscriberReference().onError(throwable);
        }

        @Override
        public void onComplete() {
            getSubscriberReference().onComplete();
        }
    }
}
