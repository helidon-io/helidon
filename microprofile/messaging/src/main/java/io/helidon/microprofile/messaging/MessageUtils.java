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

package io.helidon.microprofile.messaging;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.InvalidParameterException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MessageUtils {

    public static Object unwrap(Object value, Class<?> type) throws ExecutionException, InterruptedException {
        //TODO: Stream-line case by case
        if (type.equals(Message.class)) {
            if (value instanceof Message) {
                return value;
            } else {
                return Message.of(value);
            }
        } else {
            if (value instanceof Message) {
                Object payload = ((Message) value).getPayload();
                return unwrapCompletableFuture(payload, type);
            } else if (value instanceof CompletableFuture) {
                //Recursion for Message<CompletableFuture<Message<String>>>
                return unwrap(((CompletableFuture) value).get(), type);
            } else {
                return value;
            }
        }
    }

    public static Object unwrapCompletableFuture(Object o, Class<?> expectedType) throws ExecutionException, InterruptedException {
        if (CompletableFuture.class.isInstance(o) && !CompletableFuture.class.isAssignableFrom(expectedType)) {
            //Recursion for Message<CompletableFuture<Message<String>>>
            return unwrap(((CompletableFuture) o).get(), expectedType);
        }
        return o;
    }

    public static Object unwrap(Object o, Method method) throws ExecutionException, InterruptedException {
        if (isTypeMessage(method)) {
            return unwrap(o, Message.class);
        }
        return unwrap(o, getFirstGenericType(method));
    }

    public static boolean isTypeMessage(Method method) {
        Type returnType = method.getGenericReturnType();
        ParameterizedType parameterizedType = (ParameterizedType) returnType;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

        //TODO: Use AbstractChannel.Type enum instead
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

        } else if (Processor.class.equals(method.getReturnType())) {
            return isMessageType(actualTypeArguments[0]);

        } else if (ProcessorBuilder.class.equals(method.getReturnType())) {
            return isMessageType(actualTypeArguments[0]);

        } else if (PublisherBuilder.class.equals(method.getReturnType())) {
            return isMessageType(actualTypeArguments[0]);

        } else if (Publisher.class.equals(method.getReturnType())) {
            return isMessageType(actualTypeArguments[0]);

        }
        throw new InvalidParameterException("Unsupported method for unwrapping " + method);
    }

    private static boolean isMessageType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return Message.class.equals(parameterizedType.getRawType());
        }
        return false;
    }

    private static Class<?> getFirstGenericType(Method method) {
        Type returnType = method.getGenericReturnType();
        ParameterizedType parameterizedType = (ParameterizedType) returnType;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        Type type = actualTypeArguments[0];
        if (type instanceof ParameterizedType) {
            ParameterizedType firstParameterizedType = (ParameterizedType) type;
            return (Class<?>) firstParameterizedType.getRawType();
        }
        return (Class<?>) type;
    }

}
