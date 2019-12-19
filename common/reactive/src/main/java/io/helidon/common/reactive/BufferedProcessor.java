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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class BufferedProcessor<T, U> extends BaseProcessor<T, U> {

    private static final int BACK_PRESSURE_BUFFER_SIZE = 1024;

    private ReferencedSubscriber<? super U> referencedSubscriber;
    private BlockingQueue<U> buffer = new ArrayBlockingQueue<U>(BACK_PRESSURE_BUFFER_SIZE);
    private ReentrantLock publisherSequentialLock = new ReentrantLock();

    @Override
    public void request(long n) {
        //TODO: Move to BaseProcessor
        StreamValidationUtils.checkRequestParam309(n, this::fail);
        StreamValidationUtils.checkRecursionDepth303(5, (actDepth, t) -> fail(t));
        super.request(n);
    }


    @Override
    protected void tryRequest(Flow.Subscription subscription) {
        if (true && !getSubscriber().isClosed() && !buffer.isEmpty()) {
            try {
                submit(buffer.take());
            } catch (InterruptedException e) {
                fail(e);
            }
        } else {
            super.tryRequest(subscription);
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super U> s) {
        // https://github.com/reactive-streams/reactive-streams-jvm#3.13
        //TODO: Move to BaseProcessor
        referencedSubscriber = ReferencedSubscriber.create(s);
        super.subscribe(referencedSubscriber);
    }

    @Override
    protected void hookOnCancel(Flow.Subscription subscription) {
        //TODO: Move to BaseProcessor
        Optional.ofNullable(subscription).ifPresent(Flow.Subscription::cancel);
        // https://github.com/reactive-streams/reactive-streams-jvm#3.13
        referencedSubscriber.releaseReference();
    }

    @Override
    public void onNext(T item) {
        //TODO: Move to BaseProcessor
        publisherSequentialLock.lock();
        // https://github.com/reactive-streams/reactive-streams-jvm#2.13
        Objects.requireNonNull(item);
        try {
            hookOnNext(item);
        } catch (Throwable ex) {
            fail(ex);
        }
        publisherSequentialLock.unlock();
    }

    @Override
    //TODO: Move to BaseProcessor
    public void onSubscribe(Flow.Subscription s) {
        // https://github.com/reactive-streams/reactive-streams-jvm#1.3
        publisherSequentialLock.lock();
        // https://github.com/reactive-streams/reactive-streams-jvm#2.13
        Objects.requireNonNull(s);
        // https://github.com/reactive-streams/reactive-streams-jvm#2.5
        getSubscription().ifPresent(firstSubscription -> s.cancel());
        super.onSubscribe(s);
        publisherSequentialLock.unlock();
    }

    @Override
    protected void notEnoughRequest(U item) {
        if (!buffer.offer(item)) {
            fail(new BackPressureOverflowException(BACK_PRESSURE_BUFFER_SIZE));
        }
    }

    @Override
    public void onComplete() {
        if (buffer.isEmpty()) {
            super.onComplete();
        }
    }

    @Override
    //TODO: Move to BaseProcessor
    public void fail(Throwable ex) {
        //Upstream cancel on error with fail method proxy to avoid spec rule 2.3
        getSubscription().ifPresent(Flow.Subscription::cancel);
        super.fail(ex);
    }

    @Override
    //TODO: Move to BaseProcessor
    public void onError(Throwable ex) {
        superOnError(ex);
    }

    //TODO: Move to BaseProcessor
    protected void superOnError(Throwable ex) {
        // https://github.com/reactive-streams/reactive-streams-jvm#2.13
        Objects.requireNonNull(ex);
        done = true;
        if (error == null) {
            error = ex;
        }
        tryComplete();
    }

    private static class ReferencedSubscriber<T> implements Flow.Subscriber<T> {

        private Optional<Flow.Subscriber<T>> subscriber;

        private ReferencedSubscriber(Flow.Subscriber<T> subscriber) {
            this.subscriber = Optional.of(subscriber);
        }

        public static <T> ReferencedSubscriber<T> create(Flow.Subscriber<T> subscriber) {
            return new ReferencedSubscriber<>(subscriber);
        }

        public void releaseReference() {
            this.subscriber = Optional.empty();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscriber.ifPresent(s -> s.onSubscribe(subscription));
        }

        @Override
        public void onNext(T item) {
            subscriber.ifPresent(s -> s.onNext(item));
        }

        @Override
        public void onError(Throwable throwable) {
            subscriber.ifPresent(s -> s.onError(throwable));
        }

        @Override
        public void onComplete() {
            subscriber.ifPresent(Flow.Subscriber::onComplete);
        }
    }
}
