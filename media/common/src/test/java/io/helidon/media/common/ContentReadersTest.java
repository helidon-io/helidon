/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.common.InputStreamHelper;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.ReactiveStreamsAdapter;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link ContentReaders}.
 */
class ContentReadersTest {
    @Test
    void testStringReader() throws Exception {
        Flux<DataChunk> flux = Flux.just(DataChunk.create(new byte[] {(byte) 225, (byte) 226, (byte) 227}));

        CompletableFuture<? extends String> future =
                ContentReaders.stringReader(Charset.forName("cp1250"))
                        .apply(ReactiveStreamsAdapter.publisherToFlow(flux))
                        .toCompletableFuture();

        String s = future.get(10, TimeUnit.SECONDS);

        assertThat(s, is("áâă"));
    }

    @Test
    void testByteArrayReader() throws Exception {
        String original = "Popokatepetl";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

        CompletableFuture<? extends byte[]> future = ContentReaders.byteArrayReader()
                .apply(ReactiveStreamsAdapter.publisherToFlow(Flux.just(DataChunk.create(bytes))))
                .toCompletableFuture();

        byte[] actualBytes = future.get(10, TimeUnit.SECONDS);

        assertThat(actualBytes, is(bytes));
    }

    @Test
    void test() throws Exception {
        String original = "Popokatepetl";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

        CompletableFuture<? extends InputStream> future = ContentReaders.inputStreamReader()
                .apply(ReactiveStreamsAdapter.publisherToFlow(Flux.just(DataChunk.create(bytes))))
                .toCompletableFuture();

        InputStream inputStream = future.get(10, TimeUnit.SECONDS);

        byte[] actualBytes = InputStreamHelper.readAllBytes(inputStream);

        assertThat(actualBytes, is(bytes));
    }
}