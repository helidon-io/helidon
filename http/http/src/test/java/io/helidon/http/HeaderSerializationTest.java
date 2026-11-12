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

package io.helidon.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HeaderSerializationTest {
    @Test
    void nameBytesAreDefensiveCopy() {
        byte[] nameBytes = HeaderNames.CONTENT_LENGTH.nameBytes();
        nameBytes[0] = '\r';

        assertThat(new String(HeaderNames.CONTENT_LENGTH.nameBytes(), StandardCharsets.US_ASCII),
                   is(HeaderNames.CONTENT_LENGTH_NAME));
        assertSerialized(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "128"), "Content-Length: 128\r\n");
    }

    @Test
    void valueBytesAreDefensiveCopy() {
        byte[] valueBytes = HeaderValues.CONTENT_TYPE_JSON.valueBytes();
        valueBytes[0] = '\r';

        assertThat(new String(HeaderValues.CONTENT_TYPE_JSON.valueBytes(), StandardCharsets.US_ASCII),
                   is("application/json"));
        assertSerialized(HeaderValues.CONTENT_TYPE_JSON, "Content-Type: application/json\r\n");
    }

    @Test
    void sharedCachedBytesAreNotExposedToCustomBuffer() {
        RecordingBuffer customBuffer = new RecordingBuffer(true);

        HeaderValues.create(HeaderNames.CONTENT_LENGTH, "128").writeHttp1Header(customBuffer);
        HeaderValues.CONTENT_TYPE_JSON.writeHttp1Header(customBuffer);

        assertThat(customBuffer.arrayWrites, is(3));
        assertThat(customBuffer.readString(customBuffer.available(), StandardCharsets.US_ASCII),
                   is("Content-Length: 128\r\nContent-Type: application/json\r\n"));
        assertSerialized(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "128"), "Content-Length: 128\r\n");
        assertSerialized(HeaderValues.CONTENT_TYPE_JSON, "Content-Type: application/json\r\n");
    }

    @Test
    void freshNameBytesUseBulkWriteToCustomBuffer() {
        RecordingBuffer customBuffer = new RecordingBuffer(false);

        HeaderValues.create("X-Custom", "v").writeHttp1Header(customBuffer);

        assertThat(customBuffer.arrayWrites, is(2));
        assertThat(customBuffer.scalarWrites, is(4));
        assertThat(customBuffer.readString(customBuffer.available(), StandardCharsets.US_ASCII), is("X-Custom: v\r\n"));
    }

    private static void assertSerialized(Header header, String expected) {
        BufferData buffer = BufferData.growing(128);
        header.writeHttp1Header(buffer);
        assertThat(buffer.readString(buffer.available(), StandardCharsets.US_ASCII), is(expected));
    }

    private static class RecordingBuffer implements BufferData {
        private final BufferData delegate = BufferData.growing(128);
        private final boolean mutateArrays;
        private int arrayWrites;
        private int scalarWrites;

        private RecordingBuffer(boolean mutateArrays) {
            this.mutateArrays = mutateArrays;
        }

        @Override
        public BufferData reset() {
            delegate.reset();
            return this;
        }

        @Override
        public BufferData rewind() {
            delegate.rewind();
            return this;
        }

        @Override
        public BufferData clear() {
            delegate.clear();
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
        public BufferData write(int value) {
            scalarWrites++;
            delegate.write(value);
            return this;
        }

        @Override
        public int writeTo(ByteBuffer writeBuffer, int length) {
            return delegate.writeTo(writeBuffer, length);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            arrayWrites++;
            delegate.write(bytes, offset, length);
            if (mutateArrays && length > 0) {
                bytes[offset] = '\r';
            }
        }

        @Override
        public void write(BufferData toWrite, int length) {
            delegate.write(toWrite, length);
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
        public int capacity() {
            return delegate.capacity();
        }

        @Override
        public int get(int index) {
            return delegate.get(index);
        }
    }
}
