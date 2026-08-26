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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageHeadersTest {
    @Test
    void preservesGlobalOrderDuplicatesAndExactNames() {
        HeaderValue.TextValue first = HeaderValue.text("first");
        HeaderValue.BooleanValue middle = HeaderValue.booleanValue(true);
        HeaderValue.BinaryValue last = HeaderValue.binary(new byte[] {1, 2});
        MessageHeaders headers = MessageHeaders.create(
                MessageHeader.create("a", first),
                MessageHeader.create("b", middle),
                MessageHeader.create("a", last),
                MessageHeader.create("A", "case-sensitive"));

        assertThat(headers.entries(), is(List.of(MessageHeader.create("a", first),
                                                MessageHeader.create("b", middle),
                                                MessageHeader.create("a", last),
                                                MessageHeader.create("A", "case-sensitive"))));
        ArrayList<MessageHeader> iterated = new ArrayList<>();
        headers.forEach(iterated::add);
        assertThat(iterated, is(headers.entries()));
        assertThat(headers.size(), is(4));
        assertThat(headers.isEmpty(), is(false));
        assertThat(headers.contains("a"), is(true));
        assertThat(headers.contains("A"), is(true));
        assertThat(headers.contains("missing"), is(false));
        assertThat(headers.first("a").orElseThrow(), is(first));
        assertThat(headers.last("a").orElseThrow(), is(last));
        assertThat(headers.all("a"), is(List.of(first, last)));
        assertThat(headers.all("A"), is(List.of(HeaderValue.text("case-sensitive"))));
        assertThat(headers.first("missing").isEmpty(), is(true));
        assertThat(headers.last("missing").isEmpty(), is(true));
    }

    @Test
    void exposesOnlyImmutableSnapshotsAndViews() {
        MessageHeaders.Builder builder = MessageHeaders.builder()
                .add("a", "first")
                .add("b", "middle")
                .add("a", "last");
        MessageHeaders headers = builder.build();
        builder.add("later", "builder-change");

        assertThat(headers.size(), is(3));
        assertThrows(UnsupportedOperationException.class,
                     () -> headers.entries().add(MessageHeader.create("x", "value")));
        assertThrows(UnsupportedOperationException.class,
                     () -> headers.all("a").add(HeaderValue.text("value")));

        Map<String, List<HeaderValue>> grouped = headers.valuesByName();
        assertThat(new ArrayList<>(grouped.keySet()), is(List.of("a", "b")));
        assertThat(grouped.get("a"), is(List.of(HeaderValue.text("first"), HeaderValue.text("last"))));
        assertThrows(UnsupportedOperationException.class,
                     () -> grouped.put("x", List.of(HeaderValue.text("value"))));
        assertThrows(UnsupportedOperationException.class,
                     () -> grouped.get("a").add(HeaderValue.text("value")));
    }

    @Test
    void snapshotsListAndVarargsSources() {
        ArrayList<MessageHeader> sourceList = new ArrayList<>(List.of(MessageHeader.create("list", "value")));
        MessageHeaders fromList = MessageHeaders.create(sourceList);
        sourceList.add(MessageHeader.create("later", "list-change"));

        MessageHeader[] sourceArray = {MessageHeader.create("array", "value")};
        MessageHeaders fromArray = MessageHeaders.create(sourceArray);
        sourceArray[0] = MessageHeader.create("later", "array-change");

        assertThat(fromList.entries(), is(List.of(MessageHeader.create("list", "value"))));
        assertThat(fromArray.entries(), is(List.of(MessageHeader.create("array", "value"))));
    }

    @Test
    void builderDistinguishesAppendReplacementAndClear() {
        MessageHeaders.Builder builder = MessageHeaders.builder()
                .add("a", "first")
                .add("b", "middle")
                .add("a", "last")
                .set("a", HeaderValue.integer(42));

        assertThat(builder.build().entries(),
                   is(List.of(MessageHeader.create("b", "middle"),
                              MessageHeader.create("a", HeaderValue.integer(42)))));

        builder.remove("b");
        assertThat(builder.build().entries(),
                   is(List.of(MessageHeader.create("a", HeaderValue.integer(42)))));
        assertThat(builder.clear().build(), is(MessageHeaders.empty()));
    }

    @Test
    void failedBuilderUpdatesDoNotChangeExistingEntries() {
        MessageHeaders.Builder builder = MessageHeaders.builder().add("a", "original");

        assertThrows(NullPointerException.class, () -> builder.set("a", (String) null));
        assertThat(builder.build().entries(), is(List.of(MessageHeader.create("a", "original"))));

        assertThrows(NullPointerException.class, () -> builder.set("a", (HeaderValue) null));
        assertThat(builder.build().entries(), is(List.of(MessageHeader.create("a", "original"))));
    }

    @Test
    void validatesArguments() {
        assertThrows(NullPointerException.class, () -> MessageHeader.create(null, "value"));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (String) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (HeaderValue) null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.create((List<MessageHeader>) null));
        assertThrows(NullPointerException.class,
                     () -> MessageHeaders.create(MessageHeader.create("a", "b"), null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.builder().add((MessageHeader) null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.builder().addAll(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.empty().contains(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.empty().first(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.empty().last(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.empty().all(null));
    }
}
