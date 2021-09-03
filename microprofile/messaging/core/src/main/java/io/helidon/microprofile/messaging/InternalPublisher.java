/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.RequestedCounter;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Publisher calling underlined messaging method for every requested item.
 */
class InternalPublisher implements Publisher<Object>, Subscription {

    private Subscriber<? super Object> subscriber;
    private final Method method;
    private final Object beanInstance;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableQueue<Object> completableQueue;
    private final RequestedCounter requestedCounter = new RequestedCounter();

    InternalPublisher(Method method, Object beanInstance) {
        this.method = method;
        this.beanInstance = beanInstance;
        completableQueue = CompletableQueue.create((o, throwable) -> {
            if (Objects.isNull(throwable)) {
                subscriber.onNext(o.getValue());
                trySubmit();
            } else {
                subscriber.onError(throwable);
            }
        });
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        subscriber = s;
        subscriber.onSubscribe(this);
    }

    @Override
    public void request(long n) {
        requestedCounter.increment(n, subscriber::onError);
        trySubmit();
    }

    @SuppressWarnings("unchecked")
    private void trySubmit() {
        try {
            while (!completableQueue.isBackPressureLimitReached() && requestedCounter.tryDecrement() && !closed.get()) {
                Object result = method.invoke(beanInstance);
                if (result instanceof CompletionStage) {
                    CompletionStage<Object> completionStage = (CompletionStage<Object>) result;
                    completableQueue.add(completionStage.toCompletableFuture());
                } else {
                    subscriber.onNext(result);
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            subscriber.onError(e);
        }
    }

    @Override
    public void cancel() {
        closed.set(true);
    }
}
