/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;
import static io.helidon.messaging.connectors.jms.shim.ShimUtil.run;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaMapMessage extends JakartaMessage implements MapMessage {

    private final javax.jms.MapMessage delegate;

    JakartaMapMessage(javax.jms.MapMessage delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public boolean getBoolean(String name) throws JMSException {
        return call(() -> delegate.getBoolean(name));
    }

    @Override
    public byte getByte(String name) throws JMSException {
        return call(() -> delegate.getByte(name));
    }

    @Override
    public short getShort(String name) throws JMSException {
        return call(() -> delegate.getShort(name));
    }

    @Override
    public char getChar(String name) throws JMSException {
        return call(() -> delegate.getChar(name));
    }

    @Override
    public int getInt(String name) throws JMSException {
        return call(() -> delegate.getInt(name));
    }

    @Override
    public long getLong(String name) throws JMSException {
        return call(() -> delegate.getLong(name));
    }

    @Override
    public float getFloat(String name) throws JMSException {
        return call(() -> delegate.getFloat(name));
    }

    @Override
    public double getDouble(String name) throws JMSException {
        return call(() -> delegate.getDouble(name));
    }

    @Override
    public String getString(String name) throws JMSException {
        return call(() -> delegate.getString(name));
    }

    @Override
    public byte[] getBytes(String name) throws JMSException {
        return call(() -> delegate.getBytes(name));
    }

    @Override
    public Object getObject(String name) throws JMSException {
        return call(() -> delegate.getObject(name));
    }

    @Override
    public Enumeration getMapNames() throws JMSException {
        return call(delegate::getMapNames);
    }

    @Override
    public void setBoolean(String name, boolean value) throws JMSException {
        run(() -> delegate.setBoolean(name, value));
    }

    @Override
    public void setByte(String name, byte value) throws JMSException {
        run(() -> delegate.setByte(name, value));
    }

    @Override
    public void setShort(String name, short value) throws JMSException {
        run(() -> delegate.setShort(name, value));
    }

    @Override
    public void setChar(String name, char value) throws JMSException {
        run(() -> delegate.setChar(name, value));
    }

    @Override
    public void setInt(String name, int value) throws JMSException {
        run(() -> delegate.setInt(name, value));
    }

    @Override
    public void setLong(String name, long value) throws JMSException {
        run(() -> delegate.setLong(name, value));
    }

    @Override
    public void setFloat(String name, float value) throws JMSException {
        run(() -> delegate.setFloat(name, value));
    }

    @Override
    public void setDouble(String name, double value) throws JMSException {
        run(() -> delegate.setDouble(name, value));
    }

    @Override
    public void setString(String name, String value) throws JMSException {
        run(() -> delegate.setString(name, value));
    }

    @Override
    public void setBytes(String name, byte[] value) throws JMSException {
        run(() -> delegate.setBytes(name, value));
    }

    @Override
    public void setBytes(String name, byte[] value, int offset, int length) throws JMSException {
        run(() -> delegate.setBytes(name, value, offset, length));
    }

    @Override
    public void setObject(String name, Object value) throws JMSException {
        run(() -> delegate.setObject(name, value));
    }

    @Override
    public boolean itemExists(String name) throws JMSException {
        return call(() -> delegate.itemExists(name));
    }
}
