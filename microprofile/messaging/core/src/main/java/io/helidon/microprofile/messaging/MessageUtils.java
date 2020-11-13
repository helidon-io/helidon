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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.InvalidParameterException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.DeploymentException;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

class MessageUtils {

    private MessageUtils() {
    }

    /**
     * Unwrap values to expected types.
     * <br>
     * Examples:
     * <pre>{@code
     * Message<CompletableFuture<Message<String>>>
     * Message<CompletableFuture<String>>
     * CompletableFuture<Message<String>>
     * Message<String>
     * }</pre>
     *
     * @param value value for unwrap
     * @param type  expected type
     * @return unwrapped value
     */
    static Object unwrap(Object value, Class<?> type) {
        return unwrap(value, type, () -> CompletableFuture.completedFuture((Void) null));
    }

    /**
     * Unwrap values to expected types.
     * <br>
     * Examples:
     * <pre>{@code
     * Message<CompletableFuture<Message<String>>>
     * Message<CompletableFuture<String>>
     * CompletableFuture<Message<String>>
     * Message<String>
     * }</pre>
     *
     * @param value value for unwrap
     * @param type  expected type
     * @param onAck {@link java.util.function.Supplier} in case of message wrapping is used for completion stage inferring
     * @return unwrapped value
     */
    static Object unwrap(Object value, Class<?> type, Supplier<CompletionStage<Void>> onAck) {
        if (Message.class.isAssignableFrom(type)) {
            if (value instanceof Message) {
                return value;
            } else {
                return Message.of(value, onAck);
            }
        } else {
            if (value instanceof Message) {
                return ((Message<?>) value).getPayload();
            } else {
                return value;
            }
        }
    }

    /**
     * Same as {@link MessageUtils#unwrap(java.lang.Object, java.lang.Class)}.
     * But extracts expected type from method reflexively.
     *
     * @param value  to unwrap
     * @param method to extract expected type from
     * @return unwrapped value
     */
    static Object unwrap(Object value, Method method) {
        if (isMessageType(method)) {
            return unwrap(value, Message.class);
        }
        return unwrap(value, getFirstGenericType(method));
    }

    /**
     * Check if expected type is {@link org.eclipse.microprofile.reactive.messaging.Message}.
     *
     * @param method {@link java.lang.reflect.Method} to check
     * @return true is expected type of method is {@link org.eclipse.microprofile.reactive.messaging.Message}
     */
    static boolean isMessageType(Method method) {
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
