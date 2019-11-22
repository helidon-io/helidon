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

package io.helidon.microprofile.messaging.channel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import javax.enterprise.inject.spi.DeploymentException;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Method signature resolving utility, returns {@link MethodSignatureType} for any recognized signature.
 * Throws {@link javax.enterprise.inject.spi.DeploymentException} for any un-recognized signature.
 */
public final class MethodSignatureResolver {
    private final Class<?> returnType;
    private final Type genericReturnType;
    private final Class<?>[] parameterTypes;
    private final Type[] genericParameterTypes;
    private Method method;

    private MethodSignatureResolver(Method method) {
        this.method = method;
        returnType = method.getReturnType();
        genericReturnType = method.getGenericReturnType();
        parameterTypes = method.getParameterTypes();
        genericParameterTypes = method.getGenericParameterTypes();
    }

    /**
     * Method signature resolving utility, returns {@link MethodSignatureType} for any recognized signature.
     *
     * @param method {@link java.lang.reflect.Method} to be resolved
     * @return {@link MethodSignatureResolver}
     */
    public static MethodSignatureResolver create(Method method) {
        return new MethodSignatureResolver(method);
    }

    /**
     * Returns {@link MethodSignatureType} for any recognized signature.
     * Throws {@link javax.enterprise.inject.spi.DeploymentException}
     * for any un-recognized signature.
     *
     * @return {@link io.helidon.microprofile.messaging.channel.MethodSignatureType}
     * of recognized signature
     * @throws javax.enterprise.inject.spi.DeploymentException for un-recognized signature
     */
    public MethodSignatureType resolve() {
        // INCOMING METHODS
        if (returnsClassWithGenericParams(CompletionStage.class, MsgType.PAYLOAD) && hasFirstParam(MsgType.MESSAGE)) {
            // CompletionStage<?> method(Message<I> msg)
            return MethodSignatureType.INCOMING_COMPLETION_STAGE_2_MSG;
        }
        if (returnsClassWithGenericParams(CompletionStage.class, MsgType.PAYLOAD) && hasFirstParam(MsgType.PAYLOAD)
                && isIncoming()) {
            // CompletionStage<?> method(I payload)
            return MethodSignatureType.INCOMING_COMPLETION_STAGE_2_PAYL;
        }
        if (hasNoParams() && returnsClassWithGenericParams(SubscriberBuilder.class, MsgType.MESSAGE)) {
            // SubscriberBuilder<Message<I>> method()
            return MethodSignatureType.INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(SubscriberBuilder.class, MsgType.PAYLOAD)) {
            // SubscriberBuilder<I> method()
            return MethodSignatureType.INCOMING_SUBSCRIBER_BUILDER_PAYL_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(Subscriber.class, MsgType.MESSAGE)
                && isIncoming()) {
            // Subscriber<Message<I>> method()
            return MethodSignatureType.INCOMING_SUBSCRIBER_MSG_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(Subscriber.class, MsgType.PAYLOAD)
                && isIncoming()) {
            // Subscriber<I> method()
            return MethodSignatureType.INCOMING_SUBSCRIBER_PAYL_2_VOID;
        }
        if (returnsVoid() && hasFirstParam(MsgType.PAYLOAD)) {
            // void method(I payload)
            return MethodSignatureType.INCOMING_VOID_2_PAYL;
        }
        // PROCESSOR METHODS
        if (returnsClassWithGenericParams(CompletionStage.class, MsgType.MESSAGE) && hasFirstParam(MsgType.MESSAGE)) {
            // CompletionStage<Message<O>> method(Message<I> msg)
            return MethodSignatureType.PROCESSOR_COMPL_STAGE_MSG_2_MSG;
        }
        if (returnsClassWithGenericParams(CompletionStage.class, MsgType.PAYLOAD) && hasFirstParam(MsgType.PAYLOAD)) {
            // CompletionStage<O> method(I payload)
            return MethodSignatureType.PROCESSOR_COMPL_STAGE_PAYL_2_PAYL;
        }
        if (hasNoParams() && returnsClassWithGenericParams(ProcessorBuilder.class, MsgType.MESSAGE)) {
            // ProcessorBuilder<Message<I>, Message<O>> method();
            return MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(ProcessorBuilder.class, MsgType.PAYLOAD)) {
            // ProcessorBuilder<I, O> method();
            return MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_PAYL_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(Processor.class, MsgType.MESSAGE)) {
            // Processor<Message<I>, Message<O>> method();
            return MethodSignatureType.PROCESSOR_PROCESSOR_MSG_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(Processor.class, MsgType.PAYLOAD)) {
            // Processor<I, O> method();
            return MethodSignatureType.PROCESSOR_PROCESSOR_PAYL_2_VOID;
        }
        if (returnsClassWithGenericParams(PublisherBuilder.class, MsgType.MESSAGE)
                && hasFirstParamClassWithGeneric(PublisherBuilder.class, MsgType.MESSAGE)) {
            // PublisherBuilder<Message<O>> method(PublisherBuilder<Message<I>> pub);
            return MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_PUBLISHER_BUILDER_MSG;
        }
        if (returnsClassWithGenericParams(PublisherBuilder.class, MsgType.PAYLOAD)
                && hasFirstParamClassWithGeneric(PublisherBuilder.class, MsgType.PAYLOAD)) {
            // PublisherBuilder<O> method(PublisherBuilder<I> pub);
            return MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PUBLISHER_BUILDER_PAYL;
        }
        if (returnsClassWithGenericParams(PublisherBuilder.class, MsgType.MESSAGE) && hasFirstParam(MsgType.MESSAGE)) {
            // PublisherBuilder<Message<O>> method(Message<I>msg);
            return MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG;
        }
        if (returnsClassWithGenericParams(PublisherBuilder.class, MsgType.PAYLOAD) && hasFirstParam(MsgType.PAYLOAD)) {
            // PublisherBuilder<O> method(<I> msg);
            return MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PAYL;
        }
        if (returnsClassWithGenericParams(Publisher.class, MsgType.MESSAGE)
                && hasFirstParamClassWithGeneric(Publisher.class, MsgType.MESSAGE)) {
            // Publisher<Message<O>> method(Publisher<Message<I>> pub);
            return MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_PUBLISHER_MSG;
        }
        if (returnsClassWithGenericParams(Publisher.class, MsgType.PAYLOAD)
                && hasFirstParamClassWithGeneric(Publisher.class, MsgType.PAYLOAD)) {
            // Publisher<O> method(Publisher<I> pub);
            return MethodSignatureType.PROCESSOR_PUBLISHER_PAYL_2_PUBLISHER_PAYL;
        }
        if (returnsClassWithGenericParams(Publisher.class, MsgType.MESSAGE) && hasFirstParam(MsgType.MESSAGE)) {
            // Publisher<Message<O>> method(Message<I>msg);
            return MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_MSG;
        }
        if (returnsClassWithGenericParams(Publisher.class, MsgType.PAYLOAD) && hasFirstParam(MsgType.PAYLOAD)) {
            // Publisher<O> method(I payload);
            return MethodSignatureType.PROCESSOR_PUBLISHER_PAYL_2_PAYL;
        }
        if (returns(MsgType.MESSAGE) && hasFirstParam(MsgType.MESSAGE)) {
            // Message<O> method(Message<I> msg)
            return MethodSignatureType.PROCESSOR_MSG_2_MSG;
        }
        if (returns(MsgType.PAYLOAD) && hasFirstParam(MsgType.PAYLOAD)) {
            // O method(I payload)
            return MethodSignatureType.PROCESSOR_PAYL_2_PAYL;
        }
        // OUTGOING METHODS
        if (hasNoParams() && returnsClassWithGenericParams(CompletionStage.class, MsgType.MESSAGE)) {
            // CompletionStage<Message<U>> method()
            return MethodSignatureType.OUTGOING_COMPLETION_STAGE_MSG_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(CompletionStage.class, MsgType.PAYLOAD)) {
            // CompletionStage<U> method()
            return MethodSignatureType.OUTGOING_COMPLETION_STAGE_PAYL_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(Publisher.class, MsgType.MESSAGE)) {
            // Publisher<Message<U>> method()
            return MethodSignatureType.OUTGOING_PUBLISHER_MSG_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(Publisher.class, MsgType.PAYLOAD)) {
            // Publisher<U> method()
            return MethodSignatureType.OUTGOING_PUBLISHER_PAYL_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(PublisherBuilder.class, MsgType.MESSAGE)) {
            // PublisherBuilder<Message<U>> method()
            return MethodSignatureType.OUTGOING_PUBLISHER_BUILDER_MSG_2_VOID;
        }
        if (hasNoParams() && returnsClassWithGenericParams(PublisherBuilder.class, MsgType.PAYLOAD)) {
            // PublisherBuilder<U> method()
            return MethodSignatureType.OUTGOING_PUBLISHER_BUILDER_PAYL_2_VOID;
        }
        if (hasNoParams() && returns(MsgType.MESSAGE)) {
            // Message<U> method()
            return MethodSignatureType.OUTGOING_MSG_2_VOID;
        }
        if (hasNoParams() && returns(MsgType.PAYLOAD)) {
            // U method()
            return MethodSignatureType.OUTGOING_PAYL_2_VOID;
        }
        // Remove when TCK issue is solved https://github.com/eclipse/microprofile-reactive-messaging/issues/79
        // see io.helidon.microprofile.messaging.inner.BadSignaturePublisherPayloadBean
        if (returns(MsgType.PAYLOAD) && hasFirstParam(MsgType.PAYLOAD)) {
            // O method(I payload)
            return MethodSignatureType.INCOMING_VOID_2_PAYL;
        }
        // END OF BLOCK FOR REMOVE
        throw new DeploymentException("Unsupported method signature " + method);
    }

