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
package io.helidon.webserver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.media.common.MediaContext;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.common.MessageBodyReaderContext;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRequestScopedPublisherTest {
    @ParameterizedTest
    @ValueSource(classes = {String.class, byte[].class})
    void queuedAndLateChunksAreReleasedWhenMaterializationFails(Class<?> type) {
        ByteBufRequestChunk.DataChunkHoldingQueue queue = new ByteBufRequestChunk.DataChunkHoldingQueue();
        HttpRequestScopedPublisher publisher = new HttpRequestScopedPublisher(queue);
        List<ByteBuf> buffers = List.of(Unpooled.copiedBuffer("12345678", StandardCharsets.UTF_8),
                                       Unpooled.copiedBuffer("x", StandardCharsets.UTF_8),
                                       Unpooled.copiedBuffer("queued", StandardCharsets.UTF_8),
                                       Unpooled.copiedBuffer("late", StandardCharsets.UTF_8));
        // Keep chunks strongly reachable until cleanup, so GC cannot hide a missing explicit release.
        List<ByteBufRequestChunk> chunks = buffers.stream().map(buffer -> new ByteBufRequestChunk(buffer, queue)).toList();
        buffers.forEach(ByteBuf::release);
        try {
            publisher.emit(chunks.get(0));
            publisher.emit(chunks.get(1));
            publisher.emit(chunks.get(2));
            MessageBodyReaderContext context = MediaContext.create().readerContext()
                    .maxBufferedEntitySize(8,
                                          () -> new HttpException("Maximum materialized entity size exceeded",
                                                                  Http.Status.REQUEST_ENTITY_TOO_LARGE_413));
            MessageBodyReadableContent content = MessageBodyReadableContent.create(publisher, context);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> content.as(type).get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(HttpException.class));
            assertThat(((HttpException) failure.getCause()).status(), is(Http.Status.REQUEST_ENTITY_TOO_LARGE_413));

            publisher.emit(chunks.get(3));
            publisher.complete();
            publisher.clearAndRelease();
            assertAll(() -> assertThat(buffers.get(0).refCnt(), is(0)),
                      () -> assertThat(buffers.get(1).refCnt(), is(0)),
                      () -> assertThat(buffers.get(2).refCnt(), is(0)),
                      () -> assertThat(buffers.get(3).refCnt(), is(0)));
        } finally {
            chunks.forEach(ByteBufRequestChunk::release);
            queue.shutdown();
        }
    }
}
