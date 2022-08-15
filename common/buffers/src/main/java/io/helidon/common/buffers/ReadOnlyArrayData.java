/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

class ReadOnlyArrayData extends ReadOnlyBufferData {
    private final byte[] bytes;
    private final int offset;
    private final int length;
    private int position;

    ReadOnlyArrayData(byte[] bytes, int offset, int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        this.position = 0;
    }

    @Override
    public BufferData rewind() {
        position = 0;
        return this;
    }

    @Override
    public void writeTo(OutputStream out) {
        try {
            out.write(bytes, offset + position, length);
            position = length;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    @Override
    public int readFrom(InputStream in) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read() {
        if (position >= length) {
            throw new ArrayIndexOutOfBoundsException("This buffer has " + length + " bytes, requested to read at " + position);
        }
        return bytes[offset + position++] & 0xFF;

    }

    @Override
    public int read(byte[] bytes, int position, int length) {
        int available = this.length - this.position;
        int toRead = Math.min(length, available);
        System.arraycopy(this.bytes, this.offset + this.position, bytes, position, toRead);
        this.position += toRead;
        return toRead;

    }

    @Override
    public String readString(int length, Charset charset) {
        String result = new String(bytes, offset + position, length, charset);
        position += length;
        return result;
    }

    @Override
    public boolean consumed() {
        return position == length;
    }

    @Override
    public int writeTo(ByteBuffer writeBuffer, int length) {
        int toWrite = Math.min(writeBuffer.limit() - writeBuffer.position(), this.length - this.position);
        toWrite = Math.min(toWrite, length);
        if (toWrite == 0) {
            return 0;
        }
        writeBuffer.put(this.bytes, offset + position, toWrite);
        position += toWrite;
        return toWrite;

    }

    @Override
    public String debugDataBinary() {
        return BufferUtil.debugDataBinary(bytes, offset + position, length - position);
    }

    @Override
    public String debugDataHex(boolean fullBuffer) {
        if (fullBuffer) {
            return BufferUtil.debugDataHex(bytes, offset, offset + length);
        } else {
            return BufferUtil.debugDataHex(bytes, offset + position, offset + length - position);
        }

    }

    @Override
    public int available() {
        return length - position;
    }

    @Override
    public void skip(int length) {
        position = Math.min(this.length, position + length);
    }

    @Override
    public int indexOf(byte aByte) {
        for (int i = position; i < length; i++) {
            if (aByte == bytes[offset + i]) {
                return i - position;
            }
        }
        return -1;

    }

    @Override
    public int lastIndexOf(byte aByte, int length) {
        for (int i = length - 1; i >= position; i--) {
            byte b = bytes[offset + i];
            if (b == aByte) {
                return i - position;
            }
        }
        return -1;
    }

    @Override
    public BufferData trim(int x) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int get(int index) {
        return bytes[offset + position + index] & 0xFF;
    }
}
