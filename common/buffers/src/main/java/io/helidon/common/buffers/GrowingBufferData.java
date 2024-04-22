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
import java.util.Arrays;

class GrowingBufferData implements BufferData {
    private byte[] bytes;
    private int length;
    private int writePosition;
    private int readPosition;

    GrowingBufferData(int initialLength) {
        int usedInitial = Math.max(initialLength, 256);
        this.bytes = new byte[usedInitial];
        this.length = 0;
    }

    public boolean ready() {
        return writePosition > readPosition;
    }

    @Override
    public GrowingBufferData reset() {
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
        reset();
        length = 0;
        return this;
    }

    @Override
    public void writeTo(OutputStream out) {
        try {
            out.write(bytes, readPosition, writePosition - readPosition);
            readPosition = writePosition;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int readFrom(InputStream in) {
        try {
            int read = in.read(bytes, writePosition, bytes.length - writePosition);
            if (read > 0) {
                writePosition += read;
            }
            return read;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int read() {
        if (readPosition >= writePosition) {
            throw new ArrayIndexOutOfBoundsException("This buffer has " + length
                                                             + " bytes, requested to read at " + readPosition);
        }
        return bytes[readPosition++] & 0xFF;
    }

    public int read(byte[] bytes, int position, int length) {
        int available = writePosition - readPosition;
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

    public GrowingBufferData write(int value) {
        ensureSize(1);
        this.bytes[writePosition++] = (byte) value;
        this.length = Math.max(length, writePosition);
        return this;
    }

    public int writeTo(ByteBuffer writeBuffer, int limit) {
        int toWrite = Math.min(writeBuffer.capacity(), length - readPosition);
        toWrite = Math.min(toWrite, limit);
        if (toWrite == 0) {
            return 0;
        }
        writeBuffer.put(this.bytes, readPosition, toWrite);
        readPosition += toWrite;
        return toWrite;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        ensureSize(length);
        System.arraycopy(bytes, offset, this.bytes, writePosition, length);
        writePosition += length;
        this.length = Math.max(this.length, writePosition);
    }

    @Override
    public void write(BufferData toWrite) {
        ensureSize(toWrite.available());
        byte[] buffer = new byte[toWrite.available()];
        int read = toWrite.read(buffer);
        System.arraycopy(buffer, 0, this.bytes, writePosition, read);
        writePosition += read;
    }

    @Override
    public void write(BufferData toWrite, int length) {
        ensureSize(length);
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
        return BufferUtil.debugDataHex(bytes, fullBuffer ? 0 : readPosition, writePosition);
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
            if (bytes[i] == aByte) {
                return i - readPosition;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(byte aByte, int length) {
        for (int i = length - 1; i >= readPosition; i--) {
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
        length -= x;
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
        return "grow: l=" + length + ", r=" + readPosition + ", w=" + writePosition + ", c=" + bytes.length;
    }

    byte[] bytes() {
        return Arrays.copyOfRange(bytes, 0, length);
    }

    private void ensureSize(int i) {
        if (this.bytes.length > writePosition + i) {
            return;
        }

        byte[] current = this.bytes;
        int currentLength = current.length;
        int newLength = currentLength * 2;
        newLength = Math.max(newLength, writePosition + i);
        if (newLength < currentLength) {
            // int overflow
            throw new IllegalStateException("Growing buffer too big, cannot increase size");
        }
        this.bytes = new byte[newLength];
        System.arraycopy(current, 0, this.bytes, 0, length);
    }
}
