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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Processor calling underlined messaging method for every received item.
 * <p>
 * Example:
 * <pre>{@code
 *      @Incoming("channel-one")
 *      @Outgoing("channel-two")
 *      public String process2(String msg) {
 *          return msg.toLowerCase();
 *      }
 * }</pre>
 */
class InternalProcessor implements Processor<Object, Object> {


    private final ProcessorMethod method;
    private Subscriber<? super Object> subscriber;
    private final CompletableQueue<Object> completableQueue;

    InternalProcessor(ProcessorMethod method) {
        this.method = method;
        this.completableQueue = CompletableQueue.create();
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        subscriber = s;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscriber.onSubscribe(s);
        completableQueue.onEachComplete((o, throwable) -> {
            if (Objects.isNull(throwable)) {
                var incomingValue = o.getMetadata();
                var outgoingValue = o.getValue();
                subscriber.onNext(postProcess(incomingValue, outgoingValue));
            } else {
                subscriber.onError(throwable);
            }
        });
    }

    @Override
    public void onNext(final Object incomingValue) {
        try {
            Method method = this.method.getMethod();
            //Params size is already validated by ProcessorMethod
            Class<?> paramType = method.getParameterTypes()[0];
            Object processedValue = this.method.invoke(preProcess(incomingValue, paramType));
            //Method returns publisher, time for flattening its PROCESSOR_MSG_2_PUBLISHER or *_BUILDER
            if (processedValue instanceof Publisher || processedValue instanceof PublisherBuilder) {
                //Flatten, we are sure its invoke on every request method now
                PublisherBuilder<?> publisherBuilder;
                if (processedValue instanceof Publisher) {
                    publisherBuilder = ReactiveStreams.fromPublisher((Publisher<?>) processedValue);
                } else {
                    publisherBuilder = (PublisherBuilder<?>) processedValue;
                }
                publisherBuilder
                        .flatMapCompletionStage(o -> {
                            if (o instanceof CompletionStage) {
                                return (CompletionStage<?>) o;
                            } else {
                                return CompletableFuture.completedStage(o);
                            }
                        })
                        .map(o -> postProcess(incomingValue, o))
                        .to(subscriber)
                        .run();
            } else {
                if (!completionStageAwait(incomingValue, processedValue)) {
                    //FIXME: apply back-pressure instead of buffering
                    subscriber.onNext(postProcess(incomingValue, processedValue));
                }
            }
        } catch (Throwable e) {
            subscriber.onError(e);
        }
    }

    private Object preProcess(final Object incomingValue, final Class<?> expectedParamType) {
        Message<?> incomingMessage = (Message<?>) incomingValue;
        if (method.getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING)) {
            incomingMessage.ack();
        }
        method.beforeInvoke(incomingMessage);
        return MessageUtils.unwrap(incomingValue, expectedParamType);
    }

    @SuppressWarnings("unchecked")
    private boolean completionStageAwait(final Object incomingValue, final Object outgoingValue) {
        if (outgoingValue instanceof CompletionStage) {
            //Wait for completable stages to finish, yes it means to block see the spec
            completableQueue.add(((CompletionStage<Object>) outgoingValue).toCompletableFuture(), incomingValue);
            return true;
        }
        return false;
    }

    private Object postProcess(final Object incomingValue, final Object outgoingValue) {
        Message<?> wrappedOutgoing = (Message<?>) MessageUtils.unwrap(outgoingValue, Message.class);
        if (method.getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING)) {
            Message<?> wrappedIncoming = (Message<?>) MessageUtils.unwrap(incomingValue, Message.class);
            wrappedOutgoing = (Message<?>) MessageUtils.unwrap(outgoingValue, Message.class, wrappedIncoming::ack);
        }
        method.afterInvoke(incomingValue, wrappedOutgoing);
        return wrappedOutgoing;
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
