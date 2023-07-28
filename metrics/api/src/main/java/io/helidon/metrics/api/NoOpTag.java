/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.Iterator;
import java.util.NoSuchElementException;

record NoOpTag(String key, String value) implements Tag {

    static Tag of(String key, String value) {
        return new NoOpTag(key, value);
    }

    static Iterable<Tag> tags(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("String array of keys and values must balance (have an even length");
        }
        return () -> new Iterator<>() {

            private int slot;

            @Override
            public boolean hasNext() {
                return slot < keysAndValues.length / 2;
            }

            @Override
            public Tag next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Tag result = Tag.of(keysAndValues[2 * slot], keysAndValues[2 * slot + 1]);
                slot++;
                return result;
            }
        };
    }
}
