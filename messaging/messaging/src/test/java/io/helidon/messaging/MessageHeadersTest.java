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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageHeadersTest {
    @Test
    void messageHeaderRetainsValueSemantics() {
        MessageHeader first = MessageHeader.create("trace", MessageHeaderValue.IntegerValue.create(42));
        MessageHeader equal = MessageHeader.create("trace", MessageHeaderValue.IntegerValue.create(42));

        assertThat(first.name(), is("trace"));
        assertThat(first.value(), is(MessageHeaderValue.IntegerValue.create(42)));
        assertThat(first, is(equal));
        assertThat(first.hashCode(), is(equal.hashCode()));
        assertThat(first.toString(), is("MessageHeader[name=trace, value=IntegerValue[value=42]]"));
    }

    @Test
    void hasValueEquality() {
        MessageHeaders first = MessageHeaders.create(MessageHeader.create("trace", "value"));
        MessageHeaders equal = MessageHeaders.create(MessageHeader.create("trace", "value"));
        MessageHeaders different = MessageHeaders.create(MessageHeader.create("trace", "different"));

        assertThat(first, is(equal));
        assertThat(first.hashCode(), is(equal.hashCode()));
        assertThat(first.equals(different), is(false));
    }

    @Test
    void preservesGlobalOrderDuplicatesAndExactNames() {
        MessageHeaderValue.TextValue first = MessageHeaderValue.TextValue.create("first");
        MessageHeaderValue.BooleanValue middle = MessageHeaderValue.BooleanValue.create(true);
        MessageHeaderValue.BinaryValue binary = MessageHeaderValue.BinaryValue.create(new byte[] {1, 2});
        MessageHeaderValue.NullValue explicitNull = MessageHeaderValue.NullValue.create();
        List<MessageHeader> entries = List.of(
                MessageHeader.create("a", first),
                MessageHeader.create("b", middle),
                MessageHeader.create("a", binary),
                MessageHeader.create("A", "case-sensitive"),
                MessageHeader.create("a", explicitNull),
                MessageHeader.create("only-null", explicitNull));
        MessageHeaders headers = MessageHeaders.create(entries);
        MessageHeaders equal = MessageHeaders.create(entries);
        int originalHashCode = headers.hashCode();

        ArrayList<MessageHeader> iterated = new ArrayList<>();
        headers.forEach(iterated::add);
        assertThat(iterated, is(entries));
        assertThat(headers.size(), is(6));
        assertThat(headers.isEmpty(), is(false));
        assertThat(headers.contains("a"), is(true));
        assertThat(headers.contains("A"), is(true));
        assertThat(headers.contains("only-null"), is(true));
        assertThat(headers.contains("missing"), is(false));
        assertThat(headers.first("a").orElseThrow(), is(first));
        assertThat(headers.last("a").orElseThrow(), is(explicitNull));
        assertThat(headers.all("a"), is(List.of(first, binary, explicitNull)));
        assertThat(headers.all("A"), is(List.of(MessageHeaderValue.TextValue.create("case-sensitive"))));
        assertThat(headers.first("only-null").orElseThrow(), is(explicitNull));
        assertThat(headers.last("only-null").orElseThrow(), is(explicitNull));
        assertThat(headers.first("missing").isEmpty(), is(true));
        assertThat(headers.last("missing").isEmpty(), is(true));
        assertThat(headers.all("missing"), is(List.of()));

        Map<String, List<MessageHeaderValue>> grouped = headers.valuesByName();
        assertThat(new ArrayList<>(grouped.keySet()), is(List.of("a", "b", "A", "only-null")));
        assertThat(grouped.get("a"), is(List.of(first, binary, explicitNull)));
        assertThat(grouped.get("only-null"), is(List.of(explicitNull)));
        assertThat(headers.entries(), is(entries));
        assertThat(headers, is(equal));
        assertThat(equal, is(headers));
        assertThat(headers.hashCode(), is(originalHashCode));
        assertThat(headers.hashCode(), is(equal.hashCode()));
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
                     () -> headers.all("a").add(MessageHeaderValue.TextValue.create("value")));
        assertThrows(UnsupportedOperationException.class,
                     () -> headers.all("missing").add(MessageHeaderValue.TextValue.create("value")));

        Map<String, List<MessageHeaderValue>> grouped = headers.valuesByName();
        assertThat(new ArrayList<>(grouped.keySet()), is(List.of("a", "b")));
        assertThat(grouped.get("a"),
                   is(List.of(MessageHeaderValue.TextValue.create("first"),
                              MessageHeaderValue.TextValue.create("last"))));
        assertThrows(UnsupportedOperationException.class,
                     () -> grouped.put("x", List.of(MessageHeaderValue.TextValue.create("value"))));
        assertThrows(UnsupportedOperationException.class,
                     () -> grouped.get("a").add(MessageHeaderValue.TextValue.create("value")));
    }

    @Test
    void emptyHeadersHaveEmptyLookupViews() {
        MessageHeaders headers = MessageHeaders.empty();

        assertThat(headers.contains("missing"), is(false));
        assertThat(headers.first("missing").isEmpty(), is(true));
        assertThat(headers.last("missing").isEmpty(), is(true));
        assertThat(headers.all("missing"), is(List.of()));
        assertThat(headers.valuesByName(), is(Map.of()));
        assertThrows(UnsupportedOperationException.class,
                     () -> headers.valuesByName().put("new", List.of(MessageHeaderValue.NullValue.create())));
    }

    @Test
    void supportsConcurrentInitialLookups() throws Exception {
        MessageHeaderValue first = MessageHeaderValue.TextValue.create("first");
        MessageHeaderValue middle = MessageHeaderValue.BooleanValue.create(true);
        MessageHeaderValue last = MessageHeaderValue.NullValue.create();
        MessageHeaderValue upperCase = MessageHeaderValue.TextValue.create("case-sensitive");
        List<MessageHeader> entries = List.of(MessageHeader.create("a", first),
                                             MessageHeader.create("b", middle),
                                             MessageHeader.create("a", last),
                                             MessageHeader.create("A", upperCase));
        MessageHeaders headers = MessageHeaders.create(entries);
        Map<String, List<MessageHeaderValue>> expectedGroups = Map.of("a", List.of(first, last),
                                                                    "b", List.of(middle),
                                                                    "A", List.of(upperCase));
        int taskCount = 16;
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < taskCount; i++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS), is(true));
                    assertThat(headers.first("a").orElseThrow(), is(first));
                    assertThat(headers.last("a").orElseThrow(), is(last));
                    assertThat(headers.all("a"), is(List.of(first, last)));
                    assertThat(headers.contains("A"), is(true));
                    assertThat(headers.last("A").orElseThrow(), is(upperCase));
                    assertThat(headers.contains("missing"), is(false));
                    assertThat(headers.all("missing"), is(List.of()));
                    assertThat(headers.valuesByName(), is(expectedGroups));
                    assertThat(new ArrayList<>(headers.valuesByName().keySet()), is(List.of("a", "b", "A")));
                    assertThat(headers.entries(), is(entries));
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS), is(true));
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            tasks.forEach(task -> task.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
        }
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
                .set("a", MessageHeaderValue.IntegerValue.create(42));

        assertThat(builder.build().entries(),
                   is(List.of(MessageHeader.create("b", "middle"),
                              MessageHeader.create("a", MessageHeaderValue.IntegerValue.create(42)))));

        builder.remove("b");
        assertThat(builder.build().entries(),
                   is(List.of(MessageHeader.create("a", MessageHeaderValue.IntegerValue.create(42)))));
        assertThat(builder.clear().build(), is(MessageHeaders.empty()));
    }

    @Test
    void supportsStandardBuilderShapeAndCopying() {
        MessageHeaders original = MessageHeaders.builder().add("first", "value").build();

        MessageHeaders copy = MessageHeaders.builder(original)
                .update(builder -> builder.add("second", "value"))
                .get();

        assertThat(copy.entries(), is(List.of(MessageHeader.create("first", "value"),
                                              MessageHeader.create("second", "value"))));
        assertThrows(NullPointerException.class, () -> MessageHeaders.builder(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.builder().from(null));
    }

    @Test
    void failedBuilderUpdatesDoNotChangeExistingEntries() {
        MessageHeaders.Builder builder = MessageHeaders.builder().add("a", "original");

        assertThrows(NullPointerException.class, () -> builder.set("a", (String) null));
        assertThat(builder.build().entries(), is(List.of(MessageHeader.create("a", "original"))));

        assertThrows(NullPointerException.class, () -> builder.set("a", (MessageHeaderValue) null));
        assertThat(builder.build().entries(), is(List.of(MessageHeader.create("a", "original"))));
    }

    @Test
    void validatesArguments() {
        assertThrows(NullPointerException.class, () -> MessageHeader.create(null, "value"));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (String) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (MessageHeaderValue) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (byte[]) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (Boolean) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (Byte) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (Short) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (Integer) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (Long) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (BigInteger) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (BigDecimal) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (Float) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (Double) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (Instant) null));
        assertThrows(NullPointerException.class, () -> MessageHeader.create("name", (UUID) null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.create((List<MessageHeader>) null));
        assertThrows(NullPointerException.class,
                     () -> MessageHeaders.create(MessageHeader.create("a", "b"), null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.builder().add((MessageHeader) null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.builder().addAll(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.empty().contains(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.empty().first(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.empty().last(null));
        assertThrows(NullPointerException.class, () -> MessageHeaders.empty().all(null));

        MessageHeaders nonEmpty = MessageHeaders.create(MessageHeader.create("retained", "value"));
        assertThrows(NullPointerException.class, () -> nonEmpty.contains(null));
        assertThrows(NullPointerException.class, () -> nonEmpty.first(null));
        assertThrows(NullPointerException.class, () -> nonEmpty.last(null));
        assertThrows(NullPointerException.class, () -> nonEmpty.all(null));
        assertThat(nonEmpty.last("retained").orElseThrow(), is(MessageHeaderValue.TextValue.create("value")));
    }
}
