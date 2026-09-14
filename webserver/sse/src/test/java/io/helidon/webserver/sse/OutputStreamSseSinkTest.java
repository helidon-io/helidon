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

package io.helidon.webserver.sse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutputStreamSseSinkTest {
    @Test
    @SuppressWarnings("removal")
    void testFieldLineBreaksDoNotCreateNewFields() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServerResponse response = response(outputStream);
        OutputStreamSseSink sink = new OutputStreamSseSink(response,
                                                          (data, mediaType) -> fail("Plain data is serialized by the sink"),
                                                          () -> { });
        SseEvent event = SseEvent.builder()
                .comment("comment\nid:injected-comment\rdata:injected-comment\r\nevent:injected-comment")
                .id("id\nid:injected-id\rid:injected-id\r\nid:injected-id")
                .name("name\nevent:injected-event\revent:injected-event\r\nevent:injected-event")
                .data("line\revent:injected-data")
                .build();

        sink.emit(event);

        assertThat(outputStream.toString(StandardCharsets.UTF_8),
                   is(":comment\n:id:injected-comment\n:data:injected-comment\n:event:injected-comment\n"
                              + "id:id id:injected-id id:injected-id id:injected-id\n"
                              + "event:name event:injected-event event:injected-event event:injected-event\n"
                              + "data:line\ndata:event:injected-data\n\n"));
    }

    @Test
    @SuppressWarnings("removal")
    void testMediaDataUsesEventConsumer() {
        Object data = new Object();
        AtomicBoolean eventConsumerCalled = new AtomicBoolean();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServerResponse response = response(outputStream);
        OutputStreamSseSink sink = new OutputStreamSseSink(response, (actualData, actualMediaType) -> {
            eventConsumerCalled.set(true);
            assertThat(actualData, is(data));
            assertThat(actualMediaType, is(MediaTypes.APPLICATION_JSON));
            outputStream.writeBytes("{\"value\":\"custom\"}".getBytes(StandardCharsets.UTF_8));
        }, () -> { });

        sink.emit(SseEvent.create(data, MediaTypes.APPLICATION_JSON));

        assertThat(eventConsumerCalled.get(), is(true));
        assertThat(outputStream.toString(StandardCharsets.UTF_8), is("data:{\"value\":\"custom\"}\n\n"));
    }

    private static ServerResponse response(ByteArrayOutputStream outputStream) {
        ServerResponse response = mock(ServerResponse.class);
        when(response.status()).thenReturn(Status.OK_200);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());
        when(response.outputStream()).thenReturn(outputStream);
        return response;
    }
}
