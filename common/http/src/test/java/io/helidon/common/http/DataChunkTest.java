/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link DataChunk}.
 */
class DataChunkTest {
    @Test
    public void testSimpleWrapping() {
        byte[] bytes = "urzatron".getBytes(StandardCharsets.UTF_8);

        DataChunk chunk = DataChunk.create(bytes);

        assertThat(chunk.bytes(), is(bytes));
        assertThat(chunk.flush(), is(false));
        assertThat(chunk.id(), not(0L));
        assertThat(chunk.isReleased(), is(false));
        assertThat(chunk.data()[0].array(), is(bytes));
    }

    @Test
    public void testReleasing() {
        byte[] bytes = "urzatron".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean ab = new AtomicBoolean(false);

        DataChunk chunk = DataChunk.create(true, () -> ab.set(true), ByteBuffer.wrap(bytes));

        assertThat(chunk.bytes(), is(bytes));
        assertThat(chunk.flush(), is(true));
        assertThat(chunk.id(), not(0L));
        assertThat(chunk.data()[0].array(), is(bytes));
        assertThat(chunk.isReleased(), is(false));
        assertThat(ab.get(), is(false));
        chunk.release();
        assertThat(chunk.isReleased(), is(true));
        assertThat(ab.get(), is(true));
    }

    @Test
    public void testReleasingNoRunnable() {
        byte[] bytes = "urzatron".getBytes(StandardCharsets.UTF_8);
        DataChunk chunk = DataChunk.create(true, ByteBuffer.wrap(bytes));

        assertThat(chunk.bytes(), is(bytes));
        assertThat(chunk.flush(), is(true));
        assertThat(chunk.id(), not(0L));
        assertThat(chunk.data()[0].array(), is(bytes));
        assertThat(chunk.isReleased(), is(false));
        chunk.release();
        assertThat(chunk.isReleased(), is(true));
    }
}