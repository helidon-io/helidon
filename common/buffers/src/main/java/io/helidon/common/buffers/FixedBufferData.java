/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.buffers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

class FixedBufferData implements BufferData {
    private final byte[] bytes;
    private final int length;
    private int writePosition;
    private int readPosition;

    FixedBufferData(int length) {
        this.bytes = new byte[length];
        this.length = length;
    }

    FixedBufferData(byte[] bytes) {
        this.bytes = bytes;
        this.length = bytes.length;
        this.writePosition = this.length;
    }

    FixedBufferData(byte[] bytes, int position, int length) {
        this.bytes = bytes;
        this.length = length;
        this.writePosition = position + length;
        this.readPosition = position;
    }

    @Override
    public FixedBufferData reset() {
        this.writePosition = 0;
        this.readPosition = 0;
        return this;
    }

    @Override
    public BufferData rewind() {
        this.readPosition = 0;
        return this;
    }

    @Override
    public BufferData clear() {
        return reset();
    }

    @Override
    public void writeTo(OutputStream out) {
        try {
            out.write(bytes, readPosition, writePosition);
            readPosition = writePosition;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int readFrom(InputStream in) {
        int toRead = length - writePosition;
        int read;
        try {
            read = in.read(bytes, writePosition, toRead);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (read == -1) {
            return read;
        }
        writePosition += read;
        return read;
    }

    @Override
    public int read() {
        if (readPosition >= writePosition) {
            throw new ArrayIndexOutOfBoundsException("This buffer has " + length
                                                             + " bytes, requested to read at " + readPosition);
        }
        return bytes[readPosition++] & 0xFF;
    }

    @Override
    public int read(byte[] bytes, int position, int length) {
        int available = this.writePosition - readPosition;
        int toRead = Math.min(length, available);

        System.arraycopy(this.bytes, readPosition, bytes, position, toRead);

        readPosition += toRead;
        return toRead;
    }

    @Override
    public String readString(int length, Charset charset) {
        String result = new String(bytes, readPosition, length, charset);
        readPosition += length;
        return result;
    }

    public boolean consumed() {
        return readPosition == writePosition;
    }

    public FixedBufferData write(int value) {
        this.bytes[writePosition++] = (byte) value;
        return this;
    }

    @Override
    public int writeTo(ByteBuffer writeBuffer, int length) {
        int toWrite = Math.min(writeBuffer.limit() - writeBuffer.position(), this.length - readPosition);
        toWrite = Math.min(toWrite, length);
        if (toWrite == 0) {
            return 0;
        }
        writeBuffer.put(this.bytes, readPosition, toWrite);
        readPosition += toWrite;
        return toWrite;
    }

    public void write(byte[] bytes, int offset, int length) {
        System.arraycopy(bytes, offset, this.bytes, writePosition, length);
        writePosition += length;
    }

    @Override
    public void write(BufferData toWrite) {
        byte[] buffer = new byte[length - writePosition];
        int read = toWrite.read(buffer);
        System.arraycopy(buffer, 0, this.bytes, writePosition, read);
        writePosition += read;
    }

    @Override
    public void write(BufferData toWrite, int length) {
        byte[] buffer = new byte[length];
        int read = toWrite.read(buffer);
        System.arraycopy(buffer, 0, this.bytes, writePosition, read);
        writePosition += read;
    }

    @Override
    public String debugDataBinary() {
        return BufferUtil.debugDataBinary(bytes, 0, writePosition);
    }

    @Override
    public String debugDataHex(boolean fullBuffer) {
        if (fullBuffer) {
            return BufferUtil.debugDataHex(bytes, 0, writePosition);
        } else {
            return BufferUtil.debugDataHex(bytes, readPosition, writePosition);
        }
    }

    @Override
    public int available() {
        return writePosition - readPosition;
    }

    @Override
    public void skip(int length) {
        readPosition += length;
    }

    @Override
    public int indexOf(byte aByte) {
        for (int i = readPosition; i < (readPosition + available()); i++) {
            if (aByte == bytes[i]) {
                return i - readPosition;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(byte aByte, int length) {
        for (int i = (readPosition + length) - 1; i >= readPosition; i--) {
            byte b = bytes[i];
            if (b == aByte) {
                return i - readPosition;
            }
        }
        return -1;
    }

    @Override
    public BufferData trim(int x) {
        if (available() < x) {
            throw new IllegalArgumentException("Trimming more bytes than available");
        }
        writePosition -= x;
        return this;
    }

    @Override
    public int capacity() {
        return length - writePosition;
    }

    @Override
    public int get(int index) {
        return bytes[readPosition + index];
    }

    @Override
    public String toString() {
        return "fixed: l=" + length + ", r=" + readPosition + ", w=" + writePosition;
    }
}
