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

package io.helidon.microprofile.messaging;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Publisher calling underlined messaging method for every requested item.
 */
class InternalPublisher implements Publisher<Object>, Subscription {

    private Method method;
    private Object beanInstance;
    private Subscriber<? super Object> subscriber;
    private AtomicBoolean closed = new AtomicBoolean(false);

    InternalPublisher(Method method, Object beanInstance) {
        this.method = method;
        this.beanInstance = beanInstance;
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        subscriber = s;
        subscriber.onSubscribe(this);
    }

    @Override
    public void request(long n) {
        try {
            for (long i = 0; i < n && !closed.get(); i++) {
                Object result = method.invoke(beanInstance);
                if (result instanceof CompletionStage) {
                    CompletionStage<?> completionStage = (CompletionStage<?>) result;
                    subscriber.onNext(completionStage.toCompletableFuture().get());
                } else {
                    subscriber.onNext(result);
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | InterruptedException | ExecutionException e) {
            subscriber.onError(e);
        }
    }

    @Override
    public void cancel() {
        closed.set(true);
    }
}
