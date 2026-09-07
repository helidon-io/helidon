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

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

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
        AtomicInteger arrayWrites = new AtomicInteger();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BufferData customBuffer = (BufferData) Proxy.newProxyInstance(BufferData.class.getClassLoader(),
                                                                       new Class<?>[] {BufferData.class},
                                                                       (proxy, _, arguments) -> {
                                                                           Object value = arguments[0];
                                                                           if (value instanceof byte[] bytes) {
                                                                               arrayWrites.incrementAndGet();
                                                                               output.writeBytes(bytes);
                                                                               bytes[0] = '\r';
                                                                               return null;
                                                                           }
                                                                           output.write((Integer) value);
                                                                           return proxy;
                                                                       });

        HeaderValues.create(HeaderNames.CONTENT_LENGTH, "128").writeHttp1Header(customBuffer);
        HeaderValues.CONTENT_TYPE_JSON.writeHttp1Header(customBuffer);

        assertThat(arrayWrites.get(), is(1));
        assertThat(output.toString(StandardCharsets.US_ASCII),
                   is("Content-Length: 128\r\nContent-Type: application/json\r\n"));
        assertSerialized(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "128"), "Content-Length: 128\r\n");
        assertSerialized(HeaderValues.CONTENT_TYPE_JSON, "Content-Type: application/json\r\n");
    }

    @Test
    void freshNameBytesUseBulkWriteToCustomBuffer() {
        AtomicInteger arrayWrites = new AtomicInteger();
        AtomicInteger scalarWrites = new AtomicInteger();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BufferData customBuffer = (BufferData) Proxy.newProxyInstance(BufferData.class.getClassLoader(),
                                                                       new Class<?>[] {BufferData.class},
                                                                       (proxy, _, arguments) -> {
                                                                           Object value = arguments[0];
                                                                           if (value instanceof byte[] bytes) {
                                                                               arrayWrites.incrementAndGet();
                                                                               output.writeBytes(bytes);
                                                                               return null;
                                                                           }
                                                                           scalarWrites.incrementAndGet();
                                                                           output.write((Integer) value);
                                                                           return proxy;
                                                                       });

        HeaderValues.create("X-Custom", "v").writeHttp1Header(customBuffer);

        assertThat(arrayWrites.get(), is(2));
        assertThat(scalarWrites.get(), is(4));
        assertThat(output.toString(StandardCharsets.US_ASCII), is("X-Custom: v\r\n"));
    }

    private static void assertSerialized(Header header, String expected) {
        BufferData buffer = BufferData.growing(128);
        header.writeHttp1Header(buffer);
        assertThat(buffer.readString(buffer.available(), StandardCharsets.US_ASCII), is(expected));
    }
}
