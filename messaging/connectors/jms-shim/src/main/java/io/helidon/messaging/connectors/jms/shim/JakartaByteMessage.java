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

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;
import static io.helidon.messaging.connectors.jms.shim.ShimUtil.run;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaByteMessage extends JakartaMessage implements BytesMessage {
    private final javax.jms.BytesMessage delegate;

    JakartaByteMessage(javax.jms.BytesMessage delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public long getBodyLength() throws JMSException {
        return call(delegate::getBodyLength);
    }

    @Override
    public boolean readBoolean() throws JMSException {
        return call(delegate::readBoolean);
    }

    @Override
    public byte readByte() throws JMSException {
        return call(delegate::readByte);
    }

    @Override
    public int readUnsignedByte() throws JMSException {
        return call(delegate::readUnsignedByte);
    }

    @Override
    public short readShort() throws JMSException {
        return call(delegate::readShort);
    }

    @Override
    public int readUnsignedShort() throws JMSException {
        return call(delegate::readUnsignedShort);
    }

    @Override
    public char readChar() throws JMSException {
        return call(delegate::readChar);
    }

    @Override
    public int readInt() throws JMSException {
        return call(delegate::readInt);
    }

    @Override
    public long readLong() throws JMSException {
        return call(delegate::readLong);
    }

    @Override
    public float readFloat() throws JMSException {
        return call(delegate::readFloat);
    }

    @Override
    public double readDouble() throws JMSException {
        return call(delegate::readDouble);
    }

    @Override
    public String readUTF() throws JMSException {
        return call(delegate::readUTF);
    }

    @Override
    public int readBytes(byte[] value) throws JMSException {
        return call(() -> delegate.readBytes(value));
    }

    @Override
    public int readBytes(byte[] value, int length) throws JMSException {
        return call(() -> delegate.readBytes(value, length));
    }

    @Override
    public void writeBoolean(boolean value) throws JMSException {
        run(() -> delegate.writeBoolean(value));
    }

    @Override
    public void writeByte(byte value) throws JMSException {
        run(() -> delegate.writeByte(value));
    }

    @Override
    public void writeShort(short value) throws JMSException {
        run(() -> delegate.writeShort(value));
    }

    @Override
    public void writeChar(char value) throws JMSException {
        run(() -> delegate.writeChar(value));
    }

    @Override
    public void writeInt(int value) throws JMSException {
        run(() -> delegate.writeInt(value));
    }

    @Override
    public void writeLong(long value) throws JMSException {
        run(() -> delegate.writeLong(value));
    }

    @Override
    public void writeFloat(float value) throws JMSException {
        run(() -> delegate.writeFloat(value));
    }

    @Override
    public void writeDouble(double value) throws JMSException {
        run(() -> delegate.writeDouble(value));
    }

    @Override
    public void writeUTF(String value) throws JMSException {
        run(() -> delegate.writeUTF(value));
    }

    @Override
    public void writeBytes(byte[] value) throws JMSException {
        run(() -> delegate.writeBytes(value));
    }

    @Override
    public void writeBytes(byte[] value, int offset, int length) throws JMSException {
        run(() -> delegate.writeBytes(value, offset, length));
    }

    @Override
    public void writeObject(Object value) throws JMSException {
        run(() -> delegate.writeObject(value));
    }

    @Override
    public void reset() throws JMSException {
        run(delegate::reset);
    }
}