    private boolean hasNoParams() {
        return method.getParameterCount() == 0;
    }

    private boolean returnsVoid() {
        return Void.TYPE.equals(returnType);
    }

    private boolean returns(MsgType msgType) {
        if (returnsVoid()) return false;
        return msgType == MsgType.MESSAGE && Message.class.isAssignableFrom(returnType)
                || msgType == MsgType.PAYLOAD && !Message.class.isAssignableFrom(returnType);
    }

    private boolean hasFirstParam(MsgType msgType) {
        if (hasNoParams()) return false;
        Class<?> firstParam = parameterTypes[0];
        return msgType == MsgType.MESSAGE && Message.class.isAssignableFrom(firstParam)
                || msgType == MsgType.PAYLOAD && !Message.class.equals(firstParam);
    }

    private boolean hasFirstParamClassWithGeneric(Class<?> clazz, MsgType msgType) {
        if (hasNoParams()) return false;
        if (!clazz.isAssignableFrom(parameterTypes[0])) return false;

        Type firstParam = genericParameterTypes[0];
        if (!(firstParam instanceof ParameterizedType)) return false;

        ParameterizedType paramReturnType = (ParameterizedType) firstParam;
        Type[] actualTypeArguments = paramReturnType.getActualTypeArguments();

        if (msgType == MsgType.MESSAGE) {
            if (!(actualTypeArguments[0] instanceof ParameterizedType)) return false;
            return Message.class.equals(((ParameterizedType) actualTypeArguments[0]).getRawType());
        } else {
            return !Message.class.equals(firstParam);
        }
    }

