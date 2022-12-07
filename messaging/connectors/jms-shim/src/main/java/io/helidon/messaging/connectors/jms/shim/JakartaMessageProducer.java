/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.messaging.connectors.jms.shim;

import jakarta.jms.CompletionListener;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;
import static io.helidon.messaging.connectors.jms.shim.ShimUtil.run;

class JakartaMessageProducer<T extends javax.jms.MessageProducer> implements MessageProducer, JakartaWrapper<T> {
    private final T delegate;

    JakartaMessageProducer(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setDisableMessageID(boolean value) throws JMSException {
        run(() -> delegate.setDisableMessageID(value));
    }

    @Override
    public boolean getDisableMessageID() throws JMSException {
        return call(delegate::getDisableMessageID);
    }

    @Override
    public void setDisableMessageTimestamp(boolean value) throws JMSException {
        run(() -> delegate.setDisableMessageTimestamp(value));
    }

    @Override
    public boolean getDisableMessageTimestamp() throws JMSException {
        return call(delegate::getDisableMessageTimestamp);
    }

    @Override
    public void setDeliveryMode(int deliveryMode) throws JMSException {
        run(() -> delegate.setDeliveryMode(deliveryMode));
    }

    @Override
    public int getDeliveryMode() throws JMSException {
        return call(delegate::getDeliveryMode);
    }

    @Override
    public void setPriority(int defaultPriority) throws JMSException {
        run(() -> delegate.setPriority(defaultPriority));
    }

    @Override
    public int getPriority() throws JMSException {
        return call(delegate::getPriority);
    }

    @Override
    public void setTimeToLive(long timeToLive) throws JMSException {
        run(() -> delegate.setTimeToLive(timeToLive));
    }

    @Override
    public long getTimeToLive() throws JMSException {
        return call(delegate::getTimeToLive);
    }

    @Override
    public void setDeliveryDelay(long deliveryDelay) throws JMSException {
        run(() -> delegate.setDeliveryDelay(deliveryDelay));
    }

    @Override
    public long getDeliveryDelay() throws JMSException {
        return call(delegate::getDeliveryDelay);
    }

    @Override
    public Destination getDestination() throws JMSException {
        return JakartaJms.create(call(delegate::getDestination));
    }

    @Override
    public void close() throws JMSException {
        run(delegate::close);
    }

    @Override
    public void send(Message message) throws JMSException {
        run(() -> delegate.send(ShimUtil.message(message)));
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        run(() -> delegate.send(ShimUtil.message(message), deliveryMode, priority, timeToLive));
    }

    @Override
    public void send(Destination destination, Message message) throws JMSException {
        run(() -> delegate.send(ShimUtil.destination(destination), ShimUtil.message(message)));
    }

    @Override
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive)
            throws JMSException {
        run(() -> delegate.send(ShimUtil.destination(destination),
                                ShimUtil.message(message),
                                deliveryMode,
                                priority,
                                timeToLive));
    }

    @Override
    public void send(Message message, CompletionListener completionListener) throws JMSException {
        run(() -> delegate.send(ShimUtil.message(message), JavaxJms.create(completionListener)));
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener)
            throws JMSException {
        run(() -> delegate.send(ShimUtil.message(message),
                                deliveryMode,
                                priority,
                                timeToLive,
                                JavaxJms.create(completionListener)));
    }

    @Override
    public void send(Destination destination, Message message, CompletionListener completionListener) throws JMSException {
        run(() -> delegate.send(ShimUtil.destination(destination),
                                ShimUtil.message(message),
                                JavaxJms.create(completionListener)));
    }

    @Override
    public void send(Destination destination,
                     Message message,
                     int deliveryMode,
                     int priority,
                     long timeToLive,
                     CompletionListener completionListener) throws JMSException {
        run(() -> delegate.send(ShimUtil.destination(destination),
                                ShimUtil.message(message),
                                deliveryMode,
                                priority,
                                timeToLive,
                                JavaxJms.create(completionListener)));
    }

    @Override
    public T unwrap() {
        return delegate;
    }
}
