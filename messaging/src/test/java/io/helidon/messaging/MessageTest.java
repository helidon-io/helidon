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

package io.helidon.messaging;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageTest {
    @Test
    void replacesTextHeadersAndSnapshotsBuilderState() {
        Message.Builder<String> builder = Message.builder("payload")
                .header("trace", "first")
                .header("Trace", "case-sensitive")
                .header("trace", "last");

        Message<String> message = builder.build();
        builder.header("trace", "after-build");

        assertThat(message.headers().entries(),
                   is(List.of(MessageHeader.create("Trace", "case-sensitive"),
                              MessageHeader.create("trace", "last"))));
        assertThat(message.header("trace").orElseThrow(), is("last"));
        assertThat(message.header("Trace").orElseThrow(), is("case-sensitive"));
        assertThat(message.headerValue("trace").orElseThrow(), is(HeaderValue.text("last")));
    }

    @Test
    void appendsOrderedDuplicateTypedHeaders() {
        Message<String> message = Message.builder("payload")
                .addHeader("a", "first")
                .addHeader("b", HeaderValue.booleanValue(true))
                .addHeader(MessageHeader.create("a", HeaderValue.binary(new byte[] {1, 2})))
                .build();

        assertThat(message.headers().entries(),
                   is(List.of(MessageHeader.create("a", "first"),
                              MessageHeader.create("b", HeaderValue.booleanValue(true)),
                              MessageHeader.create("a", HeaderValue.binary(new byte[] {1, 2})))));
        assertThat(message.headerValue("a").orElseThrow(), is(HeaderValue.binary(new byte[] {1, 2})));
        assertThrows(IllegalStateException.class, () -> message.header("a"));
    }

    @Test
    void replacesCompleteHeaderCollection() {
        MessageHeaders headers = MessageHeaders.builder()
                .add("a", "first")
                .add("a", "last")
                .build();

        Message<String> message = Message.builder("payload")
                .header("discarded", "value")
                .headers(headers)
                .build();

        assertThat(message.headers(), is(headers));
        assertThat(message.header("a").orElseThrow(), is("last"));
    }

    @Test
    void failedHeaderCollectionReplacementDoesNotChangeBuilder() {
        Message.Builder<String> builder = Message.builder("payload").header("a", "original");

        assertThrows(NullPointerException.class, () -> builder.headers(null));

        assertThat(builder.build().headers().entries(), is(List.of(MessageHeader.create("a", "original"))));
    }
}