    private boolean returnsClassWithGenericParams(Class<?> clazz, MsgType msgType) {
        if (returnsVoid()) return false;
        if (!clazz.isAssignableFrom(returnType)) return false;
        if (!(genericReturnType instanceof ParameterizedType)) return false;

        ParameterizedType paramReturnType = (ParameterizedType) genericReturnType;
        Type[] actualTypeArguments = paramReturnType.getActualTypeArguments();

        if (actualTypeArguments.length == 0) return false;

        if (msgType == MsgType.MESSAGE) {
            if (!(actualTypeArguments[0] instanceof ParameterizedType)) return false;
            return Message.class.equals(((ParameterizedType) actualTypeArguments[0]).getRawType());
        } else if (msgType == MsgType.WILDCARD) {
            return actualTypeArguments[0] instanceof WildcardType;
        } else {
            if ((actualTypeArguments[0] instanceof ParameterizedType)) {
                if (Message.class.equals(((ParameterizedType) actualTypeArguments[0]).getRawType())) return false;
            }
            return !Message.class.equals(actualTypeArguments[0]);
        }
    }

    private boolean isIncoming() {
        return hasAnnotation(Incoming.class) && !hasAnnotation(Outgoing.class);
    }

    private boolean isOutgoing() {
        return !hasAnnotation(Incoming.class) && hasAnnotation(Outgoing.class);
    }

    private boolean isProcessor() {
        return hasAnnotation(Incoming.class) && hasAnnotation(Outgoing.class);
    }

    private <T extends Annotation> boolean hasAnnotation(Class<T> clazz) {
        T annotation = method.getAnnotation(clazz);
        return Objects.nonNull(annotation);
    }

    private enum MsgType {
        MESSAGE, PAYLOAD, WILDCARD;
    }
}
