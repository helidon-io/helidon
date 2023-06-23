/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.sse.webserver;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.sse.SseEvent;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseEventTest extends SseBaseTest {

    MediaContext mediaContext = MediaContext.create();
    MediaContext emptyMediaContext = MediaContext.builder()
            .discoverServices(false)
            .build();

    @Test
    void testNonString() {
        SseEvent event = SseEvent.builder()
                .data(new Object())     // not string
                .mediaContext(mediaContext)
                .build();
        assertThrows(IllegalStateException.class, () -> event.data(HelloWorld.class));
    }

    @Test
    void testNoMediaContext() {
        SseEvent event = SseEvent.builder()
                .data("{\"hello\":\"world\"}")
                .build();
        assertThrows(IllegalStateException.class, () -> event.data(HelloWorld.class));
    }

    @Test
    void testNoReader() {
        SseEvent event = SseEvent.builder()
                .data("{\"hello\":\"world\"}")
                .mediaContext(emptyMediaContext)
                .build();
        assertThrows(IllegalArgumentException.class, () -> event.data(Object.class));
    }

    @Test
    void testBadMediaType() {
        SseEvent event = SseEvent.builder()
                .data("{\"hello\":\"world\"}")
                .mediaContext(mediaContext)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> event.data(HelloWorld.class, MediaTypes.TEXT_YAML));
    }

    @Test
    void testNoMediaTypeArg() {
        SseEvent event = SseEvent.builder()
                .data("{\"hello\":\"world\"}")
                .mediaContext(emptyMediaContext)
                .build();
        assertThrows(IllegalArgumentException.class, () -> event.data(HelloWorld.class));
    }

    @Test
    void testGoodJsonb() {
        SseEvent event = SseEvent.builder()
                .data("{\"hello\":\"world\"}")
                .mediaContext(mediaContext)
                .build();
        HelloWorld json = event.data(HelloWorld.class, MediaTypes.APPLICATION_JSON);
        assertThat(json, is(notNullValue()));
        assertThat(json.getHello(), is("world"));
    }
}
