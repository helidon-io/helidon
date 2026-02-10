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
package io.helidon.http.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadableEntityBaseTest {

    private static final byte[] BYTES = new byte[1024];

    static {
        Arrays.fill(BYTES, (byte) 'A');
    }

    @Test
    void testInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                is.transferTo(bos);
                byte[] bytes = bos.toByteArray();
                assertThat(bytes.length, is(1024));
                for (int i = 0; i < bytes.length; i++) {
                    assertThat(bytes[i], is(BYTES[i]));
                }
            }
        }
    }

    @Test
    void testMultipleInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
            assertThrows(IllegalStateException.class, entityBase::inputStream);
        }
    }

    @Test
    void testBuffer() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        entityBase.buffer();
        for (int i = 0; i < 3; i++) {
            try (InputStream is = entityBase.inputStream()) {
                assertThat(is, is(notNullValue()));
            }
        }
    }

    @Test
    void testBufferAfterInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
        }
        assertThrows(IllegalStateException.class, entityBase::buffer);
    }

    @Test
    void testMaxBufferedEntityLength() {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024 - 1);
        IllegalStateException e1 = assertThrows(IllegalStateException.class, entityBase::buffer);
        assertThat(e1.getMessage(), is("Maximum buffered entity length exceeded"));
        IllegalStateException e2 = assertThrows(IllegalStateException.class, entityBase::inputStream);
        assertThat(e2.getMessage(), is("Entity has already been requested. Entity cannot be requested multiple times"));
    }

    static class Readable implements Function<Integer, BufferData> {
        private boolean done;

        @Override
        public BufferData apply(Integer estimate) {
            if (!done) {
                done = true;
                return BufferData.create(BYTES);
            }
            return BufferData.empty();
        }
    }

    static class ReadableEntityImpl extends ReadableEntityBase {

        protected ReadableEntityImpl(Function<Integer, BufferData> readEntityFunction,
                                     int maxBufferedEntityLength) {
            super(readEntityFunction, () -> { }, maxBufferedEntityLength);
        }

        @Override
        protected <T> T entityAs(GenericType<T> type) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
