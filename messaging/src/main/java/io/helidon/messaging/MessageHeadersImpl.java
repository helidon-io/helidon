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
import java.util.Objects;

final class MessageHeadersImpl implements MessageHeaders {
    private static final MessageHeaders EMPTY = new MessageHeadersImpl(List.of());

    private final List<MessageHeader> entries;

    private MessageHeadersImpl(List<? extends MessageHeader> entries) {
        this.entries = List.copyOf(entries);
    }

    static MessageHeaders empty() {
        return EMPTY;
    }

    static MessageHeaders create(List<? extends MessageHeader> entries) {
        List<? extends MessageHeader> actualEntries = Objects.requireNonNull(entries);
        return actualEntries.isEmpty() ? EMPTY : new MessageHeadersImpl(actualEntries);
    }

    @Override
    public List<MessageHeader> entries() {
        return entries;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof MessageHeadersImpl that && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return entries.toString();
    }
}
