/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Publisher calling underlined messaging method for every received item.
 */
class InternalSubscriber implements Subscriber<Object> {

    private static final Logger LOGGER = Logger.getLogger(InternalSubscriber.class.getName());

    private Subscription subscription;
    private final IncomingMethod incomingMethod;

    InternalSubscriber(IncomingMethod incomingMethod) {
        this.incomingMethod = incomingMethod;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(Object message) {
        Method method = incomingMethod.getMethod();
        try {
            Class<?> paramType = method.getParameterTypes()[0];
            Object preProcessedMessage = preProcess(message, paramType);
            Object methodResult = method.invoke(incomingMethod.getBeanInstance(), preProcessedMessage);
            postProcess(message, methodResult);
        } catch (Exception e) {
            // Notify publisher to stop sending
            subscription.cancel();
            LOGGER.log(Level.SEVERE, e, () -> "Error when invoking @Incoming method " + method.getName());
        }
    }

    private Object preProcess(final Object incomingValue, final Class<?> expectedParamType) {
        if (incomingMethod.getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING)
                && incomingValue instanceof Message) {
            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack();
        }

        return MessageUtils.unwrap(incomingValue, expectedParamType);
    }

    private void postProcess(final Object incomingValue, final Object outgoingValue) {
        if (incomingMethod.getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING)
                && incomingValue instanceof Message) {

            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack();

        }
    }

    @Override
    public void onError(Throwable t) {
        throw new MessagingException(t);
    }

    @Override
    public void onComplete() {

    }

}
