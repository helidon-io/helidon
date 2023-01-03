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

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import jakarta.jms.CompletionListener;
import jakarta.jms.Destination;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaProducer<T extends javax.jms.JMSProducer> implements JMSProducer, JakartaWrapper<T> {
    private final T delegate;
    private CompletionListener completionListener;
    private javax.jms.CompletionListener javaxCompletionListener;

    JakartaProducer(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public T unwrap() {
        return delegate;
    }

    @Override
    public JMSProducer send(Destination destination, Message message) {
        delegate.send(ShimUtil.destination(destination), ShimUtil.message(message));
        return this;
    }

    @Override
    public JMSProducer send(Destination destination, String body) {
        delegate.send(ShimUtil.destination(destination), body);
        return this;
    }

    @Override
    public JMSProducer send(Destination destination, Map<String, Object> body) {
        delegate.send(ShimUtil.destination(destination), body);
        return this;
    }

    @Override
    public JMSProducer send(Destination destination, byte[] body) {
        delegate.send(ShimUtil.destination(destination), body);
        return this;
    }

    @Override
    public JMSProducer send(Destination destination, Serializable body) {
        delegate.send(ShimUtil.destination(destination), body);
        return this;
    }

    @Override
    public JMSProducer setDisableMessageID(boolean value) {
        delegate.setDisableMessageID(value);
        return this;
    }

    @Override
    public boolean getDisableMessageID() {
        return delegate.getDisableMessageID();
    }

    @Override
    public JMSProducer setDisableMessageTimestamp(boolean value) {
        delegate.setDisableMessageTimestamp(value);
        return this;
    }

    @Override
    public boolean getDisableMessageTimestamp() {
        return delegate.getDisableMessageTimestamp();
    }

    @Override
    public JMSProducer setDeliveryMode(int deliveryMode) {
        delegate.setDeliveryMode(deliveryMode);
        return this;
    }

    @Override
    public int getDeliveryMode() {
        return delegate.getDeliveryMode();
    }

    @Override
    public JMSProducer setPriority(int priority) {
        delegate.setPriority(priority);
        return this;
    }

    @Override
    public int getPriority() {
        return delegate.getPriority();
    }

    @Override
    public JMSProducer setTimeToLive(long timeToLive) {
        delegate.setTimeToLive(timeToLive);
        return this;
    }

    @Override
    public long getTimeToLive() {
        return delegate.getTimeToLive();
    }

    @Override
    public JMSProducer setDeliveryDelay(long deliveryDelay) {
        delegate.setDeliveryDelay(deliveryDelay);
        return this;
    }

    @Override
    public long getDeliveryDelay() {
        return delegate.getDeliveryDelay();
    }

    @Override
    public JMSProducer setAsync(CompletionListener completionListener) {
        this.completionListener = completionListener;
        this.javaxCompletionListener = JavaxJms.create(completionListener);
        delegate.setAsync(this.javaxCompletionListener);
        return this;
    }

    @Override
    public CompletionListener getAsync() {
        javax.jms.CompletionListener async = delegate.getAsync();
        if (async == this.javaxCompletionListener) {
            return completionListener;
        }
        return JakartaJms.create(async);
    }

    @Override
    public JMSProducer setProperty(String name, boolean value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer setProperty(String name, byte value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer setProperty(String name, short value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer setProperty(String name, int value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer setProperty(String name, long value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer setProperty(String name, float value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer setProperty(String name, double value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer setProperty(String name, String value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer setProperty(String name, Object value) {
        delegate.setProperty(name, value);
        return this;
    }

    @Override
    public JMSProducer clearProperties() {
        delegate.clearProperties();
        return this;
    }

    @Override
    public boolean propertyExists(String name) {
        return delegate.propertyExists(name);
    }

    @Override
    public boolean getBooleanProperty(String name) {
        return delegate.getBooleanProperty(name);
    }

    @Override
    public byte getByteProperty(String name) {
        return delegate.getByteProperty(name);
    }

    @Override
    public short getShortProperty(String name) {
        return delegate.getShortProperty(name);
    }

    @Override
    public int getIntProperty(String name) {
        return delegate.getIntProperty(name);
    }

    @Override
    public long getLongProperty(String name) {
        return delegate.getLongProperty(name);
    }

    @Override
    public float getFloatProperty(String name) {
        return delegate.getFloatProperty(name);
    }

    @Override
    public double getDoubleProperty(String name) {
        return delegate.getDoubleProperty(name);
    }

    @Override
    public String getStringProperty(String name) {
        return delegate.getStringProperty(name);
    }

    @Override
    public Object getObjectProperty(String name) {
        return delegate.getObjectProperty(name);
    }

    @Override
    public Set<String> getPropertyNames() {
        return delegate.getPropertyNames();
    }

    @Override
    public JMSProducer setJMSCorrelationIDAsBytes(byte[] correlationID) {
        delegate.setJMSCorrelationIDAsBytes(correlationID);
        return this;
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() {
        return delegate.getJMSCorrelationIDAsBytes();
    }

    @Override
    public JMSProducer setJMSCorrelationID(String correlationID) {
        delegate.setJMSCorrelationID(correlationID);
        return this;
    }

    @Override
    public String getJMSCorrelationID() {
        return delegate.getJMSCorrelationID();
    }

    @Override
    public JMSProducer setJMSType(String type) {
        delegate.setJMSType(type);
        return this;
    }

    @Override
    public String getJMSType() {
        return delegate.getJMSType();
    }

    @Override
    public JMSProducer setJMSReplyTo(Destination replyTo) {
        delegate.setJMSReplyTo(ShimUtil.destination(replyTo));
        return this;
    }

    @Override
    public Destination getJMSReplyTo() {
        return JakartaJms.create(delegate.getJMSReplyTo());
    }
}
