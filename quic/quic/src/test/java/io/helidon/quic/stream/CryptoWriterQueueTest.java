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

package io.helidon.quic.stream;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoWriterQueueTest {
    @Test
    void rejectsNullBufferBeforeChangingQueue() {
        CryptoWriterQueue queue = CryptoWriterQueue.create();

        assertThrows(NullPointerException.class, () -> queue.enqueue(null));
        assertThat(queue.remaining(), is(0));

        queue.enqueue(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        assertThat(queue.remaining(), is(3));
    }

    @Test
    void usesExplicitAbsenceForFrameProduction() {
        CryptoWriterQueue queue = CryptoWriterQueue.create();

        assertThat(queue.produceFrame(32), is(Optional.empty()));

        queue.enqueue(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        assertThat(queue.produceFrame(32).orElseThrow().length(), is(3));
        assertThat(queue.produceFrame(32), is(Optional.empty()));
    }
}
