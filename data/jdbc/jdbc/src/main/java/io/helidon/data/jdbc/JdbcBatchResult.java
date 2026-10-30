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
package io.helidon.data.jdbc;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Normalizes attributable JDBC evidence without retaining driver arrays.
 */
final class JdbcBatchResult implements JdbcClient.BatchResult {
    private final List<Entry> entries;

    private JdbcBatchResult(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    static JdbcBatchResult capture(int size, int[] counts) {
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int count = counts != null && i < counts.length ? counts[i] : -1;
            Status status = count >= 0 ? Status.EXACT_COUNT : switch (count) {
                case Statement.SUCCESS_NO_INFO -> Status.SUCCESS_NO_INFO;
                case Statement.EXECUTE_FAILED -> Status.EXECUTE_FAILED;
                default -> Status.NOT_REPORTED;
            };
            entries.add(new Item(i, status, count >= 0 ? OptionalLong.of(count) : OptionalLong.empty()));
        }
        return new JdbcBatchResult(entries);
    }

    @Override
    public List<Entry> entries() {
        return entries;
    }

    @Override
    public OptionalLong totalUpdateCount() {
        long total = 0;
        for (Entry entry : entries) {
            if (entry.updateCount().isEmpty()) {
                return OptionalLong.empty();
            }
            try {
                total = Math.addExact(total, entry.updateCount().getAsLong());
            } catch (ArithmeticException _) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.of(total);
    }

    private record Item(int itemIndex, Status status, OptionalLong updateCount) implements Entry {
    }
}
