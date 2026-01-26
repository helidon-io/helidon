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

package io.helidon.jersey.webserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.Bytes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BufferingOutputStreamTest {
    @Test
    public void testBufferingOutputStreamFlush() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AtomicReference<Integer> contentLength = new AtomicReference<>();

        JaxRsService.BufferingOutputStream o = new JaxRsService.BufferingOutputStream(() -> baos,
                                                                                      contentLength::set,
                                                                                      10);
        // write byte
        // 1
        o.write(Bytes.COLON_BYTE);
        assertThat(baos.size(), is(0));
        byte[] array = new byte[2];
        array[0] = Bytes.COLON_BYTE;
        array[1] = Bytes.SPACE_BYTE;
        // 3
        o.write(array);
        assertThat(baos.size(), is(0));
        // 4
        o.write(array, 1, 1);
        assertThat(baos.size(), is(0));
        o.flush();
        assertThat(baos.size(), is(4));
        o.write(Bytes.SEMICOLON_BYTE);
        assertThat(baos.size(), is(4));
        o.flush();
        o.close();

        assertThat(baos.toString(), is("::  ;"));
        assertThat(contentLength.get(), nullValue());
    }
    @Test
    public void testBufferingOutputStreamBuffered() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AtomicInteger contentLength = new AtomicInteger();

        JaxRsService.BufferingOutputStream o = new JaxRsService.BufferingOutputStream(() -> baos,
                                                                                      contentLength::set,
                                                                                      10);
        // write byte
        // 1
        o.write(Bytes.COLON_BYTE);
        assertThat(baos.size(), is(0));
        byte[] array = new byte[2];
        array[0] = Bytes.COLON_BYTE;
        array[1] = Bytes.SPACE_BYTE;
        // 3
        o.write(array);
        assertThat(baos.size(), is(0));
        // 4
        o.write(array, 1, 1);
        assertThat(baos.size(), is(0));
        o.write(Bytes.SEMICOLON_BYTE);
        assertThat(baos.size(), is(0));
        o.close();

        assertThat(baos.toString(), is("::  ;"));
        assertThat(contentLength.get(), is(5));
    }
}
