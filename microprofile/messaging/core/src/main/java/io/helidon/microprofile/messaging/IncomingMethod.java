/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.Errors;
import io.helidon.config.Config;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.BeanManager;
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
class IncomingMethod extends AbstractMessagingMethod implements IncomingMember {

    private static final System.Logger LOGGER = System.getLogger(IncomingMethod.class.getName());

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
                Subscriber<? super Object> originalMsgSubscriber = invoke();
                subscriber = ReactiveStreams.builder()
                        .to(ProxySubscriber.wrapped(this, originalMsgSubscriber))
                        .build();
                break;
            case INCOMING_SUBSCRIBER_PAYL_2_VOID:
                Subscriber<? super Object> originalPaySubscriber = invoke();
                subscriber = ReactiveStreams.builder()
                        .to(ProxySubscriber.unwrapped(this, originalPaySubscriber))
                        .build();
                break;
            case INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID:
                SubscriberBuilder<? super Object, ?> originalMsgSubscriberBuilder = invoke();
                subscriber = ReactiveStreams.builder()
                        .to(ProxySubscriber.wrapped(this, originalMsgSubscriberBuilder.build()))
                        .build();
                break;
            case INCOMING_SUBSCRIBER_BUILDER_PAYL_2_VOID:
                SubscriberBuilder<? super Object, ?> originalPaySubscriberBuilder = invoke();
                subscriber = ReactiveStreams.builder()
                        .to(ProxySubscriber.unwrapped(this, originalPaySubscriberBuilder.build()))
                        .build();
                break;
            case INCOMING_VOID_2_PAYL:
                subscriber = ReactiveStreams.builder()
                        .peek(in -> {
                            Message<?> inMsg = (Message<?>) in;
                            AckCtx ackCtx = AckCtx.create(this, inMsg);
                            ackCtx.preAck();
                            invoke(inMsg.getPayload())
                                    .thenRun(ackCtx::postAck)
                                    .exceptionally(t -> {
                                        ackCtx.postNack(t);
                                        LOGGER.log(System.Logger.Level.ERROR,
                                                () -> "Error when invoking @Incoming method " + getMethod().getName(), t);
                                        return null;
                                    });
                        })
                        .onError(t -> LOGGER.log(System.Logger.Level.ERROR,
                                () -> "Error intercepted on channel " + getIncomingChannelName(), t))
                        .ignore()
                        .build();
                break;
            case INCOMING_COMPLETION_STAGE_2_MSG:
                subscriber = ReactiveStreams.builder()
                        .flatMap(o -> {
                            Message<?> inMsg = (Message<?>) o;
                            AckCtx ackCtx = AckCtx.create(this, inMsg);
                            ackCtx.preAck();
                            try {
                                CompletionStage<Void> result = invoke(inMsg);
                                return ReactiveStreams.fromCompletionStageNullable(result
                                        // on error resume
                                        .exceptionally(t -> {
                                            LOGGER.log(System.Logger.Level.ERROR,
                                                    () -> "Error when invoking @Incoming method " + getMethod().getName(), t);
                                            ackCtx.postNack(t);
                                            return null;
                                        })
                                        .thenRun(ackCtx::postAck));
                            } catch (Throwable t) {
                                LOGGER.log(System.Logger.Level.ERROR,
                                        () -> "Error when invoking @Incoming method " + getMethod().getName(), t);
                                ackCtx.postNack(t);
                                return ReactiveStreams.empty();
                            }
                        })
                        .onError(t -> LOGGER.log(System.Logger.Level.ERROR,
                                () -> "Error intercepted in channel " + getIncomingChannelName(), t))
                        .ignore()
                        .build();
                break;
            case INCOMING_COMPLETION_STAGE_2_PAYL:
                subscriber = ReactiveStreams.builder()
                        .flatMap(o -> {
                            Message<?> inMsg = (Message<?>) o;
                            AckCtx ackCtx = AckCtx.create(this, inMsg);
                            ackCtx.preAck();
                            try {
                                CompletionStage<Void> result = invoke(inMsg.getPayload());
                                return ReactiveStreams.fromCompletionStageNullable(result
                                        // on error resume
                                        .exceptionally(t -> {
                                            ackCtx.postNack(t);
                                            return null;
                                        })
                                        .thenRun(ackCtx::postAck));
                            } catch (Throwable t) {
                                LOGGER.log(System.Logger.Level.ERROR,
                                        () -> "Error when invoking @Incoming method " + getMethod().getName(), t);
                                ackCtx.postNack(t);
                                return ReactiveStreams.empty();
                            }
                        })
                        .onError(t -> LOGGER.log(System.Logger.Level.ERROR,
                                () -> "Error intercepted in channel " + getIncomingChannelName(), t))
                        .ignore()
                        .build();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported signature " + getMethod() + " " + getType());
        }
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Void> invoke(Object incoming) {
        Method method = getMethod();
        try {
            Object methodResult = method.invoke(getBeanInstance(), incoming);
            if (methodResult instanceof CompletionStage<?>) {
                return (CompletionStage<Void>) methodResult;
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Subscriber<? super Object> getSubscriber(String unused) {
        return subscriber;
    }

    @Override
    public String getDescription() {
        return "incoming method " + getMethod().getName();
    }
}
