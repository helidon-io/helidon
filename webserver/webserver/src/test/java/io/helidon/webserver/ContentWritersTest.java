/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;
import io.helidon.webserver.utils.CollectingSubscriber;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A test for {@link ContentWriters}
 */
public class ContentWritersTest {

    @Test
    public void byteWriter() throws Exception {
        Function<byte[], Flow.Publisher<DataChunk>> f = ContentWriters.byteArrayWriter(false);
        byte[] bytes = "abc".getBytes(StandardCharsets.ISO_8859_1);
        Flow.Publisher<DataChunk> publisher = f.apply(bytes);
        CollectingSubscriber subscriber = new CollectingSubscriber();
        subscriber.subscribeOn(publisher);
        byte[] result = subscriber.result().get();
        assertThat(bytes, Is.is(result));
    }

    @Test
    public void copyByteWriter() throws Exception {
        Function<byte[], Flow.Publisher<DataChunk>> f = ContentWriters.byteArrayWriter(true);
        byte[] bytes = "abc".getBytes(StandardCharsets.ISO_8859_1);
        Flow.Publisher<DataChunk> publisher = f.apply(bytes);
        System.arraycopy("xxx".getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 0, bytes.length);
        CollectingSubscriber subscriber = new CollectingSubscriber();
        subscriber.subscribeOn(publisher);
        byte[] result = subscriber.result().get();
        assertThat("abc".getBytes(StandardCharsets.ISO_8859_1), Is.is(result));
    }

    @Test
    public void byteWriterEmpty() throws Exception {
        Function<byte[], Flow.Publisher<DataChunk>> f = ContentWriters.byteArrayWriter(false);
        byte[] bytes = new byte[0];
        Flow.Publisher<DataChunk> publisher = f.apply(bytes);
        CollectingSubscriber subscriber = new CollectingSubscriber();
        subscriber.subscribeOn(publisher);
        byte[] result = subscriber.result().get();
        assertEquals(0, result.length);
    }

    @Test
    public void charSequenceWriter() throws Exception {
        Function<CharSequence, Flow.Publisher<DataChunk>> f = ContentWriters.charSequenceWriter(StandardCharsets.UTF_8);
        String data = "abc";
        Flow.Publisher<DataChunk> publisher = f.apply(data);
        CollectingSubscriber subscriber = new CollectingSubscriber();
        subscriber.subscribeOn(publisher);
        byte[] result = subscriber.result().get();
        assertEquals(data, new String(result, StandardCharsets.UTF_8));
    }
}
