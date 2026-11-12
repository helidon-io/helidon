/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

class TestBufferData extends ReadOnlyBufferData {
    private final BufferData delegate;

    TestBufferData(BufferData delegate) {
        this.delegate = delegate;
    }

    @Override
    public BufferData rewind() {
        delegate.rewind();
        return this;
    }

    @Override
    public void writeTo(OutputStream out) {
        delegate.writeTo(out);
    }

    @Override
    public int readFrom(InputStream in) {
        return delegate.readFrom(in);
    }

    @Override
    public int readFrom(ByteBuffer buf) {
        return delegate.readFrom(buf);
    }

    @Override
    public int read() {
        return delegate.read();
    }

    @Override
    public int read(byte[] bytes, int position, int length) {
        return delegate.read(bytes, position, length);
    }

    @Override
    public String readString(int length, Charset charset) {
        return delegate.readString(length, charset);
    }

    @Override
    public boolean consumed() {
        return delegate.consumed();
    }

    @Override
    public int writeTo(ByteBuffer writeBuffer, int length) {
        return delegate.writeTo(writeBuffer, length);
    }

    @Override
    public String debugDataBinary() {
        return delegate.debugDataBinary();
    }

    @Override
    public String debugDataHex(boolean fullBuffer) {
        return delegate.debugDataHex(fullBuffer);
    }

    @Override
    public int available() {
        return delegate.available();
    }

    @Override
    public void skip(int length) {
        delegate.skip(length);
    }

    @Override
    public int indexOf(byte aByte) {
        return delegate.indexOf(aByte);
    }

    @Override
    public int lastIndexOf(byte aByte, int length) {
        return delegate.lastIndexOf(aByte, length);
    }

    @Override
    public BufferData trim(int x) {
        delegate.trim(x);
        return this;
    }

    @Override
    public int get(int index) {
        return delegate.get(index);
    }
}
