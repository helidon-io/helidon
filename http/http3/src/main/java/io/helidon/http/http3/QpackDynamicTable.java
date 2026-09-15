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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class QpackDynamicTable {
    private static final long ENTRY_OVERHEAD = 32L;
    private static final int COMPACTION_THRESHOLD = 64;

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Entry> nameIndex = new HashMap<>();
    private final Map<String, Map<String, Entry>> exactIndex = new HashMap<>();

    private int head;
    private long maxCapacity;
    private long capacity;
    private long tailAbsoluteIndex;
    private long insertCount;
    private long size;

    long maxCapacity() {
        return maxCapacity;
    }

    void maxCapacity(long maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("QPACK maxCapacity must be non-negative: " + maxCapacity);
        }
        this.maxCapacity = maxCapacity;
        if (capacity > maxCapacity) {
            capacity = maxCapacity;
        }
        evictToCapacity();
    }

    long capacity() {
        return capacity;
    }

    void capacity(long capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("QPACK capacity must be non-negative: " + capacity);
        }
        if (capacity > maxCapacity) {
            throw new IllegalArgumentException("QPACK capacity exceeds the configured maximum: "
                                                       + capacity + " > " + maxCapacity);
        }
        this.capacity = capacity;
        evictToCapacity();
    }

    long insertCount() {
        return insertCount;
    }

    long maxEntries() {
        return maxCapacity / ENTRY_OVERHEAD;
    }

    long findExact(String name, String value, long maxAbsoluteIndex) {
        Map<String, Entry> values = exactIndex.get(name);
        if (values == null) {
            return -1;
        }
        Entry indexed = values.get(value);
        if (indexed == null) {
            return -1;
        }
        if (indexed.absoluteIndex() <= maxAbsoluteIndex) {
            return indexed.absoluteIndex();
        }
        for (int i = entries.size() - 1; i >= head; i--) {
            Entry entry = entries.get(i);
            if (entry.absoluteIndex() > maxAbsoluteIndex) {
                continue;
            }
            if (entry.field().name().equals(name) && entry.field().value().equals(value)) {
                return entry.absoluteIndex();
            }
        }
        return -1;
    }

    long findName(String name, long maxAbsoluteIndex) {
        Entry indexed = nameIndex.get(name);
        if (indexed == null) {
            return -1;
        }
        if (indexed.absoluteIndex() <= maxAbsoluteIndex) {
            return indexed.absoluteIndex();
        }
        for (int i = entries.size() - 1; i >= head; i--) {
            Entry entry = entries.get(i);
            if (entry.absoluteIndex() > maxAbsoluteIndex) {
                continue;
            }
            if (entry.field().name().equals(name)) {
                return entry.absoluteIndex();
            }
        }
        return -1;
    }

    QpackCodec.HeaderField get(long absoluteIndex) {
        if (absoluteIndex < tailAbsoluteIndex || absoluteIndex >= insertCount) {
            throw new IllegalArgumentException("Invalid QPACK dynamic table index: " + absoluteIndex);
        }
        int offset = Math.toIntExact(absoluteIndex - tailAbsoluteIndex);
        return entries.get(head + offset).field();
    }

    QpackCodec.HeaderField relativeField(long relativeIndex) {
        long absoluteIndex = insertCount - 1 - relativeIndex;
        return get(absoluteIndex);
    }

    long insert(String name, String value) {
        return insert(name, value, Long.MAX_VALUE);
    }

    long insert(long nameIndex, boolean fromStaticTable, String value) {
        String name = fromStaticTable
                ? QpackStaticTable.get(nameIndex).name()
                : relativeField(nameIndex).name();
        return insert(name, value, Long.MAX_VALUE);
    }

    long duplicate(long relativeIndex) {
        QpackCodec.HeaderField field = relativeField(relativeIndex);
        return insert(field.name(), field.value(), Long.MAX_VALUE);
    }

    long insert(String name, String value, long protectedMinAbsoluteIndex) {
        long entrySize = headerSize(name, value);
        if (entrySize > capacity) {
            return -1;
        }
        while (head < entries.size() && size + entrySize > capacity) {
            Entry oldest = entries.get(head);
            if (oldest.absoluteIndex() >= protectedMinAbsoluteIndex) {
                return -1;
            }
            evictOldest();
        }

        long absoluteIndex = insertCount++;
        QpackCodec.HeaderField field = new QpackCodec.HeaderField(name, value);
        Entry entry = new Entry(absoluteIndex, field, entrySize);
        entries.add(entry);
        nameIndex.put(name, entry);
        exactIndex.computeIfAbsent(name, _ -> new HashMap<>()).put(value, entry);
        size += entrySize;
        return absoluteIndex;
    }

    static long headerSize(String name, String value) {
        return name.length() + value.length() + ENTRY_OVERHEAD;
    }

    private void evictToCapacity() {
        while (head < entries.size() && size > capacity) {
            evictOldest();
        }
    }

    private void evictOldest() {
        Entry removed = entries.set(head++, null);
        size -= removed.size();
        tailAbsoluteIndex = removed.absoluteIndex() + 1;
        removeFromIndexes(removed);
        if (head == entries.size()) {
            entries.clear();
            head = 0;
        } else if (head >= COMPACTION_THRESHOLD && head >= entries.size() - head) {
            entries.subList(0, head).clear();
            head = 0;
        }
    }

    private void removeFromIndexes(Entry removed) {
        QpackCodec.HeaderField field = removed.field();
        nameIndex.remove(field.name(), removed);
        Map<String, Entry> values = exactIndex.get(field.name());
        if (values != null && values.remove(field.value(), removed) && values.isEmpty()) {
            exactIndex.remove(field.name());
        }
    }

    private record Entry(long absoluteIndex, QpackCodec.HeaderField field, long size) {
    }
}
