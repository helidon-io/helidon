/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.reactive.media.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link ContentReaders}.
 */
class ContentReadersTest {
    @Test
    void readBytes() {
        String original = "Popokatepetl";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

        Single<? extends byte[]> future = ContentReaders.readBytes(Multi.singleton(DataChunk.create(bytes)));

        byte[] actualBytes = future.await(Duration.ofSeconds(10));
        assertThat(actualBytes, is(bytes));
    }

    @Test
    void readURLEncodedString() {
        String original = "myParam=\"Now@is'the/time";
        String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8);
        Multi<DataChunk> chunks = Multi.singleton(DataChunk.create(encoded.getBytes(StandardCharsets.UTF_8)));

        Single<? extends String> future =
                ContentReaders.readURLEncodedString(chunks, StandardCharsets.UTF_8);

        String s = future.await(Duration.ofSeconds(10));
        assertThat(s, is(original));
    }
}
