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

package io.helidon.json.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testing.Test
public class StreamClosingTest {

    private static final String JSON_DATA = "{\"value\":\"test\"}";
    private static final TestObject TEST_OBJECT = new TestObject("test");
    private final JsonBinding jsonBinding;

    StreamClosingTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testSerializeOutputStreamNotClosed() {
        TestOutputStream outputStream = new TestOutputStream();
        jsonBinding.serialize(outputStream, TEST_OBJECT);
        assertTrue(outputStream.isOpen(), "OutputStream should remain open after serialize");
        assertDoesNotThrow(() -> outputStream.write(1), "OutputStream should still be writable");
    }

    @Test
    public void testSerializeOutputStreamNullObjectNotClosed() {
        TestOutputStream outputStream = new TestOutputStream();
        jsonBinding.serialize(outputStream, (Object) null);
        assertTrue(outputStream.isOpen(), "OutputStream should remain open after serialize null");
        assertDoesNotThrow(() -> outputStream.write(1), "OutputStream should still be writable");
    }

    @Test
    public void testSerializeOutputStreamWithTypeNotClosed() {
        TestOutputStream outputStream = new TestOutputStream();
        jsonBinding.serialize(outputStream, TEST_OBJECT, TestObject.class);
        assertTrue(outputStream.isOpen(), "OutputStream should remain open after serialize with type");
        assertDoesNotThrow(() -> outputStream.write(1), "OutputStream should still be writable");
    }

    @Test
    public void testSerializeOutputStreamWithGenericTypeNotClosed() {
        TestOutputStream outputStream = new TestOutputStream();
        jsonBinding.serialize(outputStream, TEST_OBJECT, Object.class);
        assertTrue(outputStream.isOpen(), "OutputStream should remain open after serialize with generic type");
        assertDoesNotThrow(() -> outputStream.write(1), "OutputStream should still be writable");
    }

    @Test
    public void testSerializeWriterNotClosed() {
        TestWriter writer = new TestWriter();
        jsonBinding.serialize(writer, TEST_OBJECT);
        assertTrue(writer.isOpen(), "Writer should remain open after serialize");
        assertDoesNotThrow(() -> writer.write("test"), "Writer should still be writable");
    }

    @Test
    public void testSerializeWriterNullObjectNotClosed() {
        TestWriter writer = new TestWriter();
        jsonBinding.serialize(writer, (Object) null);
        assertTrue(writer.isOpen(), "Writer should remain open after serialize null");
        assertDoesNotThrow(() -> writer.write("test"), "Writer should still be writable");
    }

    @Test
    public void testSerializeWriterWithTypeNotClosed() {
        TestWriter writer = new TestWriter();
        jsonBinding.serialize(writer, TEST_OBJECT, TestObject.class);
        assertTrue(writer.isOpen(), "Writer should remain open after serialize with type");
        assertDoesNotThrow(() -> writer.write("test"), "Writer should still be writable");
    }

    @Test
    public void testSerializeWriterWithGenericTypeNotClosed() {
        TestWriter writer = new TestWriter();
        jsonBinding.serialize(writer, TEST_OBJECT, Object.class);
        assertTrue(writer.isOpen(), "Writer should remain open after serialize with generic type");
        assertDoesNotThrow(() -> writer.write("test"), "Writer should still be writable");
    }

    @Test
    public void testDeserializeInputStreamNotClosed() {
        TestInputStream inputStream = new TestInputStream(JSON_DATA.getBytes(StandardCharsets.UTF_8));
        jsonBinding.deserialize(inputStream, TestObject.class);
        assertTrue(inputStream.isOpen(), "InputStream should remain open after deserialize");
        assertDoesNotThrow(() -> inputStream.read(), "InputStream should still be readable");
    }

    @Test
    public void testDeserializeInputStreamWithBufferSizeNotClosed() {
        TestInputStream inputStream = new TestInputStream(JSON_DATA.getBytes(StandardCharsets.UTF_8));
        jsonBinding.deserialize(inputStream, 1024, TestObject.class);
        assertTrue(inputStream.isOpen(), "InputStream should remain open after deserialize with buffer size");
        assertDoesNotThrow(() -> inputStream.read(), "InputStream should still be readable");
    }

    @Test
    public void testDeserializeReaderNotClosed() {
        TestReader reader = new TestReader(JSON_DATA);
        jsonBinding.deserialize(reader, TestObject.class);
        assertTrue(reader.isOpen(), "Reader should remain open after deserialize");
        assertDoesNotThrow(() -> reader.read(), "Reader should still be readable");
    }

    @Json.Entity
    static class TestObject {
        private String value;

        TestObject() {
        }

        TestObject(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    /**
     * Test OutputStream that tracks whether it's been closed.
     */
    static class TestOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private boolean closed = false;

        @Override
        public void write(int b) throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }
            delegate.write(b);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        public boolean isOpen() {
            return !closed;
        }

        public byte[] toByteArray() {
            return delegate.toByteArray();
        }
    }

    /**
     * Test Writer that tracks whether it's been closed.
     */
    static class TestWriter extends Writer {
        private final StringWriter delegate = new StringWriter();
        private boolean closed = false;

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Writer is closed");
            }
            delegate.write(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                throw new IOException("Writer is closed");
            }
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        public boolean isOpen() {
            return !closed;
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    /**
     * Test InputStream that tracks whether it's been closed.
     */
    static class TestInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private boolean closed = false;

        TestInputStream(byte[] data) {
            this.delegate = new ByteArrayInputStream(data);
        }

        @Override
        public int read() throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }
            return delegate.read();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        public boolean isOpen() {
            return !closed;
        }
    }

    /**
     * Test Reader that tracks whether it's been closed.
     */
    static class TestReader extends Reader {
        private final StringReader delegate;
        private boolean closed = false;

        TestReader(String data) {
            this.delegate = new StringReader(data);
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Reader is closed");
            }
            return delegate.read(cbuf, off, len);
        }

        @Override
        public void close() {
            closed = true;
            delegate.close();
        }

        public boolean isOpen() {
            return !closed;
        }
    }
}
