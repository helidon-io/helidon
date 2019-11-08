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

package io.helidon.microprofile.messaging;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.InvocationTargetException;

public class InternalProcessor implements Processor<Object, Object> {


    private ProcessorChannelMethod processorChannelMethod;
    private Subscriber<? super Object> subscriber;

    public InternalProcessor(ProcessorChannelMethod processorChannelMethod) {
        this.processorChannelMethod = processorChannelMethod;
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        subscriber = s;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscriber.onSubscribe(s);
    }

    @Override
    public void onNext(Object incomingValue) {
        try {
            //TODO: Has to be always one param in the processor, validate and propagate better
            Class<?> paramType = processorChannelMethod.method.getParameterTypes()[0];
            Object processedValue = processorChannelMethod.method.invoke(processorChannelMethod.beanInstance, MessageUtils.unwrap(incomingValue, paramType));
            subscriber.onNext(wrapValue(processedValue));
        } catch (IllegalAccessException | InvocationTargetException e) {
            subscriber.onError(e);
        }
    }

    protected Object wrapValue(Object value) {
        return value;
    }

    @Override
    public void onError(Throwable t) {
        subscriber.onError(t);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }
}
