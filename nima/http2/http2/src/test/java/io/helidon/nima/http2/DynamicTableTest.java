/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http2;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderNames;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DynamicTableTest {
    @Test
    void testDynamicTable() {
        Http2Settings settings = Http2Settings.builder()
                .add(Http2Setting.HEADER_TABLE_SIZE, 80L)
                .build();

        Http2Headers.DynamicTable table = Http2Headers.DynamicTable.create(settings);

        assertThat(table.currentTableSize(), is(0));
        assertThat(table.maxTableSize(), is(80L));
        assertThat(table.protocolMaxTableSize(), is(80L));

        // index 1
        table.add(Http.HeaderNames.create("a"), "b");
        assertThat(table.currentTableSize(), is(34)); // 32 + name.length + value.length (on bytes)
        testRecord(table, Http2Headers.StaticHeader.MAX_INDEX + 1, "a", "b");
        // index (now index 1)
        table.add(Http.HeaderNames.create("b"), "c");

        assertThat(table.currentTableSize(), is(68));
        testRecord(table, Http2Headers.StaticHeader.MAX_INDEX + 1, "b", "c");
        testRecord(table, Http2Headers.StaticHeader.MAX_INDEX + 2, "a", "b");

        // replaces index 2 (evicts 2, creates new 2)
        table.add(HeaderNames.create("c"), "de");
        assertThat(table.currentTableSize(), is(69));
        testRecord(table, Http2Headers.StaticHeader.MAX_INDEX + 1, "c", "de");
        testRecord(table, Http2Headers.StaticHeader.MAX_INDEX + 2, "b", "c");
    }

    private void testRecord(Http2Headers.DynamicTable table,
                            int index,
                            String expectedName,
                            String expectedValue) {
        Http2Headers.HeaderRecord headerRecord = table.get(index);
        assertThat(headerRecord.headerName().lowerCase(), is(expectedName));
        assertThat(headerRecord.value(), is(expectedValue));
    }
}
