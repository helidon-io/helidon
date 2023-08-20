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
import java.util.Set;

class Util {

    private Util() {
    }

    static <T> Iterable<T> iterable(T[] items) {
        if (items == null || items.length == 0) {
            return Set.of();
        }
        return () -> new Iterator<>() {

            private int slot;

            @Override
            public boolean hasNext() {
                return slot < items.length;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return items[slot++];
            }
        };
    }

    static Iterable<Double> iterable(double[] items) {
        if (items == null || items.length == 0) {
            return Set.of();
        }
        return () -> new Iterator<>() {

            private int slot;

            @Override
            public boolean hasNext() {
                return slot < items.length;
            }

            @Override
            public Double next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return items[slot++];
            }
        };
    }
}
