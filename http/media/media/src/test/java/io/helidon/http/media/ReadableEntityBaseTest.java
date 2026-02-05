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

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadableEntityBaseTest {

    @Test
    void testInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), () -> {});
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
        }
    }

    @Test
    void testMultipleInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), () -> {});
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
            assertThrows(IllegalStateException.class, entityBase::inputStream);
        }
    }

    @Test
    void testBuffer() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), () -> {});
        entityBase.buffer();
        for (int i = 0; i < 3; i++) {
            try (InputStream is = entityBase.inputStream()) {
                assertThat(is, is(notNullValue()));
            }
        }
    }

    @Test
    void testBufferAfterInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), () -> {});
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
        }
        assertThrows(IllegalStateException.class, entityBase::buffer);
    }

    static class Readable implements Function<Integer, BufferData> {
        private boolean done;

        @Override
        public BufferData apply(Integer estimate) {
            if (!done) {
                done = true;
                return BufferData.create("some data");
            }
            return BufferData.empty();
        }
    }

    static class ReadableEntityImpl extends ReadableEntityBase {

        protected ReadableEntityImpl(Function<Integer, BufferData> readEntityFunction,
                                     Runnable entityProcessedRunnable) {
            super(readEntityFunction, entityProcessedRunnable);
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
