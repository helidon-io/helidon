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

package io.helidon.microprofile.messaging.reactive;

import io.helidon.microprofile.messaging.MessageUtils;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.InvalidParameterException;

/**
 * Unwrap Message payload if incoming method Publisher or Publisher builder
 * has generic return type different than Message
 */
public class UnwrapProcessor implements Processor<Object, Object> {

    private Method method;
    private Subscriber<? super Object> subscriber;

    UnwrapProcessor() {
    }

    public static UnwrapProcessor of(Method method, Subscriber<Object> subscriber) {
        UnwrapProcessor unwrapProcessor = new UnwrapProcessor();
        unwrapProcessor.subscribe(subscriber);
        unwrapProcessor.setMethod(method);
        return unwrapProcessor;
    }

    Object unwrap(Object o) {
        return MessageUtils.unwrap(o, isTypeMessage(method));
    }

    static boolean isTypeMessage(Method method) {
        Type returnType = method.getGenericReturnType();
        ParameterizedType parameterizedType = (ParameterizedType) returnType;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (SubscriberBuilder.class.equals(method.getReturnType())) {
            if (actualTypeArguments.length != 2) {
                throw new DeploymentException("Invalid method return type " + method);
            }
            return isMessageType(actualTypeArguments[0]);
        } else if (Subscriber.class.equals(method.getReturnType())) {
            if (actualTypeArguments.length != 1) {
                throw new DeploymentException("Invalid method return type " + method);
            }
            return isMessageType(actualTypeArguments[0]);
        }
        throw new InvalidParameterException("Only methods with Subscriber or Subscriber builder should be unwrapped by processor");
    }

    private static boolean isMessageType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return Message.class.equals(parameterizedType.getRawType());
        }
        return false;
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

    public void setMethod(Method method) {
        this.method = method;
    }
}
