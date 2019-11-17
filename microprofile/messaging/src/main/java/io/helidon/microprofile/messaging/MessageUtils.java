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
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;

import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.InvalidParameterException;

public class MessageUtils {
    public static Object unwrap(Object value, Class<?> type) {
        return unwrap(value, type.equals(Message.class));
    }

    public static Object unwrap(Object value, Boolean isMessageType) {
        if (isMessageType) {
            if (value instanceof Message) {
                return value;
            } else {
                return Message.of(value);
            }
        } else {
            if (value instanceof Message) {
                return ((Message) value).getPayload();
            } else {
                return value;
            }
        }
    }

    public static Object unwrap(Object o, Method method) {
        return unwrap(o, isTypeMessage(method));
    }

    public static boolean isTypeMessage(Method method) {
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

}
