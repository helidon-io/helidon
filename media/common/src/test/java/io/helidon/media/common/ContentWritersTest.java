/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.media.common;

import java.nio.charset.StandardCharsets;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow.Publisher;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * A test for {@link io.helidon.media.common.ContentWriters}.
 */
public class ContentWritersTest {

    @Test
    public void byteWriter() throws Exception {
        byte[] bytes = "abc".getBytes(StandardCharsets.ISO_8859_1);
        Publisher<DataChunk> publisher = ContentWriters.writeBytes(bytes, false);
        byte[] result = ContentReaders.readBytes(publisher).get(5, TimeUnit.SECONDS);
        assertThat(bytes, is(result));
    }

    @Test
    public void copyByteWriter() throws Exception {
        byte[] bytes = "abc".getBytes(StandardCharsets.ISO_8859_1);
        Publisher<DataChunk> publisher = ContentWriters.writeBytes(bytes, true);
        System.arraycopy("xxx".getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 0, bytes.length);
        byte[] result = ContentReaders.readBytes(publisher).get();
        assertThat("abc".getBytes(StandardCharsets.ISO_8859_1), is(result));
    }

    @Test
    public void byteWriterEmpty() throws Exception {
        byte[] bytes = new byte[0];
        Publisher<DataChunk> publisher = ContentWriters.writeBytes(bytes, false);
        byte[] result = ContentReaders.readBytes(publisher).get(5, TimeUnit.SECONDS);
        assertThat(result.length, is(0));
    }

    @Test
    public void charSequenceWriter() throws Exception {
        String data = "abc";
        Publisher<DataChunk> publisher = ContentWriters.charSequenceWriter(StandardCharsets.UTF_8).apply(data);
        byte[] result = ContentReaders.readBytes(publisher).get(5, TimeUnit.SECONDS);
        assertThat(new String(result, StandardCharsets.UTF_8), is(data));
    }
}
