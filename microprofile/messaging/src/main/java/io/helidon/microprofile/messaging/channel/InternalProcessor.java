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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

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


    private ProcessorMethod processorMethod;
    private Subscriber<? super Object> subscriber;

    InternalProcessor(ProcessorMethod processorMethod) {
        this.processorMethod = processorMethod;
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
    @SuppressWarnings("unchecked")
    public void onNext(Object incomingValue) {
        try {
            Method method = processorMethod.getMethod();
            //Params size is already validated by ProcessorMethod
            Class<?> paramType = method.getParameterTypes()[0];
            Object processedValue = method.invoke(processorMethod.getBeanInstance(),
                    preProcess(incomingValue, paramType));
            //Method returns publisher, time for flattening its PROCESSOR_MSG_2_PUBLISHER or *_BUILDER
            if (processedValue instanceof Publisher || processedValue instanceof PublisherBuilder) {
                //Flatten, we are sure its invoke on every request method now
                PublisherBuilder<Object> publisherBuilder;
                if (processedValue instanceof Publisher) {
                    publisherBuilder = ReactiveStreams.fromPublisher((Publisher<Object>) processedValue);
                } else {
                    publisherBuilder = (PublisherBuilder<Object>) processedValue;
                }
                publisherBuilder.forEach(subVal -> {
                    try {
                        subscriber.onNext(postProcess(incomingValue, subVal));
                    } catch (ExecutionException | InterruptedException e) {
                        subscriber.onError(e);
                    }
                }).run();
            } else {
                subscriber.onNext(postProcess(incomingValue, processedValue));
            }
        } catch (IllegalAccessException | InvocationTargetException | ExecutionException | InterruptedException e) {
            subscriber.onError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object preProcess(Object incomingValue, Class<?> expectedParamType) throws ExecutionException, InterruptedException {
        if (processorMethod.getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING)
                && incomingValue instanceof Message) {
            Message incomingMessage = (Message) incomingValue;
            incomingMessage.ack().toCompletableFuture().complete(incomingMessage.getPayload());
        }

        return MessageUtils.unwrap(incomingValue, expectedParamType);
    }

    @SuppressWarnings("unchecked")
    private Object postProcess(Object incomingValue, Object outgoingValue) throws ExecutionException, InterruptedException {
        Message wrappedOutgoing = (Message) MessageUtils.unwrap(outgoingValue, Message.class);
        if (processorMethod.getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING)) {
            Message wrappedIncoming = (Message) MessageUtils.unwrap(incomingValue, Message.class);
            wrappedOutgoing = (Message) MessageUtils.unwrap(outgoingValue, Message.class, wrappedIncoming::ack);
        }
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
