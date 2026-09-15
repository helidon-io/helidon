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

package io.helidon.http.http2;

import io.helidon.http.HeaderName;

/**
 * Benchmark-only access to the package-private HPACK static table.
 */
public final class Http2StaticTableBenchmarkAccess {
    private Http2StaticTableBenchmarkAccess() {
    }

    /**
     * Look up an indexed entry without exposing its package-private type to JMH.
     *
     * @param index HPACK static index
     * @return existing static entry
     */
    public static Object get(int index) {
        return Http2Headers.StaticHeader.get(index);
    }

    /**
     * Perform the static part of {@code DynamicTable.findIndex}, including exact-match classification.
     *
     * @param name prebuilt header name
     * @param value header value
     * @return index plus one for an exact match, its negative for a name match, or zero for a miss
     */
    public static long find(HeaderName name, String value) {
        var entry = Http2Headers.StaticHeader.find(name, value);
        if (entry == null) {
            return 0;
        }
        if (entry.headerName().equals(name) && entry.hasValue() && entry.value().equals(value)) {
            return entry.index() + 1L;
        }
        return -(entry.index() + 1L);
    }

    /**
     * Check the independently specified input index before measurement.
     *
     * @param index HPACK static index
     * @param name expected header name
     */
    public static void verifyEntry(int index, String name) {
        var entry = Http2Headers.StaticHeader.get(index);
        if (entry == null || !entry.headerName().lowerCase().equals(name)) {
            throw new IllegalStateException("Unexpected HPACK entry at index " + index + ": " + entry
                                                    + ", expected name " + name);
        }
    }
}
