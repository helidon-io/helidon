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

package io.helidon.http.http3.hpack;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Iso88591Test {
    @Test
    void wrapsAppendFailure() {
        IOException cause = new IOException("test append failure");
        Appendable destination = new Appendable() {
            @Override
            public Appendable append(CharSequence value) throws IOException {
                throw cause;
            }

            @Override
            public Appendable append(CharSequence value, int start, int end) throws IOException {
                throw cause;
            }

            @Override
            public Appendable append(char value) throws IOException {
                throw cause;
            }
        };

        UncheckedIOException failure = assertThrows(
                UncheckedIOException.class,
                () -> Iso88591.Reader.create().read(BufferData.create(new byte[] {1}), destination));

        assertThat(failure.getCause(), sameInstance(cause));
    }
}
