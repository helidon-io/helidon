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

import java.lang.reflect.Method;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Unwrap Message payload if incoming method Publisher or Publisher builder
 * has generic return type different than Message.
 */
class UnwrapProcessor implements Processor<Object, Object> {

    private Method method;
    private Subscriber<? super Object> subscriber;

    UnwrapProcessor() {
    }

    static UnwrapProcessor of(Method method, Subscriber<? super Object> subscriber) {
        UnwrapProcessor unwrapProcessor = new UnwrapProcessor();
        unwrapProcessor.subscribe(subscriber);
        unwrapProcessor.setMethod(method);
        return unwrapProcessor;
    }

    Object unwrap(Object o) {
        return MessageUtils.unwrap(o, method);
    }


    @Override
    public void subscribe(Subscriber<? super Object> subscriber) {
        this.subscriber = subscriber;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscriber.onSubscribe(s);
    }

    @Override
    public void onNext(Object o) {
        subscriber.onNext(unwrap(o));
    }

    @Override
    public void onError(Throwable t) {
        subscriber.onError(t);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }

    void setMethod(Method method) {
        this.method = method;
    }
}
