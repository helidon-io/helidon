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

package io.helidon.http.http3;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QpackDynamicTableTest {
    @Test
    void indexesEntriesByAbsoluteAndRelativePosition() {
        QpackDynamicTable table = tableWithCapacity(256);

        assertThat(table.insert("first", "one"), is(0L));
        assertThat(table.insert("second", "two"), is(1L));
        assertThat(table.insert("third", "three"), is(2L));

        assertThat(table.get(0), equalTo(new QpackCodec.HeaderField("first", "one")));
        assertThat(table.get(1), equalTo(new QpackCodec.HeaderField("second", "two")));
        assertThat(table.get(2), equalTo(new QpackCodec.HeaderField("third", "three")));
        assertThat(table.relativeField(0), equalTo(new QpackCodec.HeaderField("third", "three")));
        assertThat(table.relativeField(2), equalTo(new QpackCodec.HeaderField("first", "one")));
        assertThat(table.findExact("second", "two", 2), is(1L));
        assertThat(table.findName("third", 2), is(2L));
    }

    @Test
    void findsNewestEligibleDuplicateWithinReferenceLimit() {
        QpackDynamicTable table = tableWithCapacity(256);

        assertThat(table.insert("name", "value"), is(0L));
        assertThat(table.insert("name", "other"), is(1L));
        assertThat(table.duplicate(1), is(2L));
        assertThat(table.insert("name", "latest"), is(3L));

        assertThat(table.findExact("name", "value", 3), is(2L));
        assertThat(table.findExact("name", "value", 1), is(0L));
        assertThat(table.findExact("name", "value", -1), is(-1L));
        assertThat(table.findName("name", 3), is(3L));
        assertThat(table.findName("name", 2), is(2L));
        assertThat(table.findName("name", 1), is(1L));
        assertThat(table.findName("name", -1), is(-1L));
    }

    @Test
    void evictsOldestEntryWithoutChangingAbsoluteIndexes() {
        QpackDynamicTable table = tableWithCapacity(68);
        assertThat(table.insert("a", "1"), is(0L));
        assertThat(table.insert("b", "2"), is(1L));

        assertThat(table.insert("c", "3"), is(2L));

        assertThrows(IllegalArgumentException.class, () -> table.get(0));
        assertThat(table.get(1), equalTo(new QpackCodec.HeaderField("b", "2")));
        assertThat(table.get(2), equalTo(new QpackCodec.HeaderField("c", "3")));
        assertThat(table.relativeField(1), equalTo(new QpackCodec.HeaderField("b", "2")));
    }

    @Test
    void capacityShrinkEvictsOldestEntries() {
        QpackDynamicTable table = tableWithCapacity(136);
        assertThat(table.insert("a", "1"), is(0L));
        assertThat(table.insert("b", "2"), is(1L));
        assertThat(table.insert("c", "3"), is(2L));
        assertThat(table.insert("d", "4"), is(3L));

        table.capacity(68);

        assertThrows(IllegalArgumentException.class, () -> table.get(0));
        assertThrows(IllegalArgumentException.class, () -> table.get(1));
        assertThat(table.get(2), equalTo(new QpackCodec.HeaderField("c", "3")));
        assertThat(table.get(3), equalTo(new QpackCodec.HeaderField("d", "4")));
        assertThat(table.findExact("a", "1", Long.MAX_VALUE), is(-1L));
        assertThat(table.findName("b", Long.MAX_VALUE), is(-1L));
        assertThat(table.findExact("c", "3", Long.MAX_VALUE), is(2L));
        assertThat(table.findName("d", Long.MAX_VALUE), is(3L));

        table.capacity(0);

        assertThat(table.findExact("c", "3", Long.MAX_VALUE), is(-1L));
        assertThat(table.findName("d", Long.MAX_VALUE), is(-1L));
    }

    @Test
    void evictionKeepsNewestDuplicateIndexed() {
        QpackDynamicTable table = tableWithCapacity(72);
        assertThat(table.insert("n", "v"), is(0L));
        assertThat(table.insert("n", "v"), is(1L));

        assertThat(table.insert("other", "x"), is(2L));

        assertThrows(IllegalArgumentException.class, () -> table.get(0));
        assertThat(table.findExact("n", "v", Long.MAX_VALUE), is(1L));
        assertThat(table.findName("n", Long.MAX_VALUE), is(1L));

        table.capacity(38);

        assertThat(table.findExact("n", "v", Long.MAX_VALUE), is(-1L));
        assertThat(table.findName("n", Long.MAX_VALUE), is(-1L));
        assertThat(table.findExact("other", "x", Long.MAX_VALUE), is(2L));
    }

    @Test
    void protectedEntryPreventsInsertion() {
        QpackDynamicTable table = tableWithCapacity(68);
        assertThat(table.insert("a", "1"), is(0L));
        assertThat(table.insert("b", "2"), is(1L));

        assertThat(table.insert("c", "3", 0), is(-1L));

        assertThat(table.insertCount(), is(2L));
        assertThat(table.get(0), equalTo(new QpackCodec.HeaderField("a", "1")));
        assertThat(table.get(1), equalTo(new QpackCodec.HeaderField("b", "2")));
        assertThat(table.findExact("a", "1", Long.MAX_VALUE), is(0L));
        assertThat(table.findName("b", Long.MAX_VALUE), is(1L));
        assertThat(table.findExact("c", "3", Long.MAX_VALUE), is(-1L));
        assertThat(table.findName("c", Long.MAX_VALUE), is(-1L));
    }

    @Test
    void repeatedEvictionAndCompactionPreserveOffsets() {
        QpackDynamicTable table = tableWithCapacity(144);

        for (int i = 0; i < 512; i++) {
            String value = Integer.toString(i);
            assertThat(table.insert("n", value), is((long) i));
            assertThat(table.findExact("n", value, Long.MAX_VALUE), is((long) i));
            assertThat(table.findName("n", Long.MAX_VALUE), is((long) i));

            int oldest = Math.max(0, i - 3);
            for (int absoluteIndex = oldest; absoluteIndex <= i; absoluteIndex++) {
                assertThat(table.get(absoluteIndex),
                           equalTo(new QpackCodec.HeaderField("n", Integer.toString(absoluteIndex))));
                assertThat(table.relativeField(i - absoluteIndex),
                           equalTo(new QpackCodec.HeaderField("n", Integer.toString(absoluteIndex))));
            }
            if (oldest > 0) {
                assertThrows(IllegalArgumentException.class, () -> table.get(oldest - 1));
            }
        }
    }

    private static QpackDynamicTable tableWithCapacity(long capacity) {
        QpackDynamicTable table = new QpackDynamicTable();
        table.maxCapacity(capacity);
        table.capacity(capacity);
        return table;
    }
}
