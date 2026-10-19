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

package io.helidon.webclient.sse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.helidon.http.media.MediaContext;
import io.helidon.http.sse.SseEvent;
import io.helidon.webclient.api.HttpClientResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SseSourceHandlerProviderTest {

    @Test
    void retryOnlyFollowedByData() {
        List<SseEvent> events = parse("retry:2000\n\ndata:next\n\n");

        assertThat(events.size(), is(2));
        assertThat(events.get(1).data(), is("next"));
        assertThat(events.getFirst().reconnectMillis().orElseThrow(), is(Duration.ofMillis(2000)));
        assertThat(events.getFirst().data(), sameInstance(SseEvent.NO_DATA));
        assertThrows(IllegalStateException.class, () -> events.getFirst().data(String.class));
    }

    @Test
    void retryAndDataInSameBlock() {
        List<SseEvent> events = parse("retry:2000\ndata:first\n\ndata:second\nretry:3000\n\n");

        assertThat(events.size(), is(2));
        assertThat(events.getFirst().data(), is("first"));
        assertThat(events.getFirst().reconnectMillis().orElseThrow(), is(Duration.ofMillis(2000)));
        assertThat(events.get(1).data(), is("second"));
        assertThat(events.get(1).reconnectMillis().orElseThrow(), is(Duration.ofMillis(3000)));
    }

    @Test
    void retryOnlyFollowedByEmptyData() {
        List<SseEvent> events = parse("retry:2000\n\ndata:\n\n");

        assertThat(events.size(), is(2));
        assertThat(events.getFirst().data(), sameInstance(SseEvent.NO_DATA));
        assertThat(events.getFirst().reconnectMillis().orElseThrow(), is(Duration.ofMillis(2000)));
        assertThat(events.get(1).data(), is(""));
        assertThat(events.get(1).data(String.class), is(""));
    }

    @Test
    void retryWithEmptyData() {
        List<SseEvent> events = parse("data:\nretry:2000\n\n");

        assertThat(events.size(), is(1));
        assertThat(events.getFirst().data(), is(""));
        assertThat(events.getFirst().reconnectMillis().orElseThrow(), is(Duration.ofMillis(2000)));
    }

    @Test
    void emptyDataLinesArePreserved() {
        List<SseEvent> events = parse("data:\ndata:next\ndata:\n\n");

        assertThat(events.size(), is(1));
        assertThat(events.getFirst().data(), is("\nnext\n"));
    }

    @Test
    void metadataOnlyCallbacksHaveNoData() {
        List<SseEvent> events = parse(":comment\n\nid:123\n\nevent:notice\n\n");

        assertThat(events.size(), is(3));
        assertThat(events.getFirst().comment().orElseThrow(), is("comment"));
        assertThat(events.get(1).id().orElseThrow(), is("123"));
        assertThat(events.get(2).name().orElseThrow(), is("notice"));
        for (SseEvent event : events) {
            assertThat(event.data(), sameInstance(SseEvent.NO_DATA));
        }
    }

    private static List<SseEvent> parse(String stream) {
        HttpClientResponse response = mock(HttpClientResponse.class);
        when(response.inputStream()).thenReturn(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)));
        List<SseEvent> events = new ArrayList<>();
        SseSource source = events::add;

        new SseSourceHandlerProvider().handle(source, response, MediaContext.create());
        return events;
    }
}
