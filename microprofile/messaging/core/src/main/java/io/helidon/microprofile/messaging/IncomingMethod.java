/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.Errors;
import io.helidon.config.Config;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.BeanManager;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Subscriber;

/**
 * Subscriber method with reference to processor method.
 * <p>Example:
 * <pre>{@code
 *     @Incoming("channel-name")
 *     public void exampleIncomingMethod(String msg) {
 *         ...
 *     }
 * }</pre>
 */
class IncomingMethod extends AbstractMessagingMethod {

    private static final Logger LOGGER = Logger.getLogger(IncomingMethod.class.getName());

    private Subscriber<? super Object> subscriber;

    IncomingMethod(AnnotatedMethod<?> method, Errors.Collector errors) {
        super(method.getJavaMember(), errors);
        super.setIncomingChannelName(method.getAnnotation(Incoming.class).value());
    }

    @Override
    void validate() {
        super.validate();
        if (getIncomingChannelName() == null || getIncomingChannelName().trim().isEmpty()) {
            super.errors().fatal(String.format("Missing channel name in annotation @Incoming on method %s",
                    getMethod().toString()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        switch (getType()) {
            case INCOMING_SUBSCRIBER_MSG_2_VOID:
            case INCOMING_SUBSCRIBER_PAYL_2_VOID:
                Subscriber<? super Object> originalPaylSubscriber = invoke();
                Subscriber<? super Object> unwrappedSubscriber =
                        UnwrapProcessor.of(this.getMethod(), originalPaylSubscriber);
                subscriber = new ProxySubscriber<>(this, unwrappedSubscriber);
                break;
            case INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID:
            case INCOMING_SUBSCRIBER_BUILDER_PAYL_2_VOID:
                SubscriberBuilder<? super Object, ?> originalSubscriberBuilder = invoke();
                Subscriber<? super Object> unwrappedBuilder =
                        UnwrapProcessor.of(this.getMethod(), originalSubscriberBuilder.build());
                subscriber = new ProxySubscriber<>(this, unwrappedBuilder);
                break;

            // Remove PROCESSOR_PAYL_2_PAYL when TCK issue is solved
            // https://github.com/eclipse/microprofile-reactive-messaging/issues/79
            case PROCESSOR_PAYL_2_PAYL:
            case INCOMING_VOID_2_PAYL:
                subscriber = ReactiveStreams.builder()
                        .forEach(this::invoke)
                        .build();
                break;
            case INCOMING_COMPLETION_STAGE_2_MSG:
            case INCOMING_COMPLETION_STAGE_2_PAYL:
                subscriber = ReactiveStreams.builder()
                        .flatMap(o -> ReactiveStreams.fromCompletionStageNullable(invoke(o)))
                        .onError(t -> LOGGER.log(Level.SEVERE, t,
                                () -> "Error when invoking @Incoming method " + getMethod().getName()))
                        .ignore()
                        .build();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported signature " + getMethod() + " " + getType());
        }
    }

    private CompletionStage<Void> invoke(Object incoming) {
        Method method = getMethod();
        try {
            Class<?> paramType = method.getParameterTypes()[0];
            Object preProcessedMessage = preProcess(incoming, paramType);
            Object methodResult = method.invoke(getBeanInstance(), preProcessedMessage);
            return postProcess(incoming, methodResult);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Object preProcess(final Object incomingValue, final Class<?> expectedParamType) {
        if (getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING)
                && incomingValue instanceof Message) {
            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack();
        }

        return MessageUtils.unwrap(incomingValue, expectedParamType);
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Void> postProcess(final Object incomingValue, final Object outgoingValue) {
        if (getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING)
                && incomingValue instanceof Message) {

            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack();
        }
        if (outgoingValue instanceof CompletionStage) {
            return (CompletionStage<Void>) outgoingValue;
        }
        return CompletableFuture.completedFuture(null);
    }

    Subscriber<? super Object> getSubscriber() {
        return subscriber;
    }

}
