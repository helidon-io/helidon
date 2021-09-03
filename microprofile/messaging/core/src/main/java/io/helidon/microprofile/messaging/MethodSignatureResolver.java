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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

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
final class MethodSignatureResolver {
    private final Class<?> returnType;
    private final Type genericReturnType;
    private final Class<?>[] parameterTypes;
    private final Type[] genericParameterTypes;
    private final List<Supplier<Optional<MethodSignatureType>>> resolveRules = new ArrayList<>();
    private final Method method;

    private MethodSignatureResolver(Method method) {
        this.method = method;
        returnType = method.getReturnType();
        genericReturnType = method.getGenericReturnType();
        parameterTypes = method.getParameterTypes();
        genericParameterTypes = method.getGenericParameterTypes();

        // INCOMING METHODS
        // CompletionStage<?> method(Message<I> msg)
        addRule(MethodSignatureType.INCOMING_COMPLETION_STAGE_2_MSG,
                () -> returnsClassWithGenericParams(CompletionStage.class, MsgType.PAYLOAD)
                        && hasFirstParam(MsgType.MESSAGE));
        // CompletionStage<?> method(I payload)
        addRule(MethodSignatureType.INCOMING_COMPLETION_STAGE_2_PAYL,
                () -> returnsClassWithGenericParams(CompletionStage.class, MsgType.PAYLOAD)
                        && hasFirstParam(MsgType.PAYLOAD)
                        && isIncoming());
        // SubscriberBuilder<Message<I>> method()
        addRule(MethodSignatureType.INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(SubscriberBuilder.class, MsgType.MESSAGE));
        // SubscriberBuilder<I> method()
        addRule(MethodSignatureType.INCOMING_SUBSCRIBER_BUILDER_PAYL_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(SubscriberBuilder.class, MsgType.PAYLOAD));
        // Subscriber<Message<I>> method()
        addRule(MethodSignatureType.INCOMING_SUBSCRIBER_MSG_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(Subscriber.class, MsgType.MESSAGE)
                        && isIncoming());
        // Subscriber<I> method()
        addRule(MethodSignatureType.INCOMING_SUBSCRIBER_PAYL_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(Subscriber.class, MsgType.PAYLOAD)
                        && isIncoming());
        // void method(I payload)
        addRule(MethodSignatureType.INCOMING_VOID_2_PAYL,
                () -> returnsVoid()
                        && hasFirstParam(MsgType.PAYLOAD));
        // PROCESSOR METHODS
        // CompletionStage<Message<O>> method(Message<I> msg)
        addRule(MethodSignatureType.PROCESSOR_COMPL_STAGE_MSG_2_MSG,
                () -> returnsClassWithGenericParams(CompletionStage.class, MsgType.MESSAGE)
                        && hasFirstParam(MsgType.MESSAGE));
        // CompletionStage<O> method(I payload)
        addRule(MethodSignatureType.PROCESSOR_COMPL_STAGE_PAYL_2_PAYL,
                () -> returnsClassWithGenericParams(CompletionStage.class, MsgType.PAYLOAD)
                        && hasFirstParam(MsgType.PAYLOAD));
        // ProcessorBuilder<Message<I>, Message<O>> method();
        addRule(MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_MSG_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(ProcessorBuilder.class, MsgType.MESSAGE));
        // ProcessorBuilder<I, O> method();
        addRule(MethodSignatureType.PROCESSOR_PROCESSOR_BUILDER_PAYL_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(ProcessorBuilder.class, MsgType.PAYLOAD));
        // Processor<Message<I>, Message<O>> method();
        addRule(MethodSignatureType.PROCESSOR_PROCESSOR_MSG_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(Processor.class, MsgType.MESSAGE));
        // Processor<I, O> method();
        addRule(MethodSignatureType.PROCESSOR_PROCESSOR_PAYL_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(Processor.class, MsgType.PAYLOAD));
        // PublisherBuilder<Message<O>> method(PublisherBuilder<Message<I>> pub);
        addRule(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_PUBLISHER_BUILDER_MSG,
                () -> returnsClassWithGenericParams(PublisherBuilder.class, MsgType.MESSAGE)
                        && hasFirstParamClassWithGeneric(PublisherBuilder.class, MsgType.MESSAGE));
        // PublisherBuilder<O> method(PublisherBuilder<I> pub);
        addRule(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PUBLISHER_BUILDER_PAYL,
                () -> returnsClassWithGenericParams(PublisherBuilder.class, MsgType.PAYLOAD)
                        && hasFirstParamClassWithGeneric(PublisherBuilder.class, MsgType.PAYLOAD));
        // PublisherBuilder<Message<O>> method(Message<I>msg);
        addRule(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_MSG_2_MSG,
                () -> returnsClassWithGenericParams(PublisherBuilder.class, MsgType.MESSAGE)
                        && hasFirstParam(MsgType.MESSAGE));
        // PublisherBuilder<O> method(<I> msg);
        addRule(MethodSignatureType.PROCESSOR_PUBLISHER_BUILDER_PAYL_2_PAYL,
                () -> returnsClassWithGenericParams(PublisherBuilder.class, MsgType.PAYLOAD)
                        && hasFirstParam(MsgType.PAYLOAD));
        // Publisher<Message<O>> method(Publisher<Message<I>> pub);
        addRule(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_PUBLISHER_MSG,
                () -> returnsClassWithGenericParams(Publisher.class, MsgType.MESSAGE)
                        && hasFirstParamClassWithGeneric(Publisher.class, MsgType.MESSAGE));
        // Publisher<O> method(Publisher<I> pub);
        addRule(MethodSignatureType.PROCESSOR_PUBLISHER_PAYL_2_PUBLISHER_PAYL,
                () -> returnsClassWithGenericParams(Publisher.class, MsgType.PAYLOAD)
                        && hasFirstParamClassWithGeneric(Publisher.class, MsgType.PAYLOAD));
        // Publisher<Message<O>> method(Message<I>msg);
        addRule(MethodSignatureType.PROCESSOR_PUBLISHER_MSG_2_MSG,
                () -> returnsClassWithGenericParams(Publisher.class, MsgType.MESSAGE)
                        && hasFirstParam(MsgType.MESSAGE));
        // Publisher<O> method(I payload);
        addRule(MethodSignatureType.PROCESSOR_PUBLISHER_PAYL_2_PAYL,
                () -> returnsClassWithGenericParams(Publisher.class, MsgType.PAYLOAD)
                        && hasFirstParam(MsgType.PAYLOAD));
        // Message<O> method(Message<I> msg)
        addRule(MethodSignatureType.PROCESSOR_MSG_2_MSG,
                () -> returns(MsgType.MESSAGE)
                        && hasFirstParam(MsgType.MESSAGE));
        // O method(I payload)
        addRule(MethodSignatureType.PROCESSOR_PAYL_2_PAYL,
                () -> returns(MsgType.PAYLOAD)
                        && hasFirstParam(MsgType.PAYLOAD));
        // OUTGOING METHODS
        // CompletionStage<Message<U>> method()
        addRule(MethodSignatureType.OUTGOING_COMPLETION_STAGE_MSG_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(CompletionStage.class, MsgType.MESSAGE));
        // CompletionStage<U> method()
        addRule(MethodSignatureType.OUTGOING_COMPLETION_STAGE_PAYL_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(CompletionStage.class, MsgType.PAYLOAD));
        // Publisher<Message<U>> method()
        addRule(MethodSignatureType.OUTGOING_PUBLISHER_MSG_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(Publisher.class, MsgType.MESSAGE));
        // Publisher<U> method()
        addRule(MethodSignatureType.OUTGOING_PUBLISHER_PAYL_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(Publisher.class, MsgType.PAYLOAD));
        // PublisherBuilder<Message<U>> method()
        addRule(MethodSignatureType.OUTGOING_PUBLISHER_BUILDER_MSG_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(PublisherBuilder.class, MsgType.MESSAGE));
        // PublisherBuilder<U> method()
        addRule(MethodSignatureType.OUTGOING_PUBLISHER_BUILDER_PAYL_2_VOID,
                () -> hasNoParams()
                        && returnsClassWithGenericParams(PublisherBuilder.class, MsgType.PAYLOAD));
        // Message<U> method()
        addRule(MethodSignatureType.OUTGOING_MSG_2_VOID,
                () -> hasNoParams()
                        && returns(MsgType.MESSAGE));
        // U method()
        addRule(MethodSignatureType.OUTGOING_PAYL_2_VOID,
                () -> hasNoParams()
                        && returns(MsgType.PAYLOAD));
        // Remove when TCK issue is solved https://github.com/eclipse/microprofile-reactive-messaging/issues/79
        // see io.helidon.microprofile.messaging.inner.BadSignaturePublisherPayloadBean
        // O method(I payload)
        addRule(MethodSignatureType.INCOMING_VOID_2_PAYL,
                () -> returns(MsgType.PAYLOAD)
                        && hasFirstParam(MsgType.PAYLOAD));
    }

    /**
     * Returns {@link MethodSignatureType} for any recognized signature.
     * Throws {@link javax.enterprise.inject.spi.DeploymentException}
     * for any un-recognized signature.
     *
     * @return {@link MethodSignatureType}
     * of recognized signature
     * @throws javax.enterprise.inject.spi.DeploymentException for un-recognized signature
     */
    Optional<MethodSignatureType> resolve() {
        return resolveRules
                .stream()
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .findFirst()
                .map(Optional::get);
    }

    /**
     * Method signature resolving utility, returns {@link MethodSignatureType} for any recognized signature.
     *
     * @param method {@link java.lang.reflect.Method} to be resolved
     * @return {@link MethodSignatureResolver}
     */
    static MethodSignatureResolver create(Method method) {
        return new MethodSignatureResolver(method);
    }

    private void addRule(MethodSignatureType type, Supplier<Boolean> predicate) {
        resolveRules.add(() -> Optional
                .of(type)
                .filter(t -> predicate.get()));
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
