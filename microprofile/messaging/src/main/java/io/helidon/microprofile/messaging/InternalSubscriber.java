/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Publisher calling underlined messaging method for every received item.
 */
class InternalSubscriber implements Subscriber<Object> {

    private Subscription subscription;
    private final IncomingMethod incomingMethod;

    InternalSubscriber(IncomingMethod incomingMethod) {
        this.incomingMethod = incomingMethod;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        // request one by one
        subscription.request(1);
    }

    @Override
    public void onNext(Object message) {
        Method method = incomingMethod.getMethod();
        try {
            Class<?> paramType = method.getParameterTypes()[0];
            Object preProcessedMessage = preProcess(message, paramType);
            Object methodResult = method.invoke(incomingMethod.getBeanInstance(), preProcessedMessage);
            postProcess(message, methodResult);
            subscription.request(1);
        } catch (Exception e) {
            // Notify publisher to stop sending
            subscription.cancel();
            throw new MessagingStreamException(e);
        }
    }

    private Object preProcess(Object incomingValue, Class<?> expectedParamType) throws ExecutionException, InterruptedException {
        if (incomingMethod.getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING)
                && incomingValue instanceof Message) {
            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack().toCompletableFuture().complete(null);
        }

        return MessageUtils.unwrap(incomingValue, expectedParamType);
    }

    private void postProcess(Object incomingValue, Object outgoingValue) throws ExecutionException, InterruptedException {
        if (incomingMethod.getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING)
                && incomingValue instanceof Message) {

            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack().toCompletableFuture().complete(null);

        } else if (Objects.nonNull(outgoingValue)
                && outgoingValue instanceof CompletionStage) {
            CompletionStage<?> completionStage = (CompletionStage<?>) outgoingValue;
            completionStage.toCompletableFuture().get();
        }
    }

    @Override
    public void onError(Throwable t) {
        throw new MessagingStreamException(t);
    }

    @Override
    public void onComplete() {

    }

}
