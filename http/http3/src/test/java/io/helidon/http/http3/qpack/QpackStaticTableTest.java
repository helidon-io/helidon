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

package io.helidon.http.http3.qpack;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QpackStaticTableTest {
    @Test
    void indexesEveryEntryWithFirstMatchSemantics() {
        for (int index = 0; index < QpackStaticTable.size(); index++) {
            HeaderField field = QpackStaticTable.get(index);
            int expectedExactIndex = firstExactIndex(field);
            int expectedNameIndex = firstNameIndex(field.name());

            assertThat(field.text(), QpackStaticTable.indexOf(field.name(), field.value()), is((long) expectedExactIndex));
            assertThat(field.name(), QpackStaticTable.nameIndex(field.name()), is((long) expectedNameIndex));

            QpackStaticTable.HeaderIndices indices = QpackStaticTable.indices(field.name());
            assertThat(field.text(), indices.exactIndex(field.value()), is(expectedExactIndex));
            assertThat(field.name(), indices.nameIndex(), is(expectedNameIndex));
        }
    }

    @Test
    void reportsUnknownNamesAndValuesAsAbsent() {
        assertThat(QpackStaticTable.indexOf("x-unknown", "value"), is(-1L));
        assertThat(QpackStaticTable.nameIndex("x-unknown"), is(-1L));
        assertThat(QpackStaticTable.indices("x-unknown") == null, is(true));
        assertThat(QpackStaticTable.indexOf(":method", "PATCH"), is(-1L));
        assertThat(QpackStaticTable.nameIndex(":method"), is(15L));

        QpackStaticTable.HeaderIndices indices = QpackStaticTable.indices(":method");
        assertThat(indices.exactIndex("PATCH"), is(-1));
        assertThat(indices.nameIndex(), is(15));
    }

    @Test
    void preservesNullLookupBehavior() {
        assertThrows(NullPointerException.class, () -> QpackStaticTable.indices(null));
        assertThrows(NullPointerException.class, () -> QpackStaticTable.nameIndex(null));
        assertThrows(NullPointerException.class, () -> QpackStaticTable.indexOf(null, "value"));
        assertThrows(NullPointerException.class, () -> QpackStaticTable.indexOf(":authority", null));
        assertThrows(NullPointerException.class, () -> QpackStaticTable.indexOf(":method", null));
        assertThrows(NullPointerException.class, () -> QpackStaticTable.indices(":authority").exactIndex(null));
        assertThrows(NullPointerException.class, () -> QpackStaticTable.indices(":method").exactIndex(null));
        assertThat(QpackStaticTable.indexOf("x-unknown", null), is(-1L));
    }

    @Test
    void preservesRepresentativeDuplicateNameAndExactValueIndices() {
        assertThat(QpackStaticTable.indexOf(":authority", ""), is(0L));
        assertThat(QpackStaticTable.nameIndex(":method"), is(15L));
        assertThat(QpackStaticTable.indexOf(":method", "GET"), is(17L));
        assertThat(QpackStaticTable.nameIndex(":status"), is(24L));
        assertThat(QpackStaticTable.indexOf(":status", "500"), is(71L));
        assertThat(QpackStaticTable.nameIndex("access-control-allow-headers"), is(33L));
        assertThat(QpackStaticTable.indexOf("access-control-allow-headers", "*"), is(75L));
        assertThat(QpackStaticTable.nameIndex("content-type"), is(44L));
        assertThat(QpackStaticTable.indexOf("content-type", "text/plain;charset=utf-8"), is(54L));
        assertThat(QpackStaticTable.nameIndex("x-frame-options"), is(97L));
        assertThat(QpackStaticTable.indexOf("x-frame-options", "sameorigin"), is(98L));
        assertThat(QpackStaticTable.indexOf("x-frame-options", "SAMEORIGIN"), is(-1L));
    }

    @Test
    void rejectsIndicesOutsideTheStaticTable() {
        assertThrows(IllegalArgumentException.class, () -> QpackStaticTable.get(-1));
        assertThrows(IllegalArgumentException.class, () -> QpackStaticTable.get(QpackStaticTable.size()));
    }

    private static int firstExactIndex(HeaderField expected) {
        for (int index = 0; index < QpackStaticTable.size(); index++) {
            if (QpackStaticTable.get(index).equals(expected)) {
                return index;
            }
        }
        throw new AssertionError("Static field is not indexed: " + expected);
    }

    private static int firstNameIndex(String expectedName) {
        for (int index = 0; index < QpackStaticTable.size(); index++) {
            if (QpackStaticTable.get(index).name().equals(expectedName)) {
                return index;
            }
        }
        throw new AssertionError("Static field name is not indexed: " + expectedName);
    }
}
