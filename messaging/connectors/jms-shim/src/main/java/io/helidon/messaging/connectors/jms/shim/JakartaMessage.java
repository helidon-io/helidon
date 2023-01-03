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

import java.util.Enumeration;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;
import static io.helidon.messaging.connectors.jms.shim.ShimUtil.run;

class JakartaMessage<T extends javax.jms.Message> implements Message, JakartaWrapper<T> {
    private final T delegate;

    JakartaMessage(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getJMSMessageID() throws JMSException {
        return call(delegate::getJMSMessageID);
    }

    @Override
    public void setJMSMessageID(String id) throws JMSException {
        run(() -> delegate.setJMSMessageID(id));
    }

    @Override
    public long getJMSTimestamp() throws JMSException {
        return call(delegate::getJMSTimestamp);
    }

    @Override
    public void setJMSTimestamp(long timestamp) throws JMSException {
        run(() -> delegate.setJMSTimestamp(timestamp));
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
        return call(delegate::getJMSCorrelationIDAsBytes);
    }

    @Override
    public void setJMSCorrelationIDAsBytes(byte[] correlationID) throws JMSException {
        run(() -> delegate.setJMSCorrelationIDAsBytes(correlationID));
    }

    @Override
    public void setJMSCorrelationID(String correlationID) throws JMSException {
        run(() -> delegate.setJMSCorrelationID(correlationID));
    }

    @Override
    public String getJMSCorrelationID() throws JMSException {
        return call(delegate::getJMSCorrelationID);
    }

    @Override
    public Destination getJMSReplyTo() throws JMSException {
        return JakartaJms.create(call(delegate::getJMSReplyTo));
    }

    @Override
    public void setJMSReplyTo(Destination replyTo) throws JMSException {
        run(() -> delegate.setJMSReplyTo(ShimUtil.destination(replyTo)));
    }

    @Override
    public Destination getJMSDestination() throws JMSException {
        return JakartaJms.create(call(delegate::getJMSDestination));
    }

    @Override
    public void setJMSDestination(Destination destination) throws JMSException {
        run(() -> delegate.setJMSDestination(ShimUtil.destination(destination)));
    }

    @Override
    public int getJMSDeliveryMode() throws JMSException {
        return call(delegate::getJMSDeliveryMode);
    }

    @Override
    public void setJMSDeliveryMode(int deliveryMode) throws JMSException {
        run(() -> delegate.setJMSDeliveryMode(deliveryMode));
    }

    @Override
    public boolean getJMSRedelivered() throws JMSException {
        return call(delegate::getJMSRedelivered);
    }

    @Override
    public void setJMSRedelivered(boolean redelivered) throws JMSException {
        run(() -> delegate.setJMSRedelivered(redelivered));
    }

    @Override
    public String getJMSType() throws JMSException {
        return call(delegate::getJMSType);
    }

    @Override
    public void setJMSType(String type) throws JMSException {
        run(() -> delegate.setJMSType(type));
    }

    @Override
    public long getJMSExpiration() throws JMSException {
        return call(delegate::getJMSExpiration);
    }

    @Override
    public void setJMSExpiration(long expiration) throws JMSException {
        run(() -> delegate.setJMSExpiration(expiration));
    }

    @Override
    public long getJMSDeliveryTime() throws JMSException {
        return call(delegate::getJMSDeliveryTime);
    }

    @Override
    public void setJMSDeliveryTime(long deliveryTime) throws JMSException {
        run(() -> delegate.setJMSDeliveryTime(deliveryTime));
    }

    @Override
    public int getJMSPriority() throws JMSException {
        return call(delegate::getJMSPriority);
    }

    @Override
    public void setJMSPriority(int priority) throws JMSException {
        run(() -> delegate.setJMSPriority(priority));
    }

    @Override
    public void clearProperties() throws JMSException {
        run(delegate::clearProperties);
    }

    @Override
    public boolean propertyExists(String name) throws JMSException {
        return call(() -> delegate.propertyExists(name));
    }

    @Override
    public boolean getBooleanProperty(String name) throws JMSException {
        return call(() -> delegate.getBooleanProperty(name));
    }

    @Override
    public byte getByteProperty(String name) throws JMSException {
        return call(() -> delegate.getByteProperty(name));
    }

    @Override
    public short getShortProperty(String name) throws JMSException {
        return call(() -> delegate.getShortProperty(name));
    }

    @Override
    public int getIntProperty(String name) throws JMSException {
        return call(() -> delegate.getIntProperty(name));
    }

    @Override
    public long getLongProperty(String name) throws JMSException {
        return call(() -> delegate.getLongProperty(name));
    }

    @Override
    public float getFloatProperty(String name) throws JMSException {
        return call(() -> delegate.getFloatProperty(name));
    }

    @Override
    public double getDoubleProperty(String name) throws JMSException {
        return call(() -> delegate.getDoubleProperty(name));
    }

    @Override
    public String getStringProperty(String name) throws JMSException {
        return call(() -> delegate.getStringProperty(name));
    }

    @Override
    public Object getObjectProperty(String name) throws JMSException {
        return call(() -> delegate.getObjectProperty(name));
    }

    @Override
    public Enumeration getPropertyNames() throws JMSException {
        return call(delegate::getPropertyNames);
    }

    @Override
    public void setBooleanProperty(String name, boolean value) throws JMSException {
        run(() -> delegate.setBooleanProperty(name, value));
    }

    @Override
    public void setByteProperty(String name, byte value) throws JMSException {
        run(() -> delegate.setByteProperty(name, value));
    }

    @Override
    public void setShortProperty(String name, short value) throws JMSException {
        run(() -> delegate.setShortProperty(name, value));
    }

    @Override
    public void setIntProperty(String name, int value) throws JMSException {
        run(() -> delegate.setIntProperty(name, value));
    }

    @Override
    public void setLongProperty(String name, long value) throws JMSException {
        run(() -> delegate.setLongProperty(name, value));
    }

    @Override
    public void setFloatProperty(String name, float value) throws JMSException {
        run(() -> delegate.setFloatProperty(name, value));
    }

    @Override
    public void setDoubleProperty(String name, double value) throws JMSException {
        run(() -> delegate.setDoubleProperty(name, value));
    }

    @Override
    public void setStringProperty(String name, String value) throws JMSException {
        run(() -> delegate.setStringProperty(name, value));
    }

    @Override
    public void setObjectProperty(String name, Object value) throws JMSException {
        run(() -> delegate.setObjectProperty(name, value));
    }

    @Override
    public void acknowledge() throws JMSException {
        run(delegate::acknowledge);
    }

    @Override
    public void clearBody() throws JMSException {
        run(delegate::clearBody);
    }

    @Override
    public <T> T getBody(Class<T> c) throws JMSException {
        return call(() -> delegate.getBody(c));
    }

    @Override
    public boolean isBodyAssignableTo(Class c) throws JMSException {
        return call(() -> delegate.isBodyAssignableTo(c));
    }

    public T unwrap() {
        return delegate;
    }
}
